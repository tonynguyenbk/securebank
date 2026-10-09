import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { DataTable, type Column } from '../../components/DataTable'
import { FilterBar, type FilterDef } from '../../components/FilterBar'
import { PageHeader } from '../../components/Layout'
import { Money } from '../../components/Money'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import { FraudStatusTag, RiskBadge } from '../../components/Status'
import { useFraudAlerts, useFraudStats } from '../../features/fraud/hooks'
import type { FraudAlertStatus, FraudAlertSummary, RiskLevel } from '../../types/api'
import { formatDateTime, maskAccountNumber } from '../../utils/format'
import { strOrUndef, useSearchState } from '../../utils/useSearchState'

const STATUSES: FraudAlertStatus[] = ['OPEN', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'CLOSED']
const LEVELS: RiskLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export function FraudQueuePage() {
  const { t, i18n } = useTranslation()
  const { filters, page, sort, apply, setPage, setSort } = useSearchState(['status', 'riskLevel'] as const)
  const sortValue = sort || 'createdAt,desc'
  const [field, dir] = sortValue.split(',') as [string, 'asc' | 'desc']
  const stats = useFraudStats()
  const q = useFraudAlerts({
    status: strOrUndef<FraudAlertStatus>(filters.status),
    riskLevel: strOrUndef<RiskLevel>(filters.riskLevel),
    page,
    size: 20,
    sort: sortValue,
  })

  const defs: FilterDef[] = [
    { name: 'status', label: t('fraud.filters.status'), type: 'select', options: STATUSES.map((s) => ({ value: s, label: t(`fraudStatus.${s}`) })) },
    { name: 'riskLevel', label: t('fraud.filters.level'), type: 'select', options: LEVELS.map((l) => ({ value: l, label: t(`risk.${l}`) })) },
  ]

  const columns: Column<FraudAlertSummary>[] = [
    { key: 'risk', header: t('fraud.col.risk'), sortField: 'riskScore', mobile: 'end', cell: (a) => <RiskBadge score={a.riskScore} level={a.riskLevel} /> },
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
    {
      key: 'customer',
      header: t('fraud.col.customer'),
      mobile: 'title',
      cell: (a) => (
        <span className="block min-w-0">
          <span className="block truncate font-medium">{a.customerName}</span>
          <span className="figures block text-[11px] text-ink-2">{maskAccountNumber(a.sourceAccountNumber)}</span>
        </span>
      ),
    },
    { key: 'amount', header: t('fraud.col.amount'), align: 'right', sortField: 'amount', cell: (a) => <Money value={a.amount} /> },
    { key: 'status', header: t('fraud.col.status'), mobile: 'end', cell: (a) => <FraudStatusTag status={a.status} /> },
    { key: 'created', header: t('fraud.col.created'), align: 'right', sortField: 'createdAt', cell: (a) => <span className="figures text-[12px] whitespace-nowrap text-ink-2">{formatDateTime(a.createdAt, i18n.language)}</span> },
  ]

  const s = stats.data
  return (
    <div>
      <PageHeader
        title={t('fraud.title')}
        actions={
          s && (
            <p className="figures text-[13px] text-ink-2">
              <span className="text-brass">{t('fraud.headOpen', { count: s.open })}</span> · {t('fraud.headReview', { count: s.underReview })} ·{' '}
              <span className={s.critical ? 'text-debit' : ''}>{t('fraud.headCritical', { count: s.critical })}</span>
            </p>
          )
        }
      >
        {t('fraud.subtitle')}
      </PageHeader>
      <FilterBar defs={defs} values={filters} onApply={apply} label={t('fraud.filters.label')} />
      <DataTable
        caption={t('fraud.title')}
        columns={columns}
        rows={q.data?.content}
        rowKey={(a) => a.id}
        rowHref={(a) => `/ops/fraud/${a.id}`}
        rowClassName={(a) => (a.status === 'OPEN' && a.riskLevel === 'CRITICAL' ? 'shadow-[inset_3px_0_0_var(--debit)]' : '')}
        loading={q.isFetching}
        error={q.error}
        onRetry={() => q.refetch()}
        sort={{ field, dir }}
        onSortChange={(x) => setSort(`${x.field},${x.dir}`)}
        empty={<EmptyState compact title={t('fraud.empty')} body={t('fraud.emptyBody')} />}
      />
      {q.data && <Pagination {...q.data} onChange={setPage} />}
    </div>
  )
}
