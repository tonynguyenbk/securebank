import type { AccountDetail, AccountSummary } from '../../types/api'

export type TransferValues = { sourceId: string; destination: string; amount: string; description: string }
export type TransferField = 'source' | 'destination' | 'amount' | 'description'
/** i18n keys (or `errors.<CODE>`) per field, plus one form-level message. */
export type TransferErrors = Partial<Record<TransferField | 'form', string>>

export const DESCRIPTION_MAX = 255

/** Client-side checks mirroring the server's rules, so most problems surface before submitting. */
export function validateTransfer(v: TransferValues, source: AccountSummary | undefined, detail: AccountDetail | undefined): TransferErrors {
  const e: TransferErrors = {}
  if (!source) e.source = 'transfer.errors.sourceRequired'
  else if (source.status === 'FROZEN') e.source = 'errors.ACCOUNT_FROZEN'
  else if (source.status === 'CLOSED') e.source = 'errors.ACCOUNT_CLOSED'

  if (!v.destination) e.destination = 'transfer.errors.destinationRequired'
  else if (!/^\d{10}$/.test(v.destination)) e.destination = 'transfer.errors.destinationFormat'
  else if (source && v.destination === source.accountNumber) e.destination = 'errors.SAME_ACCOUNT_TRANSFER'

  const amount = Number(v.amount || 0)
  if (!amount) e.amount = 'errors.INVALID_TRANSFER_AMOUNT'
  else if (detail && amount > detail.limits.perTransactionLimit) e.amount = 'errors.TRANSFER_LIMIT_EXCEEDED'
  else if (detail && amount > detail.limits.remainingToday) e.amount = 'errors.DAILY_LIMIT_EXCEEDED'
  else if (source && amount > source.balance) e.amount = 'errors.INSUFFICIENT_FUNDS'

  if (v.description.length > DESCRIPTION_MAX) e.description = 'transfer.errors.descriptionLength'
  return e
}

/** Where a server error code belongs on the form. */
export function fieldForCode(code: string): TransferField | 'form' {
  switch (code) {
    case 'ACCOUNT_FROZEN':
    case 'ACCOUNT_NOT_OWNED':
      return 'source'
    case 'ACCOUNT_NOT_FOUND':
    case 'SAME_ACCOUNT_TRANSFER':
    case 'ACCOUNT_CLOSED':
    case 'CURRENCY_MISMATCH':
      return 'destination'
    case 'INSUFFICIENT_FUNDS':
    case 'INVALID_TRANSFER_AMOUNT':
    case 'TRANSFER_LIMIT_EXCEEDED':
    case 'DAILY_LIMIT_EXCEEDED':
      return 'amount'
    default:
      return 'form'
  }
}

export function fieldForValidation(field: string): TransferField | 'form' {
  if (field === 'sourceAccountNumber') return 'source'
  if (field === 'destinationAccountNumber') return 'destination'
  if (field === 'amount') return 'amount'
  if (field === 'description') return 'description'
  return 'form'
}
