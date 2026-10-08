import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

/**
 * Filters + page kept in the URL (?status=SUCCESS&page=2) so they survive reloads, back/forward and sharing.
 * `page` in the URL is 1-based for humans; the API is 0-based.
 */
export function useSearchState<K extends string>(keys: readonly K[]) {
  const [params, setParams] = useSearchParams()
  const keyList = keys.join(',')

  const filters = useMemo(
    () => Object.fromEntries(keyList.split(',').map((k) => [k, params.get(k) ?? ''])) as Record<K, string>,
    [params, keyList],
  )
  const page = Math.max(0, Number(params.get('page') ?? 1) - 1) || 0
  const sort = params.get('sort') ?? ''

  const apply = useCallback(
    (next: Record<string, string>) => {
      setParams((prev) => {
        const p = new URLSearchParams(prev)
        for (const [k, v] of Object.entries(next)) {
          if (v) p.set(k, v)
          else p.delete(k)
        }
        p.delete('page')
        return p
      })
    },
    [setParams],
  )
  const setPage = useCallback(
    (n: number) =>
      setParams((prev) => {
        const p = new URLSearchParams(prev)
        if (n > 0) p.set('page', String(n + 1))
        else p.delete('page')
        return p
      }),
    [setParams],
  )
  const setSort = useCallback(
    (s: string) =>
      setParams((prev) => {
        const p = new URLSearchParams(prev)
        p.set('sort', s)
        p.delete('page')
        return p
      }),
    [setParams],
  )

  return { filters, page, sort, apply, setPage, setSort }
}

export const numOrUndef = (v: string) => (v ? Number(v) : undefined)
export const strOrUndef = <T extends string>(v: string) => (v ? (v as T) : undefined)
