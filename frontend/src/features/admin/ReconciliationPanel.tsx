import { CircleCheck, CircleX, Scale } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../components/Button'
import { Money } from '../../components/Money'
import { ErrorState } from '../../components/States'
import { formatDateTimeSeconds } from '../../utils/format'
import { useReconciliation } from './hooks'

/** AUDITOR / ADMIN only: re-reads the ledger lines and checks Σ debit = Σ credit and the entry count. */
export function ReconciliationPanel({ transactionId }: { transactionId: string }) {
  const { t, i18n } = useTranslation()
  const [run, setRun] = useState(false)
  const q = useReconciliation(transactionId, run)
  const r = q.data

  return (
    <div aria-live="polite">
      {!r && !q.isError && (
        <>
          <p className="mb-3 text-[13px] text-ink-2">{t('ops.recon.explain')}</p>
          <Button variant="secondary" size="sm" icon={<Scale size={14} strokeWidth={1.5} aria-hidden />} loading={q.isFetching} onClick={() => setRun(true)}>
            {t('ops.recon.run')}
          </Button>
        </>
      )}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact />}
      {r && (
        <div>
          <p className={`flex items-center gap-2 font-medium ${r.balanced ? 'text-credit' : 'text-debit'}`}>
            {r.balanced ? <CircleCheck size={16} strokeWidth={1.75} aria-hidden /> : <CircleX size={16} strokeWidth={1.75} aria-hidden />}
            {r.balanced ? t('ops.recon.balanced') : t('ops.recon.unbalanced')}
          </p>
          <dl className="figures mt-3 grid grid-cols-[auto_1fr] gap-x-6 gap-y-1 text-[13px]">
            <dt className="text-ink-2">{t('ops.recon.entries')}</dt>
            <dd className="text-right">
              {r.entryCount} / {r.status === 'SUCCESS' ? 2 : 0}
            </dd>
            <dt className="text-ink-2">Σ {t('slip.DEBIT')}</dt>
            <dd className="text-right">
              <Money value={r.debitTotal} />
            </dd>
            <dt className="text-ink-2">Σ {t('slip.CREDIT')}</dt>
            <dd className="text-right">
              <Money value={r.creditTotal} />
            </dd>
          </dl>
          <p className="figures mt-3 text-[11px] text-ink-2">
            {t('ops.recon.checkedAt', { time: formatDateTimeSeconds(r.checkedAt, i18n.language) })}
          </p>
          <Button variant="ghost" size="sm" className="mt-2" loading={q.isFetching} onClick={() => q.refetch()}>
            {t('ops.recon.again')}
          </Button>
        </div>
      )}
    </div>
  )
}
