import { Bell, Menu, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Wordmark } from '../components/Layout'
import { PreviewBadge } from '../components/PreviewBadge'
import { ThemeSwitch } from '../components/ThemeSwitch'
import { useUnreadCount } from '../features/notifications/hooks'
import { UserMenu } from './UserMenu'

const NAV = [
  { to: '/dashboard', key: 'nav.overview' },
  { to: '/accounts', key: 'nav.accounts' },
  { to: '/transfer', key: 'nav.transfer' },
  { to: '/transactions', key: 'nav.activity' },
]

/** Customer portal: top bar + content, max-width 1120 px (DESIGN_SYSTEM §5). */
export function AppShell() {
  const { t } = useTranslation()
  const location = useLocation()
  // Open state is tied to the path it was opened on, so navigating closes it without an effect.
  const [openOn, setOpenOn] = useState<string | null>(null)
  const menuOpen = openOn === location.pathname
  const unread = useUnreadCount()
  const count = unread.data?.count ?? 0


  const link = ({ isActive }: { isActive: boolean }) =>
    `relative inline-flex h-14 items-center px-1 transition-colors duration-150 ${
      isActive ? 'text-ink after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:bg-vault' : 'text-ink-2 hover:text-ink'
    }`

  return (
    <div className="flex min-h-dvh flex-col">
      <a href="#main" className="sr-only z-50 bg-sheet px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2">
        {t('nav.skip')}
      </a>
      <header className="no-print sticky top-0 z-30 border-b border-rule bg-paper/95 backdrop-blur-none">
        <div className="mx-auto flex h-14 max-w-[1120px] items-center gap-6 px-4 sm:px-8">
          <Link to="/dashboard" className="shrink-0" aria-label={t('nav.home')}>
            <Wordmark />
          </Link>
          <nav aria-label={t('nav.main')} className="hidden h-full items-center gap-6 md:flex">
            {NAV.map((n) => (
              <NavLink key={n.to} to={n.to} className={link}>
                {t(n.key)}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <PreviewBadge />
            <Link
              to="/notifications"
              aria-label={count ? t('nav.notificationsUnread', { count }) : t('nav.notifications')}
              className="relative inline-flex size-9 items-center justify-center rounded-sheet border border-rule text-ink-2 hover:text-ink"
            >
              <Bell size={16} strokeWidth={1.5} aria-hidden />
              {count > 0 && (
                <span aria-hidden className="figures absolute -top-1.5 -right-1.5 min-w-[18px] rounded-[4px] bg-debit px-1 text-center text-[10px] leading-[18px] text-on-debit">
                  {count > 9 ? '9+' : count}
                </span>
              )}
            </Link>
            <div className="hidden items-center gap-2 sm:flex">
              <LanguageSwitch />
              <ThemeSwitch />
            </div>
            <UserMenu />
            <button
              type="button"
              className="inline-flex size-9 cursor-pointer items-center justify-center rounded-sheet border border-rule md:hidden"
              aria-expanded={menuOpen}
              aria-controls="mobile-nav"
              aria-label={menuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
              onClick={() => setOpenOn(menuOpen ? null : location.pathname)}
            >
              {menuOpen ? <X size={17} strokeWidth={1.5} aria-hidden /> : <Menu size={17} strokeWidth={1.5} aria-hidden />}
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav id="mobile-nav" aria-label={t('nav.main')} className="border-t border-rule bg-paper px-4 pb-4 md:hidden">
            <ul>
              {NAV.map((n) => (
                <li key={n.to} className="border-b border-rule">
                  <NavLink to={n.to} className={({ isActive }) => `flex min-h-12 items-center ${isActive ? 'font-medium text-vault' : 'text-ink'}`}>
                    {t(n.key)}
                  </NavLink>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex items-center gap-2 sm:hidden">
              <LanguageSwitch />
              <ThemeSwitch />
            </div>
          </nav>
        )}
      </header>
      <main id="main" className="mx-auto w-full max-w-[1120px] flex-1 px-4 pt-7 pb-16 sm:px-8 sm:pt-9">
        <Outlet />
      </main>
    </div>
  )
}
