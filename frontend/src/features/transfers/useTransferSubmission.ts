import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { ApiError } from '../../api/errors'
import { transfersApi, type TransferResult } from '../../api/endpoints'
import type { CreateTransferRequest } from '../../types/api'
import { accountKeys } from '../accounts/hooks'
import { notificationKeys } from '../notifications/hooks'
import { txKeys } from '../transactions/hooks'

/**
 * Idempotency-Key policy for POST /transfers (contract §2, spec §12):
 * - a new key (crypto.randomUUID) per intentional submission;
 * - the SAME key when the same payload is retried after a transport failure, a timeout, a 5xx or
 *   IDEMPOTENCY_REQUEST_IN_PROGRESS — the server then replays the stored result instead of moving money twice;
 * - a fresh key after a success, after a definitive business answer (4xx: the server already stored it, and
 *   retrying under the old key would only replay the same rejection), or when any form value changes.
 */
export function useTransferSubmission() {
  const qc = useQueryClient()
  const current = useRef<{ key: string; fingerprint: string } | null>(null)

  const keyFor = (body: CreateTransferRequest) => {
    const fingerprint = JSON.stringify(body)
    if (!current.current || current.current.fingerprint !== fingerprint) {
      current.current = { key: crypto.randomUUID(), fingerprint }
    }
    return current.current.key
  }

  const mutation = useMutation<TransferResult & { key: string }, ApiError, CreateTransferRequest>({
    mutationFn: async (body) => {
      const key = keyFor(body)
      const result = await transfersApi.create(body, key)
      return { ...result, key }
    },
    onSuccess: () => {
      current.current = null
      qc.invalidateQueries({ queryKey: accountKeys.all })
      qc.invalidateQueries({ queryKey: txKeys.all })
      qc.invalidateQueries({ queryKey: notificationKeys.all })
    },
    onError: (err) => {
      if (!err.transient) current.current = null
      // A rejection is persisted server-side (REJECTED transaction + notification): refresh those views.
      if (err.status === 422) {
        qc.invalidateQueries({ queryKey: txKeys.all })
        qc.invalidateQueries({ queryKey: notificationKeys.all })
      }
    },
  })

  return {
    ...mutation,
    /** The key that the next submit of `body` will use (shown in the review dialog). */
    pendingKey: (body: CreateTransferRequest) => (current.current?.fingerprint === JSON.stringify(body) ? current.current.key : null),
  }
}
