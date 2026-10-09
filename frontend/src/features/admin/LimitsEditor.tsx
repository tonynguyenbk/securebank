import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../api/errors'
import { AmountInput } from '../../components/AmountInput'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Alert } from '../../components/States'
import { useToast } from '../../components/Toast'
import type { TransferLimits } from '../../types/api'
import { errorText } from '../../utils/errorMessage'
import { useUpdateLimits } from './hooks'

/** PUT /admin/accounts/{id}/limits — both > 0 and per-transaction ≤ daily (checked here and on the server). */
export function LimitsEditor({ accountId, limits }: { accountId: string; limits: TransferLimits }) {
  const { t } = useTranslation()
  const toast = useToast()
  const [per, setPer] = useState(String(limits.perTransactionLimit))
  const [daily, setDaily] = useState(String(limits.dailyLimit))
  const [errors, setErrors] = useState<{ per?: string; daily?: string; form?: string }>({})
  const update = useUpdateLimits(accountId)
  const changed = Number(per) !== limits.perTransactionLimit || Number(daily) !== limits.dailyLimit

  function submit(e: FormEvent) {
    e.preventDefault()
    const p = Number(per || 0)
    const d = Number(daily || 0)
    const next: typeof errors = {}
    if (p <= 0) next.per = t('ops.limits.positive')
    if (d <= 0) next.daily = t('ops.limits.positive')
    if (!next.per && !next.daily && p > d) next.per = t('ops.limits.perAboveDaily')
    setErrors(next)
    if (next.per || next.daily) return
    update.mutate(
      { perTransactionLimit: p, dailyLimit: d },
      {
        onSuccess: () => toast.success(t('ops.limits.saved')),
        onError: (err) => {
          if (err instanceof ApiError && err.code === 'VALIDATION_FAILED') {
            const fe = Object.fromEntries(err.fieldErrors.map((f) => [f.field === 'dailyLimit' ? 'daily' : 'per', t('ops.limits.serverRule')]))
            setErrors({ ...fe, form: errorText(t, err) })
          } else setErrors({ form: errorText(t, err) })
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label={t('ops.limits.per')} error={errors.per}>
          {(s) => <AmountInput {...s} value={per} onValueChange={setPer} />}
        </Field>
        <Field label={t('ops.limits.daily')} error={errors.daily}>
          {(s) => <AmountInput {...s} value={daily} onValueChange={setDaily} />}
        </Field>
      </div>
      {errors.form && <Alert>{errors.form}</Alert>}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-[12px] text-ink-2">{t('ops.limits.audited')}</p>
        <Button type="submit" size="sm" disabled={!changed} loading={update.isPending}>
          {t('ops.limits.save')}
        </Button>
      </div>
    </form>
  )
}
