import { Archive, Check, CircleDot, Clock, Eye, Lock, ShieldX, X } from 'lucide-react'
import type { ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import type { AccountStatus, FraudAlertStatus, RiskLevel, TransactionStatus } from '../types/api'
import type { StampKind } from '../utils/status'

type Icon = ComponentType<{ size?: number; strokeWidth?: number; className?: string; 'aria-hidden'?: boolean }>

function Tag({ icon: I, label, tone }: { icon: Icon; label: string; tone: string }) {
  return (
    <span className={`inline-flex items-center gap-1 text-[12px] font-medium whitespace-nowrap ${tone}`}>
      <I size={13} strokeWidth={1.75} aria-hidden />
      {label}
    </span>
  )
}

const TX: Record<TransactionStatus, [Icon, string]> = {
  SUCCESS: [Check, 'text-credit'],
  PENDING: [Clock, 'text-brass'],
  FAILED: [X, 'text-debit'],
  REJECTED: [X, 'text-debit'],
}
export function TxStatus({ status }: { status: TransactionStatus }) {
  const { t } = useTranslation()
  const [icon, tone] = TX[status]
  return <Tag icon={icon} tone={tone} label={t(`txStatus.${status}`)} />
}

const ACC: Record<AccountStatus, [Icon, string]> = {
  ACTIVE: [Check, 'text-credit'],
  FROZEN: [Lock, 'text-debit'],
  CLOSED: [Archive, 'text-ink-2'],
}
export function AccountStatusTag({ status }: { status: AccountStatus }) {
  const { t } = useTranslation()
  const [icon, tone] = ACC[status]
  return <Tag icon={icon} tone={tone} label={t(`accountStatus.${status}`)} />
}

const FRAUD: Record<FraudAlertStatus, [Icon, string]> = {
  OPEN: [CircleDot, 'text-brass'],
  UNDER_REVIEW: [Eye, 'text-vault'],
  APPROVED: [Check, 'text-credit'],
  REJECTED: [ShieldX, 'text-debit'],
  CLOSED: [Archive, 'text-ink-2'],
}
export function FraudStatusTag({ status }: { status: FraudAlertStatus }) {
  const { t } = useTranslation()
  const [icon, tone] = FRAUD[status]
  return <Tag icon={icon} tone={tone} label={t(`fraudStatus.${status}`)} />
}

const RISK_STEP: Record<RiskLevel, number> = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 }
const RISK_TONE: Record<RiskLevel, string> = { LOW: 'text-ink-2', MEDIUM: 'text-brass', HIGH: 'text-debit', CRITICAL: 'text-debit' }

/** Score + a 4-cell meter + level word: never colour alone. CRITICAL is the only solid badge. */
export function RiskBadge({ score, level, compact = false }: { score: number; level: RiskLevel; compact?: boolean }) {
  const { t } = useTranslation()
  const step = RISK_STEP[level]
  const solid = level === 'CRITICAL'
  return (
    <span
      className={`inline-flex items-center gap-2 rounded-[4px] border px-1.5 py-0.5 whitespace-nowrap ${
        solid ? 'border-debit bg-debit text-on-debit' : `border-current/40 ${RISK_TONE[level]}`
      }`}
      aria-label={t('risk.aria', { score, level: t(`risk.${level}`) })}
    >
      <span className="figures w-[2ch] text-right text-[13px] font-medium" aria-hidden>
        {score}
      </span>
      <span className="flex gap-[2px]" aria-hidden>
        {[1, 2, 3, 4].map((i) => (
          <span key={i} className={`h-2.5 w-1 rounded-[1px] ${i <= step ? 'bg-current' : 'bg-current/20'}`} />
        ))}
      </span>
      {!compact && (
        <span className="text-[11px] font-semibold tracking-wide uppercase" aria-hidden>
          {t(`risk.${level}`)}
        </span>
      )}
    </span>
  )
}

const STAMP_TONE: Record<StampKind, string> = {
  POSTED: 'text-credit',
  REJECTED: 'text-debit',
  FAILED: 'text-debit',
  FROZEN: 'text-debit',
  PENDING: 'text-brass',
}

/** Rubber posting stamp: Plex Mono inside a double rule, slightly rotated. `land` plays the one orchestrated motion. */
export function StatusStamp({ kind, land = false, date, className = '' }: { kind: StampKind; land?: boolean; date?: string; className?: string }) {
  const { t } = useTranslation()
  return (
    <span
      role="img"
      aria-label={t(`stamp.${kind}`)}
      style={{ ['--stamp-rot' as string]: '-7deg', transform: 'rotate(-7deg)' }}
      className={`inline-flex flex-col items-center rounded-[5px] border-[3px] border-double border-current px-3 py-1 leading-tight select-none ${STAMP_TONE[kind]} ${land ? 'animate-stamp' : ''} ${className}`}
    >
      <span className="figures text-[15px] font-medium tracking-[0.14em] uppercase">{t(`stamp.${kind}`)}</span>
      {date && <span className="figures text-[10px] tracking-wider opacity-80">{date}</span>}
    </span>
  )
}
