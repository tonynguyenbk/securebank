import type {
  AccountStatus,
  AdminLedgerEntry,
  AuditLogDetail,
  FraudAlertDetail,
  Notification,
  Role,
  TransactionStatus,
} from '../types/api'

// In-memory store behind the preview's mock API. Persisted to sessionStorage so a reload keeps state.

export type MUser = {
  id: string
  username: string
  password: string
  fullName: string
  email: string
  phone: string | null
  roles: Role[]
  createdAt: string
}
export type MCustomer = { id: string; userId: string; fullName: string; email: string; phone: string | null; createdAt: string }
export type MAccount = {
  id: string
  accountNumber: string
  customerId: string
  balance: number
  status: AccountStatus
  createdAt: string
  updatedAt: string
  perTransactionLimit: number
  dailyLimit: number
  limitsUpdatedAt: string
}
export type MTransaction = {
  id: string
  reference: string
  sourceAccountId: string
  sourceAccountNumber: string
  destinationAccountId: string | null
  destinationAccountNumber: string
  amount: number
  description: string | null
  status: TransactionStatus
  failureCode: string | null
  failureReason: string | null
  createdBy: string
  createdAt: string
  completedAt: string | null
}
export type MLedger = AdminLedgerEntry & { transactionId: string }
export type MIdempotency = { userId: string; key: string; hash: string; status: number; body: unknown }
export type MNotification = Notification & { recipientUserId: string }
export type MRefreshToken = { token: string; userId: string; revoked: boolean; expiresAt: number }

export type DB = {
  version: number
  seededAt: string
  users: MUser[]
  customers: MCustomer[]
  accounts: MAccount[]
  transactions: MTransaction[]
  ledger: MLedger[]
  idempotency: MIdempotency[]
  fraud: FraudAlertDetail[]
  audit: AuditLogDetail[]
  notifications: MNotification[]
  refreshTokens: MRefreshToken[]
  denylist: string[]
  refSeq: Record<string, number>
  nextAccountNumber: number
  loginFailures: Record<string, number[]>
}

export const DB_VERSION = 4
const STORAGE_KEY = 'sb.mock.db'

export function emptyDb(now: Date): DB {
  return {
    version: DB_VERSION,
    seededAt: now.toISOString(),
    users: [],
    customers: [],
    accounts: [],
    transactions: [],
    ledger: [],
    idempotency: [],
    fraud: [],
    audit: [],
    notifications: [],
    refreshTokens: [],
    denylist: [],
    refSeq: {},
    nextAccountNumber: 1000000101,
    loginFailures: {},
  }
}

export function loadDb(): DB | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const db = JSON.parse(raw) as DB
    return db.version === DB_VERSION ? db : null
  } catch {
    return null
  }
}

export function saveDb(db: DB) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(db))
  } catch {
    // quota or blocked storage: state lives in memory until reload
  }
}

export function clearDb() {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
}

// ── Lookups ─────────────────────────────────────────────────────────────────
export const findUser = (db: DB, id: string) => db.users.find((u) => u.id === id)
export const findCustomer = (db: DB, id: string) => db.customers.find((c) => c.id === id)
export const customerOfUser = (db: DB, userId: string) => db.customers.find((c) => c.userId === userId)
export const findAccount = (db: DB, id: string) => db.accounts.find((a) => a.id === id)
export const accountByNumber = (db: DB, n: string) => db.accounts.find((a) => a.accountNumber === n)
export const customerNameOfAccount = (db: DB, a: MAccount | undefined) => (a ? (findCustomer(db, a.customerId)?.fullName ?? '') : '')
