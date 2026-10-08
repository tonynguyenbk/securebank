import { ArrowDownLeft, ArrowUpRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ButtonLink } from '../../components/Button'
import { DataTable, type Column } from '../../components/DataTable'
import { FilterBar, type FilterDef } from '../../components/FilterBar'
import { PageHeader } from '../../components/Layout'
import { Money } from '../../components/Money'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import { TxStatus } from '../../components/Status'
import { useAccounts } from '../../features/accounts/hooks'
import { useTransactions } from '../../features/transactions/hooks'
import type { TransactionStatus, TransactionSummary } from '../../types/api'
import { formatDateTime, maskAccountNumber } from '../../utils/format'
import { numOrUndef, strOrUndef, useSearchState } from '../../utils/useSearchState'

const KEYS = ['accountId', 'status', 'fromDate', 'toDate', 'minAmount', 'maxAmount'] as const
const STATUSES: TransactionStatus[] = ['SUCCESS', 'REJECTED', 'FAILED', 'PENDING']

export function TransactionsPage() {
  const { t, i18n } = useTranslation()
  const accounts = useAccounts()
  const { filters, page, sort, apply, setPage, setSort } = useSearchState(KEYS)
  const sortValue = sort || 'createdAt,desc'
  const query = useTransactions({
    accountId: strOrUndef(filters.accountId),
    status: strOrUndef<TransactionStatus>(filters.status),
    fromDate: strOrUndef(filters.fromDate),
    toDate: strOrUndef(filters.toDate),
    minAmount: numOrUndef(filters.minAmount),
    maxAmount: numOrUndef(filters.maxAmount),
    page,
    size: 20,
    sort: sortValue,
  })
  const [field, dir] = sortValue.split(',') as [string, 'asc' | 'desc']
  const filtered = Object.values(filters).some(Boolean)

  const defs: FilterDef[] = [
    { name: 'accountId', label: t('transactions.filters.account'), type: 'select', options: (accounts.data ?? []).map((a) => ({ value: a.id, label: a.accountNumber })) },
    { name: 'status', label: t('transactions.filters.status'), type: 'select', options: STATUSES.map((s) => ({ value: s, label: t(`txStatus.${s}`) })) },
    { name: 'fromDate', label: t('transactions.filters.from'), type: 'date' },
    { name: 'toDate', label: t('transactions.filters.to'), type: 'date' },
    { name: 'minAmount', label: t('transactions.filters.min'), type: 'amount' },
    { name: 'maxAmount', label: t('transactions.filters.max'), type: 'amount' },
  ]

  const columns: Column<TransactionSummary>[] = [
    {
      key: 'date',
      header: t('transactions.col.date'),
      sortField: 'createdAt',
      cell: (tx) => <span className="figures text-[12px] whitespace-nowrap text-ink-2">{formatDateTime(tx.createdAt, i18n.language)}</span>,
    },
    {
      key: 'ref',
      header: t('transactions.col.reference'),
      mobile: 'title',
      cell: (tx) => (
        <Link to={`/transactions/${tx.id}`} className="figures text-[13px] text-vault underline-offset-4 hover:underline">
          {tx.transactionReference}
        </Link>
      ),
    },
    {
      key: 'party',
      header: t('transactions.col.counterparty'),
      mobile: 'title',
      cell: (tx) => {
        const out = tx.direction === 'OUT'
        const Icon = out ? ArrowUpRight : ArrowDownLeft
        return (
          <span className="flex min-w-0 items-center gap-2">
            <Icon size={14} strokeWidth={1.5} aria-hidden className="shrink-0 text-ink-2" />
            <span className="sr-only">{out ? t('activity.to') : t('activity.from')}</span>
            <span className="min-w-0">
              <span className="block truncate font-medium">{tx.counterpartyName}</span>
              <span className="figures block text-[11px] text-ink-2">{maskAccountNumber(out ? tx.destinationAccountNumber : tx.sourceAccountNumber)}</span>
            </span>
          </span>
        )
      },
    },
    { key: 'desc', header: t('transactions.col.description'), className: 'max-w-[16rem]', cell: (tx) => <span className="line-clamp-2 text-[13px] text-ink-2">{tx.description ?? '—'}</span> },
    {
      key: 'amount',
      header: t('transactions.col.amount'),
      align: 'right',
      sortField: 'amount',
      mobile: 'end',
      cell: (tx) => (
        <Money
          value={tx.amount}
          sign={tx.direction === 'OUT' ? '-' : '+'}
          tone={tx.status === 'SUCCESS'}
          className={tx.status === 'SUCCESS' ? '' : 'text-ink-2 line-through decoration-ink-2/50'}
        />
      ),
    },
    { key: 'status', header: t('transactions.col.status'), align: 'right', mobile: 'end', cell: (tx) => <TxStatus status={tx.status} /> },
  ]

  return (
    <div>
      <PageHeader title={t('transactions.title')} actions={<ButtonLink to="/transfer">{t('dashboard.sendMoney')}</ButtonLink>}>
        {t('transactions.subtitle')}
      </PageHeader>
      <FilterBar defs={defs} values={filters} onApply={apply} label={t('transactions.filters.label')} />
      <DataTable
        caption={t('transactions.title')}
        columns={columns}
        rows={query.data?.content}
        rowKey={(r) => r.id}
        rowHref={(r) => `/transactions/${r.id}`}
        loading={query.isFetching}
        error={query.error}
        onRetry={() => query.refetch()}
        sort={{ field, dir }}
        onSortChange={(s) => setSort(`${s.field},${s.dir}`)}
        empty={
          filtered ? (
            <EmptyState compact title={t('common.noResults')} body={t('transactions.emptyFiltered')} />
          ) : (
            <EmptyState title={t('dashboard.noActivity')} body={t('dashboard.noActivityBody')} action={<ButtonLink to="/transfer" variant="secondary" size="sm">{t('dashboard.sendFirst')}</ButtonLink>} />
          )
        }
      />
      {query.data && <Pagination {...query.data} onChange={setPage} />}
    </div>
  )
}
