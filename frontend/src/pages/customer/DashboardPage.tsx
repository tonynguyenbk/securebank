import { ArrowRight, Send } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { ButtonLink } from '../../components/Button'
import { Sheet } from '../../components/Layout'
import { EmptyState, ErrorState, Skeleton, SkeletonRows } from '../../components/States'
import { PassbookCard } from '../../features/accounts/PassbookCard'
import { useAccounts } from '../../features/accounts/hooks'
import { NotificationItem } from '../../features/notifications/NotificationItem'
import { useMarkRead, useNotifications } from '../../features/notifications/hooks'
import { ActivityLedger } from '../../features/transactions/ActivityLedger'
import { FlowSummary } from '../../features/transactions/FlowSummary'
import { useTransactions } from '../../features/transactions/hooks'
import { bankDayKey, bankHour, formatDate } from '../../utils/format'

function greetingKey(hour: number) {
  if (hour < 4) return 'dashboard.greeting.evening'
  if (hour < 11) return 'dashboard.greeting.morning'
  if (hour < 14) return 'dashboard.greeting.midday'
  if (hour < 18) return 'dashboard.greeting.afternoon'
  return 'dashboard.greeting.evening'
}

export function DashboardPage() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const accounts = useAccounts()
  const recent = useTransactions({ page: 0, size: 6, sort: 'createdAt,desc' })
  const [now] = useState(() => new Date())
  const thirtyDaysAgo = bankDayKey(new Date(now.getTime() - 29 * 86_400_000))
  const flow = useTransactions({ fromDate: thirtyDaysAgo, status: 'SUCCESS', page: 0, size: 100 })
  const unread = useNotifications({ channel: 'IN_APP', unreadOnly: true, page: 0, size: 4 })
  const markRead = useMarkRead()

  return (
    <div className="space-y-8">
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="figures text-[12px] text-ink-2">{formatDate(now.toISOString(), i18n.language)}</p>
          <h1 className="mt-1 font-display text-[24px] leading-tight font-semibold tracking-tight sm:text-[28px]">
            {t(greetingKey(bankHour(now)), { name: user?.fullName ?? '' })}
          </h1>
        </div>
        <ButtonLink to="/transfer" icon={<Send size={15} strokeWidth={1.5} aria-hidden />}>
          {t('dashboard.sendMoney')}
        </ButtonLink>
      </header>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)]">
        <div className="flex flex-col gap-5 [&>*:only-child]:flex-1">
          {accounts.isPending && <Skeleton className="h-[210px] w-full" />}
          {accounts.isError && <ErrorState error={accounts.error} onRetry={() => accounts.refetch()} />}
          {accounts.data?.length === 0 && <EmptyState title={t('dashboard.noAccounts')} body={t('dashboard.noAccountsBody')} />}
          {accounts.data?.map((a) => <PassbookCard key={a.id} account={a} />)}
        </div>

        <Sheet title={t('flow.title')} actions={<span className="figures text-[11px] text-ink-2">{t('flow.window', { days: 30 })}</span>}>
          {flow.isError ? (
            <ErrorState error={flow.error} onRetry={() => flow.refetch()} compact />
          ) : (
            <FlowSummary items={flow.data?.content} loading={flow.isPending} />
          )}
        </Sheet>
      </div>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)]">
        <section aria-labelledby="recent-h">
          <div className="mb-2 flex items-baseline justify-between">
            <h2 id="recent-h" className="font-display text-base font-semibold">
              {t('dashboard.recent')}
            </h2>
            <Link to="/transactions" className="inline-flex items-center gap-1 text-[13px] text-vault underline-offset-4 hover:underline">
              {t('dashboard.viewAll')} <ArrowRight size={13} strokeWidth={1.5} aria-hidden />
            </Link>
          </div>
          {recent.isPending && <SkeletonRows rows={5} />}
          {recent.isError && <ErrorState error={recent.error} onRetry={() => recent.refetch()} />}
          {recent.data && recent.data.content.length === 0 && (
            <EmptyState title={t('dashboard.noActivity')} body={t('dashboard.noActivityBody')} action={<ButtonLink to="/transfer" variant="secondary" size="sm">{t('dashboard.sendFirst')}</ButtonLink>} />
          )}
          {recent.data && recent.data.content.length > 0 && <ActivityLedger items={recent.data.content} />}
        </section>

        <section aria-labelledby="unread-h">
          <div className="mb-2 flex items-baseline justify-between">
            <h2 id="unread-h" className="font-display text-base font-semibold">
              {t('dashboard.notices')}
            </h2>
            <Link to="/notifications" className="text-[13px] text-vault underline-offset-4 hover:underline">
              {t('dashboard.allNotices')}
            </Link>
          </div>
          <div className="border-t border-ink">
            {unread.isPending && <SkeletonRows rows={3} />}
            {unread.isError && <ErrorState error={unread.error} onRetry={() => unread.refetch()} compact />}
            {unread.data && unread.data.content.length === 0 && <p className="py-5 text-[13px] text-ink-2">{t('dashboard.noNotices')}</p>}
            {unread.data && unread.data.content.length > 0 && (
              <ul>
                {unread.data.content.map((n) => (
                  <NotificationItem key={n.id} n={n} compact onMarkRead={(id) => markRead.mutate([id])} marking={markRead.isPending} />
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}
