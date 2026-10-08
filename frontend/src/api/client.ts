import axios, { type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { clearSession, getAccessToken, getRefreshToken, storeTokens } from '../auth/session'
import type { TokenResponse } from '../types/api'
import { normalizeError } from './errors'

export const API_BASE = `${import.meta.env.BASE_URL}api/v1`

/** Correlation IDs: 8–64 chars [A-Za-z0-9._-] (contract §0). One per request, prefixed so they're easy to grep. */
export function newCorrelationId(): string {
  return `web-${crypto.randomUUID()}`
}

export const http = axios.create({
  baseURL: API_BASE,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
})

http.interceptors.request.use((config) => {
  config.headers.set('X-Correlation-Id', newCorrelationId())
  const token = getAccessToken()
  if (token && !config.headers.has('Authorization')) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

// ── Single-flight refresh ───────────────────────────────────────────────────
// Several queries can hit 401 at once when the access token expires; they all wait on one refresh.
let refreshing: Promise<TokenResponse> | null = null

export function refreshSession(): Promise<TokenResponse> {
  if (!refreshing) {
    const refreshToken = getRefreshToken()
    refreshing = (
      refreshToken
        ? axios
            .post<TokenResponse>(`${API_BASE}/auth/refresh`, { refreshToken }, {
              headers: { 'X-Correlation-Id': newCorrelationId(), 'Content-Type': 'application/json' },
              timeout: 15_000,
            })
            .then((r) => {
              storeTokens(r.data)
              return r.data
            })
        : Promise.reject(normalizeError(new Error('No refresh token')))
    )
      .catch((err) => {
        const e = normalizeError(err)
        // Only an explicit rejection ends the session; a network blip keeps the refresh token for later.
        if (e.status === 401 || e.status === 400 || !getRefreshToken()) clearSession()
        throw e
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

const NO_REFRESH_PATHS = ['/auth/login', '/auth/refresh', '/auth/register', '/auth/logout']

http.interceptors.response.use(
  (res) => res,
  async (error) => {
    const config = error?.config as RetriableConfig | undefined
    const status = error?.response?.status
    const url: string = config?.url ?? ''
    if (status === 401 && config && !config._retried && !NO_REFRESH_PATHS.some((p) => url.startsWith(p))) {
      config._retried = true
      try {
        const tokens = await refreshSession()
        config.headers.set('Authorization', `Bearer ${tokens.accessToken}`)
        return http.request(config)
      } catch (refreshError) {
        return Promise.reject(normalizeError(refreshError))
      }
    }
    return Promise.reject(normalizeError(error))
  },
)

/** Drops undefined/empty params so URLs stay clean. */
export function cleanParams<T extends object>(params: T | undefined): Record<string, string | number | boolean> | undefined {
  if (!params) return undefined
  const out: Record<string, string | number | boolean> = {}
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    out[k] = v as string | number | boolean
  }
  return out
}

export async function get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.get<T>(url, { ...config, params: cleanParams(params) })
  return res.data
}
