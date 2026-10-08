import { LogOut } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/useAuth'
import { getAccessExpiry } from '../../auth/session'
import { Button } from '../../components/Button'
import { LanguageSwitch } from '../../components/LanguageSwitch'
import { Facts, PageHeader, Sheet } from '../../components/Layout'
import { ErrorState, Skeleton } from '../../components/States'
import { ThemeSwitch } from '../../components/ThemeSwitch'
import { useCustomerMe } from '../../features/accounts/hooks'
import { formatDate, formatTime } from '../../utils/format'

export function ProfilePage() {
  const { t, i18n } = useTranslation()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const customer = useCustomerMe()
  const [busy, setBusy] = useState(false)
  const [expiry] = useState(() => getAccessExpiry())
  const lang = i18n.language

  return (
    <div className="max-w-3xl">
      <PageHeader title={t('profile.title')}>{t('profile.subtitle')}</PageHeader>
      <div className="space-y-5">
        <Sheet title={t('profile.customer')}>
          {customer.isPending && <Skeleton className="h-32 w-full" />}
          {customer.isError && <ErrorState error={customer.error} onRetry={() => customer.refetch()} compact />}
          {customer.data && (
            <Facts
              items={[
                [t('register.fullName'), customer.data.fullName],
                [t('register.email'), customer.data.email],
                [t('register.phone'), customer.data.phone ? <span key="p" className="figures">{customer.data.phone}</span> : '—'],
                [t('profile.customerSince'), <span key="c" className="figures text-[13px]">{formatDate(customer.data.createdAt, lang)}</span>],
                [t('profile.customerId'), <span key="i" className="figures text-[12px] break-all text-ink-2">{customer.data.id}</span>],
              ]}
            />
          )}
        </Sheet>

        <Sheet title={t('profile.security')}>
          {user && (
            <Facts
              items={[
                [t('register.username'), <span key="u" className="figures">{user.username}</span>],
                [t('profile.roles'), user.roles.map((r) => t(`roles.${r}`)).join(', ')],
                [t('profile.session'), expiry ? t('profile.sessionUntil', { time: formatTime(new Date(expiry).toISOString(), lang) }) : '—'],
              ]}
            />
          )}
          <p className="mt-4 max-w-[64ch] text-[13px] text-ink-2">{t('profile.tokenNote')}</p>
          <Button
            variant="secondary"
            className="mt-4"
            loading={busy}
            icon={<LogOut size={15} strokeWidth={1.5} aria-hidden />}
            onClick={async () => {
              setBusy(true)
              await logout()
              navigate('/login', { replace: true })
            }}
          >
            {t('nav.signOut')}
          </Button>
        </Sheet>

        <Sheet title={t('profile.preferences')}>
          <div className="flex flex-wrap items-center gap-x-8 gap-y-4">
            <div>
              <p className="mb-1.5 text-[13px] text-ink-2">{t('lang.label')}</p>
              <LanguageSwitch />
            </div>
            <div>
              <p className="mb-1.5 text-[13px] text-ink-2">{t('profile.theme')}</p>
              <ThemeSwitch />
            </div>
          </div>
          <p className="mt-4 text-[13px] text-ink-2">{t('profile.prefsNote')}</p>
        </Sheet>
      </div>
    </div>
  )
}
