import { ArrowLeft, Printer } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { AccountNumber } from '../../components/AccountNumber'
import { Button } from '../../components/Button'
import { Facts, PageHeader } from '../../components/Layout'
import { LedgerSlip } from '../../components/LedgerSlip'
import { Money } from '../../components/Money'
import { ErrorState, Skeleton } from '../../components/States'
import { TxStatus } from '../../components/Status'
import { useTransaction } from '../../features/transactions/hooks'
import { slipFromDetail } from '../../features/transactions/slip'
import { formatDateTimeSeconds } from '../../utils/format'
import { MissingRecord } from '../StatusPages'

export function TransactionDetailPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const q = useTransaction(id)

  if (q.isError && q.error instanceof ApiError && q.error.status === 404) return <MissingRecord backTo="/transactions" backLabel={t('transactions.back')} />

  return (
    <div>
      <Link to="/transactions" className="no-print mb-4 inline-flex items-center gap-1.5 text-[13px] text-vault underline-offset-4 hover:underline">
        <ArrowLeft size={14} strokeWidth={1.5} aria-hidden /> {t('transactions.back')}
      </Link>
      {q.isPending && <Skeleton className="h-96 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <>
          <PageHeader
            eyebrow={q.data.direction === 'OUT' ? t('transactions.outgoing') : t('transactions.incoming')}
            title={<span className="figures">{q.data.transactionReference}</span>}
            actions={
              <Button variant="secondary" size="sm" icon={<Printer size={14} strokeWidth={1.5} aria-hidden />} onClick={() => window.print()} className="no-print">
                {t('common.print')}
              </Button>
            }
          />
          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
            <div className="print-area">
              <LedgerSlip data={slipFromDetail(q.data, user?.fullName ?? '')} />
            </div>
            <section className="no-print rounded-sheet border border-rule bg-sheet px-5 py-4 sm:px-6" aria-label={t('transactions.details')}>
              <Facts
                items={[
                  [t('transactions.col.status'), <TxStatus key="s" status={q.data.status} />],
                  [t('transactions.col.amount'), <Money key="a" value={q.data.amount} sign={q.data.direction === 'OUT' ? '-' : '+'} />],
                  [t('transactions.from'), <AccountNumber key="f" value={q.data.sourceAccountNumber} masked={q.data.direction === 'IN'} />],
                  [t('transactions.to'), <AccountNumber key="to" value={q.data.destinationAccountNumber} masked={q.data.direction === 'OUT'} />],
                  [t('transactions.counterparty'), q.data.counterpartyName],
                  [t('transactions.created'), <span key="c" className="figures text-[13px]">{formatDateTimeSeconds(q.data.createdAt, i18n.language)}</span>],
                  [t('transactions.completed'), <span key="d" className="figures text-[13px]">{formatDateTimeSeconds(q.data.completedAt, i18n.language)}</span>],
                  ...(q.data.failureCode
                    ? ([[t('slip.reason'), <span key="r" className="text-debit">{t(`errors.${q.data.failureCode}`, { defaultValue: q.data.failureReason ?? q.data.failureCode })}</span>]] as [string, ReactNode][])
                    : []),
                ]}
              />
              <p className="mt-4 text-[12px] text-ink-2">{t('transactions.ledgerNote')}</p>
            </section>
          </div>
        </>
      )}
    </div>
  )
}
