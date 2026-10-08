// Types mirror docs/contracts/api.md (v1, frozen). Keep field names and unions in sync with that file.

export type Role = 'CUSTOMER' | 'BANK_STAFF' | 'AUDITOR' | 'ADMIN'
export const STAFF_ROLES: Role[] = ['BANK_STAFF', 'AUDITOR', 'ADMIN']

export type PageResponse<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export type ApiErrorBody = {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  correlationId?: string
  fieldErrors?: { field: string; message: string }[]
}

// ── Identity ────────────────────────────────────────────────────────────────
export type UserResponse = {
  id: string
  username: string
  fullName: string
  email: string
  phone: string | null
  roles: Role[]
  createdAt: string
}
export type TokenResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  user: UserResponse
}
export type RegisterRequest = { username: string; password: string; fullName: string; email: string; phone?: string }
export type LoginRequest = { username: string; password: string }

// ── Banking core: customer ──────────────────────────────────────────────────
export type CustomerResponse = { id: string; userId: string; fullName: string; email: string; phone: string | null; createdAt: string }

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED'
export type AccountSummary = {
  id: string
  accountNumber: string
  type: 'CURRENT'
  currency: 'VND'
  balance: number
  status: AccountStatus
  createdAt: string
}
export type TransferLimits = {
  perTransactionLimit: number
  dailyLimit: number
  usedToday: number
  remainingToday: number
  updatedAt: string
}
export type AccountDetail = AccountSummary & { customerId: string; limits: TransferLimits; updatedAt: string }
export type AccountBalance = {
  accountId: string
  accountNumber: string
  balance: number
  currency: 'VND'
  status: AccountStatus
  asOf: string
}
export type AccountLookup = { accountNumber: string; holderName: string; currency: 'VND' }

export type EntryType = 'DEBIT' | 'CREDIT'
export type LedgerLine = {
  entryType: EntryType
  accountNumber: string
  amount: number
  balanceBefore: number | null
  balanceAfter: number | null
  createdAt: string
}
export type TransactionStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REJECTED'

export type TransferResponse = {
  transactionId: string
  transactionReference: string
  status: TransactionStatus
  sourceAccountNumber: string
  destinationAccountNumber: string
  destinationHolderName: string
  amount: number
  currency: 'VND'
  description: string | null
  remainingBalance: number
  createdAt: string
  completedAt: string | null
  ledgerEntries: LedgerLine[]
}
export type CreateTransferRequest = {
  sourceAccountNumber: string
  destinationAccountNumber: string
  amount: number
  currency: 'VND'
  description?: string
}

export type TransactionSummary = {
  id: string
  transactionReference: string
  direction: 'OUT' | 'IN'
  sourceAccountNumber: string
  destinationAccountNumber: string
  counterpartyName: string
  amount: number
  currency: 'VND'
  description: string | null
  status: TransactionStatus
  failureCode: string | null
  createdAt: string
}
export type TransactionDetail = TransactionSummary & {
  failureReason: string | null
  completedAt: string | null
  ledgerEntries: LedgerLine[]
}
export type StatementEntry = {
  id: string
  transactionId: string
  transactionReference: string
  entryType: EntryType
  amount: number
  balanceBefore: number
  balanceAfter: number
  counterpartyAccountNumber: string
  counterpartyName: string
  description: string | null
  createdAt: string
}

export type TransferFilters = {
  accountId?: string
  status?: TransactionStatus
  fromDate?: string
  toDate?: string
  minAmount?: number
  maxAmount?: number
  page?: number
  size?: number
  sort?: string
}

// ── Banking core: staff / admin ─────────────────────────────────────────────
export type CustomerAdmin = {
  id: string
  userId: string
  fullName: string
  email: string
  phone: string | null
  accountCount: number
  createdAt: string
}
export type AccountAdmin = {
  id: string
  accountNumber: string
  customerId: string
  customerName: string
  currency: 'VND'
  balance: number
  status: AccountStatus
  createdAt: string
  updatedAt: string
}
export type CustomerAdminDetail = CustomerAdmin & { accounts: AccountAdmin[] }
export type AccountAdminDetail = AccountAdmin & { limits: TransferLimits }
export type AccountLimits = TransferLimits & { accountId: string }

export type AdminTransaction = {
  id: string
  transactionReference: string
  sourceAccountId: string
  sourceAccountNumber: string
  sourceCustomerName: string
  destinationAccountId: string | null
  destinationAccountNumber: string
  destinationCustomerName: string | null
  amount: number
  currency: 'VND'
  description: string | null
  status: TransactionStatus
  failureCode: string | null
  failureReason: string | null
  createdBy: string
  createdAt: string
  completedAt: string | null
}
export type AdminLedgerEntry = {
  id: string
  accountId: string
  accountNumber: string
  entryType: EntryType
  amount: number
  balanceBefore: number
  balanceAfter: number
  createdAt: string
}
export type AdminTransactionDetail = AdminTransaction & { ledgerEntries: AdminLedgerEntry[] }
export type Reconciliation = {
  transactionId: string
  transactionReference: string
  status: TransactionStatus
  entryCount: number
  debitTotal: number
  creditTotal: number
  balanced: boolean
  entries: AdminLedgerEntry[]
  checkedAt: string
}
export type OpsStatsToday = {
  date: string
  transactionsToday: number
  successfulToday: number
  failedOrRejectedToday: number
  totalTransferredToday: number
  frozenAccounts: number
  currency: 'VND'
}
export type DailyStat = { date: string; count: number; amount: number }

export type AdminTransactionFilters = {
  status?: TransactionStatus
  accountNumber?: string
  reference?: string
  fromDate?: string
  toDate?: string
  minAmount?: number
  maxAmount?: number
  page?: number
  size?: number
  sort?: string
}

// ── Fraud ───────────────────────────────────────────────────────────────────
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type FraudAlertStatus = 'OPEN' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'CLOSED'
export type FraudRuleCode = 'HIGH_AMOUNT' | 'HIGH_FREQUENCY' | 'DAILY_VELOCITY' | 'NEW_BENEFICIARY'
export type FraudAlertSummary = {
  id: string
  transactionId: string
  transactionReference: string
  customerId: string
  customerName: string
  sourceAccountId: string
  sourceAccountNumber: string
  amount: number
  currency: 'VND'
  riskScore: number
  riskLevel: RiskLevel
  status: FraudAlertStatus
  createdAt: string
}
export type FraudRuleHit = { ruleCode: FraudRuleCode; description: string; scoreContribution: number; details: string }
export type FraudTimelineEntry = { at: string; status: FraudAlertStatus; actorUsername: string | null; note: string | null }
export type FraudAlertDetail = FraudAlertSummary & {
  destinationAccountNumber: string
  destinationCustomerName: string
  transactionOccurredAt: string
  rules: FraudRuleHit[]
  reviewedBy: string | null
  reviewedByUsername: string | null
  reviewedAt: string | null
  reviewNote: string | null
  timeline: FraudTimelineEntry[]
}
export type FraudStats = { open: number; underReview: number; critical: number; high: number; createdToday: number }
export type FraudAlertFilters = {
  status?: FraudAlertStatus
  riskLevel?: RiskLevel
  customerId?: string
  page?: number
  size?: number
  sort?: string
}

/** Allowed review transitions (contract §4). CLOSED is terminal. */
export const FRAUD_TRANSITIONS: Record<FraudAlertStatus, FraudAlertStatus[]> = {
  OPEN: ['UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CLOSED'],
  UNDER_REVIEW: ['APPROVED', 'REJECTED', 'CLOSED'],
  APPROVED: ['CLOSED'],
  REJECTED: ['CLOSED'],
  CLOSED: [],
}
export const NOTE_REQUIRED: FraudAlertStatus[] = ['APPROVED', 'REJECTED', 'CLOSED']

// ── Audit ───────────────────────────────────────────────────────────────────
export type AuditLog = {
  id: string
  eventId: string
  occurredAt: string
  receivedAt: string
  actorUserId: string | null
  actorUsername: string | null
  actorRole: string | null
  action: string
  resourceType: string
  resourceId: string | null
  outcome: 'SUCCESS' | 'FAILURE'
  correlationId: string | null
  sourceService: string
  ipAddress?: string | null
}
export type AuditLogDetail = AuditLog & { before: Record<string, unknown> | null; after: Record<string, unknown> | null }
export type AuditFilters = {
  actor?: string
  actorUserId?: string
  action?: string
  resourceType?: string
  resourceId?: string
  from?: string
  to?: string
  correlationId?: string
  page?: number
  size?: number
}
export const AUDIT_RESOURCE_TYPES = ['USER', 'ACCOUNT', 'TRANSACTION', 'FRAUD_ALERT'] as const

// ── Notifications ───────────────────────────────────────────────────────────
export type NotificationTemplate =
  | 'TRANSFER_SENT'
  | 'TRANSFER_RECEIVED'
  | 'TRANSFER_REJECTED'
  | 'ACCOUNT_FROZEN'
  | 'ACCOUNT_UNFROZEN'
  | 'WELCOME'
export type Notification = {
  id: string
  channel: 'EMAIL' | 'SMS' | 'IN_APP'
  status: 'PENDING' | 'SENT' | 'FAILED'
  templateCode: NotificationTemplate
  params: Record<string, string | number>
  subject: string
  message: string
  read: boolean
  relatedTransactionId: string | null
  createdAt: string
  sentAt: string | null
}
export type NotificationFilters = { channel?: Notification['channel']; unreadOnly?: boolean; page?: number; size?: number }
