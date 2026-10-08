import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { transfersApi } from '../../api/endpoints'
import type { TransferFilters } from '../../types/api'

export const txKeys = {
  all: ['transfers'] as const,
  list: (f: TransferFilters) => ['transfers', 'list', f] as const,
  detail: (id: string) => ['transfers', 'detail', id] as const,
}

export function useTransactions(filters: TransferFilters) {
  return useQuery({ queryKey: txKeys.list(filters), queryFn: () => transfersApi.list(filters), placeholderData: keepPreviousData })
}

export function useTransaction(id: string | undefined) {
  return useQuery({ queryKey: txKeys.detail(id ?? ''), queryFn: () => transfersApi.detail(id!), enabled: !!id })
}
