import type { SlipData } from '../../components/LedgerSlip'
import type { AdminTransactionDetail, TransactionDetail, TransferResponse } from '../../types/api'

/** Receipt right after POST /transfers. The caller is the sender. */
export function slipFromTransfer(res: TransferResponse, senderName: string): SlipData {
  return {
    reference: res.transactionReference,
    status: res.status,
    amount: res.amount,
    description: res.description,
    createdAt: res.createdAt,
    completedAt: res.completedAt,
    from: { accountNumber: res.sourceAccountNumber, name: senderName },
    to: { accountNumber: res.destinationAccountNumber, name: res.destinationHolderName },
    lines: res.ledgerEntries.map((l) => ({ ...l, holder: l.entryType === 'DEBIT' ? senderName : res.destinationHolderName })),
    remainingBalance: res.remainingBalance,
  }
}

/** Customer view of a stored transaction: the caller is either side. */
export function slipFromDetail(tx: TransactionDetail, myName: string): SlipData {
  const out = tx.direction === 'OUT'
  const from = { accountNumber: tx.sourceAccountNumber, name: out ? myName : tx.counterpartyName }
  const to = { accountNumber: tx.destinationAccountNumber, name: out ? tx.counterpartyName : myName }
  const mine = tx.ledgerEntries.find((l) => l.entryType === (out ? 'DEBIT' : 'CREDIT'))
  return {
    reference: tx.transactionReference,
    status: tx.status,
    amount: tx.amount,
    description: tx.description,
    createdAt: tx.createdAt,
    completedAt: tx.completedAt,
    from,
    to,
    lines: tx.ledgerEntries.map((l) => ({ ...l, holder: l.entryType === 'DEBIT' ? from.name : to.name })),
    remainingBalance: mine?.balanceAfter ?? null,
    failureCode: tx.failureCode,
  }
}

export function slipFromAdmin(tx: AdminTransactionDetail): SlipData {
  return {
    reference: tx.transactionReference,
    status: tx.status,
    amount: tx.amount,
    description: tx.description,
    createdAt: tx.createdAt,
    completedAt: tx.completedAt,
    from: { accountNumber: tx.sourceAccountNumber, name: tx.sourceCustomerName },
    to: { accountNumber: tx.destinationAccountNumber, name: tx.destinationCustomerName ?? '' },
    lines: tx.ledgerEntries.map((l) => ({
      entryType: l.entryType,
      accountNumber: l.accountNumber,
      amount: l.amount,
      balanceBefore: l.balanceBefore,
      balanceAfter: l.balanceAfter,
      holder: l.entryType === 'DEBIT' ? tx.sourceCustomerName : (tx.destinationCustomerName ?? ''),
    })),
    failureCode: tx.failureCode,
  }
}
