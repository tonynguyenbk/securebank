import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../../api/endpoints'
import type { NotificationFilters } from '../../types/api'

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (f: NotificationFilters) => ['notifications', 'list', f] as const,
  unread: ['notifications', 'unread'] as const,
}

export function useUnreadCount(enabled = true) {
  return useQuery({ queryKey: notificationKeys.unread, queryFn: notificationsApi.unreadCount, enabled, refetchInterval: 30_000 })
}

export function useNotifications(filters: NotificationFilters) {
  return useQuery({ queryKey: notificationKeys.list(filters), queryFn: () => notificationsApi.mine(filters), placeholderData: keepPreviousData })
}

export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (ids: string[]) => Promise.all(ids.map((id) => notificationsApi.markRead(id))),
    onSettled: () => qc.invalidateQueries({ queryKey: notificationKeys.all }),
  })
}
