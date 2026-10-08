import type {
  AccountAdmin,
  AccountAdminDetail,
  AccountBalance,
  AccountDetail,
  AccountLimits,
  AccountLookup,
  AccountStatus,
  AccountSummary,
  AdminTransaction,
  AdminTransactionDetail,
  AdminTransactionFilters,
  AuditFilters,
  AuditLog,
  AuditLogDetail,
  CreateTransferRequest,
  CustomerAdmin,
  CustomerAdminDetail,
  CustomerResponse,
  DailyStat,
  FraudAlertDetail,
  FraudAlertFilters,
  FraudAlertStatus,
  FraudAlertSummary,
  FraudStats,
  LoginRequest,
  Notification,
  NotificationFilters,
  OpsStatsToday,
  PageResponse,
  Reconciliation,
  RegisterRequest,
  StatementEntry,
  TokenResponse,
  TransactionDetail,
  TransactionSummary,
  TransferFilters,
  TransferResponse,
  UserResponse,
} from '../types/api'
import { get, http } from './client'

type Paging = { page?: number; size?: number }

// ── Identity ────────────────────────────────────────────────────────────────
export const authApi = {
  login: (body: LoginRequest) => http.post<TokenResponse>('/auth/login', body).then((r) => r.data),
  register: (body: RegisterRequest) => http.post<UserResponse>('/auth/register', body).then((r) => r.data),
  logout: (refreshToken: string) => http.post<void>('/auth/logout', { refreshToken }).then(() => undefined),
  me: () => get<UserResponse>('/auth/me'),
}

// ── Banking core: customer ──────────────────────────────────────────────────
export const customerApi = {
  me: () => get<CustomerResponse>('/customers/me'),
}

export const accountsApi = {
  list: () => get<AccountSummary[]>('/accounts'),
  detail: (id: string) => get<AccountDetail>(`/accounts/${id}`),
  balance: (id: string) => get<AccountBalance>(`/accounts/${id}/balance`),
  statement: (id: string, params: { fromDate?: string; toDate?: string } & Paging) =>
    get<PageResponse<StatementEntry>>(`/accounts/${id}/statement`, params),
  lookup: (accountNumber: string, signal?: AbortSignal) => get<AccountLookup>('/accounts/lookup', { accountNumber }, { signal }),
}

export type TransferResult = { data: TransferResponse; replayed: boolean }

export const transfersApi = {
  create: async (body: CreateTransferRequest, idempotencyKey: string): Promise<TransferResult> => {
    const res = await http.post<TransferResponse>('/transfers', body, { headers: { 'Idempotency-Key': idempotencyKey } })
    return { data: res.data, replayed: String(res.headers['idempotent-replayed']) === 'true' }
  },
  list: (filters: TransferFilters) => get<PageResponse<TransactionSummary>>('/transfers', filters),
  detail: (id: string) => get<TransactionDetail>(`/transfers/${id}`),
}

// ── Banking core: staff / admin ─────────────────────────────────────────────
export const adminApi = {
  customers: (params: { q?: string } & Paging) => get<PageResponse<CustomerAdmin>>('/admin/customers', params),
  customer: (id: string) => get<CustomerAdminDetail>(`/admin/customers/${id}`),
  accounts: (params: { q?: string; status?: AccountStatus } & Paging) => get<PageResponse<AccountAdmin>>('/admin/accounts', params),
  account: (id: string) => get<AccountAdminDetail>(`/admin/accounts/${id}`),
  freeze: (id: string, reason: string) => http.patch<AccountAdmin>(`/admin/accounts/${id}/freeze`, { reason }).then((r) => r.data),
  unfreeze: (id: string, reason: string) => http.patch<AccountAdmin>(`/admin/accounts/${id}/unfreeze`, { reason }).then((r) => r.data),
  limits: (id: string) => get<AccountLimits>(`/admin/accounts/${id}/limits`),
  updateLimits: (id: string, body: { perTransactionLimit: number; dailyLimit: number }) =>
    http.put<AccountLimits>(`/admin/accounts/${id}/limits`, body).then((r) => r.data),
  transactions: (filters: AdminTransactionFilters) => get<PageResponse<AdminTransaction>>('/admin/transactions', filters),
  transaction: (id: string) => get<AdminTransactionDetail>(`/admin/transactions/${id}`),
  reconciliation: (id: string) => get<Reconciliation>(`/admin/reconciliation/transactions/${id}`),
  statsToday: () => get<OpsStatsToday>('/admin/stats/today'),
  statsDaily: (days = 14) => get<DailyStat[]>('/admin/stats/daily', { days }),
}

// ── Fraud ───────────────────────────────────────────────────────────────────
export const fraudApi = {
  alerts: (filters: FraudAlertFilters) => get<PageResponse<FraudAlertSummary>>('/fraud/alerts', filters),
  alert: (id: string) => get<FraudAlertDetail>(`/fraud/alerts/${id}`),
  review: (id: string, body: { status: FraudAlertStatus; note?: string }) =>
    http.patch<FraudAlertDetail>(`/fraud/alerts/${id}/review`, body).then((r) => r.data),
  stats: () => get<FraudStats>('/fraud/alerts/stats'),
}

// ── Audit ───────────────────────────────────────────────────────────────────
export const auditApi = {
  logs: (filters: AuditFilters) => get<PageResponse<AuditLog>>('/audit/logs', filters),
  log: (id: string) => get<AuditLogDetail>(`/audit/logs/${id}`),
  actions: () => get<string[]>('/audit/logs/actions'),
}

// ── Notifications ───────────────────────────────────────────────────────────
export const notificationsApi = {
  mine: (filters: NotificationFilters) => get<PageResponse<Notification>>('/notifications/me', filters),
  unreadCount: () => get<{ count: number }>('/notifications/me/unread-count'),
  markRead: (id: string) => http.patch<void>(`/notifications/${id}/read`).then(() => undefined),
}
