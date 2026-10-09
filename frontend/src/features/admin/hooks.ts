import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/endpoints'
import type { AccountStatus, AdminTransactionFilters } from '../../types/api'

export const adminKeys = {
  all: ['admin'] as const,
  customers: (p: object) => ['admin', 'customers', p] as const,
  customer: (id: string) => ['admin', 'customer', id] as const,
  accounts: (p: object) => ['admin', 'accounts', p] as const,
  account: (id: string) => ['admin', 'account', id] as const,
  transactions: (p: object) => ['admin', 'transactions', p] as const,
  transaction: (id: string) => ['admin', 'transaction', id] as const,
  reconciliation: (id: string) => ['admin', 'reconciliation', id] as const,
  today: ['admin', 'stats', 'today'] as const,
  daily: (days: number) => ['admin', 'stats', 'daily', days] as const,
}

export const useOpsToday = () => useQuery({ queryKey: adminKeys.today, queryFn: adminApi.statsToday, refetchInterval: 30_000 })
export const useOpsDaily = (days = 14) => useQuery({ queryKey: adminKeys.daily(days), queryFn: () => adminApi.statsDaily(days) })

export function useAdminCustomers(p: { q?: string; page: number; size: number }) {
  return useQuery({ queryKey: adminKeys.customers(p), queryFn: () => adminApi.customers(p), placeholderData: keepPreviousData })
}
export const useAdminCustomer = (id?: string) => useQuery({ queryKey: adminKeys.customer(id ?? ''), queryFn: () => adminApi.customer(id!), enabled: !!id })

export function useAdminAccounts(p: { q?: string; status?: AccountStatus; page: number; size: number }) {
  return useQuery({ queryKey: adminKeys.accounts(p), queryFn: () => adminApi.accounts(p), placeholderData: keepPreviousData })
}
export const useAdminAccount = (id?: string) => useQuery({ queryKey: adminKeys.account(id ?? ''), queryFn: () => adminApi.account(id!), enabled: !!id })

export function useAdminTransactions(f: AdminTransactionFilters, enabled = true) {
  return useQuery({ queryKey: adminKeys.transactions(f), queryFn: () => adminApi.transactions(f), placeholderData: keepPreviousData, enabled })
}
export const useAdminTransaction = (id?: string) =>
  useQuery({ queryKey: adminKeys.transaction(id ?? ''), queryFn: () => adminApi.transaction(id!), enabled: !!id })

/** Reconciliation is an explicit check (button), not loaded on page open. */
export const useReconciliation = (id: string, enabled: boolean) =>
  useQuery({ queryKey: adminKeys.reconciliation(id), queryFn: () => adminApi.reconciliation(id), enabled, staleTime: 0 })

export function useSetAccountStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, freeze, reason }: { id: string; freeze: boolean; reason: string }) =>
      freeze ? adminApi.freeze(id, reason) : adminApi.unfreeze(id, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.all })
      qc.invalidateQueries({ queryKey: ['audit'] })
    },
  })
}

export function useUpdateLimits(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { perTransactionLimit: number; dailyLimit: number }) => adminApi.updateLimits(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: adminKeys.account(id) })
      qc.invalidateQueries({ queryKey: ['audit'] })
    },
  })
}
