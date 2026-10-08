import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fraudApi } from '../../api/endpoints'
import type { FraudAlertFilters, FraudAlertStatus } from '../../types/api'

export const fraudKeys = {
  all: ['fraud'] as const,
  list: (f: FraudAlertFilters) => ['fraud', 'list', f] as const,
  detail: (id: string) => ['fraud', 'detail', id] as const,
  stats: ['fraud', 'stats'] as const,
}

export function useFraudStats() {
  return useQuery({ queryKey: fraudKeys.stats, queryFn: fraudApi.stats, refetchInterval: 30_000 })
}

export function useFraudAlerts(filters: FraudAlertFilters) {
  return useQuery({ queryKey: fraudKeys.list(filters), queryFn: () => fraudApi.alerts(filters), placeholderData: keepPreviousData })
}

export function useFraudAlert(id: string | undefined) {
  return useQuery({ queryKey: fraudKeys.detail(id ?? ''), queryFn: () => fraudApi.alert(id!), enabled: !!id })
}

export function useReviewAlert(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { status: FraudAlertStatus; note?: string }) => fraudApi.review(id, body),
    onSuccess: (data) => {
      qc.setQueryData(fraudKeys.detail(id), data)
      qc.invalidateQueries({ queryKey: fraudKeys.all })
      qc.invalidateQueries({ queryKey: ['audit'] })
    },
  })
}
