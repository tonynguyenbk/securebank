import { formatAmount } from '../utils/format'

type Props = {
  value: number
  /** Ledger sign column. "auto" derives it from the value's sign. */
  sign?: '+' | '-' | 'auto' | 'none'
  /** Colour credits/debits. */
  tone?: boolean
  currency?: boolean
  className?: string
}

/** Amount in Plex Mono, vi-VN grouping, with a fixed-width sign column so +/− line up like a ledger. */
export function Money({ value, sign = 'none', tone = false, currency = true, className = '' }: Props) {
  const s = sign === 'auto' ? (value < 0 ? '-' : value > 0 ? '+' : 'none') : sign
  const color = tone ? (s === '+' ? 'text-credit' : s === '-' ? 'text-debit' : '') : ''
  return (
    <span className={`figures inline-flex items-baseline justify-end whitespace-nowrap ${color} ${className}`}>
      {s !== 'none' && (
        <span className="inline-block w-[1.1ch] text-center" aria-hidden>
          {s === '+' ? '+' : '−'}
        </span>
      )}
      {s !== 'none' && <span className="sr-only">{s === '+' ? '+' : '-'}</span>}
      {formatAmount(value)}
      {currency && <span className="ml-1 opacity-70">₫</span>}
    </span>
  )
}
