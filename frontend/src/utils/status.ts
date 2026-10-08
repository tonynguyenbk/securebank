import type { TransactionStatus } from '../types/api'

export type StampKind = 'POSTED' | 'REJECTED' | 'FAILED' | 'PENDING' | 'FROZEN'

export function stampFor(status: TransactionStatus): StampKind {
  return status === 'SUCCESS' ? 'POSTED' : status
}
