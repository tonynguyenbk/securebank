import { RotateCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { AccountNumber } from '../../components/AccountNumber'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { Money } from '../../components/Money'
import { Alert } from '../../components/States'

export type ReviewData = {
  sourceNumber: string
  sourceBalance: number
  destination: string
  holderName: string
  amount: number
  description: string
}

type Props = {
  open: boolean
  data: ReviewData | null
  submitting: boolean
  /** Set after a transport failure: the next submit re-sends with the same idempotency key. */
  transientError: string | null
  idempotencyKey: string | null
  onConfirm: () => void
  onClose: () => void
}

export function ReviewDialog({ open, data, submitting, transientError, idempotencyKey, onConfirm, onClose }: Props) {
  const { t } = useTranslation()
  if (!data) return null
  const after = data.sourceBalance - data.amount
  return (
    <Dialog
      open={open}
      onClose={onClose}
      busy={submitting}
      title={t('transfer.review.title')}
      description={t('transfer.review.subtitle')}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            {t('transfer.review.edit')}
          </Button>
          <Button onClick={onConfirm} loading={submitting} icon={transientError ? <RotateCw size={15} strokeWidth={1.5} aria-hidden /> : undefined}>
            {submitting ? t('transfer.review.sending') : transientError ? t('transfer.review.retry') : t('transfer.review.confirm')}
          </Button>
        </>
      }
    >
      <dl className="text-[14px]">
        <Row label={t('transfer.review.from')}>
          <AccountNumber value={data.sourceNumber} />
        </Row>
        <Row label={t('transfer.review.to')}>
          <span className="block font-medium">{data.holderName}</span>
          <AccountNumber value={data.destination} className="text-[13px] text-ink-2" />
        </Row>
        <Row label={t('transfer.review.description')}>{data.description || <span className="text-ink-2">—</span>}</Row>
        <div className="flex items-baseline justify-between gap-4 border-y-2 border-double border-ink/70 py-3">
          <dt className="font-medium">{t('transfer.review.amount')}</dt>
          <dd>
            <Money value={data.amount} className="text-[22px] font-medium" />
          </dd>
        </div>
        <Row label={t('transfer.review.balanceAfter')}>
          <Money value={after} />
        </Row>
      </dl>

      <div aria-live="polite" className="mt-4">
        {transientError && (
          <Alert tone="warning">
            <p className="font-medium">{transientError}</p>
            <p className="mt-1 text-ink-2">{t('transfer.review.sameKey')}</p>
          </Alert>
        )}
      </div>
      {idempotencyKey && (
        <p className="figures mt-3 text-[11px] break-all text-ink-2">
          {t('transfer.review.key')}: {idempotencyKey}
        </p>
      )}
    </Dialog>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-rule py-2.5 last:border-b-0">
      <dt className="shrink-0 text-ink-2">{label}</dt>
      <dd className="min-w-0 text-right break-words">{children}</dd>
    </div>
  )
}
