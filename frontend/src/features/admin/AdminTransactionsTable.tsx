import type { UseQueryResult } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import type { ApiError } from '../../api/errors'
import { DataTable, type Column, type Sort } from '../../components/DataTable'
import { Money } from '../../components/Money'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import { TxStatus } from '../../components/Status'
import type { AdminTransaction, PageResponse } from '../../types/api'
import { formatDateTime, formatAccountNumber } from '../../utils/format'

type Props = {
  query: UseQueryResult<PageResponse<AdminTransaction>, ApiError>
  onPage: (p: number) => void
  sort?: Sort
  onSortChange?: (s: Sort) => void
  dense?: boolean
}

export function AdminTransactionsTable({ query, onPage, sort, onSortChange, dense }: Props) {
  const { t, i18n } = useTranslation()
  const party = (name: string | null, number: string) => (
    <span className="block min-w-0">
      <span className="block truncate">{name ?? '—'}</span>
      <span className="figures block text-[11px] text-ink-2">{formatAccountNumber(number)}</span>
    </span>
  )
  const columns: Column<AdminTransaction>[] = [
    { key: 'date', header: t('transactions.col.date'), sortField: 'createdAt', cell: (x) => <span className="figures text-[12px] whitespace-nowrap text-ink-2">{formatDateTime(x.createdAt, i18n.language)}</span> },
    {
      key: 'ref',
      header: t('transactions.col.reference'),
      mobile: 'title',
      cell: (x) => (
        <Link to={`/ops/transactions/${x.id}`} className="figures text-[13px] text-vault underline-offset-4 hover:underline">
          {x.transactionReference}
        </Link>
      ),
    },
    { key: 'from', header: t('fraud.from'), cell: (x) => party(x.sourceCustomerName, x.sourceAccountNumber) },
    { key: 'to', header: t('fraud.to'), cell: (x) => party(x.destinationCustomerName, x.destinationAccountNumber) },
    { key: 'amount', header: t('transactions.col.amount'), align: 'right', sortField: 'amount', mobile: 'end', cell: (x) => <Money value={x.amount} className={x.status === 'SUCCESS' ? '' : 'text-ink-2'} /> },
    {
      key: 'status',
      header: t('transactions.col.status'),
      mobile: 'end',
      cell: (x) => (
        <span className="block">
          <TxStatus status={x.status} />
          {x.failureCode && <span className="figures block text-[10px] text-ink-2">{x.failureCode}</span>}
        </span>
      ),
    },
  ]
  return (
    <>
      <DataTable
        dense={dense}
        caption={t('ops.transactions.title')}
        columns={columns}
        rows={query.data?.content}
        rowKey={(x) => x.id}
        rowHref={(x) => `/ops/transactions/${x.id}`}
        loading={query.isFetching}
        error={query.error}
        onRetry={() => query.refetch()}
        sort={sort}
        onSortChange={onSortChange}
        empty={<EmptyState compact title={t('common.noResults')} body={t('transactions.emptyFiltered')} />}
      />
      {query.data && <Pagination {...query.data} onChange={onPage} />}
    </>
  )
}
