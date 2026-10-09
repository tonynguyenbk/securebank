import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AccountNumber } from '../../components/AccountNumber'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { Field, Textarea } from '../../components/Field'
import { Alert } from '../../components/States'
import { useToast } from '../../components/Toast'
import { errorText } from '../../utils/errorMessage'
import { useSetAccountStatus } from './hooks'

type Props = {
  open: boolean
  onClose: () => void
  account: { id: string; accountNumber: string; customerName?: string }
  freeze: boolean
  /** Pre-filled reason, e.g. the fraud alert reference. */
  suggestedReason?: string
}

/** Freeze / unfreeze with a mandatory reason (3–255 chars), recorded in the audit log. */
export function FreezeDialog({ open, onClose, account, freeze, suggestedReason = '' }: Props) {
  const { t } = useTranslation()
  const toast = useToast()
  const [reason, setReason] = useState(suggestedReason)
  const [error, setError] = useState<string | null>(null)
  const mutation = useSetAccountStatus()

  function close() {
    setError(null)
    onClose()
  }

  function submit() {
    const r = reason.trim()
    if (r.length < 3 || r.length > 255) {
      setError(t('ops.freeze.reasonRule'))
      return
    }
    mutation.mutate(
      { id: account.id, freeze, reason: r },
      {
        onSuccess: () => {
          toast.success(t(freeze ? 'ops.freeze.frozen' : 'ops.freeze.unfrozen', { number: account.accountNumber }))
          setReason('')
          close()
        },
        onError: (e) => setError(errorText(t, e)),
      },
    )
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      busy={mutation.isPending}
      size="sm"
      title={freeze ? t('ops.freeze.title') : t('ops.freeze.unfreezeTitle')}
      description={freeze ? t('ops.freeze.body') : t('ops.freeze.unfreezeBody')}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={mutation.isPending}>
            {t('common.cancel')}
          </Button>
          <Button variant={freeze ? 'danger' : 'primary'} onClick={submit} loading={mutation.isPending}>
            {freeze ? t('ops.freeze.confirm') : t('ops.freeze.unfreezeConfirm')}
          </Button>
        </>
      }
    >
      <p className="mb-4 flex flex-wrap items-baseline gap-x-2">
        <AccountNumber value={account.accountNumber} className="font-medium" />
        {account.customerName && <span className="text-ink-2">{account.customerName}</span>}
      </p>
      <Field label={t('ops.freeze.reason')} hint={t('ops.freeze.reasonHint')} error={error}>
        {(s) => <Textarea {...s} rows={3} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} />}
      </Field>
      {mutation.isError && !error && (
        <div className="mt-3">
          <Alert>{errorText(t, mutation.error)}</Alert>
        </div>
      )}
    </Dialog>
  )
}
