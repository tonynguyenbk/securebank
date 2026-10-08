import type {
  AccountAdmin,
  AccountDetail,
  AccountSummary,
  AdminLedgerEntry,
  AdminTransaction,
  AuditLog,
  AuditLogDetail,
  CustomerAdmin,
  FraudAlertSummary,
  LedgerLine,
  Notification,
  StatementEntry,
  TransactionDetail,
  TransactionSummary,
  TransferResponse,
  UserResponse,
} from '../types/api'
import { accountByNumber, customerNameOfAccount, findAccount, findCustomer, findUser, type DB, type MAccount, type MLedger, type MNotification, type MTransaction, type MUser } from './db'
import { limitsOf } from './engine'

export const maskNumber = (n: string) => `******${n.slice(-4)}`

export function userView(u: MUser): UserResponse {
  return { id: u.id, username: u.username, fullName: u.fullName, email: u.email, phone: u.phone, roles: u.roles, createdAt: u.createdAt }
}

export function accountSummary(a: MAccount): AccountSummary {
  return { id: a.id, accountNumber: a.accountNumber, type: 'CURRENT', currency: 'VND', balance: a.balance, status: a.status, createdAt: a.createdAt }
}

export function accountDetail(db: DB, a: MAccount, now: Date): AccountDetail {
  return { ...accountSummary(a), customerId: a.customerId, limits: limitsOf(db, a, now), updatedAt: a.updatedAt }
}

function ledgerOf(db: DB, txId: string): MLedger[] {
  return db.ledger.filter((l) => l.transactionId === txId).sort((a, b) => (a.entryType === 'DEBIT' ? -1 : b.entryType === 'DEBIT' ? 1 : 0))
}

/** Customer view of the two ledger lines: the counterparty's number is masked and its balances hidden. */
function customerLedger(db: DB, txId: string, customerId: string): LedgerLine[] {
  return ledgerOf(db, txId).map((l) => {
    const own = findAccount(db, l.accountId)?.customerId === customerId
    return {
      entryType: l.entryType,
      accountNumber: own ? l.accountNumber : maskNumber(l.accountNumber),
      amount: l.amount,
      balanceBefore: own ? l.balanceBefore : null,
      balanceAfter: own ? l.balanceAfter : null,
      createdAt: l.createdAt,
    }
  })
}

export function transferResponse(db: DB, tx: MTransaction, customerId: string): TransferResponse {
  const source = findAccount(db, tx.sourceAccountId)
  const dest = accountByNumber(db, tx.destinationAccountNumber)
  return {
    transactionId: tx.id,
    transactionReference: tx.reference,
    status: tx.status,
    sourceAccountNumber: tx.sourceAccountNumber,
    destinationAccountNumber: tx.destinationAccountNumber,
    destinationHolderName: customerNameOfAccount(db, dest),
    amount: tx.amount,
    currency: 'VND',
    description: tx.description,
    remainingBalance: ledgerOf(db, tx.id).find((l) => l.entryType === 'DEBIT')?.balanceAfter ?? source?.balance ?? 0,
    createdAt: tx.createdAt,
    completedAt: tx.completedAt,
    ledgerEntries: customerLedger(db, tx.id, customerId),
  }
}

export function transactionSummary(db: DB, tx: MTransaction, customerId: string): TransactionSummary {
  const source = findAccount(db, tx.sourceAccountId)
  const dest = tx.destinationAccountId ? findAccount(db, tx.destinationAccountId) : undefined
  const out = source?.customerId === customerId
  return {
    id: tx.id,
    transactionReference: tx.reference,
    direction: out ? 'OUT' : 'IN',
    sourceAccountNumber: tx.sourceAccountNumber,
    destinationAccountNumber: tx.destinationAccountNumber,
    counterpartyName: out ? customerNameOfAccount(db, dest) : customerNameOfAccount(db, source),
    amount: tx.amount,
    currency: 'VND',
    description: tx.description,
    status: tx.status,
    failureCode: tx.failureCode,
    createdAt: tx.createdAt,
  }
}

export function transactionDetail(db: DB, tx: MTransaction, customerId: string): TransactionDetail {
  return {
    ...transactionSummary(db, tx, customerId),
    failureReason: tx.failureReason,
    completedAt: tx.completedAt,
    ledgerEntries: customerLedger(db, tx.id, customerId),
  }
}

export function statementEntries(db: DB, account: MAccount): StatementEntry[] {
  return db.ledger
    .filter((l) => l.accountId === account.id)
    .map((l) => {
      const tx = db.transactions.find((t) => t.id === l.transactionId)!
      const counterNumber = l.entryType === 'DEBIT' ? tx.destinationAccountNumber : tx.sourceAccountNumber
      return {
        id: l.id,
        transactionId: tx.id,
        transactionReference: tx.reference,
        entryType: l.entryType,
        amount: l.amount,
        balanceBefore: l.balanceBefore,
        balanceAfter: l.balanceAfter,
        counterpartyAccountNumber: maskNumber(counterNumber),
        counterpartyName: customerNameOfAccount(db, accountByNumber(db, counterNumber)),
        description: tx.description,
        createdAt: l.createdAt,
      }
    })
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

// ── Admin ───────────────────────────────────────────────────────────────────
export function customerAdmin(db: DB, customerId: string): CustomerAdmin {
  const c = findCustomer(db, customerId)!
  return {
    id: c.id,
    userId: c.userId,
    fullName: c.fullName,
    email: c.email,
    phone: c.phone,
    accountCount: db.accounts.filter((a) => a.customerId === c.id).length,
    createdAt: c.createdAt,
  }
}

export function accountAdmin(db: DB, a: MAccount): AccountAdmin {
  return {
    id: a.id,
    accountNumber: a.accountNumber,
    customerId: a.customerId,
    customerName: customerNameOfAccount(db, a),
    currency: 'VND',
    balance: a.balance,
    status: a.status,
    createdAt: a.createdAt,
    updatedAt: a.updatedAt,
  }
}

export function adminTransaction(db: DB, tx: MTransaction): AdminTransaction {
  const source = findAccount(db, tx.sourceAccountId)
  const dest = tx.destinationAccountId ? findAccount(db, tx.destinationAccountId) : undefined
  return {
    id: tx.id,
    transactionReference: tx.reference,
    sourceAccountId: tx.sourceAccountId,
    sourceAccountNumber: tx.sourceAccountNumber,
    sourceCustomerName: customerNameOfAccount(db, source),
    destinationAccountId: tx.destinationAccountId,
    destinationAccountNumber: tx.destinationAccountNumber,
    destinationCustomerName: dest ? customerNameOfAccount(db, dest) : null,
    amount: tx.amount,
    currency: 'VND',
    description: tx.description,
    status: tx.status,
    failureCode: tx.failureCode,
    failureReason: tx.failureReason,
    createdBy: findUser(db, tx.createdBy)?.username ?? tx.createdBy,
    createdAt: tx.createdAt,
    completedAt: tx.completedAt,
  }
}

export function adminLedger(db: DB, txId: string): AdminLedgerEntry[] {
  return ledgerOf(db, txId).map(({ transactionId: _t, ...rest }) => rest)
}

export function fraudSummary(a: FraudAlertSummary): FraudAlertSummary {
  return {
    id: a.id,
    transactionId: a.transactionId,
    transactionReference: a.transactionReference,
    customerId: a.customerId,
    customerName: a.customerName,
    sourceAccountId: a.sourceAccountId,
    sourceAccountNumber: a.sourceAccountNumber,
    amount: a.amount,
    currency: a.currency,
    riskScore: a.riskScore,
    riskLevel: a.riskLevel,
    status: a.status,
    createdAt: a.createdAt,
  }
}

export function auditView(log: AuditLogDetail, restricted: boolean): AuditLog {
  const { before: _b, after: _a, ...rest } = log
  if (restricted) {
    const { ipAddress: _ip, ...noIp } = rest
    return noIp
  }
  return rest
}

export function auditDetailView(log: AuditLogDetail, restricted: boolean): AuditLogDetail {
  if (!restricted) return log
  const { ipAddress: _ip, ...noIp } = log
  return noIp
}

export function notificationView(n: MNotification): Notification {
  const { recipientUserId: _r, ...rest } = n
  return rest
}
