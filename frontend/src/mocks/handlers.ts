import { delay, http, HttpResponse } from 'msw'
import type { AccountStatus, FraudAlertStatus, PageResponse, Role, TransactionStatus } from '../types/api'
import { STAFF_ROLES } from '../types/api'
import { bankDayKey, bankDayStart } from '../utils/format'
import { accountByNumber, clearDb, customerOfUser, findAccount, findCustomer, findUser, loadDb, saveDb, type DB, type MUser } from './db'
import { audit, errorBody, executeTransfer, MockError, notify, openAccount, reviewAlert, setAccountStatus, updateLimits, type Ctx } from './engine'
import type { ErrorCodeName } from './errorCodes'
import { seed } from './seed'
import * as v from './views'

const BASE = `${import.meta.env.BASE_URL}api/v1`
const ACCESS_TTL = 15 * 60
const REFRESH_TTL = 7 * 24 * 3600

let db: DB = loadDb() ?? persistFresh()

function persistFresh(): DB {
  const fresh = seed(new Date())
  saveDb(fresh)
  return fresh
}

export function resetMockData() {
  clearDb()
  db = persistFresh()
}

// ── Request plumbing ────────────────────────────────────────────────────────
type Req = {
  request: Request
  params: Record<string, string>
  url: URL
  path: string
  ctx: Ctx
  user: MUser | null
}

const CORRELATION = /^[A-Za-z0-9._-]{8,64}$/

function makeCtx(request: Request): Ctx {
  const header = request.headers.get('X-Correlation-Id')
  return {
    now: new Date(),
    uuid: () => crypto.randomUUID(),
    correlationId: header && CORRELATION.test(header) ? header : `gw-${crypto.randomUUID()}`,
    ip: '113.161.77.24',
  }
}

function json(req: Req, body: unknown, status = 200, extra: Record<string, string> = {}) {
  return HttpResponse.json(body as never, { status, headers: { 'X-Correlation-Id': req.ctx.correlationId ?? '', ...extra } })
}

function fail(req: Req, code: ErrorCodeName, fieldErrors?: { field: string; message: string }[]) {
  const body = errorBody(code, req.path, req.ctx, fieldErrors)
  return json(req, body, body.status)
}

function noContent(req: Req) {
  return new HttpResponse(null, { status: 204, headers: { 'X-Correlation-Id': req.ctx.correlationId ?? '' } })
}

type Access = 'public' | 'any' | Role[]
type Resolver = (req: Req) => Response | Promise<Response>

function route(method: 'get' | 'post' | 'put' | 'patch', path: string, access: Access, resolve: Resolver, mutates = false) {
  return http[method](`${BASE}${path}`, async ({ request, params }) => {
    await delay(mutates ? 380 + Math.random() * 320 : 140 + Math.random() * 220)
    const url = new URL(request.url)
    const req: Req = {
      request,
      params: params as Record<string, string>,
      url,
      path: url.pathname.replace(import.meta.env.BASE_URL.replace(/\/$/, ''), ''),
      ctx: makeCtx(request),
      user: null,
    }
    if (access !== 'public') {
      const auth = authenticate(request)
      if ('error' in auth) return fail(req, auth.error)
      req.user = auth.user
      if (Array.isArray(access) && !auth.user.roles.some((r) => access.includes(r))) return fail(req, 'FORBIDDEN_OPERATION')
    }
    try {
      const res = await resolve(req)
      if (mutates) saveDb(db)
      return res
    } catch (e) {
      if (e instanceof MockError) {
        if (mutates) saveDb(db)
        return fail(req, e.code, e.fieldErrors)
      }
      console.error('[mock api]', e)
      return fail(req, 'INTERNAL_ERROR')
    }
  })
}

// In-flight bookkeeping: the mock answers after a delay, so overlapping requests really overlap.
const inFlightKeys = new Set<string>()
const lockedAccounts = new Set<string>()
const editing = new Set<string>()

/** Runs `fn` while holding `resource`; an overlapping write to the same resource gets 409 CONCURRENT_UPDATE. */
async function exclusive<T>(resource: string, fn: () => Promise<T>): Promise<T> {
  if (editing.has(resource)) throw new MockError('CONCURRENT_UPDATE')
  editing.add(resource)
  try {
    return await fn()
  } finally {
    editing.delete(resource)
  }
}

async function body(req: Req): Promise<Record<string, unknown>> {
  try {
    const b = await req.request.json()
    if (typeof b !== 'object' || b === null) throw new MockError('MALFORMED_REQUEST')
    return b as Record<string, unknown>
  } catch (e) {
    if (e instanceof MockError) throw e
    throw new MockError('MALFORMED_REQUEST')
  }
}

const q = (req: Req, name: string) => req.url.searchParams.get(name) ?? undefined
const qNum = (req: Req, name: string) => {
  const raw = q(req, name)
  if (raw === undefined || raw === '') return undefined
  const n = Number(raw)
  return Number.isFinite(n) ? n : undefined
}

function paginate<T>(req: Req, items: T[]): PageResponse<T> {
  const page = Math.max(0, Math.floor(qNum(req, 'page') ?? 0))
  const size = Math.min(100, Math.max(1, Math.floor(qNum(req, 'size') ?? 20)))
  return {
    content: items.slice(page * size, page * size + size),
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
  }
}

function sortBy<T>(items: T[], sort: string | undefined, fallback = 'createdAt,desc'): T[] {
  const [field, dir] = (sort || fallback).split(',')
  const sign = dir === 'asc' ? 1 : -1
  return [...items].sort((a, b) => {
    const x = (a as Record<string, unknown>)[field]
    const y = (b as Record<string, unknown>)[field]
    if (typeof x === 'number' && typeof y === 'number') return (x - y) * sign
    return String(x ?? '').localeCompare(String(y ?? '')) * sign
  })
}

function inDayRange(iso: string, fromDate?: string, toDate?: string) {
  const t = new Date(iso).getTime()
  if (fromDate && /^\d{4}-\d{2}-\d{2}$/.test(fromDate) && t < bankDayStart(fromDate).getTime()) return false
  if (toDate && /^\d{4}-\d{2}-\d{2}$/.test(toDate) && t >= bankDayStart(toDate).getTime() + 86_400_000) return false
  return true
}

// ── Fake JWTs ───────────────────────────────────────────────────────────────
const b64url = (s: string) => btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
const unb64url = (s: string) => atob(s.replace(/-/g, '+').replace(/_/g, '/'))

function issueTokens(user: MUser) {
  const now = Math.floor(Date.now() / 1000)
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = b64url(
    JSON.stringify({ sub: user.id, username: user.username, roles: user.roles, jti: crypto.randomUUID(), typ: 'access', iss: 'securebank-identity', iat: now, exp: now + ACCESS_TTL }),
  )
  const accessToken = `${header}.${payload}.mock-signature-not-valid-outside-preview`
  const refreshToken = Array.from(crypto.getRandomValues(new Uint8Array(32)), (b) => b.toString(16).padStart(2, '0')).join('')
  db.refreshTokens.push({ token: refreshToken, userId: user.id, revoked: false, expiresAt: Date.now() + REFRESH_TTL * 1000 })
  return { accessToken, tokenType: 'Bearer' as const, expiresIn: ACCESS_TTL, refreshToken, refreshExpiresIn: REFRESH_TTL, user: v.userView(user) }
}

type Claims = { sub: string; jti: string; exp: number; typ: string }

function readClaims(token: string): Claims | null {
  try {
    const [, payload] = token.split('.')
    return JSON.parse(unb64url(payload)) as Claims
  } catch {
    return null
  }
}

function authenticate(request: Request): { user: MUser } | { error: ErrorCodeName } {
  const header = request.headers.get('Authorization')
  if (!header?.startsWith('Bearer ')) return { error: 'UNAUTHENTICATED' }
  const claims = readClaims(header.slice(7))
  if (!claims || claims.typ !== 'access' || claims.exp * 1000 < Date.now() || db.denylist.includes(claims.jti)) return { error: 'AUTH_TOKEN_INVALID' }
  const user = findUser(db, claims.sub)
  return user ? { user } : { error: 'AUTH_TOKEN_INVALID' }
}

const isStaffLimited = (u: MUser) => u.roles.includes('BANK_STAFF') && !u.roles.includes('ADMIN') && !u.roles.includes('AUDITOR')
const C: Role[] = ['CUSTOMER']
const SAA: Role[] = STAFF_ROLES
const SAD: Role[] = ['BANK_STAFF', 'ADMIN']
const AAD: Role[] = ['AUDITOR', 'ADMIN']

function ownCustomer(req: Req) {
  const c = customerOfUser(db, req.user!.id)
  if (!c) throw new MockError('CUSTOMER_NOT_FOUND')
  return c
}

function ownAccount(req: Req, accountId: string) {
  const a = findAccount(db, accountId)
  if (!a) throw new MockError('ACCOUNT_NOT_FOUND')
  if (a.customerId !== ownCustomer(req).id) throw new MockError('ACCOUNT_NOT_OWNED')
  return a
}

// ── Validation helpers (register) ───────────────────────────────────────────
function validateRegistration(b: Record<string, unknown>) {
  const errors: { field: string; message: string }[] = []
  const s = (k: string) => (typeof b[k] === 'string' ? (b[k] as string) : '')
  if (!/^[a-z0-9._-]{3,50}$/.test(s('username'))) errors.push({ field: 'username', message: 'must be 3-50 characters [a-z0-9._-]' })
  const pw = s('password')
  if (pw.length < 8 || pw.length > 100 || !/[A-Z]/.test(pw) || !/[a-z]/.test(pw) || !/\d/.test(pw) || !/[^A-Za-z0-9]/.test(pw)) {
    errors.push({ field: 'password', message: 'must be 8-100 characters with upper, lower, digit and symbol' })
  }
  if (!s('fullName').trim() || s('fullName').length > 200) errors.push({ field: 'fullName', message: 'size must be between 1 and 200' })
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s('email'))) errors.push({ field: 'email', message: 'must be a well-formed email address' })
  if (b.phone != null && b.phone !== '' && !/^\+?[0-9]{9,15}$/.test(s('phone'))) errors.push({ field: 'phone', message: 'must match ^\\+?[0-9]{9,15}$' })
  if (errors.length) throw new MockError('VALIDATION_FAILED', errors)
}

// ── Handlers ────────────────────────────────────────────────────────────────
export const handlers = [
  // Identity
  route('post', '/auth/register', 'public', async (req) => {
    const b = await body(req)
    validateRegistration(b)
    const username = String(b.username)
    if (db.users.some((u) => u.username === username)) throw new MockError('AUTH_USERNAME_TAKEN')
    const now = req.ctx.now.toISOString()
    const user: MUser = {
      id: crypto.randomUUID(),
      username,
      password: String(b.password),
      fullName: String(b.fullName).trim(),
      email: String(b.email),
      phone: b.phone ? String(b.phone) : null,
      roles: ['CUSTOMER'],
      createdAt: now,
    }
    db.users.push(user)
    audit(db, req.ctx, { actor: user, action: 'USER_REGISTERED', resourceType: 'USER', resourceId: user.id, sourceService: 'identity-service', after: { username, roles: 'CUSTOMER' } })
    const customer = { id: crypto.randomUUID(), userId: user.id, fullName: user.fullName, email: user.email, phone: user.phone, createdAt: now }
    db.customers.push(customer)
    const account = openAccount(db, req.ctx, customer.id)
    audit(db, req.ctx, { actor: null, action: 'ACCOUNT_OPENED', resourceType: 'ACCOUNT', resourceId: account.id, sourceService: 'banking-core-service', after: { accountNumber: account.accountNumber, currency: 'VND', status: 'ACTIVE' } })
    notify(db, req.ctx, user.id, ['IN_APP'], 'WELCOME', { accountNumber: account.accountNumber })
    return json(req, v.userView(user), 201)
  }, true),

  route('post', '/auth/login', 'public', async (req) => {
    const b = await body(req)
    const username = typeof b.username === 'string' ? b.username.trim() : ''
    const password = typeof b.password === 'string' ? b.password : ''
    const now = Date.now()
    const recent = (db.loginFailures[username] ?? []).filter((t) => t > now - 5 * 60_000)
    db.loginFailures[username] = recent
    if (recent.length >= 5) throw new MockError('AUTH_LOGIN_RATE_LIMITED')
    const user = db.users.find((u) => u.username === username)
    if (!user || user.password !== password) {
      recent.push(now)
      audit(db, req.ctx, { actor: null, actorUsername: username || null, action: 'LOGIN_FAILURE', resourceType: 'USER', resourceId: user?.id ?? null, outcome: 'FAILURE', sourceService: 'identity-service', after: { reason: 'AUTH_INVALID_CREDENTIALS' } })
      throw new MockError('AUTH_INVALID_CREDENTIALS')
    }
    delete db.loginFailures[username]
    audit(db, req.ctx, { actor: user, action: 'LOGIN_SUCCESS', resourceType: 'USER', resourceId: user.id, sourceService: 'identity-service' })
    return json(req, issueTokens(user))
  }, true),

  route('post', '/auth/refresh', 'public', async (req) => {
    const b = await body(req)
    const record = db.refreshTokens.find((t) => t.token === b.refreshToken)
    if (!record || record.expiresAt < Date.now()) throw new MockError('AUTH_REFRESH_TOKEN_INVALID')
    if (record.revoked) {
      // Reuse of a rotated token: revoke the whole family.
      db.refreshTokens.forEach((t) => { if (t.userId === record.userId) t.revoked = true })
      throw new MockError('AUTH_REFRESH_TOKEN_INVALID')
    }
    const user = findUser(db, record.userId)
    if (!user) throw new MockError('AUTH_REFRESH_TOKEN_INVALID')
    record.revoked = true
    db.refreshTokens = db.refreshTokens.filter((t) => t.expiresAt > Date.now())
    return json(req, issueTokens(user))
  }, true),

  route('post', '/auth/logout', 'any', async (req) => {
    const b = await body(req).catch(() => ({}) as Record<string, unknown>)
    const record = db.refreshTokens.find((t) => t.token === b.refreshToken && t.userId === req.user!.id)
    if (record) record.revoked = true
    const claims = readClaims(req.request.headers.get('Authorization')!.slice(7))
    if (claims) db.denylist.push(claims.jti)
    audit(db, req.ctx, { actor: req.user, action: 'LOGOUT', resourceType: 'USER', resourceId: req.user!.id, sourceService: 'identity-service' })
    return noContent(req)
  }, true),

  route('get', '/auth/me', 'any', (req) => json(req, v.userView(req.user!))),

  // Banking core — customer
  route('get', '/customers/me', C, (req) => {
    const c = ownCustomer(req)
    return json(req, { id: c.id, userId: c.userId, fullName: c.fullName, email: c.email, phone: c.phone, createdAt: c.createdAt })
  }),

  route('get', '/accounts', C, (req) => {
    const c = ownCustomer(req)
    return json(req, db.accounts.filter((a) => a.customerId === c.id).map(v.accountSummary))
  }),

  route('get', '/accounts/lookup', C, (req) => {
    const n = q(req, 'accountNumber') ?? ''
    const a = accountByNumber(db, n)
    if (!a || a.status === 'CLOSED') throw new MockError('ACCOUNT_NOT_FOUND')
    return json(req, { accountNumber: a.accountNumber, holderName: findCustomer(db, a.customerId)?.fullName ?? '', currency: 'VND' })
  }),

  route('get', '/accounts/:id', C, (req) => json(req, v.accountDetail(db, ownAccount(req, req.params.id), req.ctx.now))),

  route('get', '/accounts/:id/balance', C, (req) => {
    const a = ownAccount(req, req.params.id)
    return json(req, { accountId: a.id, accountNumber: a.accountNumber, balance: a.balance, currency: 'VND', status: a.status, asOf: req.ctx.now.toISOString() })
  }),

  route('get', '/accounts/:id/statement', C, (req) => {
    const a = ownAccount(req, req.params.id)
    const from = q(req, 'fromDate')
    const to = q(req, 'toDate')
    return json(req, paginate(req, v.statementEntries(db, a).filter((e) => inDayRange(e.createdAt, from, to))))
  }),

  route('post', '/transfers', C, async (req) => {
    const b = await body(req)
    const customer = ownCustomer(req)
    const key = req.request.headers.get('Idempotency-Key')
    const scopedKey = `${req.user!.id}:${key}`
    const src = typeof b.sourceAccountNumber === 'string' ? b.sourceAccountNumber : ''
    // Same key still running → 409 IN_PROGRESS; another transfer holding the source row → 409 ACCOUNT_BUSY.
    if (key && inFlightKeys.has(scopedKey)) throw new MockError('IDEMPOTENCY_REQUEST_IN_PROGRESS')
    const stored = key && db.idempotency.some((r) => r.userId === req.user!.id && r.key === key)
    if (!stored && src && lockedAccounts.has(src)) throw new MockError('ACCOUNT_BUSY')
    if (key) inFlightKeys.add(scopedKey)
    if (src && !stored) lockedAccounts.add(src)
    try {
      await delay(450) // the "row lock" is held for the duration of the posting
      const out = executeTransfer(db, req.ctx, req.user!, b, key, (tx) => v.transferResponse(db, tx, customer.id))
      return json(req, out.body, out.status, out.replayed ? { 'Idempotent-Replayed': 'true' } : {})
    } finally {
      if (key) inFlightKeys.delete(scopedKey)
      if (src && !stored) lockedAccounts.delete(src)
    }
  }, true),

  route('get', '/transfers', C, (req) => {
    const c = ownCustomer(req)
    const own = new Set(db.accounts.filter((a) => a.customerId === c.id).map((a) => a.id))
    const accountId = q(req, 'accountId')
    const status = q(req, 'status') as TransactionStatus | undefined
    const min = qNum(req, 'minAmount')
    const max = qNum(req, 'maxAmount')
    const from = q(req, 'fromDate')
    const to = q(req, 'toDate')
    const list = db.transactions.filter((t) => {
      const asSender = own.has(t.sourceAccountId)
      const asReceiver = t.destinationAccountId !== null && own.has(t.destinationAccountId) && t.status !== 'REJECTED'
      if (!asSender && !asReceiver) return false
      if (accountId && t.sourceAccountId !== accountId && !(t.destinationAccountId === accountId && t.status !== 'REJECTED')) return false
      if (status && t.status !== status) return false
      if (min !== undefined && t.amount < min) return false
      if (max !== undefined && t.amount > max) return false
      return inDayRange(t.createdAt, from, to)
    })
    const views = list.map((t) => v.transactionSummary(db, t, c.id))
    return json(req, paginate(req, sortBy(views, q(req, 'sort'))))
  }),

  route('get', '/transfers/:id', C, (req) => {
    const c = ownCustomer(req)
    const t = db.transactions.find((x) => x.id === req.params.id)
    const own = new Set(db.accounts.filter((a) => a.customerId === c.id).map((a) => a.id))
    const visible = t && (own.has(t.sourceAccountId) || (t.destinationAccountId && own.has(t.destinationAccountId) && t.status !== 'REJECTED'))
    if (!t || !visible) throw new MockError('TRANSACTION_NOT_FOUND')
    return json(req, v.transactionDetail(db, t, c.id))
  }),

  // Banking core — staff / admin
  route('get', '/admin/customers', SAA, (req) => {
    const term = (q(req, 'q') ?? '').trim().toLowerCase()
    const list = db.customers
      .filter((c) => !term || [c.fullName, c.email, c.phone ?? ''].some((f) => f.toLowerCase().includes(term)))
      .map((c) => v.customerAdmin(db, c.id))
    return json(req, paginate(req, sortBy(list, q(req, 'sort'), 'fullName,asc')))
  }),

  route('get', '/admin/customers/:id', SAA, (req) => {
    const c = findCustomer(db, req.params.id)
    if (!c) throw new MockError('CUSTOMER_NOT_FOUND')
    return json(req, { ...v.customerAdmin(db, c.id), accounts: db.accounts.filter((a) => a.customerId === c.id).map((a) => v.accountAdmin(db, a)) })
  }),

  route('get', '/admin/accounts', SAA, (req) => {
    const term = (q(req, 'q') ?? '').trim().toLowerCase()
    const status = q(req, 'status') as AccountStatus | undefined
    const list = db.accounts
      .map((a) => v.accountAdmin(db, a))
      .filter((a) => (!status || a.status === status) && (!term || a.accountNumber.includes(term) || a.customerName.toLowerCase().includes(term)))
    return json(req, paginate(req, sortBy(list, q(req, 'sort'), 'accountNumber,asc')))
  }),

  route('get', '/admin/accounts/:id', SAA, (req) => {
    const a = findAccount(db, req.params.id)
    if (!a) throw new MockError('ACCOUNT_NOT_FOUND')
    return json(req, { ...v.accountAdmin(db, a), limits: v.accountDetail(db, a, req.ctx.now).limits })
  }),

  route('patch', '/admin/accounts/:id/freeze', SAD, async (req) => {
    const reason = String((await body(req)).reason ?? '').trim()
    if (reason.length < 3 || reason.length > 255) throw new MockError('VALIDATION_FAILED', [{ field: 'reason', message: 'size must be between 3 and 255' }])
    return exclusive(`account:${req.params.id}`, async () => {
      await delay(300)
      return json(req, v.accountAdmin(db, setAccountStatus(db, req.ctx, req.user!, req.params.id, 'FROZEN', reason)))
    })
  }, true),

  route('patch', '/admin/accounts/:id/unfreeze', SAD, async (req) => {
    const reason = String((await body(req)).reason ?? '').trim()
    if (reason.length < 3 || reason.length > 255) throw new MockError('VALIDATION_FAILED', [{ field: 'reason', message: 'size must be between 3 and 255' }])
    return exclusive(`account:${req.params.id}`, async () => {
      await delay(300)
      return json(req, v.accountAdmin(db, setAccountStatus(db, req.ctx, req.user!, req.params.id, 'ACTIVE', reason)))
    })
  }, true),

  route('get', '/admin/accounts/:id/limits', SAA, (req) => {
    const a = findAccount(db, req.params.id)
    if (!a) throw new MockError('ACCOUNT_NOT_FOUND')
    return json(req, { accountId: a.id, ...v.accountDetail(db, a, req.ctx.now).limits })
  }),

  route('put', '/admin/accounts/:id/limits', SAD, async (req) => {
    const b = await body(req)
    return exclusive(`account:${req.params.id}`, async () => {
      await delay(300)
      const a = updateLimits(db, req.ctx, req.user!, req.params.id, b)
      return json(req, { accountId: a.id, ...v.accountDetail(db, a, req.ctx.now).limits })
    })
  }, true),

  route('get', '/admin/transactions', SAA, (req) => {
    const status = q(req, 'status') as TransactionStatus | undefined
    const accountNumber = q(req, 'accountNumber')
    const reference = q(req, 'reference')?.trim().toUpperCase()
    const min = qNum(req, 'minAmount')
    const max = qNum(req, 'maxAmount')
    const from = q(req, 'fromDate')
    const to = q(req, 'toDate')
    const list = db.transactions
      .filter((t) => {
        if (status && t.status !== status) return false
        if (accountNumber && t.sourceAccountNumber !== accountNumber && t.destinationAccountNumber !== accountNumber) return false
        if (reference && !t.reference.includes(reference)) return false
        if (min !== undefined && t.amount < min) return false
        if (max !== undefined && t.amount > max) return false
        return inDayRange(t.createdAt, from, to)
      })
      .map((t) => v.adminTransaction(db, t))
    return json(req, paginate(req, sortBy(list, q(req, 'sort'))))
  }),

  route('get', '/admin/transactions/:id', SAA, (req) => {
    const t = db.transactions.find((x) => x.id === req.params.id)
    if (!t) throw new MockError('TRANSACTION_NOT_FOUND')
    return json(req, { ...v.adminTransaction(db, t), ledgerEntries: v.adminLedger(db, t.id) })
  }),

  route('get', '/admin/reconciliation/transactions/:id', AAD, (req) => {
    const t = db.transactions.find((x) => x.id === req.params.id)
    if (!t) throw new MockError('TRANSACTION_NOT_FOUND')
    const entries = v.adminLedger(db, t.id)
    const debitTotal = entries.filter((e) => e.entryType === 'DEBIT').reduce((s, e) => s + e.amount, 0)
    const creditTotal = entries.filter((e) => e.entryType === 'CREDIT').reduce((s, e) => s + e.amount, 0)
    const expected = t.status === 'SUCCESS' ? 2 : 0
    return json(req, {
      transactionId: t.id,
      transactionReference: t.reference,
      status: t.status,
      entryCount: entries.length,
      debitTotal,
      creditTotal,
      balanced: debitTotal === creditTotal && entries.length === expected,
      entries,
      checkedAt: req.ctx.now.toISOString(),
    })
  }),

  route('get', '/admin/stats/today', SAA, (req) => {
    const day = bankDayKey(req.ctx.now)
    const start = bankDayStart(day).getTime()
    const today = db.transactions.filter((t) => new Date(t.createdAt).getTime() >= start)
    const ok = today.filter((t) => t.status === 'SUCCESS')
    return json(req, {
      date: day,
      transactionsToday: today.length,
      successfulToday: ok.length,
      failedOrRejectedToday: today.filter((t) => t.status === 'FAILED' || t.status === 'REJECTED').length,
      totalTransferredToday: ok.reduce((s, t) => s + t.amount, 0),
      frozenAccounts: db.accounts.filter((a) => a.status === 'FROZEN').length,
      currency: 'VND',
    })
  }),

  route('get', '/admin/stats/daily', SAA, (req) => {
    const days = qNum(req, 'days') ?? 14
    if (!Number.isInteger(days) || days < 1 || days > 90) throw new MockError('VALIDATION_FAILED', [{ field: 'days', message: 'must be between 1 and 90' }])
    const out: { date: string; count: number; amount: number }[] = []
    for (let i = days - 1; i >= 0; i--) {
      const day = bankDayKey(new Date(req.ctx.now.getTime() - i * 86_400_000))
      const start = bankDayStart(day).getTime()
      const ok = db.transactions.filter((t) => {
        const at = new Date(t.createdAt).getTime()
        return t.status === 'SUCCESS' && at >= start && at < start + 86_400_000
      })
      out.push({ date: day, count: ok.length, amount: ok.reduce((s, t) => s + t.amount, 0) })
    }
    return json(req, out)
  }),

  // Fraud
  route('get', '/fraud/alerts/stats', SAA, (req) => {
    const start = bankDayStart(bankDayKey(req.ctx.now)).getTime()
    const unresolved = db.fraud.filter((a) => a.status === 'OPEN' || a.status === 'UNDER_REVIEW')
    return json(req, {
      open: db.fraud.filter((a) => a.status === 'OPEN').length,
      underReview: db.fraud.filter((a) => a.status === 'UNDER_REVIEW').length,
      critical: unresolved.filter((a) => a.riskLevel === 'CRITICAL').length,
      high: unresolved.filter((a) => a.riskLevel === 'HIGH').length,
      createdToday: db.fraud.filter((a) => new Date(a.createdAt).getTime() >= start).length,
    })
  }),

  route('get', '/fraud/alerts', SAA, (req) => {
    const status = q(req, 'status') as FraudAlertStatus | undefined
    const level = q(req, 'riskLevel')
    const customerId = q(req, 'customerId')
    const list = db.fraud
      .filter((a) => (!status || a.status === status) && (!level || a.riskLevel === level) && (!customerId || a.customerId === customerId))
      .map(v.fraudSummary)
    return json(req, paginate(req, sortBy(list, q(req, 'sort'))))
  }),

  route('get', '/fraud/alerts/:id', SAA, (req) => {
    const a = db.fraud.find((x) => x.id === req.params.id)
    if (!a) throw new MockError('FRAUD_ALERT_NOT_FOUND')
    return json(req, a)
  }),

  route('patch', '/fraud/alerts/:id/review', SAD, async (req) => {
    const b = await body(req)
    return exclusive(`alert:${req.params.id}`, async () => {
      await delay(300)
      return json(req, reviewAlert(db, req.ctx, req.user!, req.params.id, b))
    })
  }, true),

  // Audit
  route('get', '/audit/logs/actions', SAA, (req) => json(req, [...new Set(db.audit.map((l) => l.action))].sort())),

  route('get', '/audit/logs', SAA, (req) => {
    const restricted = isStaffLimited(req.user!)
    const actor = q(req, 'actor')?.toLowerCase()
    const f = {
      actorUserId: q(req, 'actorUserId'),
      action: q(req, 'action'),
      resourceType: q(req, 'resourceType'),
      resourceId: q(req, 'resourceId'),
      correlationId: q(req, 'correlationId'),
    }
    const from = q(req, 'from') ? new Date(q(req, 'from')!).getTime() : undefined
    const to = q(req, 'to') ? new Date(q(req, 'to')!).getTime() : undefined
    const list = db.audit
      .filter((l) => {
        if (restricted && !['ACCOUNT', 'TRANSACTION', 'FRAUD_ALERT'].includes(l.resourceType)) return false
        if (actor && !(l.actorUsername ?? '').toLowerCase().includes(actor)) return false
        if (f.actorUserId && l.actorUserId !== f.actorUserId) return false
        if (f.action && l.action !== f.action) return false
        if (f.resourceType && l.resourceType !== f.resourceType) return false
        if (f.resourceId && l.resourceId !== f.resourceId) return false
        if (f.correlationId && l.correlationId !== f.correlationId) return false
        const t = new Date(l.occurredAt).getTime()
        if (from !== undefined && !Number.isNaN(from) && t < from) return false
        if (to !== undefined && !Number.isNaN(to) && t > to) return false
        return true
      })
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
      .map((l) => v.auditView(l, restricted))
    return json(req, paginate(req, list))
  }),

  route('get', '/audit/logs/:id', SAA, (req) => {
    const restricted = isStaffLimited(req.user!)
    const log = db.audit.find((l) => l.id === req.params.id)
    if (!log || (restricted && !['ACCOUNT', 'TRANSACTION', 'FRAUD_ALERT'].includes(log.resourceType))) throw new MockError('AUDIT_LOG_NOT_FOUND')
    return json(req, v.auditDetailView(log, restricted))
  }),

  // Notifications
  route('get', '/notifications/me/unread-count', 'any', (req) =>
    json(req, { count: db.notifications.filter((n) => n.recipientUserId === req.user!.id && n.channel === 'IN_APP' && !n.read).length }),
  ),

  route('get', '/notifications/me', 'any', (req) => {
    const channel = q(req, 'channel')
    const unreadOnly = q(req, 'unreadOnly') === 'true'
    const list = db.notifications
      .filter((n) => n.recipientUserId === req.user!.id && (!channel || n.channel === channel) && (!unreadOnly || !n.read))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(v.notificationView)
    return json(req, paginate(req, list))
  }),

  route('patch', '/notifications/:id/read', 'any', (req) => {
    const n = db.notifications.find((x) => x.id === req.params.id && x.recipientUserId === req.user!.id)
    if (!n) throw new MockError('NOTIFICATION_NOT_FOUND')
    n.read = true
    return noContent(req)
  }, true),
]
