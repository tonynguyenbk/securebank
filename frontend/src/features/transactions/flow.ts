import type { TransactionSummary } from '../../types/api'
import { bankDayKey } from '../../utils/format'

export type Day = { day: string; inflow: number; outflow: number }

export function buildDays(items: TransactionSummary[], days: number, now = new Date()): Day[] {
  const map = new Map<string, Day>()
  for (let i = days - 1; i >= 0; i--) {
    const day = bankDayKey(new Date(now.getTime() - i * 86_400_000))
    map.set(day, { day, inflow: 0, outflow: 0 })
  }
  for (const tx of items) {
    if (tx.status !== 'SUCCESS') continue
    const d = map.get(bankDayKey(new Date(tx.createdAt)))
    if (!d) continue
    if (tx.direction === 'IN') d.inflow += tx.amount
    else d.outflow += tx.amount
  }
  return [...map.values()]
}

