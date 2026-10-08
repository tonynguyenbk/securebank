import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { DataTable, type Column } from '../../components/DataTable'
import { FilterBar } from '../../components/FilterBar'
import { Money } from '../../components/Money'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import type { StatementEntry } from '../../types/api'
import { formatShortDate, formatTime, maskAccountNumber } from '../../utils/format'
import { useStatement } from './hooks'

/** Passbook columns: date · reference · particulars · debit · credit · running balance. */
export function StatementTable({ accountId }: { accountId: string }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language
  const [range, setRange] = useState({ fromDate: '', toDate: '' })
  const [page, setPage] = useState(0)
  const q = useStatement(accountId, { fromDate: range.fromDate || undefined, toDate: range.toDate || undefined, page, size: 15 })

  const columns: Column<StatementEntry>[] = [
    {
      key: 'date',
      header: t('statement.col.date'),
      cell: (e) => (
        <span className="figures text-[12px] leading-tight whitespace-nowrap text-ink-2">
          {formatShortDate(e.createdAt, lang)}
          <span className="block text-[11px]">{formatTime(e.createdAt, lang)}</span>
        </span>
      ),
    },
    {
      key: 'ref',
      header: t('statement.col.reference'),
      mobile: 'title',
      cell: (e) => (
        <Link to={`/transactions/${e.transactionId}`} className="figures text-[12px] text-vault underline-offset-4 hover:underline">
          {e.transactionReference}
        </Link>
      ),
    },
    {
      key: 'particulars',
      header: t('statement.col.particulars'),
      mobile: 'title',
      cell: (e) => (
        <span className="block min-w-0">
          <span className="block truncate">
            {e.entryType === 'DEBIT' ? t('activity.to') : t('activity.from')} <span className="font-medium">{e.counterpartyName}</span>{' '}
            <span className="figures text-[11px] text-ink-2">{maskAccountNumber(e.counterpartyAccountNumber)}</span>
          </span>
          {e.description && <span className="block truncate text-[12px] text-ink-2">{e.description}</span>}
        </span>
      ),
    },
    {
      key: 'signed',
      header: t('transactions.col.amount'),
      mobileOnly: true,
      mobile: 'end',
      cell: (e) => <Money value={e.amount} sign={e.entryType === 'DEBIT' ? '-' : '+'} tone currency={false} />,
    },
    { key: 'debit', header: t('statement.col.debit'), align: 'right', mobile: 'hidden', cell: (e) => (e.entryType === 'DEBIT' ? <Money value={e.amount} currency={false} className="text-debit" /> : <span className="text-ink-2">·</span>) },
    { key: 'credit', header: t('statement.col.credit'), align: 'right', mobile: 'hidden', cell: (e) => (e.entryType === 'CREDIT' ? <Money value={e.amount} currency={false} className="text-credit" /> : <span className="text-ink-2">·</span>) },
    { key: 'balance', header: t('statement.col.balance'), align: 'right', cell: (e) => <Money value={e.balanceAfter} currency={false} className="font-medium" /> },
  ]

  return (
    <div>
      <FilterBar
        label={t('statement.filter')}
        defs={[
          { name: 'fromDate', label: t('transactions.filters.from'), type: 'date' },
          { name: 'toDate', label: t('transactions.filters.to'), type: 'date' },
        ]}
        values={range}
        onApply={(v) => {
          setRange({ fromDate: v.fromDate ?? '', toDate: v.toDate ?? '' })
          setPage(0)
        }}
      />
      <DataTable
        dense
        caption={t('statement.title')}
        columns={columns}
        rows={q.data?.content}
        rowKey={(e) => e.id}
        rowHref={(e) => `/transactions/${e.transactionId}`}
        loading={q.isFetching}
        error={q.error}
        onRetry={() => q.refetch()}
        empty={<EmptyState compact title={t('statement.empty')} body={t('statement.emptyBody')} />}
      />
      {q.data && <Pagination {...q.data} onChange={setPage} />}
      <p className="mt-3 text-[12px] text-ink-2">{t('statement.note')}</p>
    </div>
  )
}
