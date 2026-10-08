import { isAxiosError } from 'axios'
import type { ApiErrorBody } from '../types/api'

/** Codes the client produces itself when there is no ApiError body to read. */
export type ClientErrorCode = 'NETWORK_ERROR' | 'TIMEOUT' | 'BACKEND_UNAVAILABLE' | 'UNKNOWN_ERROR'

/** Every error that leaves the API layer is one of these — UI code never sees raw axios errors. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly correlationId?: string
  readonly fieldErrors: { field: string; message: string }[]
  /** True when the request may never have reached the server (safe to retry with the same idempotency key). */
  readonly transient: boolean

  constructor(init: {
    status: number
    code: string
    message: string
    correlationId?: string
    fieldErrors?: { field: string; message: string }[]
    transient?: boolean
  }) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.correlationId = init.correlationId
    this.fieldErrors = init.fieldErrors ?? []
    this.transient = init.transient ?? false
  }
}

function isApiErrorBody(v: unknown): v is ApiErrorBody {
  return typeof v === 'object' && v !== null && typeof (v as ApiErrorBody).code === 'string' && typeof (v as ApiErrorBody).status === 'number'
}

/** Codes after which the same request (same Idempotency-Key) may safely be sent again. */
const RETRY_SAME_KEY = new Set(['IDEMPOTENCY_REQUEST_IN_PROGRESS', 'ACCOUNT_BUSY'])

export function normalizeError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  if (isAxiosError(err)) {
    const correlationId = (err.response?.headers?.['x-correlation-id'] as string | undefined) ?? undefined
    if (err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT') {
      return new ApiError({ status: 0, code: 'TIMEOUT', message: 'Request timed out', transient: true })
    }
    if (!err.response) {
      return new ApiError({ status: 0, code: 'NETWORK_ERROR', message: err.message, transient: true })
    }
    const { status, data } = err.response
    if (isApiErrorBody(data)) {
      return new ApiError({
        status,
        code: data.code,
        message: data.message,
        correlationId: data.correlationId ?? correlationId,
        fieldErrors: data.fieldErrors,
        transient: status >= 500 || RETRY_SAME_KEY.has(data.code),
      })
    }
    // No ApiError body: the gateway/proxy (or a static host without a backend) answered.
    if (status === 502 || status === 503 || status === 504 || status === 404 || typeof data === 'string') {
      return new ApiError({ status, code: 'BACKEND_UNAVAILABLE', message: `HTTP ${status}`, correlationId, transient: status >= 500 })
    }
    return new ApiError({ status, code: 'UNKNOWN_ERROR', message: `HTTP ${status}`, correlationId, transient: status >= 500 })
  }
  return new ApiError({ status: 0, code: 'UNKNOWN_ERROR', message: err instanceof Error ? err.message : String(err) })
}
