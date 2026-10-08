import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import type { Notification } from '../../types/api'
import { formatDateTime } from '../../utils/format'
import { renderNotification } from './render'

type Props = { n: Notification; onMarkRead?: (id: string) => void; marking?: boolean; compact?: boolean }

export function NotificationItem({ n, onMarkRead, marking, compact }: Props) {
  const { t, i18n } = useTranslation()
  const { title, body } = renderNotification(t, n)
  const unread = !n.read && n.channel === 'IN_APP'
  return (
    <li className={`flex gap-3 border-b border-rule ${compact ? 'py-3' : 'py-4'}`}>
      <span aria-hidden className={`mt-2 size-1.5 shrink-0 rounded-full ${unread ? 'bg-vault' : 'bg-transparent'}`} />
      <div className="min-w-0 flex-1">
        <p className={`text-[14px] ${unread ? 'font-semibold' : 'font-medium'}`}>
          {unread && <span className="sr-only">{t('notifications.unread')}: </span>}
          {title}
        </p>
        <p className={`text-[13px] text-ink-2 ${compact ? 'line-clamp-2' : ''}`}>{body}</p>
        <p className="figures mt-1 flex flex-wrap gap-x-3 text-[11px] text-ink-2">
          <span>{formatDateTime(n.createdAt, i18n.language)}</span>
          {!compact && <span>{t(`notifications.channel.${n.channel}`)}</span>}
          {n.relatedTransactionId && (
            <Link to={`/transactions/${n.relatedTransactionId}`} className="text-vault underline-offset-4 hover:underline">
              {t('notifications.viewTransaction')}
            </Link>
          )}
        </p>
      </div>
      {unread && onMarkRead && (
        <button
          type="button"
          onClick={() => onMarkRead(n.id)}
          disabled={marking}
          className="inline-flex h-8 shrink-0 cursor-pointer items-center gap-1 self-start rounded-sheet px-2 text-[12px] text-vault hover:bg-vault-tint disabled:opacity-50"
        >
          <Check size={13} strokeWidth={1.75} aria-hidden />
          {t('notifications.markRead')}
        </button>
      )}
    </li>
  )
}
