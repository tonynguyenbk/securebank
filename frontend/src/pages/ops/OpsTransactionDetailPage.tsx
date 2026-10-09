import { ArrowLeft } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { usePermissions } from '../../auth/permissions'
import { AccountNumber } from '../../components/AccountNumber'
import { DataTable, type Column } from '../../components/DataTable'
import { Facts, PageHeader, Sheet } from '../../components/Layout'
import { LedgerSlip } from '../../components/LedgerSlip'
import { Money } from '../../components/Money'
import { ErrorState, Skeleton } from '../../components/States'
import { TxStatus } from '../../components/Status'
import { ReconciliationPanel } from '../../features/admin/ReconciliationPanel'
import { useAdminTransaction } from '../../features/admin/hooks'
import { slipFromAdmin } from '../../features/transactions/slip'
import type { AdminLedgerEntry } from '../../types/api'
import { formatDateTimeSeconds } from '../../utils/format'
import { MissingRecord } from '../StatusPages'

export function OpsTransactionDetailPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const perms = usePermissions()
  const q = useAdminTransaction(id)

  if (q.isError && q.error instanceof ApiError && q.error.status === 404) return <MissingRecord backTo="/ops/transactions" backLabel={t('ops.transactions.back')} />
  const x = q.data

  const ledgerColumns: Column<AdminLedgerEntry>[] = [
    { key: 'type', header: t('ops.ledger.type'), mobile: 'title', cell: (e) => <span className={`figures text-[12px] font-medium ${e.entryType === 'DEBIT' ? 'text-debit' : 'text-credit'}`}>{t(`slip.${e.entryType}`)}</span> },
    {
      key: 'account',
      header: t('ops.accounts.account'),
      mobile: 'title',
      cell: (e) => (
        <Link to={`/ops/accounts/${e.accountId}`} className="text-vault underline-offset-4 hover:underline">
          <AccountNumber value={e.accountNumber} />
        </Link>
      ),
    },
    { key: 'amount', header: t('transactions.col.amount'), align: 'right', mobile: 'end', cell: (e) => <Money value={e.amount} sign={e.entryType === 'DEBIT' ? '-' : '+'} tone /> },
    { key: 'before', header: t('ops.ledger.before'), align: 'right', cell: (e) => <Money value={e.balanceBefore} currency={false} /> },
    { key: 'after', header: t('ops.ledger.after'), align: 'right', cell: (e) => <Money value={e.balanceAfter} currency={false} /> },
    { key: 'id', header: t('ops.ledger.id'), cell: (e) => <span className="figures text-[11px] break-all text-ink-2">{e.id}</span> },
  ]

  return (
    <div>
      <Link to="/ops/transactions" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-vault underline-offset-4 hover:underline">
        <ArrowLeft size={14} strokeWidth={1.5} aria-hidden /> {t('ops.transactions.back')}
      </Link>
      {q.isPending && <Skeleton className="h-96 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {x && (
        <div className="space-y-6">
          <PageHeader eyebrow={t('ops.transactions.one')} title={<span className="figures">{x.transactionReference}</span>} actions={<TxStatus status={x.status} />} />
          <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
            <LedgerSlip data={slipFromAdmin(x)} showFullNumbers />
            <div className="space-y-6">
              <Sheet title={t('transactions.details')}>
                <Facts
                  items={[
                    [t('fraud.from'), <Party key="f" name={x.sourceCustomerName} number={x.sourceAccountNumber} to={`/ops/accounts/${x.sourceAccountId}`} />],
                    [t('fraud.to'), <Party key="t" name={x.destinationCustomerName ?? '—'} number={x.destinationAccountNumber} to={x.destinationAccountId ? `/ops/accounts/${x.destinationAccountId}` : undefined} />],
                    [t('ops.transactions.createdBy'), <span key="c" className="figures text-[13px]">{x.createdBy}</span>],
                    [t('transactions.created'), <span key="d" className="figures text-[13px]">{formatDateTimeSeconds(x.createdAt, lang)}</span>],
                    [t('transactions.completed'), <span key="e" className="figures text-[13px]">{formatDateTimeSeconds(x.completedAt, lang)}</span>],
                    ...(x.failureCode
                      ? ([[t('slip.reason'), <span key="r" className="text-debit"><span className="figures block text-[12px]">{x.failureCode}</span>{x.failureReason}</span>]] as [string, ReactNode][])
                      : []),
                    [t('ops.transactions.id'), <span key="i" className="figures text-[11px] break-all text-ink-2">{x.id}</span>],
                  ]}
                />
              </Sheet>
              {perms.canReconcile && (
                <Sheet title={t('ops.recon.title')}>
                  <ReconciliationPanel transactionId={x.id} />
                </Sheet>
              )}
            </div>
          </div>
          <Sheet title={t('ops.ledger.title')}>
            <DataTable dense caption={t('ops.ledger.title')} columns={ledgerColumns} rows={x.ledgerEntries} rowKey={(e) => e.id} empty={<p className="py-5 text-[13px] text-ink-2">{t('slip.noLines')}</p>} />
          </Sheet>
        </div>
      )}
    </div>
  )
}

function Party({ name, number, to }: { name: string; number: string; to?: string }) {
  return (
    <span className="block">
      <span className="block font-medium">{name}</span>
      {to ? (
        <Link to={to} className="text-[13px] text-vault underline-offset-4 hover:underline">
          <AccountNumber value={number} />
        </Link>
      ) : (
        <AccountNumber value={number} className="text-[13px] text-ink-2" />
      )}
    </span>
  )
}
