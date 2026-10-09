import { ArrowLeft } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { AccountNumber } from '../../components/AccountNumber'
import { DataTable, type Column } from '../../components/DataTable'
import { Facts, PageHeader, Sheet } from '../../components/Layout'
import { Money } from '../../components/Money'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import { AccountStatusTag, FraudStatusTag, RiskBadge } from '../../components/Status'
import { useAdminCustomer } from '../../features/admin/hooks'
import { useFraudAlerts } from '../../features/fraud/hooks'
import type { AccountAdmin, FraudAlertSummary } from '../../types/api'
import { formatDate, formatDateTime } from '../../utils/format'
import { MissingRecord } from '../StatusPages'

export function OpsCustomerDetailPage() {
  const { id } = useParams()
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const q = useAdminCustomer(id)
  const alerts = useFraudAlerts({ customerId: id, page: 0, size: 10 })

  if (q.isError && q.error instanceof ApiError && q.error.status === 404) return <MissingRecord backTo="/ops/customers" backLabel={t('ops.customers.back')} />
  const c = q.data

  const accountCols: Column<AccountAdmin>[] = [
    {
      key: 'n',
      header: t('accounts.col.number'),
      mobile: 'title',
      cell: (a) => (
        <Link to={`/ops/accounts/${a.id}`} className="text-vault underline-offset-4 hover:underline">
          <AccountNumber value={a.accountNumber} />
        </Link>
      ),
    },
    { key: 's', header: t('accounts.col.status'), mobile: 'end', cell: (a) => <AccountStatusTag status={a.status} /> },
    { key: 'o', header: t('accounts.col.opened'), cell: (a) => <span className="figures text-[12px] text-ink-2">{formatDate(a.createdAt, lang)}</span> },
    { key: 'b', header: t('accounts.col.balance'), align: 'right', mobile: 'end', cell: (a) => <Money value={a.balance} /> },
  ]
  const alertCols: Column<FraudAlertSummary>[] = [
    { key: 'r', header: t('fraud.col.risk'), mobile: 'end', cell: (a) => <RiskBadge score={a.riskScore} level={a.riskLevel} compact /> },
    {
      key: 'ref',
      header: t('fraud.col.transaction'),
      mobile: 'title',
      cell: (a) => (
        <Link to={`/ops/fraud/${a.id}`} className="figures text-[13px] text-vault underline-offset-4 hover:underline">
          {a.transactionReference}
        </Link>
      ),
    },
    { key: 'a', header: t('fraud.col.amount'), align: 'right', cell: (a) => <Money value={a.amount} /> },
    { key: 's', header: t('fraud.col.status'), mobile: 'end', cell: (a) => <FraudStatusTag status={a.status} /> },
    { key: 'c', header: t('fraud.col.created'), align: 'right', cell: (a) => <span className="figures text-[12px] text-ink-2">{formatDateTime(a.createdAt, lang)}</span> },
  ]

  return (
    <div>
      <Link to="/ops/customers" className="mb-4 inline-flex items-center gap-1.5 text-[13px] text-vault underline-offset-4 hover:underline">
        <ArrowLeft size={14} strokeWidth={1.5} aria-hidden /> {t('ops.customers.back')}
      </Link>
      {q.isPending && <Skeleton className="h-72 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {c && (
        <div className="space-y-6">
          <PageHeader eyebrow={t('roles.CUSTOMER')} title={c.fullName} />
          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
            <Sheet title={t('profile.customer')}>
              <Facts
                items={[
                  [t('register.email'), <span key="e" className="break-all">{c.email}</span>],
                  [t('register.phone'), <span key="p" className="figures">{c.phone ?? '—'}</span>],
                  [t('profile.customerSince'), <span key="s" className="figures text-[13px]">{formatDate(c.createdAt, lang)}</span>],
                  [t('profile.customerId'), <span key="i" className="figures text-[11px] break-all text-ink-2">{c.id}</span>],
                  [t('ops.customers.userId'), <span key="u" className="figures text-[11px] break-all text-ink-2">{c.userId}</span>],
                ]}
              />
            </Sheet>
            <Sheet title={t('ops.customers.accounts')}>
              <DataTable dense caption={t('ops.customers.accounts')} columns={accountCols} rows={c.accounts} rowKey={(a) => a.id} rowHref={(a) => `/ops/accounts/${a.id}`} />
            </Sheet>
          </div>
          <Sheet title={t('ops.customers.alerts')}>
            <DataTable
              dense
              caption={t('ops.customers.alerts')}
              columns={alertCols}
              rows={alerts.data?.content}
              rowKey={(a) => a.id}
              rowHref={(a) => `/ops/fraud/${a.id}`}
              loading={alerts.isFetching}
              error={alerts.error}
              onRetry={() => alerts.refetch()}
              empty={<EmptyState compact title={t('ops.customers.noAlerts')} />}
            />
          </Sheet>
        </div>
      )}
    </div>
  )
}
