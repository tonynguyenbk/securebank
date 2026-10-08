import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { accountsApi, customerApi } from '../../api/endpoints'

export const accountKeys = {
  all: ['accounts'] as const,
  list: ['accounts', 'list'] as const,
  detail: (id: string) => ['accounts', 'detail', id] as const,
  statement: (id: string, p: object) => ['accounts', 'statement', id, p] as const,
}

export function useAccounts() {
  return useQuery({ queryKey: accountKeys.list, queryFn: accountsApi.list })
}

export function useAccount(id: string | undefined) {
  return useQuery({ queryKey: accountKeys.detail(id ?? ''), queryFn: () => accountsApi.detail(id!), enabled: !!id })
}

export function useStatement(id: string, params: { fromDate?: string; toDate?: string; page: number; size: number }) {
  return useQuery({
    queryKey: accountKeys.statement(id, params),
    queryFn: () => accountsApi.statement(id, params),
    placeholderData: keepPreviousData,
  })
}

export function useCustomerMe() {
  return useQuery({ queryKey: ['customer', 'me'], queryFn: customerApi.me })
}
