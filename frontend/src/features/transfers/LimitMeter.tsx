import { useTranslation } from 'react-i18next'
import { Money } from '../../components/Money'
import type { TransferLimits } from '../../types/api'

/** Daily limit usage bar; the pending amount is shown as a hatched extension so the user sees what's left after sending. */
export function LimitMeter({ limits, pending = 0 }: { limits: TransferLimits; pending?: number }) {
  const { t } = useTranslation()
  const used = Math.min(1, limits.usedToday / limits.dailyLimit)
  const extra = Math.max(0, Math.min(1 - used, pending / limits.dailyLimit))
  const over = limits.usedToday + pending > limits.dailyLimit
  return (
    <div>
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 text-[12px] text-ink-2">
        <span>{t('limits.daily')}</span>
        <span className="figures">
          <Money value={limits.usedToday} currency={false} /> / <Money value={limits.dailyLimit} currency={false} />
        </span>
      </div>
      <div
        role="meter"
        aria-label={t('limits.daily')}
        aria-valuemin={0}
        aria-valuemax={limits.dailyLimit}
        aria-valuenow={limits.usedToday}
        className="mt-1.5 flex h-1.5 overflow-hidden rounded-[2px] bg-rule"
      >
        <span className="h-full bg-vault" style={{ width: `${used * 100}%` }} />
        {extra > 0 && (
          <span
            className={`h-full ${over ? 'bg-debit' : 'bg-vault/45'}`}
            style={{ width: `${extra * 100}%`, marginLeft: used > 0 ? 2 : 0 }}
          />
        )}
      </div>
      <p className="figures mt-1.5 text-[11px] text-ink-2">
        {t('limits.remaining')} <Money value={limits.remainingToday} /> · {t('limits.perTx')} <Money value={limits.perTransactionLimit} />
      </p>
    </div>
  )
}
