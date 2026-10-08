import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import type { EntryType, TransactionStatus } from '../types/api'
import { formatDateTimeSeconds } from '../utils/format'
import { AccountNumber } from './AccountNumber'
import { Money } from './Money'
import { StatusStamp } from './Status'
import { stampFor } from '../utils/status'

export type SlipLine = {
  entryType: EntryType
  accountNumber: string
  holder: string
  amount: number
  balanceBefore: number | null
  balanceAfter: number | null
}

export type SlipData = {
  reference: string
  status: TransactionStatus
  amount: number
  description: string | null
  createdAt: string
  completedAt: string | null
  from: { accountNumber: string; name: string }
  to: { accountNumber: string; name: string }
  lines: SlipLine[]
  remainingBalance?: number | null
  failureCode?: string | null
}

/**
 * The double-entry receipt (DESIGN_SYSTEM §6): the two real ledger lines side by side, joined by a rule,
 * with balance before/after, a debit = credit check and the rubber posting stamp.
 */
export function LedgerSlip({ data, landStamp = false, showFullNumbers = false }: { data: SlipData; landStamp?: boolean; showFullNumbers?: boolean }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const debit = data.lines.filter((l) => l.entryType === 'DEBIT').reduce((s, l) => s + l.amount, 0)
  const credit = data.lines.filter((l) => l.entryType === 'CREDIT').reduce((s, l) => s + l.amount, 0)
  const balanced = data.lines.length > 0 && debit === credit
  const ordered = [...data.lines].sort((a) => (a.entryType === 'DEBIT' ? -1 : 1))

  return (
    <article aria-label={t('slip.aria', { ref: data.reference })} className="relative overflow-hidden rounded-sheet border border-rule bg-sheet">
      {/* Header strip */}
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b-2 border-double border-ink/70 px-5 pt-4 pb-3 sm:px-6">
        <p className="text-[11px] font-semibold tracking-[0.18em] text-ink-2 uppercase">{t('slip.title')}</p>
        <p className="figures text-[13px]">{data.reference}</p>
      </div>

      {/* Ledger lines */}
      <div className="px-5 sm:px-6">
        {ordered.length === 0 ? (
          <p className="py-5 text-[13px] text-ink-2">{t('slip.noLines')}</p>
        ) : (
          ordered.map((l, i) => (
            <div key={i} className={`grid grid-cols-[4.5rem_minmax(0,1fr)_auto] items-start gap-x-3 py-3.5 ${i > 0 ? 'border-t border-rule' : ''}`}>
              <span className={`figures pt-0.5 text-[11px] font-medium tracking-[0.12em] ${l.entryType === 'DEBIT' ? 'text-debit' : 'text-credit'}`}>
                {t(`slip.${l.entryType}`)}
              </span>
              <span className="min-w-0">
                <span className="block truncate font-medium">{l.holder || '—'}</span>
                <AccountNumber value={l.accountNumber} masked={!showFullNumbers} className="text-[12px] text-ink-2" />
                {l.balanceBefore !== null && l.balanceAfter !== null && (
                  <span className="figures mt-1 block text-[11px] text-ink-2">
                    {t('slip.balanceMove')}{' '}
                    <Money value={l.balanceBefore} currency={false} /> → <Money value={l.balanceAfter} currency={false} />
                  </span>
                )}
              </span>
              <Money value={l.amount} sign={l.entryType === 'DEBIT' ? '-' : '+'} tone className="pt-px text-[15px]" />
            </div>
          ))
        )}
      </div>

      {/* Control total */}
      {ordered.length > 0 && (
        <div className="figures mx-5 flex flex-wrap items-center justify-between gap-2 border-y border-ink/70 py-2 text-[11px] text-ink-2 sm:mx-6">
          <span>
            Σ {t('slip.DEBIT')} <Money value={debit} currency={false} /> {balanced ? '=' : '≠'} Σ {t('slip.CREDIT')} <Money value={credit} currency={false} />
          </span>
          <span className={balanced ? 'text-credit' : 'text-debit'}>{balanced ? t('slip.balanced') : t('slip.unbalanced')}</span>
        </div>
      )}

      {/* Particulars + stamp */}
      <div className="relative grid gap-x-6 gap-y-2 px-5 py-4 text-[13px] sm:grid-cols-2 sm:px-6 sm:pb-16">
        <Particular label={t('slip.amount')} value={<Money value={data.amount} className="text-[15px] font-medium" />} />
        <Particular label={t('slip.beneficiary')} value={data.to.name || '—'} />
        <Particular label={t('slip.description')} value={data.description || '—'} />
        <Particular label={data.status === 'SUCCESS' ? t('slip.time') : t('slip.processed')} value={<span className="figures">{formatDateTimeSeconds(data.completedAt ?? data.createdAt, lang)}</span>} />
        {data.remainingBalance != null && <Particular label={t('slip.remaining')} value={<Money value={data.remainingBalance} />} />}
        {data.failureCode && <Particular label={t('slip.reason')} value={<span className="text-debit">{t(`errors.${data.failureCode}`, { defaultValue: data.failureCode })}</span>} />}
      </div>

      <div className="pointer-events-none flex justify-end px-5 pb-4 sm:absolute sm:right-8 sm:bottom-10 sm:p-0">
        <StatusStamp kind={stampFor(data.status)} land={landStamp} date={formatStampDate(data.completedAt ?? data.createdAt)} className="bg-sheet/70" />
      </div>

      {/* Perforation */}
      <div className="figures flex justify-between gap-2 border-t border-dashed border-rule px-5 py-2 text-[10px] tracking-wider text-ink-2 uppercase sm:px-6">
        <span>SecureBank · {t('slip.footer')}</span>
        <span>VND</span>
      </div>
    </article>
  )
}

function Particular({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="min-w-0">
      <p className="text-[11px] tracking-wider text-ink-2 uppercase">{label}</p>
      <div className="mt-0.5 break-words">{value}</div>
    </div>
  )
}

function formatStampDate(iso: string) {
  return new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Ho_Chi_Minh', day: '2-digit', month: '2-digit', year: '2-digit' }).format(new Date(iso))
}
