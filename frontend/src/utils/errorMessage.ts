import type { TFunction } from 'i18next'
import { ApiError, normalizeError } from '../api/errors'

/** Readable EN/VI message for any error, keyed by the API error code (errors.* in the locale files). */
export function errorText(t: TFunction, err: unknown): string {
  const e = err instanceof ApiError ? err : normalizeError(err)
  if (e.code === 'BACKEND_UNAVAILABLE' && import.meta.env.VITE_API_MODE !== 'mock') return t('errors.BACKEND_UNAVAILABLE')
  return t(`errors.${e.code}`, { defaultValue: t('errors.UNKNOWN_ERROR') })
}

export function errorCode(err: unknown): string {
  return (err instanceof ApiError ? err : normalizeError(err)).code
}
