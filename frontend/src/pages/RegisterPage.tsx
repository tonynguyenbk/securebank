import { Check, Circle } from 'lucide-react'
import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/errors'
import { authApi } from '../api/endpoints'
import { useAuth } from '../auth/useAuth'
import { Button } from '../components/Button'
import { Field, Input } from '../components/Field'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Wordmark } from '../components/Layout'
import { PreviewBadge } from '../components/PreviewBadge'
import { Alert } from '../components/States'
import { ThemeSwitch } from '../components/ThemeSwitch'
import { useToast } from '../components/Toast'
import { errorText } from '../utils/errorMessage'

type Form = { username: string; password: string; fullName: string; email: string; phone: string }
type Errors = Partial<Record<keyof Form, string>>

// Contract §1 validation rules.
const PASSWORD_RULES = [
  { key: 'length', test: (p: string) => p.length >= 8 && p.length <= 100 },
  { key: 'upper', test: (p: string) => /[A-Z]/.test(p) },
  { key: 'lower', test: (p: string) => /[a-z]/.test(p) },
  { key: 'digit', test: (p: string) => /\d/.test(p) },
  { key: 'symbol', test: (p: string) => /[^A-Za-z0-9]/.test(p) },
] as const

function validate(f: Form): Errors {
  const e: Errors = {}
  if (!/^[a-z0-9._-]{3,50}$/.test(f.username)) e.username = 'register.errors.username'
  if (!PASSWORD_RULES.every((r) => r.test(f.password))) e.password = 'register.errors.password'
  if (!f.fullName.trim() || f.fullName.length > 200) e.fullName = 'register.errors.fullName'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) e.email = 'register.errors.email'
  if (f.phone && !/^\+?[0-9]{9,15}$/.test(f.phone)) e.phone = 'register.errors.phone'
  return e
}

export function RegisterPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [form, setForm] = useState<Form>({ username: '', password: '', fullName: '', email: '', phone: '' })
  const [errors, setErrors] = useState<Errors>({})
  const [touched, setTouched] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const set = (k: keyof Form) => (e: ChangeEvent<HTMLInputElement>) => {
    const value = k === 'username' ? e.target.value.toLowerCase() : e.target.value
    const next = { ...form, [k]: value }
    setForm(next)
    if (touched) setErrors(validate(next))
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setTouched(true)
    const v = validate(form)
    setErrors(v)
    setFormError(null)
    if (Object.keys(v).length) return
    setSubmitting(true)
    try {
      await authApi.register({ username: form.username, password: form.password, fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone || undefined })
      await login(form.username, form.password)
      toast.success(t('register.done'))
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setSubmitting(false)
      if (err instanceof ApiError && err.code === 'AUTH_USERNAME_TAKEN') {
        setErrors({ username: 'errors.AUTH_USERNAME_TAKEN' })
        return
      }
      if (err instanceof ApiError && err.code === 'VALIDATION_FAILED' && err.fieldErrors.length) {
        const fe: Errors = {}
        for (const f of err.fieldErrors) if (f.field in form) fe[f.field as keyof Form] = `register.errors.${f.field}`
        setErrors(fe)
        return
      }
      setFormError(errorText(t, err))
    }
  }

  const err = (k: keyof Form) => (errors[k] ? t(errors[k]!) : null)

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="mx-auto flex w-full max-w-[1120px] items-center justify-between gap-3 px-4 py-5 sm:px-8">
        <Link to="/login" aria-label={t('nav.home')}>
          <Wordmark />
        </Link>
        <div className="flex items-center gap-2 sm:gap-3">
          <PreviewBadge />
          <LanguageSwitch />
          <ThemeSwitch />
        </div>
      </header>
      <main className="mx-auto w-full max-w-[1120px] flex-1 px-4 pb-12 sm:px-8">
        <section className="mx-auto max-w-2xl rounded-sheet border border-rule bg-sheet">
          <div className="border-b-2 border-double border-ink/70 px-6 pt-7 pb-5 sm:px-10">
            <p className="text-[11px] font-semibold tracking-[0.18em] text-ink-2 uppercase">{t('register.eyebrow')}</p>
            <h1 className="mt-1 font-display text-[26px] leading-tight font-semibold tracking-tight">{t('register.title')}</h1>
            <p className="mt-2 max-w-[56ch] text-ink-2">{t('register.subtitle')}</p>
          </div>
          <form noValidate onSubmit={onSubmit} className="grid gap-5 px-6 py-7 sm:grid-cols-2 sm:px-10">
            <Field label={t('register.fullName')} error={err('fullName')} className="sm:col-span-2">
              {(s) => <Input {...s} autoComplete="name" value={form.fullName} onChange={set('fullName')} placeholder={t('register.fullNamePh')} />}
            </Field>
            <Field label={t('register.username')} hint={t('register.usernameHint')} error={err('username')}>
              {(s) => <Input {...s} autoComplete="username" autoCapitalize="none" spellCheck={false} value={form.username} onChange={set('username')} className="figures" />}
            </Field>
            <Field label={t('register.email')} error={err('email')}>
              {(s) => <Input {...s} type="email" autoComplete="email" value={form.email} onChange={set('email')} />}
            </Field>
            <Field
              label={t('register.password')}
              error={err('password')}
              className="sm:col-span-2"
              status={
                <ul className="flex flex-wrap gap-x-4 gap-y-1" aria-label={t('register.rulesLabel')}>
                  {PASSWORD_RULES.map((r) => {
                    const ok = r.test(form.password)
                    return (
                      <li key={r.key} className={`flex items-center gap-1.5 ${ok ? 'text-credit' : 'text-ink-2'}`}>
                        {ok ? <Check size={13} strokeWidth={2} aria-hidden /> : <Circle size={10} strokeWidth={1.5} aria-hidden />}
                        <span>{t(`register.rules.${r.key}`)}</span>
                        <span className="sr-only">{ok ? t('register.ruleMet') : t('register.ruleNotMet')}</span>
                      </li>
                    )
                  })}
                </ul>
              }
            >
              {(s) => <Input {...s} type="password" autoComplete="new-password" value={form.password} onChange={set('password')} />}
            </Field>
            <Field label={t('register.phone')} optional={t('common.optional')} hint={t('register.phoneHint')} error={err('phone')}>
              {(s) => <Input {...s} type="tel" inputMode="tel" autoComplete="tel" value={form.phone} onChange={set('phone')} className="figures" placeholder="+84901234567" />}
            </Field>

            {formError && (
              <div className="sm:col-span-2">
                <Alert>{formError}</Alert>
              </div>
            )}

            <div className="flex flex-col-reverse items-start gap-4 border-t border-rule pt-5 sm:col-span-2 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-[13px] text-ink-2">
                {t('register.haveAccount')}{' '}
                <Link to="/login" className="font-medium text-vault underline-offset-4 hover:underline">
                  {t('register.signIn')}
                </Link>
              </p>
              <Button type="submit" loading={submitting} className="w-full sm:w-auto">
                {submitting ? t('register.submitting') : t('register.submit')}
              </Button>
            </div>
          </form>
        </section>
      </main>
    </div>
  )
}
