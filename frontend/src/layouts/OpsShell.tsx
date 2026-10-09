import { ArrowLeftRight, Gauge, LogOut, Menu, ScrollText, ShieldAlert, Users, Wallet, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Wordmark } from '../components/Layout'
import { PreviewBadge } from '../components/PreviewBadge'
import { ResetPreviewButton } from '../components/PreviewTools'
import { ThemeSwitch } from '../components/ThemeSwitch'
import { useFraudStats } from '../features/fraud/hooks'

const NAV = [
  { to: '/ops/dashboard', key: 'ops.nav.today', icon: Gauge },
  { to: '/ops/fraud', key: 'ops.nav.fraud', icon: ShieldAlert, badge: true },
  { to: '/ops/accounts', key: 'ops.nav.accounts', icon: Wallet },
  { to: '/ops/customers', key: 'ops.nav.customers', icon: Users },
  { to: '/ops/transactions', key: 'ops.nav.transactions', icon: ArrowLeftRight },
  { to: '/ops/audit', key: 'ops.nav.audit', icon: ScrollText },
]

/** Bank operations portal: dark ink console sidebar (collapses to a top drawer below 1024 px). */
export function OpsShell() {
  const { t } = useTranslation()
  const location = useLocation()
  // Open state is tied to the path it was opened on, so navigating closes it without an effect.
  const [openOn, setOpenOn] = useState<string | null>(null)
  const drawer = openOn === location.pathname

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[232px_minmax(0,1fr)]">
      <a href="#main" className="sr-only z-50 bg-sheet px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2">
        {t('nav.skip')}
      </a>
      {/* < 1024 px: top bar + drawer */}
      <header className="no-print sticky top-0 z-30 bg-console text-on-console lg:hidden">
        <div className="flex h-14 items-center gap-3 px-4 sm:px-6">
          <Wordmark tone="console" sub={t('ops.portal')} />
          <div className="ml-auto flex items-center gap-2">
            <PreviewBadge />
            <button
              type="button"
              aria-expanded={drawer}
              aria-controls="ops-drawer"
              aria-label={drawer ? t('nav.closeMenu') : t('nav.openMenu')}
              onClick={() => setOpenOn(drawer ? null : location.pathname)}
              className="inline-flex size-9 cursor-pointer items-center justify-center rounded-sheet border border-console-rule"
            >
              {drawer ? <X size={17} strokeWidth={1.5} aria-hidden /> : <Menu size={17} strokeWidth={1.5} aria-hidden />}
            </button>
          </div>
        </div>
        {drawer && (
          <div id="ops-drawer" className="max-h-[calc(100dvh-3.5rem)] overflow-y-auto border-t border-console-rule px-3 pb-4">
            <SidebarBody />
          </div>
        )}
      </header>

      {/* ≥ 1024 px: fixed sidebar */}
      <div className="no-print hidden border-r border-console-rule bg-console lg:block">
        <aside className="sticky top-0 flex h-dvh flex-col overflow-y-auto px-3 py-5 text-on-console">
          <div className="px-3 pb-6">
            <Wordmark tone="console" sub={t('ops.portal')} />
          </div>
          <SidebarBody />
        </aside>
      </div>

      <main id="main" className="min-w-0 px-4 pt-7 pb-16 sm:px-8 lg:px-10 lg:pt-9">
        <div className="mx-auto max-w-[1280px]">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

function SidebarBody() {
  const { t } = useTranslation()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const stats = useFraudStats()
  const open = stats.data ? stats.data.open + stats.data.underReview : null
  const [busy, setBusy] = useState(false)

  return (
    <div className="flex flex-1 flex-col">
      <nav aria-label={t('ops.nav.label')}>
        <ul className="space-y-0.5">
          {NAV.map(({ to, key, icon: Icon, badge }) => (
            <li key={to}>
              <NavLink
                to={to}
                className={({ isActive }) =>
                  `flex min-h-10 items-center gap-3 rounded-sheet px-3 transition-colors duration-150 ${
                    isActive ? 'bg-console-2 text-on-console shadow-[inset_2px_0_0_var(--vault)]' : 'text-on-console-2 hover:text-on-console'
                  }`
                }
              >
                <Icon size={16} strokeWidth={1.5} aria-hidden />
                <span className="flex-1">{t(key)}</span>
                {badge && open !== null && open > 0 && (
                  <span className="figures text-[12px] text-brass" aria-label={t('ops.nav.openAlerts', { count: open })}>
                    {open}
                  </span>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <div className="mt-auto space-y-3 border-t border-console-rule px-3 pt-4">
        {user && (
          <div className="min-w-0">
            <p className="truncate text-[13px] font-medium">{user.fullName}</p>
            <p className="figures truncate text-[11px] text-on-console-2">
              {user.username} · {user.roles.map((r) => t(`roles.${r}`)).join(', ')}
            </p>
          </div>
        )}
        <div className="flex flex-wrap items-center gap-2">
          <LanguageSwitch tone="console" />
          <ThemeSwitch tone="console" />
          <span className="hidden lg:inline-flex">
            <PreviewBadge />
          </span>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={async () => {
            setBusy(true)
            await logout()
            navigate('/login', { replace: true })
          }}
          className="inline-flex min-h-9 cursor-pointer items-center gap-2 text-[13px] text-on-console-2 hover:text-on-console disabled:opacity-60"
        >
          <LogOut size={15} strokeWidth={1.5} aria-hidden />
          {busy ? t('nav.signingOut') : t('nav.signOut')}
        </button>
        <ResetPreviewButton tone="console" />
      </div>
    </div>
  )
}
