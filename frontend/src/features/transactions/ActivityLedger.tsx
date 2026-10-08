import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { Money } from '../../components/Money'
import { TxStatus } from '../../components/Status'
import type { TransactionSummary } from '../../types/api'
import { formatShortDate, formatTime, maskAccountNumber } from '../../utils/format'

/** Compact ledger of recent movements: date · ref · counterparty · description · signed amount · status. */
export function ActivityLedger({ items }: { items: TransactionSummary[] }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  return (
    <ol className="border-t border-ink">
      {items.map((tx) => {
        const out = tx.direction === 'OUT'
        const other = out ? tx.destinationAccountNumber : tx.sourceAccountNumber
        const muted = tx.status !== 'SUCCESS'
        return (
          <li key={tx.id} className="border-b border-rule">
            <Link
              to={`/transactions/${tx.id}`}
              className="grid grid-cols-[3.5rem_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-0.5 py-3 transition-colors duration-150 hover:bg-vault-tint/60 sm:grid-cols-[4rem_9.5rem_minmax(0,1fr)_auto_7rem] sm:gap-x-4"
            >
              <span className="figures row-span-2 text-[12px] leading-tight text-ink-2 sm:row-span-1">
                {formatShortDate(tx.createdAt, lang)}
                <span className="block text-[11px]">{formatTime(tx.createdAt, lang)}</span>
              </span>
              <span className="figures hidden text-[12px] text-ink-2 sm:block">{tx.transactionReference}</span>
              <span className="min-w-0">
                <span className="flex items-baseline gap-1.5">
                  <span className="min-w-0 truncate">
                    {out ? t('activity.to') : t('activity.from')} <span className="font-medium">{tx.counterpartyName}</span>
                  </span>
                  <span className="figures shrink-0 text-[12px] text-ink-2">{maskAccountNumber(other)}</span>
                </span>
                {tx.description && <span className="block truncate text-[13px] text-ink-2">{tx.description}</span>}
              </span>
              <Money
                value={tx.amount}
                sign={out ? '-' : '+'}
                tone={!muted}
                className={`text-[14px] ${muted ? 'text-ink-2 line-through decoration-ink-2/50' : ''}`}
              />
              <span className="col-start-2 col-end-4 justify-self-end sm:col-auto sm:justify-self-end">
                <TxStatus status={tx.status} />
              </span>
            </Link>
          </li>
        )
      })}
    </ol>
  )
}
