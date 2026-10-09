import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { auditApi } from '../../api/endpoints'
import type { AuditFilters } from '../../types/api'

export const auditKeys = {
  list: (f: AuditFilters) => ['audit', 'list', f] as const,
  detail: (id: string) => ['audit', 'detail', id] as const,
  actions: ['audit', 'actions'] as const,
}

export const useAuditLogs = (f: AuditFilters) => useQuery({ queryKey: auditKeys.list(f), queryFn: () => auditApi.logs(f), placeholderData: keepPreviousData })
export const useAuditLog = (id: string | null) => useQuery({ queryKey: auditKeys.detail(id ?? ''), queryFn: () => auditApi.log(id!), enabled: !!id })
export const useAuditActions = () => useQuery({ queryKey: auditKeys.actions, queryFn: auditApi.actions, staleTime: 5 * 60_000 })
