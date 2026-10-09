import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../components/Button'
import { Field, Textarea } from '../../components/Field'
import { Alert } from '../../components/States'
import { useToast } from '../../components/Toast'
import { FRAUD_TRANSITIONS, NOTE_REQUIRED, type FraudAlertDetail, type FraudAlertStatus } from '../../types/api'
import { errorText } from '../../utils/errorMessage'
import { useReviewAlert } from './hooks'

/** Decision form: only the transitions the contract allows from the current status are offered. */
export function ReviewPanel({ alert }: { alert: FraudAlertDetail }) {
  const { t } = useTranslation()
  const toast = useToast()
  const options = FRAUD_TRANSITIONS[alert.status]
  const [target, setTarget] = useState<FraudAlertStatus | ''>('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const review = useReviewAlert(alert.id)
  const needsNote = target !== '' && NOTE_REQUIRED.includes(target)

  if (options.length === 0) return <p className="text-[13px] text-ink-2">{t('fraud.review.terminal')}</p>

  function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!target) return setError(t('fraud.review.pick'))
    if (needsNote && !note.trim()) return setError(t('fraud.review.noteRequired'))
    review.mutate(
      { status: target, ...(note.trim() ? { note: note.trim() } : {}) },
      {
        onSuccess: () => {
          toast.success(t('fraud.review.saved', { status: t(`fraudStatus.${target}`) }))
          setTarget('')
          setNote('')
        },
        onError: (err) => setError(errorText(t, err)),
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate>
      <fieldset>
        <legend className="mb-2 font-medium">{t('fraud.review.decision')}</legend>
        <div className="space-y-1.5">
          {options.map((o) => (
            <label
              key={o}
              className={`flex cursor-pointer items-start gap-3 rounded-sheet border px-3 py-2.5 transition-colors duration-150 ${
                target === o ? 'border-vault bg-vault-tint/60' : 'border-rule hover:border-ink-2/60'
              }`}
            >
              <input type="radio" name="decision" value={o} checked={target === o} onChange={() => setTarget(o)} className="mt-1 accent-[var(--vault)]" />
              <span>
                <span className="block font-medium">{t(`fraud.review.option.${o}`)}</span>
                <span className="block text-[12px] text-ink-2">{t(`fraud.review.hint.${o}`)}</span>
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      <Field label={t('fraud.review.note')} optional={needsNote ? t('fraud.review.required') : t('common.optional')} className="mt-4">
        {(s) => <Textarea {...s} rows={3} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} placeholder={t('fraud.review.notePh')} />}
      </Field>
      <div aria-live="polite" className="mt-3">
        {error && <Alert>{error}</Alert>}
      </div>
      <Button type="submit" className="mt-3 w-full" loading={review.isPending} variant={target === 'REJECTED' ? 'danger' : 'primary'}>
        {t('fraud.review.submit')}
      </Button>
    </form>
  )
}
