import { CheckCheck } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../components/Button'
import { PageHeader } from '../../components/Layout'
import { Pagination } from '../../components/Pagination'
import { EmptyState, ErrorState, SkeletonRows } from '../../components/States'
import { useToast } from '../../components/Toast'
import { NotificationItem } from '../../features/notifications/NotificationItem'
import { useMarkRead, useNotifications, useUnreadCount } from '../../features/notifications/hooks'
import type { Notification } from '../../types/api'
import { errorText } from '../../utils/errorMessage'

const CHANNELS: Notification['channel'][] = ['IN_APP', 'EMAIL', 'SMS']

export function NotificationsPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [channel, setChannel] = useState<Notification['channel']>('IN_APP')
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const q = useNotifications({ channel, unreadOnly: unreadOnly || undefined, page, size: 15 })
  const unread = useUnreadCount()
  const mark = useMarkRead()
  const unreadOnPage = (q.data?.content ?? []).filter((n) => !n.read && n.channel === 'IN_APP').map((n) => n.id)

  const markRead = (ids: string[]) =>
    mark.mutate(ids, {
      onSuccess: () => ids.length > 1 && toast.success(t('notifications.allMarked')),
      onError: (e) => toast.error(errorText(t, e)),
    })

  return (
    <div className="max-w-3xl">
      <PageHeader title={t('nav.notifications')}>{t('notifications.subtitle')}</PageHeader>

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-rule">
        <div role="tablist" aria-label={t('notifications.channelLabel')} className="flex gap-5">
          {CHANNELS.map((c) => (
            <button
              key={c}
              role="tab"
              type="button"
              aria-selected={channel === c}
              onClick={() => {
                setChannel(c)
                setPage(0)
              }}
              className={`relative -mb-px inline-flex h-11 cursor-pointer items-center gap-1.5 border-b-2 ${
                channel === c ? 'border-vault text-ink' : 'border-transparent text-ink-2 hover:text-ink'
              }`}
            >
              {t(`notifications.channel.${c}`)}
              {c === 'IN_APP' && (unread.data?.count ?? 0) > 0 && <span className="figures text-[11px] text-vault">{unread.data!.count}</span>}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-4 pb-2">
          {channel === 'IN_APP' && (
            <label className="inline-flex cursor-pointer items-center gap-2 text-[13px]">
              <input
                type="checkbox"
                checked={unreadOnly}
                onChange={(e) => {
                  setUnreadOnly(e.target.checked)
                  setPage(0)
                }}
                className="size-4 accent-[var(--vault)]"
              />
              {t('notifications.unreadOnly')}
            </label>
          )}
          {unreadOnPage.length > 0 && (
            <Button variant="ghost" size="sm" loading={mark.isPending} icon={<CheckCheck size={14} strokeWidth={1.5} aria-hidden />} onClick={() => markRead(unreadOnPage)}>
              {t('notifications.markAll')}
            </Button>
          )}
        </div>
      </div>

      {channel !== 'IN_APP' && <p className="mb-3 text-[13px] text-ink-2">{t('notifications.deliveryNote')}</p>}

      {q.isPending && <SkeletonRows rows={6} />}
      {q.isError && <ErrorState error={q.error} onRetry={() => q.refetch()} />}
      {q.data && q.data.content.length === 0 && <EmptyState title={unreadOnly ? t('dashboard.noNotices') : t('notifications.empty')} body={t('notifications.emptyBody')} />}
      {q.data && q.data.content.length > 0 && (
        <ul aria-busy={q.isFetching || undefined}>
          {q.data.content.map((n) => (
            <NotificationItem key={n.id} n={n} onMarkRead={(id) => markRead([id])} marking={mark.isPending} />
          ))}
        </ul>
      )}
      {q.data && <Pagination {...q.data} onChange={setPage} />}
    </div>
  )
}
