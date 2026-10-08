import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { accountsApi } from '../../api/endpoints'

/** Debounced GET /accounts/lookup once 10 digits are typed; 404 means "no such account". */
export function useBeneficiaryLookup(accountNumber: string) {
  const [debounced, setDebounced] = useState('')
  useEffect(() => {
    const id = window.setTimeout(() => setDebounced(accountNumber), 350)
    return () => window.clearTimeout(id)
  }, [accountNumber])
  const ready = /^\d{10}$/.test(debounced) && debounced === accountNumber
  return useQuery({
    queryKey: ['lookup', debounced],
    queryFn: ({ signal }) => accountsApi.lookup(debounced, signal),
    enabled: ready,
    retry: false,
    staleTime: 60_000,
  })
}
