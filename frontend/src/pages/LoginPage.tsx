import { Eye, EyeOff, LoaderCircle, TriangleAlert } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { homeFor } from '../auth/context'
import { useAuth } from '../auth/useAuth'
import { Guilloche } from '../components/Guilloche'
import { Wordmark } from '../components/Layout'
import { errorCode, errorText } from '../utils/errorMessage'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { PreviewBadge } from '../components/PreviewBadge'
import { ThemeSwitch } from '../components/ThemeSwitch'

type Role = 'CUSTOMER' | 'BANK_STAFF' | 'AUDITOR' | 'ADMIN'

// Seed credentials from the local demo profile (spec §30). Shown only because this is a portfolio demo.
const DEMO_USERS: { username: string; password: string; role: Role; account?: string }[] = [
  { username: 'customer1', password: 'Customer@123', role: 'CUSTOMER', account: '1000000001' },
  { username: 'customer2', password: 'Customer@123', role: 'CUSTOMER', account: '1000000002' },
  { username: 'staff1', password: 'Staff@123', role: 'BANK_STAFF' },
  { username: 'auditor1', password: 'Auditor@123', role: 'AUDITOR' },
  { username: 'admin1', password: 'Admin@123', role: 'ADMIN' },
]

const YEAR = new Date().getFullYear()

type FieldErrors = Partial<Record<'username' | 'password', string>>

export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const ids = { user: useId(), pass: useId(), userErr: useId(), passErr: useId() }
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    const next: FieldErrors = {}
    if (!username.trim()) next.username = t('login.required', { field: t('login.username').toLowerCase() })
    if (!password) next.password = t('login.required', { field: t('login.password').toLowerCase() })
    setErrors(next)
    setFormError(null)
    if (Object.keys(next).length) return

    setSubmitting(true)
    try {
      const user = await login(username.trim(), password)
      const from = (location.state as { from?: string } | null)?.from
      const home = homeFor(user)
      // Only honour the intended path if it belongs to this user's portal.
      const target = from && from.startsWith('/ops') === home.startsWith('/ops') ? from : home
      navigate(target, { replace: true })
    } catch (err) {
      const code = errorCode(err)
      setFormError(code === 'BACKEND_UNAVAILABLE' || code === 'NETWORK_ERROR' ? t('login.backendOffline') : errorText(t, err))
      setSubmitting(false)
    }
  }

  function fill(u: (typeof DEMO_USERS)[number]) {
    setUsername(u.username)
    setPassword(u.password)
    setErrors({})
    setFormError(null)
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="mx-auto flex w-full max-w-[1120px] items-center justify-between px-4 py-5 sm:px-8">
        <Wordmark />
        <div className="flex items-center gap-3">
          <PreviewBadge />
          <LanguageSwitch />
          <ThemeSwitch />
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-[1120px] flex-1 items-start px-4 pb-10 sm:px-8 lg:items-center">
        <section className="w-full overflow-hidden rounded-sheet border border-rule bg-sheet">
          {/* Cheque-style header band */}
          <div className="relative h-24 overflow-hidden bg-band text-white sm:h-28">
            <Guilloche seed="securebank-login" className="absolute inset-0 h-full w-full text-white/25" />
            <div className="relative flex h-full items-end justify-between px-6 pb-4 sm:px-10">
              <p className="font-display text-lg font-semibold tracking-tight sm:text-xl">{t('login.band')}</p>
              <p className="figures text-xs text-white/80">VND · {YEAR}</p>
            </div>
          </div>

          <div className="grid lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
            <form noValidate onSubmit={onSubmit} className="px-6 py-8 sm:px-10 sm:py-10" aria-describedby={formError ? 'form-error' : undefined}>
              <h1 className="font-display text-[28px] leading-tight font-semibold tracking-tight text-balance">{t('login.title')}</h1>
              <p className="mt-2 max-w-[44ch] text-ink-2">{t('login.subtitle')}</p>

              <div className="mt-8 space-y-5">
                <div>
                  <label htmlFor={ids.user} className="mb-1.5 block font-medium">
                    {t('login.username')}
                  </label>
                  <input
                    id={ids.user}
                    autoComplete="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    aria-invalid={!!errors.username}
                    aria-describedby={errors.username ? ids.userErr : undefined}
                    className={inputClass(!!errors.username)}
                  />
                  {errors.username && <FieldError id={ids.userErr}>{errors.username}</FieldError>}
                </div>

                <div>
                  <label htmlFor={ids.pass} className="mb-1.5 block font-medium">
                    {t('login.password')}
                  </label>
                  <div className="relative">
                    <input
                      id={ids.pass}
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="current-password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      aria-invalid={!!errors.password}
                      aria-describedby={errors.password ? ids.passErr : undefined}
                      className={`${inputClass(!!errors.password)} pr-11`}
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}
                      className="absolute inset-y-0 right-0 flex w-11 cursor-pointer items-center justify-center text-ink-2 hover:text-ink"
                    >
                      {showPassword ? <EyeOff size={17} strokeWidth={1.5} /> : <Eye size={17} strokeWidth={1.5} />}
                    </button>
                  </div>
                  {errors.password && <FieldError id={ids.passErr}>{errors.password}</FieldError>}
                </div>
              </div>

              {formError && (
                <div id="form-error" role="alert" className="mt-6 flex gap-2.5 border-l-2 border-brass bg-brass/8 px-3.5 py-3 text-[13px]">
                  <TriangleAlert size={16} strokeWidth={1.5} className="mt-0.5 shrink-0 text-brass" aria-hidden />
                  <p>{formError}</p>
                </div>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="mt-7 inline-flex h-11 w-full cursor-pointer items-center justify-center gap-2 rounded-sheet bg-vault px-5 font-medium text-on-vault transition-colors duration-150 hover:bg-vault-deep disabled:cursor-progress disabled:opacity-80 sm:w-auto sm:min-w-40"
              >
                {submitting && <LoaderCircle size={16} className="animate-spin" aria-hidden />}
                {submitting ? t('login.submitting') : t('login.submit')}
              </button>
              <p className="mt-6 text-[13px] text-ink-2">
                {t('login.noAccount')}{' '}
                <Link to="/register" className="font-medium text-vault underline-offset-4 hover:underline">
                  {t('login.register')}
                </Link>
              </p>
            </form>

            <aside className="border-t border-rule px-6 py-8 sm:px-10 sm:py-10 lg:border-t-0 lg:border-l">
              <h2 className="font-display text-base font-semibold">{t('login.demoTitle')}</h2>
              <p className="mt-1 text-[13px] text-ink-2">{t('login.demoHint')}</p>
              <table className="mt-5 w-full text-left text-[13px]">
                <thead>
                  <tr className="border-b border-ink text-xs tracking-wide text-ink-2 uppercase">
                    <th scope="col" className="py-2 pr-3 font-medium">{t('login.colUser')}</th>
                    <th scope="col" className="py-2 pr-3 font-medium">{t('login.colRole')}</th>
                    <th scope="col" className="py-2 text-right font-medium">{t('login.colAccount')}</th>
                  </tr>
                </thead>
                <tbody>
                  {DEMO_USERS.map((u) => (
                    <tr key={u.username} className="border-b border-rule last:border-b-0">
                      <td className="py-0">
                        <button
                          type="button"
                          onClick={() => fill(u)}
                          aria-label={t('login.useAccount', { user: u.username })}
                          className="figures flex min-h-11 w-full cursor-pointer items-center pr-3 text-left text-vault underline-offset-4 hover:underline"
                        >
                          {u.username}
                        </button>
                      </td>
                      <td className="py-2 pr-3 text-ink-2">{t(`roles.${u.role}`)}</td>
                      <td className="figures py-2 text-right">{u.account ? formatAccount(u.account) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </aside>
          </div>

          <footer className="figures flex flex-wrap justify-between gap-2 border-t border-dashed border-rule px-6 py-3 text-[11px] text-ink-2 sm:px-10">
            <span>{t('login.footer')}</span>
            <span>build {import.meta.env.VITE_BUILD_SHA?.slice(0, 7) ?? 'local'}</span>
          </footer>
        </section>
      </main>
    </div>
  )
}

function FieldError({ id, children }: { id: string; children: string }) {
  return (
    <p id={id} className="mt-1.5 text-[13px] text-debit">
      {children}
    </p>
  )
}

function inputClass(invalid: boolean) {
  return `h-11 w-full rounded-sheet border bg-sheet px-3 text-[15px] outline-none transition-colors duration-150 focus-visible:outline-2 focus-visible:outline-offset-0 ${
    invalid ? 'border-debit' : 'border-rule hover:border-ink-2/60 focus:border-vault'
  }`
}

function formatAccount(n: string) {
  return n.replace(/(\d{4})(\d{4})(\d+)/, '$1 $2 $3')
}
