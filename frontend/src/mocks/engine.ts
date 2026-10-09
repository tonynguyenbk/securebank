import type { AccountStatus, FraudAlertDetail, FraudAlertStatus, FraudRuleHit, NotificationTemplate, RiskLevel } from '../types/api'
import { FRAUD_TRANSITIONS, NOTE_REQUIRED } from '../types/api'
import { bankDayKey, bankDayStart, formatVnd } from '../utils/format'
import {
  accountByNumber,
  customerNameOfAccount,
  customerOfUser,
  findAccount,
  findCustomer,
  type DB,
  type MAccount,
  type MTransaction,
  type MUser,
} from './db'
import { ERROR_CODES, type ErrorCodeName } from './errorCodes'

// Business rules of the mock backend. Seeding replays its history through these same functions,
// so seeded data obeys exactly the rules a live transfer in the preview does.

export type Ctx = { now: Date; uuid: () => string; correlationId: string | null; ip: string | null }

export class MockError extends Error {
  readonly code: ErrorCodeName
  readonly fieldErrors?: { field: string; message: string }[]
  constructor(code: ErrorCodeName, fieldErrors?: { field: string; message: string }[]) {
    super(code)
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export const DEFAULT_PER_TX_LIMIT = 100_000_000
export const DEFAULT_DAILY_LIMIT = 500_000_000

const iso = (d: Date) => d.toISOString()
const plus = (d: Date, ms: number) => new Date(d.getTime() + ms)

// ── Audit & notifications ───────────────────────────────────────────────────
type AuditInput = {
  actor: MUser | null
  action: string
  resourceType: 'USER' | 'ACCOUNT' | 'TRANSACTION' | 'FRAUD_ALERT'
  resourceId: string | null
  outcome?: 'SUCCESS' | 'FAILURE'
  sourceService: string
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
  actorUsername?: string | null
}

export function audit(db: DB, ctx: Ctx, input: AuditInput) {
  const occurredAt = ctx.now
  db.audit.push({
    id: ctx.uuid(),
    eventId: ctx.uuid(),
    occurredAt: iso(occurredAt),
    receivedAt: iso(plus(occurredAt, 40 + Math.floor((ctx.now.getTime() % 7) * 23))),
    actorUserId: input.actor?.id ?? null,
    actorUsername: input.actor?.username ?? input.actorUsername ?? null,
    actorRole: input.actor?.roles[0] ?? null,
    action: input.action,
    resourceType: input.resourceType,
    resourceId: input.resourceId,
    outcome: input.outcome ?? 'SUCCESS',
    correlationId: ctx.correlationId,
    sourceService: input.sourceService,
    ipAddress: input.actor || input.actorUsername ? ctx.ip : null,
    before: input.before ?? null,
    after: input.after ?? null,
  })
}

/** Masked as the notification service sends it: "******0002". */
const masked = (n: string) => `******${n.slice(-4)}`

// English subject/message, as the notification service renders them. Params follow contract section 6.
const ENGLISH: Record<NotificationTemplate, (p: Record<string, string | number>) => [string, string]> = {
  TRANSFER_SENT: (p) => [
    `You sent ${formatVnd(Number(p.amount))}`,
    `${formatVnd(Number(p.amount))} was sent to ${p.counterpartyName} (${p.counterpartyAccountNumber}), ref ${p.reference}. Balance: ${formatVnd(Number(p.balanceAfter))}.`,
  ],
  TRANSFER_RECEIVED: (p) => [
    `You received ${formatVnd(Number(p.amount))}`,
    `${p.counterpartyName} (${p.counterpartyAccountNumber}) sent you ${formatVnd(Number(p.amount))}, ref ${p.reference}. Balance: ${formatVnd(Number(p.balanceAfter))}.`,
  ],
  TRANSFER_REJECTED: (p) => [
    'Transfer not completed',
    `Your transfer of ${formatVnd(Number(p.amount))} to ${p.counterpartyAccountNumber} (ref ${p.reference}) was rejected: ${p.failureCode}.`,
  ],
  ACCOUNT_FROZEN: (p) => ['Account frozen', `Account ${p.accountNumber} has been frozen. Outgoing transfers are blocked.`],
  ACCOUNT_UNFROZEN: (p) => ['Account active again', `Account ${p.accountNumber} is active again.`],
  WELCOME: (p) => ['Welcome to SecureBank', `Hello ${p.fullName}, your current account is open.`],
}

export function notify(
  db: DB,
  ctx: Ctx,
  recipientUserId: string,
  channels: ('IN_APP' | 'EMAIL' | 'SMS')[],
  templateCode: NotificationTemplate,
  params: Record<string, string | number>,
  relatedTransactionId: string | null = null,
) {
  const [subject, message] = ENGLISH[templateCode](params)
  for (const channel of channels) {
    db.notifications.push({
      id: ctx.uuid(),
      recipientUserId,
      channel,
      status: 'SENT',
      templateCode,
      params,
      subject,
      message,
      read: false,
      relatedTransactionId,
      createdAt: iso(plus(ctx.now, 300)),
      sentAt: iso(plus(ctx.now, channel === 'IN_APP' ? 300 : 1800)),
    })
  }
}

// ── Accounts ────────────────────────────────────────────────────────────────
export function nextReference(db: DB, now: Date): string {
  const day = bankDayKey(now)
  const seq = (db.refSeq[day] ?? 0) + 1
  db.refSeq[day] = seq
  return `TX${day.replace(/-/g, '')}${String(seq).padStart(4, '0')}`
}

export function usedToday(db: DB, accountId: string, now: Date): number {
  const start = bankDayStart(bankDayKey(now)).getTime()
  return db.transactions
    .filter((t) => t.sourceAccountId === accountId && t.status === 'SUCCESS' && new Date(t.createdAt).getTime() >= start)
    .reduce((s, t) => s + t.amount, 0)
}

export function limitsOf(db: DB, a: MAccount, now: Date) {
  const used = usedToday(db, a.id, now)
  return {
    perTransactionLimit: a.perTransactionLimit,
    dailyLimit: a.dailyLimit,
    usedToday: used,
    remainingToday: Math.max(0, a.dailyLimit - used),
    updatedAt: a.limitsUpdatedAt,
  }
}

export function openAccount(db: DB, ctx: Ctx, customerId: string, opts: { id?: string; number?: string; balance?: number } = {}) {
  const accountNumber = opts.number ?? String(db.nextAccountNumber++)
  const account: MAccount = {
    id: opts.id ?? ctx.uuid(),
    accountNumber,
    customerId,
    balance: opts.balance ?? 0,
    status: 'ACTIVE',
    createdAt: iso(ctx.now),
    updatedAt: iso(ctx.now),
    perTransactionLimit: DEFAULT_PER_TX_LIMIT,
    dailyLimit: DEFAULT_DAILY_LIMIT,
    limitsUpdatedAt: iso(ctx.now),
  }
  db.accounts.push(account)
  return account
}

export function setAccountStatus(db: DB, ctx: Ctx, actor: MUser, accountId: string, target: AccountStatus, reason: string) {
  const a = findAccount(db, accountId)
  if (!a) throw new MockError('ACCOUNT_NOT_FOUND')
  if (a.status === 'CLOSED') throw new MockError('ACCOUNT_CLOSED')
  if (a.status === target) throw new MockError('ACCOUNT_STATUS_UNCHANGED')
  const before = { status: a.status }
  a.status = target
  a.updatedAt = iso(ctx.now)
  audit(db, ctx, {
    actor,
    action: target === 'FROZEN' ? 'ACCOUNT_FREEZE' : 'ACCOUNT_UNFREEZE',
    resourceType: 'ACCOUNT',
    resourceId: a.id,
    sourceService: 'banking-core-service',
    before,
    after: { status: a.status, reason },
  })
  const owner = findCustomer(db, a.customerId)
  if (owner) {
    notify(db, ctx, owner.userId, ['IN_APP', 'EMAIL'], target === 'FROZEN' ? 'ACCOUNT_FROZEN' : 'ACCOUNT_UNFROZEN', {
      accountNumber: masked(a.accountNumber),
      status: a.status,
    })
  }
  return a
}

export function updateLimits(db: DB, ctx: Ctx, actor: MUser, accountId: string, body: unknown) {
  const a = findAccount(db, accountId)
  if (!a) throw new MockError('ACCOUNT_NOT_FOUND')
  const b = (body ?? {}) as { perTransactionLimit?: unknown; dailyLimit?: unknown }
  const per = Number(b.perTransactionLimit)
  const daily = Number(b.dailyLimit)
  const errors: { field: string; message: string }[] = []
  if (!Number.isFinite(per) || per <= 0) errors.push({ field: 'perTransactionLimit', message: 'must be greater than 0' })
  if (!Number.isFinite(daily) || daily <= 0) errors.push({ field: 'dailyLimit', message: 'must be greater than 0' })
  if (!errors.length && per > daily) errors.push({ field: 'perTransactionLimit', message: 'must not exceed dailyLimit' })
  if (errors.length) throw new MockError('VALIDATION_FAILED', errors)
  const before = { perTransactionLimit: a.perTransactionLimit, dailyLimit: a.dailyLimit }
  a.perTransactionLimit = per
  a.dailyLimit = daily
  a.limitsUpdatedAt = iso(ctx.now)
  a.updatedAt = iso(ctx.now)
  audit(db, ctx, {
    actor,
    action: 'TRANSFER_LIMIT_UPDATE',
    resourceType: 'ACCOUNT',
    resourceId: a.id,
    sourceService: 'banking-core-service',
    before,
    after: { perTransactionLimit: per, dailyLimit: daily },
  })
  return a
}

// ── Transfers ───────────────────────────────────────────────────────────────
export type TransferOutcome = { status: number; body: unknown; replayed: boolean; transaction?: MTransaction }

function canonical(body: Record<string, unknown>): string {
  return JSON.stringify({
    sourceAccountNumber: body.sourceAccountNumber ?? null,
    destinationAccountNumber: body.destinationAccountNumber ?? null,
    amount: body.amount ?? null,
    currency: body.currency ?? null,
    description: body.description ?? null,
  })
}

/** Cheap stand-in for the backend's SHA-256 request hash: a stable string hash is enough in a single tab. */
function hashOf(s: string): string {
  let h1 = 0x811c9dc5
  let h2 = 0x01000193
  for (let i = 0; i < s.length; i++) {
    h1 = Math.imul(h1 ^ s.charCodeAt(i), 16777619)
    h2 = Math.imul(h2 ^ s.charCodeAt(i), 2246822507)
  }
  return (h1 >>> 0).toString(16) + (h2 >>> 0).toString(16)
}

/**
 * POST /transfers. Returns the HTTP status + body (TransferResponse in its customer view is built by the caller
 * from `transaction`). Throws MockError for non-persisted errors (400/403/404/409).
 */
export function executeTransfer(
  db: DB,
  ctx: Ctx,
  user: MUser,
  rawBody: unknown,
  idempotencyKey: string | null,
  render: (tx: MTransaction) => unknown,
): TransferOutcome {
  if (!idempotencyKey) throw new MockError('IDEMPOTENCY_KEY_REQUIRED')
  if (!/^[A-Za-z0-9_-]{8,100}$/.test(idempotencyKey)) {
    throw new MockError('VALIDATION_FAILED', [{ field: 'Idempotency-Key', message: 'must be 8-100 characters [A-Za-z0-9_-]' }])
  }
  const body = (rawBody ?? {}) as Record<string, unknown>
  const hash = hashOf(canonical(body))
  const stored = db.idempotency.find((r) => r.userId === user.id && r.key === idempotencyKey)
  if (stored) {
    if (stored.hash !== hash) throw new MockError('IDEMPOTENCY_KEY_CONFLICT')
    return { status: stored.status, body: stored.body, replayed: true }
  }

  // 400 — request shape
  const errors: { field: string; message: string }[] = []
  const src = typeof body.sourceAccountNumber === 'string' ? body.sourceAccountNumber : ''
  const dst = typeof body.destinationAccountNumber === 'string' ? body.destinationAccountNumber : ''
  if (!/^\d{10}$/.test(src)) errors.push({ field: 'sourceAccountNumber', message: 'must be a 10-digit account number' })
  if (!/^\d{10}$/.test(dst)) errors.push({ field: 'destinationAccountNumber', message: 'must be a 10-digit account number' })
  if (body.description != null && (typeof body.description !== 'string' || body.description.length > 255)) {
    errors.push({ field: 'description', message: 'size must be between 0 and 255' })
  }
  if (typeof body.currency !== 'string') errors.push({ field: 'currency', message: 'must not be null' })
  if (errors.length) throw new MockError('VALIDATION_FAILED', errors)
  const amount = Number(body.amount)
  if (typeof body.amount !== 'number' || !Number.isFinite(amount) || amount <= 0 || Math.round(amount * 100) !== amount * 100) {
    throw new MockError('INVALID_TRANSFER_AMOUNT')
  }
  if (body.currency !== 'VND') throw new MockError('CURRENCY_NOT_SUPPORTED')
  if (src === dst) throw new MockError('SAME_ACCOUNT_TRANSFER')

  // 404 / 403
  const source = accountByNumber(db, src)
  if (!source) throw new MockError('ACCOUNT_NOT_FOUND')
  const customer = customerOfUser(db, user.id)
  if (!customer || source.customerId !== customer.id) throw new MockError('ACCOUNT_NOT_OWNED')
  const dest = accountByNumber(db, dst)
  if (!dest) throw new MockError('ACCOUNT_NOT_FOUND')

  const description = typeof body.description === 'string' && body.description.trim() ? body.description.trim() : null
  const tx: MTransaction = {
    id: ctx.uuid(),
    reference: nextReference(db, ctx.now),
    sourceAccountId: source.id,
    sourceAccountNumber: source.accountNumber,
    destinationAccountId: dest.id,
    destinationAccountNumber: dest.accountNumber,
    amount,
    description,
    status: 'PENDING',
    failureCode: null,
    failureReason: null,
    createdBy: user.id,
    createdAt: iso(ctx.now),
    completedAt: null,
  }

  // 422 — business rules, persisted as REJECTED
  let rejection: ErrorCodeName | null = null
  if (source.status === 'FROZEN') rejection = 'ACCOUNT_FROZEN'
  else if (source.status === 'CLOSED' || dest.status === 'CLOSED') rejection = 'ACCOUNT_CLOSED'
  else if (amount > source.perTransactionLimit) rejection = 'TRANSFER_LIMIT_EXCEEDED'
  else if (usedToday(db, source.id, ctx.now) + amount > source.dailyLimit) rejection = 'DAILY_LIMIT_EXCEEDED'
  else if (source.balance < amount) rejection = 'INSUFFICIENT_FUNDS'

  if (rejection) {
    tx.status = 'REJECTED'
    tx.failureCode = rejection
    tx.failureReason = REJECTION_REASON[rejection] ?? rejection
    tx.completedAt = iso(ctx.now)
    db.transactions.push(tx)
    audit(db, ctx, {
      actor: user,
      action: 'TRANSFER_REJECTED',
      resourceType: 'TRANSACTION',
      resourceId: tx.id,
      outcome: 'FAILURE',
      sourceService: 'banking-core-service',
      after: { reference: tx.reference, amount, sourceAccountNumber: src, destinationAccountNumber: dst, failureCode: rejection },
    })
    notify(db, ctx, user.id, ['IN_APP'], 'TRANSFER_REJECTED', {
      amount,
      currency: 'VND',
      reference: tx.reference,
      accountNumber: masked(src),
      counterpartyAccountNumber: masked(dst),
      failureCode: rejection,
    }, tx.id)
    const errBody = errorBody(rejection, '/api/v1/transfers', ctx)
    db.idempotency.push({ userId: user.id, key: idempotencyKey, hash, status: 422, body: errBody })
    return { status: 422, body: errBody, replayed: false, transaction: tx }
  }

  // Post the double entry atomically.
  const at = iso(plus(ctx.now, 120))
  const srcBefore = source.balance
  const dstBefore = dest.balance
  source.balance = round2(srcBefore - amount)
  dest.balance = round2(dstBefore + amount)
  source.updatedAt = at
  dest.updatedAt = at
  db.ledger.push(
    { id: ctx.uuid(), transactionId: tx.id, accountId: source.id, accountNumber: source.accountNumber, entryType: 'DEBIT', amount, balanceBefore: srcBefore, balanceAfter: source.balance, createdAt: at },
    { id: ctx.uuid(), transactionId: tx.id, accountId: dest.id, accountNumber: dest.accountNumber, entryType: 'CREDIT', amount, balanceBefore: dstBefore, balanceAfter: dest.balance, createdAt: at },
  )
  tx.status = 'SUCCESS'
  tx.completedAt = at
  db.transactions.push(tx)

  audit(db, ctx, {
    actor: user,
    action: 'TRANSFER_COMPLETED',
    resourceType: 'TRANSACTION',
    resourceId: tx.id,
    sourceService: 'banking-core-service',
    after: { reference: tx.reference, amount, sourceAccountNumber: src, destinationAccountNumber: dst, status: 'SUCCESS' },
  })
  const destCustomer = findCustomer(db, dest.customerId)
  notify(db, ctx, user.id, ['IN_APP', 'EMAIL'], 'TRANSFER_SENT', {
    amount, currency: 'VND', reference: tx.reference, accountNumber: masked(src), counterpartyName: destCustomer?.fullName ?? '', counterpartyAccountNumber: masked(dst), balanceAfter: source.balance,
  }, tx.id)
  if (destCustomer) {
    notify(db, ctx, destCustomer.userId, ['IN_APP', 'SMS'], 'TRANSFER_RECEIVED', {
      amount, currency: 'VND', reference: tx.reference, accountNumber: masked(dst), counterpartyName: customer.fullName, counterpartyAccountNumber: masked(src), balanceAfter: dest.balance,
    }, tx.id)
  }
  evaluateFraud(db, { ...ctx, now: plus(ctx.now, 900) }, tx)

  const resBody = render(tx)
  db.idempotency.push({ userId: user.id, key: idempotencyKey, hash, status: 201, body: resBody })
  return { status: 201, body: resBody, replayed: false, transaction: tx }
}

const REJECTION_REASON: Partial<Record<ErrorCodeName, string>> = {
  ACCOUNT_FROZEN: 'Source account is frozen',
  ACCOUNT_CLOSED: 'Account is closed',
  TRANSFER_LIMIT_EXCEEDED: 'Amount exceeds the per-transaction limit',
  DAILY_LIMIT_EXCEEDED: 'Amount exceeds the remaining daily limit',
  INSUFFICIENT_FUNDS: 'Available balance is lower than the amount',
}

function round2(n: number) {
  return Math.round(n * 100) / 100
}

export function errorBody(code: ErrorCodeName, path: string, ctx: Pick<Ctx, 'now' | 'correlationId'>, fieldErrors?: { field: string; message: string }[]) {
  const [status, message] = ERROR_CODES[code]
  return {
    timestamp: ctx.now.toISOString(),
    status,
    code,
    message,
    path,
    ...(ctx.correlationId ? { correlationId: ctx.correlationId } : {}),
    ...(fieldErrors?.length ? { fieldErrors } : {}),
  }
}

// ── Fraud ───────────────────────────────────────────────────────────────────
export function riskLevel(score: number): RiskLevel {
  if (score >= 80) return 'CRITICAL'
  if (score >= 60) return 'HIGH'
  if (score >= 30) return 'MEDIUM'
  return 'LOW'
}

export function evaluateFraud(db: DB, ctx: Ctx, tx: MTransaction): FraudAlertDetail | null {
  const source = findAccount(db, tx.sourceAccountId)
  if (!source) return null
  const customer = findCustomer(db, source.customerId)
  if (!customer) return null
  const ownAccountIds = new Set(db.accounts.filter((a) => a.customerId === customer.id).map((a) => a.id))
  const outgoing = db.transactions.filter((t) => ownAccountIds.has(t.sourceAccountId) && t.status === 'SUCCESS')
  const txTime = new Date(tx.createdAt).getTime()
  const rules: FraudRuleHit[] = []

  if (tx.amount >= 100_000_000) {
    rules.push({ ruleCode: 'HIGH_AMOUNT', description: 'Amount at or above 100,000,000 VND', scoreContribution: 40, details: `amount=${tx.amount}` })
  }
  const lastMinute = outgoing.filter((t) => {
    const at = new Date(t.createdAt).getTime()
    return at <= txTime && at > txTime - 60_000
  }).length
  if (lastMinute > 5) {
    rules.push({ ruleCode: 'HIGH_FREQUENCY', description: 'More than 5 outgoing transfers within 60 seconds', scoreContribution: 30, details: `count60s=${lastMinute}` })
  }
  const dayStart = bankDayStart(bankDayKey(new Date(tx.createdAt))).getTime()
  const todayTotal = outgoing.filter((t) => {
    const at = new Date(t.createdAt).getTime()
    return at >= dayStart && at <= txTime
  }).reduce((s, t) => s + t.amount, 0)
  if (todayTotal > 200_000_000) {
    rules.push({ ruleCode: 'DAILY_VELOCITY', description: 'Outgoing total today above 200,000,000 VND', scoreContribution: 20, details: `todayTotal=${todayTotal}` })
  }
  const seenBefore = outgoing.some((t) => t.id !== tx.id && t.destinationAccountNumber === tx.destinationAccountNumber && new Date(t.createdAt).getTime() < txTime)
  if (!seenBefore && tx.amount >= 10_000_000) {
    rules.push({ ruleCode: 'NEW_BENEFICIARY', description: 'First transfer to this beneficiary of at least 10,000,000 VND', scoreContribution: 20, details: `destination=${tx.destinationAccountNumber}` })
  }

  const score = rules.reduce((s, r) => s + r.scoreContribution, 0)
  if (score < 30) return null
  const dest = findAccount(db, tx.destinationAccountId ?? '')
  const alert: FraudAlertDetail = {
    id: ctx.uuid(),
    transactionId: tx.id,
    transactionReference: tx.reference,
    customerId: customer.id,
    customerName: customer.fullName,
    sourceAccountId: source.id,
    sourceAccountNumber: source.accountNumber,
    amount: tx.amount,
    currency: 'VND',
    riskScore: score,
    riskLevel: riskLevel(score),
    status: 'OPEN',
    createdAt: iso(ctx.now),
    destinationAccountNumber: tx.destinationAccountNumber,
    destinationCustomerName: customerNameOfAccount(db, dest),
    transactionOccurredAt: tx.createdAt,
    rules,
    reviewedBy: null,
    reviewedByUsername: null,
    reviewedAt: null,
    reviewNote: null,
    timeline: [{ at: iso(ctx.now), status: 'OPEN', actorUsername: null, note: null }],
  }
  db.fraud.push(alert)
  audit(db, ctx, {
    actor: null,
    actorUsername: null,
    action: 'FRAUD_ALERT_CREATED',
    resourceType: 'FRAUD_ALERT',
    resourceId: alert.id,
    sourceService: 'fraud-service',
    after: { transactionReference: tx.reference, riskScore: score, riskLevel: alert.riskLevel, rules: rules.map((r) => r.ruleCode).join(',') },
  })
  return alert
}

export function reviewAlert(db: DB, ctx: Ctx, actor: MUser, alertId: string, body: unknown) {
  const alert = db.fraud.find((a) => a.id === alertId)
  if (!alert) throw new MockError('FRAUD_ALERT_NOT_FOUND')
  const b = (body ?? {}) as { status?: unknown; note?: unknown }
  const target = b.status as FraudAlertStatus
  const note = typeof b.note === 'string' ? b.note.trim() : ''
  if (!target || !(target in FRAUD_TRANSITIONS)) {
    throw new MockError('VALIDATION_FAILED', [{ field: 'status', message: 'must be one of OPEN, UNDER_REVIEW, APPROVED, REJECTED, CLOSED' }])
  }
  if (note.length > 1000) throw new MockError('VALIDATION_FAILED', [{ field: 'note', message: 'size must be between 1 and 1000' }])
  if (NOTE_REQUIRED.includes(target) && !note) {
    throw new MockError('VALIDATION_FAILED', [{ field: 'note', message: 'is required for this status' }])
  }
  if (!FRAUD_TRANSITIONS[alert.status].includes(target)) throw new MockError('FRAUD_ALERT_INVALID_TRANSITION')
  const before = { status: alert.status }
  alert.status = target
  alert.reviewedBy = actor.id
  alert.reviewedByUsername = actor.username
  alert.reviewedAt = iso(ctx.now)
  if (note) alert.reviewNote = note
  alert.timeline.push({ at: iso(ctx.now), status: target, actorUsername: actor.username, note: note || null })
  audit(db, ctx, {
    actor,
    action: 'FRAUD_ALERT_REVIEW',
    resourceType: 'FRAUD_ALERT',
    resourceId: alert.id,
    sourceService: 'fraud-service',
    before,
    after: { status: target, ...(note ? { note } : {}) },
  })
  return alert
}
