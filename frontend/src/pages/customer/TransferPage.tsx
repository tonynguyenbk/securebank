import { CircleCheck, CircleX, LoaderCircle } from 'lucide-react'
import { useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/errors'
import { useAuth } from '../../auth/useAuth'
import { AccountNumber } from '../../components/AccountNumber'
import { AmountInput } from '../../components/AmountInput'
import { Button } from '../../components/Button'
import { Field, Input, Select, Textarea } from '../../components/Field'
import { PageHeader, Sheet } from '../../components/Layout'
import { Money } from '../../components/Money'
import { Alert, EmptyState, ErrorState, Skeleton } from '../../components/States'
import { useToast } from '../../components/Toast'
import { useAccount, useAccounts } from '../../features/accounts/hooks'
import { LimitMeter } from '../../features/transfers/LimitMeter'
import { ReviewDialog, type ReviewData } from '../../features/transfers/ReviewDialog'
import { TransferReceipt } from '../../features/transfers/TransferReceipt'
import { useBeneficiaryLookup } from '../../features/transfers/useBeneficiaryLookup'
import { useTransferSubmission } from '../../features/transfers/useTransferSubmission'
import {
  DESCRIPTION_MAX,
  fieldForCode,
  fieldForValidation,
  validateTransfer,
  type TransferErrors,
  type TransferField,
  type TransferValues,
} from '../../features/transfers/validation'
import type { CreateTransferRequest, TransferResponse } from '../../types/api'
import { errorText } from '../../utils/errorMessage'

export function TransferPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const toast = useToast()
  const [params] = useSearchParams()
  const accounts = useAccounts()
  const usable = useMemo(() => accounts.data ?? [], [accounts.data])

  const [values, setValues] = useState<TransferValues>({ sourceId: params.get('from') ?? '', destination: '', amount: '', description: '' })
  const sourceId = values.sourceId || usable.find((a) => a.status === 'ACTIVE')?.id || usable[0]?.id || ''
  const source = usable.find((a) => a.id === sourceId)
  const detail = useAccount(sourceId || undefined)
  const lookup = useBeneficiaryLookup(values.destination)
  const holder = lookup.data?.accountNumber === values.destination ? lookup.data.holderName : null

  const [errors, setErrors] = useState<TransferErrors>({})
  const [submitted, setSubmitted] = useState(false)
  const [review, setReview] = useState<ReviewData | null>(null)
  const [transientError, setTransientError] = useState<string | null>(null)
  const [done, setDone] = useState<{ result: TransferResponse; replayed: boolean } | null>(null)
  const submission = useTransferSubmission()

  const set = (field: keyof TransferValues, errField: TransferField) => (value: string) => {
    const next = { ...values, sourceId, [field]: value }
    setValues(next)
    // Re-validate live once the user has tried to submit; before that, only clear this field's error.
    setErrors((prev) =>
      submitted
        ? validateTransfer(next, usable.find((a) => a.id === next.sourceId), detail.data)
        : { ...prev, [errField]: undefined, form: undefined },
    )
  }

  const body = (): CreateTransferRequest => ({
    sourceAccountNumber: source?.accountNumber ?? '',
    destinationAccountNumber: values.destination,
    amount: Number(values.amount || 0),
    currency: 'VND',
    ...(values.description.trim() ? { description: values.description.trim() } : {}),
  })

  function onReview(e: FormEvent) {
    e.preventDefault()
    setSubmitted(true)
    const v = validateTransfer({ ...values, sourceId }, source, detail.data)
    if (!v.destination && lookup.isError) v.destination = 'errors.ACCOUNT_NOT_FOUND'
    setErrors(v)
    const first = (['source', 'destination', 'amount', 'description'] as const).find((k) => v[k])
    if (first) {
      document.getElementById(`transfer-${first}`)?.focus()
      return
    }
    setTransientError(null)
    setReview({
      sourceNumber: source!.accountNumber,
      sourceBalance: source!.balance,
      destination: values.destination,
      holderName: holder ?? '—',
      amount: Number(values.amount),
      description: values.description.trim(),
    })
  }

  function onConfirm() {
    submission.mutate(body(), {
      onSuccess: (res) => {
        setReview(null)
        setTransientError(null)
        setDone({ result: res.data, replayed: res.replayed })
        toast.success(t('transfer.toast.sent'))
      },
      onError: (err) => {
        if (err.transient) {
          // Keep the dialog open: "Retry" re-sends the same payload with the same Idempotency-Key.
          setTransientError(errorText(t, err))
          return
        }
        setReview(null)
        const next: TransferErrors = {}
        if (err instanceof ApiError && err.code === 'VALIDATION_FAILED' && err.fieldErrors.length) {
          for (const f of err.fieldErrors) next[fieldForValidation(f.field)] = 'errors.VALIDATION_FAILED'
        } else {
          next[fieldForCode(err.code)] = `errors.${err.code}`
        }
        setErrors(next)
        if (err.status === 422) toast.error(t('transfer.toast.rejected'))
      },
    })
  }

  function reset() {
    setDone(null)
    setSubmitted(false)
    setErrors({})
    setValues({ sourceId, destination: '', amount: '', description: '' })
    submission.reset()
  }

  if (done) return <TransferReceipt result={done.result} replayed={done.replayed} senderName={user?.fullName ?? ''} onNew={reset} />

  const msg = (k?: string) => (k ? t(k, { defaultValue: t('errors.UNKNOWN_ERROR') }) : null)
  const amountNum = Number(values.amount || 0)

  return (
    <div>
      <PageHeader title={t('transfer.title')}>{t('transfer.subtitle')}</PageHeader>

      {accounts.isPending && <Skeleton className="h-80 w-full" />}
      {accounts.isError && <ErrorState error={accounts.error} onRetry={() => accounts.refetch()} />}
      {accounts.data && usable.length === 0 && <EmptyState title={t('transfer.noAccounts')} body={t('transfer.noAccountsBody')} />}

      {usable.length > 0 && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
          <form noValidate onSubmit={onReview} className="rounded-sheet border border-rule bg-sheet">
            <div className="space-y-5 px-5 py-6 sm:px-7">
              <Field label={t('transfer.source')} error={msg(errors.source)}>
                {(s) => (
                  <Select {...s} id="transfer-source" value={sourceId} onChange={(e) => set('sourceId', 'source')(e.target.value)} className="figures">
                    {usable.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.accountNumber} · {t(`accountStatus.${a.status}`)}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>

              <Field
                label={t('transfer.destination')}
                hint={!holder && !lookup.isFetching && !lookup.isError ? t('transfer.destinationHint') : undefined}
                error={msg(errors.destination)}
                status={<BeneficiaryStatus loading={lookup.isFetching} name={holder} notFound={lookup.isError && values.destination.length === 10 && !errors.destination} />}
              >
                {(s) => (
                  <Input
                    {...s}
                    id="transfer-destination"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={10}
                    placeholder="1000000002"
                    value={values.destination}
                    onChange={(e) => set('destination', 'destination')(e.target.value.replace(/\D/g, '').slice(0, 10))}
                    className="figures tracking-wider"
                  />
                )}
              </Field>

              <Field
                label={t('transfer.amount')}
                error={msg(errors.amount)}
                hint={
                  source ? (
                    <span>
                      {t('transfer.available')} <Money value={source.balance} />
                    </span>
                  ) : undefined
                }
              >
                {(s) => <AmountInput {...s} id="transfer-amount" value={values.amount} onValueChange={set('amount', 'amount')} placeholder="0" />}
              </Field>

              <Field label={t('transfer.description')} optional={`${values.description.length}/${DESCRIPTION_MAX}`} error={msg(errors.description)}>
                {(s) => (
                  <Textarea
                    {...s}
                    id="transfer-description"
                    rows={2}
                    value={values.description}
                    placeholder={t('transfer.descriptionPh')}
                    onChange={(e) => set('description', 'description')(e.target.value)}
                  />
                )}
              </Field>

              {errors.form && <Alert>{msg(errors.form)}</Alert>}
            </div>
            <div className="flex flex-col-reverse gap-3 border-t border-rule px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-7">
              <p className="text-[12px] text-ink-2">{t('transfer.reviewNote')}</p>
              <Button type="submit" className="w-full sm:w-auto">
                {t('transfer.reviewCta')}
              </Button>
            </div>
          </form>

          <aside className="space-y-5">
            {source && (
              <Sheet title={t('transfer.fromAccount')}>
                <AccountNumber value={source.accountNumber} className="text-[13px] text-ink-2" />
                <p className="mt-3 text-[12px] text-ink-2">{t('account.available')}</p>
                <Money value={source.balance} className="text-[22px] font-medium" />
                {amountNum > 0 && amountNum <= source.balance && (
                  <p className="mt-1 text-[12px] text-ink-2">
                    {t('transfer.after')} <Money value={source.balance - amountNum} />
                  </p>
                )}
                <div className="mt-4 border-t border-rule pt-4">
                  {detail.data ? <LimitMeter limits={detail.data.limits} pending={amountNum} /> : <Skeleton className="h-10 w-full" />}
                </div>
              </Sheet>
            )}
            <div className="border-l-2 border-rule pl-4 text-[13px] text-ink-2">
              <p className="font-medium text-ink">{t('transfer.safetyTitle')}</p>
              <p className="mt-1">{t('transfer.safetyBody')}</p>
            </div>
          </aside>
        </div>
      )}

      <ReviewDialog
        open={!!review}
        data={review}
        submitting={submission.isPending}
        transientError={transientError}
        idempotencyKey={review ? submission.pendingKey(body()) : null}
        onConfirm={onConfirm}
        onClose={() => {
          setReview(null)
          setTransientError(null)
        }}
      />
    </div>
  )
}

function BeneficiaryStatus({ loading, name, notFound }: { loading: boolean; name: string | null; notFound: boolean }) {
  const { t } = useTranslation()
  if (loading)
    return (
      <span className="inline-flex items-center gap-1.5 text-ink-2">
        <LoaderCircle size={13} className="animate-spin" aria-hidden /> {t('transfer.lookup.checking')}
      </span>
    )
  if (name)
    return (
      <span className="inline-flex flex-wrap items-center gap-1.5">
        <CircleCheck size={14} strokeWidth={1.75} className="text-credit" aria-hidden />
        <span className="text-ink-2">{t('transfer.lookup.holder')}</span> <span className="font-medium">{name}</span>
      </span>
    )
  if (notFound)
    return (
      <span className="inline-flex items-center gap-1.5 text-debit">
        <CircleX size={14} strokeWidth={1.75} aria-hidden /> {t('transfer.lookup.notFound')}
      </span>
    )
  return null
}
