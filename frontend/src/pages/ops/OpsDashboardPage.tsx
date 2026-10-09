import { ArrowRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { DataTable, type Column } from '../../components/DataTable'
import { PageHeader, Sheet } from '../../components/Layout'
import { Money } from '../../components/Money'
import { EmptyState, ErrorState, Skeleton } from '../../components/States'
import { RiskBadge } from '../../components/Status'
import { VolumeChart } from '../../features/admin/VolumeChart'
import { useOpsDaily, useOpsToday } from '../../features/admin/hooks'
import { useFraudAlerts, useFraudStats } from '../../features/fraud/hooks'
import type { FraudAlertSummary } from '../../types/api'
import { formatDate, formatDateTime } from '../../utils/format'

export function OpsDashboardPage() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const today = useOpsToday()
  const fraud = useFraudStats()
  const daily = useOpsDaily(14)
  const queue = useFraudAlerts({ status: 'OPEN', page: 0, size: 6, sort: 'riskScore,desc' })
  const lang = i18n.language
  const s = today.data
  const f = fraud.data

  const columns: Column<FraudAlertSummary>[] = [
    { key: 'risk', header: t('fraud.col.risk'), mobile: 'end', cell: (a) => <RiskBadge score={a.riskScore} level={a.riskLevel} compact /> },
    {
      key: 'who',
      header: t('fraud.col.customer'),
      mobile: 'title',
      cell: (a) => (
        <span className="block min-w-0">
          <span className="block truncate font-medium">{a.customerName}</span>
          <Link to={`/ops/fraud/${a.id}`} className="figures text-[12px] text-vault underline-offset-4 hover:underline">
            {a.transactionReference}
          </Link>
        </span>
      ),
    },
    { key: 'amount', header: t('fraud.col.amount'), align: 'right', cell: (a) => <Money value={a.amount} className="whitespace-nowrap" /> },
    { key: 'created', header: t('fraud.col.created'), align: 'right', cell: (a) => <span className="figures text-[12px] text-ink-2">{formatDateTime(a.createdAt, lang)}</span> },
  ]

  return (
    <div className="space-y-6">
      <PageHeader eyebrow={s ? formatDate(`${s.date}T05:00:00Z`, lang) : undefined} title={t('ops.dashboard.title', { name: user?.fullName ?? '' })}>
        {t('ops.dashboard.subtitle')}
      </PageHeader>

      <section aria-label={t('ops.dashboard.kpis')} className="overflow-hidden rounded-sheet border border-rule">
        {today.isError ? (
          <div className="bg-sheet px-5">
            <ErrorState error={today.error} onRetry={() => today.refetch()} compact />
          </div>
        ) : (
          <dl className="grid grid-cols-2 gap-px bg-rule md:grid-cols-3 xl:grid-cols-6">
            <Kpi label={t('ops.kpi.transactions')} to="/ops/transactions" value={s?.transactionsToday} />
            <Kpi label={t('ops.kpi.successful')} to="/ops/transactions?status=SUCCESS" value={s?.successfulToday} tone="text-credit" />
            <Kpi label={t('ops.kpi.failed')} to="/ops/transactions?status=REJECTED" value={s?.failedOrRejectedToday} tone={s?.failedOrRejectedToday ? 'text-debit' : undefined} />
            <Kpi
              label={t('ops.kpi.fraud')}
              to="/ops/fraud"
              value={f ? f.open + f.underReview : undefined}
              note={f ? t('ops.kpi.fraudNote', { open: f.open, critical: f.critical }) : undefined}
              tone={f && f.critical > 0 ? 'text-debit' : f && f.open > 0 ? 'text-brass' : undefined}
            />
            <Kpi label={t('ops.kpi.frozen')} to="/ops/accounts?status=FROZEN" value={s?.frozenAccounts} />
            <Kpi label={t('ops.kpi.volume')} to="/ops/transactions?status=SUCCESS" value={s ? <Money value={s.totalTransferredToday} /> : undefined} wide />
          </dl>
        )}
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(0,1fr)]">
        <Sheet title={t('ops.dashboard.chartTitle')} actions={<span className="figures text-[11px] text-ink-2">{t('ops.dashboard.chartNote')}</span>}>
          {daily.isPending && <Skeleton className="h-64 w-full" />}
          {daily.isError && <ErrorState error={daily.error} onRetry={() => daily.refetch()} compact />}
          {daily.data && <VolumeChart data={daily.data} />}
        </Sheet>

        <Sheet
          title={t('ops.dashboard.queue')}
          actions={
            <Link to="/ops/fraud" className="inline-flex items-center gap-1 text-[13px] text-vault underline-offset-4 hover:underline">
              {t('ops.dashboard.openQueue')} <ArrowRight size={13} strokeWidth={1.5} aria-hidden />
            </Link>
          }
        >
          <DataTable
            dense
            caption={t('ops.dashboard.queue')}
            columns={columns.filter((c) => c.key !== 'created')}
            rows={queue.data?.content}
            rowKey={(a) => a.id}
            rowHref={(a) => `/ops/fraud/${a.id}`}
            loading={queue.isFetching}
            error={queue.error}
            onRetry={() => queue.refetch()}
            empty={<EmptyState compact title={t('ops.dashboard.queueEmpty')} body={t('ops.dashboard.queueEmptyBody')} />}
          />
          {f && f.underReview > 0 && (
            <p className="mt-3 text-[13px]">
              <Link to="/ops/fraud?status=UNDER_REVIEW" className="text-vault underline-offset-4 hover:underline">
                {t('ops.dashboard.underReview', { count: f.underReview })}
              </Link>
            </p>
          )}
        </Sheet>
      </div>
    </div>
  )
}

function Kpi({ label, value, note, to, tone, wide }: { label: string; value: ReactNode | undefined; note?: string; to: string; tone?: string; wide?: boolean }) {
  return (
    <Link to={to} className={`group flex min-w-0 flex-col gap-3 bg-sheet px-4 py-4 transition-colors duration-150 hover:bg-vault-tint/50 sm:px-5 ${wide ? 'col-span-2 md:col-span-1' : ''}`}>
      <dt className="min-h-[2.5em] text-[12px] leading-snug text-ink-2">{label}</dt>
      <dd>
        {value === undefined ? (
          <Skeleton className="h-7 w-16" />
        ) : (
          <span className={`figures block leading-none font-medium tracking-tight ${wide ? 'text-[19px]' : 'text-[24px]'} ${tone ?? ''}`}>{value}</span>
        )}
        {note && <span className="figures mt-1.5 block text-[11px] text-ink-2">{note}</span>}
      </dd>
    </Link>
  )
}
