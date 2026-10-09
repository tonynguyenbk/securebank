import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { DataTable, type Column } from '../../components/DataTable'
import { FilterBar } from '../../components/FilterBar'
import { PageHeader } from '../../components/Layout'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import { useAdminCustomers } from '../../features/admin/hooks'
import type { CustomerAdmin } from '../../types/api'
import { formatDate } from '../../utils/format'
import { strOrUndef, useSearchState } from '../../utils/useSearchState'

export function OpsCustomersPage() {
  const { t, i18n } = useTranslation()
  const { filters, page, apply, setPage } = useSearchState(['q'] as const)
  const q = useAdminCustomers({ q: strOrUndef(filters.q), page, size: 20 })

  const columns: Column<CustomerAdmin>[] = [
    {
      key: 'name',
      header: t('ops.customers.name'),
      mobile: 'title',
      cell: (c) => (
        <Link to={`/ops/customers/${c.id}`} className="font-medium text-vault underline-offset-4 hover:underline">
          {c.fullName}
        </Link>
      ),
    },
    { key: 'email', header: t('register.email'), cell: (c) => <span className="text-[13px] break-all">{c.email}</span> },
    { key: 'phone', header: t('register.phone'), cell: (c) => <span className="figures text-[13px]">{c.phone ?? '—'}</span> },
    { key: 'accounts', header: t('ops.customers.accounts'), align: 'right', mobile: 'end', cell: (c) => <span className="figures">{c.accountCount}</span> },
    { key: 'since', header: t('profile.customerSince'), align: 'right', cell: (c) => <span className="figures text-[12px] text-ink-2">{formatDate(c.createdAt, i18n.language)}</span> },
  ]

  return (
    <div>
      <PageHeader title={t('ops.customers.title')}>{t('ops.customers.subtitle')}</PageHeader>
      <FilterBar defs={[{ name: 'q', label: t('ops.customers.search'), type: 'text', placeholder: t('ops.customers.searchPh') }]} values={filters} onApply={apply} label={t('ops.customers.search')} />
      <DataTable
        caption={t('ops.customers.title')}
        columns={columns}
        rows={q.data?.content}
        rowKey={(c) => c.id}
        rowHref={(c) => `/ops/customers/${c.id}`}
        loading={q.isFetching}
        error={q.error}
        onRetry={() => q.refetch()}
        empty={<EmptyState compact title={t('common.noResults')} body={t('ops.customers.empty')} />}
      />
      {q.data && <Pagination {...q.data} onChange={setPage} />}
    </div>
  )
}
