// Formatting helpers. Amounts always use vi-VN grouping (25.000.000 ₫) in both languages;
// dates/times are always shown in the bank's zone, Asia/Ho_Chi_Minh, in the selected language.

export const BANK_TZ = 'Asia/Ho_Chi_Minh'

const vnd = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 })

/** "25.000.000" — grouping only, no sign, no currency. */
export function formatAmount(value: number): string {
  return vnd.format(Math.abs(value))
}

/** "25.000.000 ₫" */
export function formatVnd(value: number): string {
  return `${formatAmount(value)} ₫`
}

/** Compact form for chart axes: 1,2 tr / 150 tr / 1,5 tỷ (VI) — 1.2M / 150M / 1.5B (EN). */
export function formatCompactVnd(value: number, lang: string): string {
  return new Intl.NumberFormat(lang === 'vi' ? 'vi-VN' : 'en-US', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}

function locale(lang: string) {
  return lang === 'vi' ? 'vi-VN' : 'en-GB'
}

export function formatDateTime(iso: string | null | undefined, lang: string): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(locale(lang), {
    timeZone: BANK_TZ,
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(iso))
}

export function formatDateTimeSeconds(iso: string | null | undefined, lang: string): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(locale(lang), {
    timeZone: BANK_TZ,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(iso))
}

export function formatDate(iso: string | null | undefined, lang: string): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(locale(lang), { timeZone: BANK_TZ, day: '2-digit', month: 'short', year: 'numeric' }).format(
    new Date(iso),
  )
}

/** "08 Oct" — ledger-style short date column. */
export function formatShortDate(iso: string, lang: string): string {
  // Vietnamese short months ("thg 10") wrap in narrow columns; banks print dd/MM instead.
  const opts: Intl.DateTimeFormatOptions = lang === 'vi' ? { timeZone: BANK_TZ, day: '2-digit', month: '2-digit' } : { timeZone: BANK_TZ, day: '2-digit', month: 'short' }
  return new Intl.DateTimeFormat(locale(lang), opts).format(new Date(iso))
}

export function formatTime(iso: string, lang: string): string {
  return new Intl.DateTimeFormat(locale(lang), { timeZone: BANK_TZ, hour: '2-digit', minute: '2-digit', hour12: false }).format(
    new Date(iso),
  )
}

/** Formats a YYYY-MM-DD day key (already in bank time) for chart labels. */
export function formatDayKey(day: string, lang: string): string {
  const [y, m, d] = day.split('-').map(Number)
  return new Intl.DateTimeFormat(locale(lang), { timeZone: 'UTC', day: '2-digit', month: lang === 'vi' ? '2-digit' : 'short' }).format(
    new Date(Date.UTC(y, m - 1, d)),
  )
}

/** YYYY-MM-DD of an instant in the bank's time zone. */
export function bankDayKey(date: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: BANK_TZ, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(
    date,
  )
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? ''
  return `${get('year')}-${get('month')}-${get('day')}`
}

/** Start of a bank-time day (YYYY-MM-DD) as a UTC instant. Vietnam has no DST: UTC+7 all year. */
export function bankDayStart(day: string): Date {
  return new Date(`${day}T00:00:00+07:00`)
}

/** "1000 0000 01" — or the masked form as returned by the API ("******0002"). */
export function formatAccountNumber(n: string): string {
  if (n.includes('*')) return `•••• ${n.replace(/\*/g, '')}`
  return n.replace(/^(\d{4})(\d{4})(\d+)$/, '$1 $2 $3')
}

/** "•••• 0001" */
export function maskAccountNumber(n: string): string {
  return `•••• ${n.replace(/\*/g, '').slice(-4)}`
}

/** Digits only, grouped vi-VN style while typing: "1500000" → "1.500.000". */
export function groupDigits(digits: string): string {
  const clean = digits.replace(/\D/g, '').replace(/^0+(?=\d)/, '')
  return clean.replace(/\B(?=(\d{3})+(?!\d))/g, '.')
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

/** The given name in Vietnamese order is the last word: "Nguyễn Văn An" → "An". */
export function givenName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/)
  return parts[parts.length - 1] ?? fullName
}

/** Hour of day in bank time, for greetings. */
export function bankHour(date: Date = new Date()): number {
  return Number(new Intl.DateTimeFormat('en-GB', { timeZone: BANK_TZ, hour: '2-digit', hour12: false }).format(date)) % 24
}
