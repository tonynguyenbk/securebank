import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { usePermissions } from '../../auth/permissions'
import { DataTable, type Column } from '../../components/DataTable'
import { FilterBar, type FilterDef } from '../../components/FilterBar'
import { PageHeader } from '../../components/Layout'
import { Pagination } from '../../components/Pagination'
import { EmptyState } from '../../components/States'
import { AuditDetailDialog } from '../../features/audit/AuditDetailDialog'
import { useAuditActions, useAuditLogs } from '../../features/audit/hooks'
import { AUDIT_RESOURCE_TYPES, type AuditLog } from '../../types/api'
import { bankDayStart, formatDateTimeSeconds } from '../../utils/format'
import { strOrUndef, useSearchState } from '../../utils/useSearchState'

const KEYS = ['actor', 'action', 'resourceType', 'from', 'to', 'correlationId'] as const
const STAFF_TYPES = ['ACCOUNT', 'TRANSACTION', 'FRAUD_ALERT']

export function AuditPage() {
  const { t, i18n } = useTranslation()
  const perms = usePermissions()
  const { filters, page, apply, setPage } = useSearchState(KEYS)
  const [params, setParams] = useSearchParams()
  const openId = params.get('log')
  const actions = useAuditActions()
  // Date filters are bank days; the API takes instants.
  const q = useAuditLogs({
    actor: strOrUndef(filters.actor),
    action: strOrUndef(filters.action),
    resourceType: strOrUndef(filters.resourceType),
    from: filters.from ? bankDayStart(filters.from).toISOString() : undefined,
    to: filters.to ? new Date(bankDayStart(filters.to).getTime() + 86_400_000 - 1).toISOString() : undefined,
    correlationId: strOrUndef(filters.correlationId?.trim()),
    page,
    size: 25,
  })

  const setOpen = (id: string | null) =>
    setParams((prev) => {
      const p = new URLSearchParams(prev)
      if (id) p.set('log', id)
      else p.delete('log')
      return p
    })

  const types = perms.seesIpAddress ? [...AUDIT_RESOURCE_TYPES] : STAFF_TYPES
  const defs: FilterDef[] = [
    { name: 'actor', label: t('audit.col.actor'), type: 'text', placeholder: 'staff1', mono: true },
    { name: 'action', label: t('audit.col.action'), type: 'select', options: (actions.data ?? []).map((a) => ({ value: a, label: a })) },
    { name: 'resourceType', label: t('audit.col.resource'), type: 'select', options: types.map((r) => ({ value: r, label: r })) },
    { name: 'from', label: t('transactions.filters.from'), type: 'date' },
    { name: 'to', label: t('transactions.filters.to'), type: 'date' },
    { name: 'correlationId', label: t('common.correlationId'), type: 'text', placeholder: 'web-…', mono: true },
  ]

  const columns: Column<AuditLog>[] = [
    { key: 'time', header: t('audit.col.time'), cell: (l) => <span className="figures text-[12px] whitespace-nowrap text-ink-2">{formatDateTimeSeconds(l.occurredAt, i18n.language)}</span> },
    {
      key: 'action',
      header: t('audit.col.action'),
      mobile: 'title',
      cell: (l) => (
        <button type="button" onClick={() => setOpen(l.id)} className="figures cursor-pointer text-left text-[12px] font-medium text-vault underline-offset-4 hover:underline">
          {l.action}
        </button>
      ),
    },
    {
      key: 'actor',
      header: t('audit.col.actor'),
      mobile: 'title',
      cell: (l) =>
        l.actorUsername ? (
          <span className="block">
            <span className="figures text-[13px]">{l.actorUsername}</span>
            {l.actorRole && <span className="block text-[11px] text-ink-2">{t(`roles.${l.actorRole}`, { defaultValue: l.actorRole })}</span>}
          </span>
        ) : (
          <span className="text-[13px] text-ink-2">{t('fraud.system')}</span>
        ),
    },
    { key: 'resource', header: t('audit.col.resource'), cell: (l) => <span className="figures text-[12px]">{l.resourceType}{l.resourceId && <span className="block text-[10px] text-ink-2">{l.resourceId.slice(0, 13)}…</span>}</span> },
    { key: 'outcome', header: t('audit.col.outcome'), mobile: 'end', cell: (l) => <span className={`text-[12px] font-medium ${l.outcome === 'SUCCESS' ? 'text-credit' : 'text-debit'}`}>{t(`audit.outcome.${l.outcome}`)}</span> },
    {
      key: 'corr',
      header: t('common.correlationId'),
      cell: (l) =>
        l.correlationId ? (
          <button
            type="button"
            title={t('audit.filterByCorrelation')}
            onClick={() => apply({ ...filters, correlationId: l.correlationId! })}
            className="figures max-w-[11rem] cursor-pointer truncate text-left text-[11px] text-ink-2 hover:text-vault hover:underline"
          >
            {l.correlationId}
          </button>
        ) : (
          '—'
        ),
    },
  ]

  return (
    <div>
      <PageHeader title={t('audit.title')}>{perms.seesIpAddress ? t('audit.subtitle') : t('audit.subtitleStaff')}</PageHeader>
      <FilterBar defs={defs} values={filters} onApply={apply} label={t('audit.filter')} />
      <DataTable
        dense
        caption={t('audit.title')}
        columns={columns}
        rows={q.data?.content}
        rowKey={(l) => l.id}
        onRowClick={(l) => setOpen(l.id)}
        loading={q.isFetching}
        error={q.error}
        onRetry={() => q.refetch()}
        empty={<EmptyState compact title={t('common.noResults')} body={t('audit.empty')} />}
      />
      {q.data && <Pagination {...q.data} onChange={setPage} />}
      <AuditDetailDialog id={openId} onClose={() => setOpen(null)} />
    </div>
  )
}
