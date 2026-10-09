import { useTranslation } from 'react-i18next'
import { FilterBar, type FilterDef } from '../../components/FilterBar'
import { PageHeader } from '../../components/Layout'
import { AdminTransactionsTable } from '../../features/admin/AdminTransactionsTable'
import { useAdminTransactions } from '../../features/admin/hooks'
import type { TransactionStatus } from '../../types/api'
import { numOrUndef, strOrUndef, useSearchState } from '../../utils/useSearchState'

const KEYS = ['reference', 'accountNumber', 'status', 'fromDate', 'toDate', 'minAmount', 'maxAmount'] as const

export function OpsTransactionsPage() {
  const { t } = useTranslation()
  const { filters, page, sort, apply, setPage, setSort } = useSearchState(KEYS)
  const sortValue = sort || 'createdAt,desc'
  const [field, dir] = sortValue.split(',') as [string, 'asc' | 'desc']
  const q = useAdminTransactions({
    reference: strOrUndef(filters.reference),
    accountNumber: strOrUndef(filters.accountNumber),
    status: strOrUndef<TransactionStatus>(filters.status),
    fromDate: strOrUndef(filters.fromDate),
    toDate: strOrUndef(filters.toDate),
    minAmount: numOrUndef(filters.minAmount),
    maxAmount: numOrUndef(filters.maxAmount),
    page,
    size: 20,
    sort: sortValue,
  })
  const defs: FilterDef[] = [
    { name: 'reference', label: t('transactions.col.reference'), type: 'text', placeholder: 'TX2026…', mono: true },
    { name: 'accountNumber', label: t('accounts.col.number'), type: 'text', placeholder: '1000000001', mono: true },
    { name: 'status', label: t('transactions.filters.status'), type: 'select', options: (['SUCCESS', 'REJECTED', 'FAILED', 'PENDING'] as const).map((s) => ({ value: s, label: t(`txStatus.${s}`) })) },
    { name: 'fromDate', label: t('transactions.filters.from'), type: 'date' },
    { name: 'toDate', label: t('transactions.filters.to'), type: 'date' },
    { name: 'minAmount', label: t('transactions.filters.min'), type: 'amount' },
    { name: 'maxAmount', label: t('transactions.filters.max'), type: 'amount' },
  ]
  return (
    <div>
      <PageHeader title={t('ops.transactions.title')}>{t('ops.transactions.subtitle')}</PageHeader>
      <FilterBar defs={defs} values={filters} onApply={apply} label={t('transactions.filters.label')} />
      <AdminTransactionsTable query={q} onPage={setPage} sort={{ field, dir }} onSortChange={(s) => setSort(`${s.field},${s.dir}`)} />
    </div>
  )
}
