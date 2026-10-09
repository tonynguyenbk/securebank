import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Dialog } from '../../components/Dialog'
import { Facts } from '../../components/Layout'
import { ErrorState, Skeleton } from '../../components/States'
import { formatDateTimeSeconds } from '../../utils/format'
import { useAuditLog } from './hooks'

function show(v: unknown): string {
  if (v === undefined) return '—'
  if (v === null) return 'null'
  return typeof v === 'object' ? JSON.stringify(v) : String(v)
}

/** Field-by-field before/after table; changed rows are marked, never by colour alone. */
export function AuditDiff({ before, after }: { before: Record<string, unknown> | null; after: Record<string, unknown> | null }) {
  const { t } = useTranslation()
  const keys = [...new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})])]
  if (keys.length === 0) return <p className="text-[13px] text-ink-2">{t('audit.noChanges')}</p>
  return (
    <table className="w-full table-fixed text-left text-[13px]">
      <caption className="sr-only">{t('audit.diff')}</caption>
      <thead>
        <tr className="border-b border-ink text-[11px] tracking-wider text-ink-2 uppercase">
          <th scope="col" className="w-[30%] py-2 pr-3 font-medium">{t('audit.field')}</th>
          <th scope="col" className="py-2 pr-3 font-medium">{t('audit.before')}</th>
          <th scope="col" className="py-2 font-medium">{t('audit.after')}</th>
        </tr>
      </thead>
      <tbody>
        {keys.map((k) => {
          const b = before?.[k]
          const a = after?.[k]
          const changed = JSON.stringify(b) !== JSON.stringify(a)
          return (
            <tr key={k} className={`border-b border-rule align-top ${changed ? 'bg-vault-tint/40' : ''}`}>
              <th scope="row" className="figures py-2 pr-3 font-normal break-words">
                {changed && <span aria-hidden className="mr-1 text-vault">●</span>}
                {k}
                {changed && <span className="sr-only"> ({t('audit.changed')})</span>}
              </th>
              <td className={`figures py-2 pr-3 break-words ${changed && b !== undefined ? 'text-debit line-through decoration-debit/40' : 'text-ink-2'}`}>{show(b)}</td>
              <td className={`figures py-2 break-words ${changed ? 'text-ink' : 'text-ink-2'}`}>{show(a)}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}

export function AuditDetailDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const q = useAuditLog(id)
  const l = q.data
  return (
    <Dialog open={!!id} onClose={onClose} size="lg" title={l ? <span className="figures">{l.action}</span> : t('audit.record')} description={l ? formatDateTimeSeconds(l.occurredAt, i18n.language) : undefined}>
      {q.isPending && <Skeleton className="h-48 w-full" />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} compact />}
      {l && (
        <div className="space-y-6">
          <Facts
            items={[
              [t('audit.col.actor'), l.actorUsername ? <span key="a"><span className="figures">{l.actorUsername}</span> {l.actorRole && <span className="text-ink-2">· {t(`roles.${l.actorRole}`, { defaultValue: l.actorRole })}</span>}</span> : <span key="a" className="text-ink-2">{t('fraud.system')}</span>],
              [t('audit.col.outcome'), <span key="o" className={l.outcome === 'SUCCESS' ? 'text-credit' : 'text-debit'}>{t(`audit.outcome.${l.outcome}`)}</span>],
              [t('audit.col.resource'), <span key="r" className="figures text-[12px] break-all">{l.resourceType}{l.resourceId ? ` · ${l.resourceId}` : ''}</span>],
              [t('audit.service'), <span key="s" className="figures text-[12px]">{l.sourceService}</span>],
              [t('common.correlationId'), <span key="c" className="figures text-[12px] break-all">{l.correlationId ?? '—'}</span>],
              ...(l.ipAddress !== undefined ? ([[t('audit.ip'), <span key="i" className="figures text-[12px]">{l.ipAddress ?? '—'}</span>]] as [string, ReactNode][]) : []),
              [t('audit.eventId'), <span key="e" className="figures text-[11px] break-all text-ink-2">{l.eventId}</span>],
              [t('audit.received'), <span key="v" className="figures text-[12px]">{formatDateTimeSeconds(l.receivedAt, i18n.language)}</span>],
            ]}
          />
          <div>
            <h3 className="mb-2 font-display text-[15px] font-semibold">{t('audit.diff')}</h3>
            <AuditDiff before={l.before} after={l.after} />
          </div>
          <p className="text-[12px] text-ink-2">{t('audit.appendOnly')}</p>
        </div>
      )}
    </Dialog>
  )
}
