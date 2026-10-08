import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import type { MouseEvent, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { ErrorState, Skeleton } from './States'

export type Column<T> = {
  key: string
  header: ReactNode
  cell: (row: T) => ReactNode
  align?: 'left' | 'right'
  className?: string
  /** Server sort field; makes the header a sort button. */
  sortField?: string
  /** How the column shows in the stacked (< 768 px) layout. */
  mobile?: 'title' | 'end' | 'field' | 'hidden'
  /** Only used by the stacked (< 768 px) layout. */
  mobileOnly?: boolean
}

export type Sort = { field: string; dir: 'asc' | 'desc' }

type Props<T> = {
  columns: Column<T>[]
  rows: T[] | undefined
  rowKey: (row: T) => string
  caption: string
  loading?: boolean
  error?: unknown
  onRetry?: () => void
  empty?: ReactNode
  rowHref?: (row: T) => string
  rowClassName?: (row: T) => string
  sort?: Sort
  onSortChange?: (s: Sort) => void
  dense?: boolean
}

/**
 * Ledger-style table: heavy ink rule under the header, hairline rules between rows, right-aligned figures.
 * Below 768 px rows become stacked cards so nothing scrolls sideways.
 */
export function DataTable<T>({ columns, rows, rowKey, caption, loading, error, onRetry, empty, rowHref, rowClassName, sort, onSortChange, dense }: Props<T>) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  if (error && !rows) return <ErrorState error={error} onRetry={onRetry} />

  const go = (row: T) => (e: MouseEvent) => {
    if (!rowHref) return
    if ((e.target as HTMLElement).closest('a,button,input,select,label')) return
    navigate(rowHref(row))
  }
  const pad = dense ? 'py-2' : 'py-3'

  const sortHeader = (c: Column<T>) => {
    if (!c.sortField || !onSortChange) return c.header
    const active = sort?.field === c.sortField
    const Icon = !active ? ArrowUpDown : sort?.dir === 'asc' ? ArrowUp : ArrowDown
    return (
      <button
        type="button"
        onClick={() => onSortChange({ field: c.sortField!, dir: active && sort?.dir === 'desc' ? 'asc' : 'desc' })}
        className={`inline-flex cursor-pointer items-center gap-1 uppercase hover:text-ink ${c.align === 'right' ? 'flex-row-reverse' : ''}`}
      >
        {c.header}
        <Icon size={12} strokeWidth={1.75} aria-hidden className={active ? 'text-ink' : 'opacity-50'} />
      </button>
    )
  }

  const tableColumns = columns.filter((c) => !c.mobileOnly)
  const body = loading && !rows
  const isEmpty = !body && rows && rows.length === 0

  return (
    <div className={loading && rows ? 'opacity-60 transition-opacity' : ''} aria-busy={loading || undefined}>
      {/* ≥ 768 px: table */}
      <table className="hidden w-full border-collapse text-left md:table">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-ink">
            {tableColumns.map((c) => (
              <th
                key={c.key}
                scope="col"
                aria-sort={c.sortField && sort?.field === c.sortField ? (sort.dir === 'asc' ? 'ascending' : 'descending') : undefined}
                className={`py-2 pr-4 text-[11px] font-medium tracking-wider whitespace-nowrap text-ink-2 uppercase last:pr-0 ${c.align === 'right' ? 'text-right' : ''}`}
              >
                {sortHeader(c)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {body &&
            Array.from({ length: 6 }, (_, i) => (
              <tr key={i} className="border-b border-rule">
                {tableColumns.map((c) => (
                  <td key={c.key} className={`${pad} pr-4 last:pr-0`}>
                    <Skeleton className={`h-3 ${c.align === 'right' ? 'ml-auto w-20' : 'w-3/4'}`} />
                  </td>
                ))}
              </tr>
            ))}
          {rows?.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={go(row)}
              className={`border-b border-rule transition-colors duration-150 ${rowHref ? 'cursor-pointer hover:bg-vault-tint/60' : ''} ${rowClassName?.(row) ?? ''}`}
            >
              {tableColumns.map((c) => (
                <td key={c.key} className={`${pad} pr-4 align-middle last:pr-0 ${c.align === 'right' ? 'text-right' : ''} ${c.className ?? ''}`}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {/* < 768 px: stacked rows */}
      <ul className="border-t border-ink md:hidden" aria-label={caption}>
        {body &&
          Array.from({ length: 4 }, (_, i) => (
            <li key={i} className="space-y-2 border-b border-rule py-4">
              <Skeleton className="h-3 w-1/2" />
              <Skeleton className="h-3 w-3/4" />
            </li>
          ))}
        {rows?.map((row) => {
          const title = columns.filter((c) => c.mobile === 'title')
          const end = columns.filter((c) => c.mobile === 'end')
          const fields = columns.filter((c) => !c.mobile || c.mobile === 'field')
          return (
            <li key={rowKey(row)} onClick={go(row)} className={`border-b border-rule py-3.5 ${rowHref ? 'cursor-pointer' : ''} ${rowClassName?.(row) ?? ''}`}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 space-y-0.5">{title.map((c) => <div key={c.key}>{c.cell(row)}</div>)}</div>
                <div className="shrink-0 space-y-0.5 text-right">{end.map((c) => <div key={c.key}>{c.cell(row)}</div>)}</div>
              </div>
              {fields.length > 0 && (
                <dl className="mt-2 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1 text-[13px]">
                  {fields.map((c) => (
                    <div key={c.key} className="contents">
                      <dt className="text-ink-2">{c.header}</dt>
                      <dd className="min-w-0 text-right break-words">{c.cell(row)}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </li>
          )
        })}
      </ul>

      {isEmpty && (empty ?? <p className="py-8 text-ink-2">{t('common.noResults')}</p>)}
      {error && rows ? <ErrorState error={error} onRetry={onRetry} compact /> : null}
    </div>
  )
}
