import type { FraudAlertStatus, Role } from '../types/api'
import { bankDayKey, bankDayStart } from '../utils/format'
import { emptyDb, findUser, type DB, type MUser } from './db'
import { audit, executeTransfer, notify, openAccount, reviewAlert, setAccountStatus, type Ctx } from './engine'
import { transferResponse } from './views'

// Deterministic demo history: fixed people, fixed story beats, PRNG-chosen everyday transfers.
// Times are relative to the moment the preview is first opened, so the data always looks current.

const id = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`

type Person = {
  key: string
  username: string
  password: string
  fullName: string
  phone: string | null
  roles: Role[]
  userId: string
  customerId?: string
  accountId?: string
  accountNumber?: string
  target?: number // balance the account must have when seeding finishes
  joinedDaysAgo: number
}

// Contract §7 demo users first (exact IDs, numbers and balances), then extra seeded customers.
const PEOPLE: Person[] = [
  { key: 'an', username: 'customer1', password: 'Customer@123', fullName: 'Nguyễn Văn An', phone: '+84901234561', roles: ['CUSTOMER'], userId: id(101), customerId: id(1101), accountId: id(2101), accountNumber: '1000000001', target: 25_000_000, joinedDaysAgo: 64 },
  { key: 'binh', username: 'customer2', password: 'Customer@123', fullName: 'Trần Thị Bình', phone: '+84912345672', roles: ['CUSTOMER'], userId: id(102), customerId: id(1102), accountId: id(2102), accountNumber: '1000000002', target: 10_000_000, joinedDaysAgo: 58 },
  { key: 'chau', username: 'staff1', password: 'Staff@123', fullName: 'Lê Minh Châu', phone: null, roles: ['BANK_STAFF'], userId: id(201), joinedDaysAgo: 120 },
  { key: 'dung', username: 'auditor1', password: 'Auditor@123', fullName: 'Phạm Quốc Dũng', phone: null, roles: ['AUDITOR'], userId: id(301), joinedDaysAgo: 120 },
  { key: 'ha', username: 'admin1', password: 'Admin@123', fullName: 'Hoàng Thu Hà', phone: null, roles: ['ADMIN'], userId: id(401), joinedDaysAgo: 120 },
  { key: 'long', username: 'long.dh', password: 'Customer@123', fullName: 'Đỗ Hoàng Long', phone: '+84983112240', roles: ['CUSTOMER'], userId: id(103), customerId: id(1103), accountId: id(2103), accountNumber: '1000000101', target: 846_300_000, joinedDaysAgo: 92 },
  { key: 'mai', username: 'mai.vt', password: 'Customer@123', fullName: 'Vũ Thị Mai', phone: '+84868045519', roles: ['CUSTOMER'], userId: id(104), customerId: id(1104), accountId: id(2104), accountNumber: '1000000102', target: 64_450_000, joinedDaysAgo: 81 },
  { key: 'thang', username: 'thang.bd', password: 'Customer@123', fullName: 'Bùi Đức Thắng', phone: '+84375226801', roles: ['CUSTOMER'], userId: id(105), customerId: id(1105), accountId: id(2105), accountNumber: '1000000103', target: 118_900_000, joinedDaysAgo: 47 },
  { key: 'anh', username: 'anh.pn', password: 'Customer@123', fullName: 'Phan Ngọc Ánh', phone: '+84933870114', roles: ['CUSTOMER'], userId: id(106), customerId: id(1106), accountId: id(2106), accountNumber: '1000000104', target: 18_750_000, joinedDaysAgo: 75 },
  { key: 'tung', username: 'tung.nt', password: 'Customer@123', fullName: 'Ngô Thanh Tùng', phone: '+84909556032', roles: ['CUSTOMER'], userId: id(107), customerId: id(1107), accountId: id(2107), accountNumber: '1000000105', target: 1_254_000_000, joinedDaysAgo: 110 },
  { key: 'trang', username: 'trang.dt', password: 'Customer@123', fullName: 'Đặng Thu Trang', phone: '+84776301928', roles: ['CUSTOMER'], userId: id(108), customerId: id(1108), accountId: id(2108), accountNumber: '1000000106', target: 9_420_000, joinedDaysAgo: 33 },
]
const P = Object.fromEntries(PEOPLE.map((p) => [p.key, p])) as Record<string, Person>

const IPS = ['14.161.22.87', '113.172.40.15', '171.244.9.203', '27.72.98.140', '118.69.183.6', '42.115.61.29']

function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function uuidFrom(rand: () => number) {
  return () => {
    const hex = Array.from({ length: 32 }, () => Math.floor(rand() * 16).toString(16))
    hex[12] = '4'
    hex[16] = ((parseInt(hex[16], 16) & 0x3) | 0x8).toString(16)
    const s = hex.join('')
    return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`
  }
}

type Review = { status: FraudAlertStatus; by: string; note?: string; afterMin: number }
type TransferEvent = {
  kind: 'transfer'
  at: Date
  from: string
  to: string
  amount: number
  description: string
  /** Amount decided at replay time: the sender's balance + this, so it is always rejected for insufficient funds. */
  overdraw?: number
  reviews?: Review[]
  freezeAfterMin?: { by: string; reason: string; afterMin: number }
}
type LoginEvent = { kind: 'login'; at: Date; who: string; ok: boolean }
type SeedEvent = TransferEvent | LoginEvent

// Everyday relationships: [from, to, min, max, step, descriptions, weight]
const FLOWS: [string, string, number, number, number, string[], number][] = [
  ['an', 'binh', 150_000, 2_400_000, 50_000, ['Trả tiền ăn tối', 'Tiền cà phê tuần này', 'Chia tiền taxi về quê', 'Góp quà sinh nhật Linh', 'Tiền lẩu thứ Sáu'], 5],
  ['binh', 'an', 200_000, 2_800_000, 50_000, ['Hoàn tiền vé xem phim', 'Trả tiền đi chợ', 'Gửi lại tiền đặt đồ online', 'Tiền phở sáng nay'], 4],
  ['binh', 'trang', 300_000, 1_200_000, 50_000, ['Học phí lớp tiếng Anh', 'Tiền mỹ phẩm đặt hộ'], 2],
  ['anh', 'an', 400_000, 1_800_000, 100_000, ['Trả nợ tuần trước', 'Tiền vé concert'], 2],
  ['an', 'anh', 150_000, 700_000, 10_000, ['Tiền trà sữa', 'Góp tiền quà cưới'], 2],
  ['mai', 'trang', 200_000, 1_500_000, 50_000, ['Tiền thuê váy cưới', 'Chia tiền khách sạn'], 2],
  ['trang', 'mai', 150_000, 900_000, 50_000, ['Trả lại tiền cọc', 'Tiền ship hàng'], 2],
  ['tung', 'long', 12_000_000, 68_000_000, 500_000, ['Thanh toán hoá đơn số 0912', 'Tạm ứng hợp đồng vận chuyển', 'Thanh toán đợt 2 HĐ 14/2026'], 3],
  ['long', 'tung', 9_000_000, 54_000_000, 500_000, ['Thanh toán lô hàng tháng 9', 'Tiền thuê kho quý IV'], 3],
  ['long', 'mai', 3_000_000, 12_000_000, 100_000, ['Hoa hồng đại lý tháng 9', 'Thưởng doanh số'], 2],
  ['thang', 'tung', 2_000_000, 9_000_000, 100_000, ['Tiền hàng điện thoại', 'Trả góp xe máy'], 1],
  ['tung', 'thang', 3_000_000, 15_000_000, 100_000, ['Đặt hàng phụ kiện', 'Hoàn tiền hàng lỗi'], 1],
]

function at(now: Date, daysAgo: number, hh: number, mm: number, ss = 0): Date {
  const day = bankDayKey(new Date(now.getTime() - daysAgo * 86_400_000))
  return new Date(bankDayStart(day).getTime() + ((hh * 60 + mm) * 60 + ss) * 1000)
}

function script(now: Date): SeedEvent[] {
  const rand = mulberry32(20261008)
  const pick = <T,>(xs: T[]) => xs[Math.floor(rand() * xs.length)]
  const events: SeedEvent[] = []
  const totalWeight = FLOWS.reduce((s, f) => s + f[6], 0)

  for (let d = 21; d >= 1; d--) {
    const n = 3 + Math.floor(rand() * 4)
    for (let i = 0; i < n; i++) {
      let r = rand() * totalWeight
      let flow = FLOWS[0]
      for (const f of FLOWS) {
        r -= f[6]
        if (r <= 0) {
          flow = f
          break
        }
      }
      const [from, to, min, max, step, descs] = flow
      if (from === 'thang' && d < 6) continue // Thắng's account is frozen from day 4
      const amount = Math.round((min + rand() * (max - min)) / step) * step
      const hh = 7 + Math.floor(rand() * 14)
      events.push({ kind: 'transfer', at: at(now, d, hh, Math.floor(rand() * 60), Math.floor(rand() * 60)), from, to, amount, description: pick(descs) })
    }
  }

  const story: TransferEvent[] = [
    { kind: 'transfer', at: at(now, 19, 9, 2), from: 'an', to: 'tung', amount: 6_500_000, description: 'Tiền nhà tháng 10 – phòng 302' },
    { kind: 'transfer', at: at(now, 17, 14, 21), from: 'thang', to: 'mai', amount: 150_000_000, description: 'Chuyển tiền mua hàng' },
    {
      kind: 'transfer', at: at(now, 16, 10, 44), from: 'long', to: 'thang', amount: 100_000_000, description: 'Đặt cọc mua đất Long An',
      reviews: [
        { status: 'UNDER_REVIEW', by: 'staff1', afterMin: 38 },
        { status: 'APPROVED', by: 'staff1', note: 'Đã gọi xác nhận với khách hàng qua số đăng ký. Giao dịch hợp lệ, có hợp đồng đặt cọc.', afterMin: 95 },
      ],
    },
    { kind: 'transfer', at: at(now, 12, 9, 15), from: 'tung', to: 'long', amount: 95_000_000, description: 'Thanh toán HĐ 2026/09 đợt 1' },
    { kind: 'transfer', at: at(now, 12, 11, 5), from: 'tung', to: 'long', amount: 95_000_000, description: 'Thanh toán HĐ 2026/09 đợt 2' },
    {
      kind: 'transfer', at: at(now, 12, 15, 30), from: 'tung', to: 'anh', amount: 25_000_000, description: 'Gửi em gái tiền học',
      reviews: [{ status: 'CLOSED', by: 'admin1', note: 'Chuyển tiền trong gia đình, không có dấu hiệu gian lận.', afterMin: 260 }],
    },
    { kind: 'transfer', at: at(now, 10, 8, 30), from: 'long', to: 'an', amount: 18_500_000, description: 'Lương tháng 9 – Công ty TNHH Hoàng Long' },
    { kind: 'transfer', at: at(now, 9, 20, 12), from: 'an', to: 'tung', amount: 120_000_000, description: 'Mua xe' },
    { kind: 'transfer', at: at(now, 6, 18, 40), from: 'binh', to: 'long', amount: 0, overdraw: 2_500_000, description: 'Thanh toán khoá học' },
    { kind: 'transfer', at: at(now, 1, 19, 5), from: 'an', to: 'mai', amount: 12_000_000, description: 'Đặt cọc tour Phú Quốc' },
    {
      kind: 'transfer', at: at(now, 2, 16, 20), from: 'long', to: 'tung', amount: 100_000_000, description: 'Thanh toán HĐ 2026/10',
      reviews: [{ status: 'UNDER_REVIEW', by: 'staff1', afterMin: 50 }],
    },
  ]
  // HIGH_FREQUENCY: six quick transfers in under a minute.
  for (let i = 0; i < 6; i++) {
    story.push({
      kind: 'transfer', at: at(now, 8, 21, 14, 3 + i * 8), from: 'anh', to: 'trang', amount: 500_000, description: 'Quỹ lớp 12A1',
      reviews: i === 5 ? [{ status: 'APPROVED', by: 'admin1', note: 'Khách hàng thu quỹ lớp, đã xác minh qua điện thoại.', afterMin: 720 }] : undefined,
    })
  }
  // Thắng: three 100M transfers to new beneficiaries in one afternoon → confirmed fraud, account frozen.
  story.push(
    { kind: 'transfer', at: at(now, 4, 13, 2), from: 'thang', to: 'trang', amount: 100_000_000, description: 'Chuyển tiền', reviews: [{ status: 'UNDER_REVIEW', by: 'staff1', afterMin: 70 }] },
    { kind: 'transfer', at: at(now, 4, 13, 9), from: 'thang', to: 'anh', amount: 100_000_000, description: 'Chuyển tiền', reviews: [{ status: 'UNDER_REVIEW', by: 'staff1', afterMin: 64 }] },
    {
      kind: 'transfer', at: at(now, 4, 13, 17), from: 'thang', to: 'long', amount: 100_000_000, description: 'CK',
      reviews: [
        { status: 'UNDER_REVIEW', by: 'staff1', afterMin: 12 },
        { status: 'REJECTED', by: 'staff1', note: 'Khách hàng báo mất điện thoại lúc 12:40. Xác nhận gian lận, đã phong toả tài khoản.', afterMin: 55 },
      ],
      freezeAfterMin: { by: 'staff1', reason: 'Nghi ngờ chiếm đoạt tài khoản – khách hàng báo mất điện thoại', afterMin: 50 },
    },
  )
  // Today, relative to "now" so it is never in the future.
  const ago = (min: number) => new Date(now.getTime() - min * 60_000)
  story.push(
    { kind: 'transfer', at: ago(212), from: 'tung', to: 'long', amount: 100_000_000, description: 'Thanh toán HĐ 2026/10 đợt 2' },
    { kind: 'transfer', at: ago(175), from: 'binh', to: 'an', amount: 1_200_000, description: 'Trả tiền vé concert' },
    { kind: 'transfer', at: ago(131), from: 'long', to: 'trang', amount: 100_000_000, description: 'Ứng vốn cửa hàng' },
    { kind: 'transfer', at: ago(96), from: 'mai', to: 'anh', amount: 0, overdraw: 30_000_000, description: 'Thanh toán tiền hàng' },
    { kind: 'transfer', at: ago(64), from: 'an', to: 'binh', amount: 350_000, description: 'Cà phê sáng' },
    { kind: 'transfer', at: ago(27), from: 'anh', to: 'mai', amount: 2_300_000, description: 'Tiền vé xe khách' },
  )
  events.push(...story)

  const logins: LoginEvent[] = [
    { kind: 'login', at: ago(240), who: 'staff1', ok: true },
    { kind: 'login', at: ago(185), who: 'customer2', ok: true },
    { kind: 'login', at: ago(70), who: 'customer1', ok: true },
    { kind: 'login', at: at(now, 1, 8, 2), who: 'auditor1', ok: true },
    { kind: 'login', at: at(now, 2, 8, 41), who: 'admin1', ok: true },
    { kind: 'login', at: at(now, 3, 22, 10), who: 'customer2', ok: false },
    { kind: 'login', at: at(now, 3, 22, 11), who: 'customer2', ok: true },
    { kind: 'login', at: at(now, 4, 12, 58), who: 'thang.bd', ok: true },
  ]
  events.push(...logins)
  return events.filter((e) => e.at.getTime() < now.getTime()).sort((a, b) => a.at.getTime() - b.at.getTime())
}

function base(now: Date, openings: Record<string, number>, ctxBase: Omit<Ctx, 'now'>): DB {
  const db = emptyDb(now)
  for (const p of PEOPLE) {
    const joined = new Date(now.getTime() - p.joinedDaysAgo * 86_400_000)
    const user: MUser = {
      id: p.userId,
      username: p.username,
      password: p.password,
      fullName: p.fullName,
      email: `${p.username}@demo.securebank.local`,
      phone: p.phone,
      roles: p.roles,
      createdAt: joined.toISOString(),
    }
    db.users.push(user)
    if (p.customerId && p.accountId && p.accountNumber) {
      db.customers.push({ id: p.customerId, userId: p.userId, fullName: p.fullName, email: user.email, phone: p.phone, createdAt: joined.toISOString() })
      const ctx = { ...ctxBase, now: new Date(joined.getTime() + 1500) }
      openAccount(db, ctx, p.customerId, { id: p.accountId, number: p.accountNumber, balance: openings[p.key] ?? 0 })
      audit(db, { ...ctx, now: joined }, { actor: user, action: 'USER_REGISTERED', resourceType: 'USER', resourceId: user.id, sourceService: 'identity-service', after: { username: user.username, roles: 'CUSTOMER' } })
      audit(db, ctx, { actor: null, action: 'ACCOUNT_OPENED', resourceType: 'ACCOUNT', resourceId: p.accountId, sourceService: 'banking-core-service', after: { accountNumber: p.accountNumber, currency: 'VND', status: 'ACTIVE' } })
      notify(db, ctx, p.userId, ['IN_APP'], 'WELCOME', { fullName: p.fullName, username: p.username })
    }
  }
  db.nextAccountNumber = 1000000107
  return db
}

function replay(db: DB, events: SeedEvent[], rand: () => number, uuid: () => string, now: Date) {
  const ctxAt = (now: Date): Ctx => ({ now, uuid, correlationId: `web-${uuid()}`, ip: IPS[Math.floor(rand() * IPS.length)] })
  const byUsername = (u: string) => db.users.find((x) => x.username === u)!
  for (const e of events) {
    if (e.kind === 'login') {
      const user = byUsername(e.who)
      audit(db, ctxAt(e.at), e.ok
        ? { actor: user, action: 'LOGIN_SUCCESS', resourceType: 'USER', resourceId: user.id, sourceService: 'identity-service' }
        : { actor: null, actorUsername: user.username, action: 'LOGIN_FAILURE', resourceType: 'USER', resourceId: null, outcome: 'FAILURE', sourceService: 'identity-service', after: { reason: 'AUTH_INVALID_CREDENTIALS' } })
      continue
    }
    const from = P[e.from]
    const to = P[e.to]
    const user = findUser(db, from.userId)!
    const source = db.accounts.find((a) => a.id === from.accountId)!
    const amount = e.overdraw ? Math.ceil((source.balance + e.overdraw) / 100_000) * 100_000 : e.amount
    const ctx = ctxAt(e.at)
    const out = executeTransfer(
      db,
      ctx,
      user,
      { sourceAccountNumber: from.accountNumber, destinationAccountNumber: to.accountNumber, amount, currency: 'VND', description: e.description },
      uuid().replace(/-/g, ''),
      (tx) => transferResponse(db, tx, from.customerId!),
    )
    const tx = out.transaction
    if (!tx) continue
    const alert = db.fraud.find((a) => a.transactionId === tx.id)
    if (e.freezeAfterMin) {
      setAccountStatus(db, ctxAt(new Date(e.at.getTime() + e.freezeAfterMin.afterMin * 60_000)), byUsername(e.freezeAfterMin.by), source.id, 'FROZEN', e.freezeAfterMin.reason)
    }
    if (alert && e.reviews) {
      for (const r of e.reviews) {
        const when = new Date(e.at.getTime() + r.afterMin * 60_000)
        if (when.getTime() > now.getTime()) continue
        reviewAlert(db, ctxAt(when), byUsername(r.by), alert.id, { status: r.status, note: r.note })
      }
    }
  }
}

function netByAccount(db: DB): Record<string, number> {
  const net: Record<string, number> = {}
  for (const l of db.ledger) net[l.accountId] = (net[l.accountId] ?? 0) + (l.entryType === 'CREDIT' ? l.amount : -l.amount)
  return net
}

/** Builds the seeded store. Opening balances are solved so every account ends exactly on its target balance. */
export function seed(now: Date = new Date()): DB {
  const events = script(now)
  let openings: Record<string, number> = Object.fromEntries(PEOPLE.filter((p) => p.target).map((p) => [p.key, p.target!]))
  let db: DB | null = null
  for (let iter = 0; iter < 10; iter++) {
    const rand = mulberry32(4242)
    const uuid = uuidFrom(mulberry32(777))
    db = base(now, openings, { uuid, correlationId: null, ip: null })
    replay(db, events, rand, uuid, now)
    const net = netByAccount(db)
    const next: Record<string, number> = {}
    for (const p of PEOPLE) {
      if (!p.target || !p.accountId) continue
      next[p.key] = Math.max(500_000, p.target - (net[p.accountId] ?? 0))
    }
    // Fixed point: the same openings produce the same history, so we're done.
    if (PEOPLE.every((p) => !p.target || next[p.key] === openings[p.key])) break
    openings = next
  }
  const result = db!
  // Older notifications have been read; keep the last day and a half unread.
  const cutoff = now.getTime() - 36 * 3_600_000
  for (const n of result.notifications) {
    if (n.channel !== 'IN_APP' || new Date(n.createdAt).getTime() < cutoff) n.read = true
  }
  result.idempotency = [] // seeded keys are internal; real clients start clean
  return result
}
