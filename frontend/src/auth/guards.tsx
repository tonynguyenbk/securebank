import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import type { Role } from '../types/api'
import { hasRole, homeFor } from './context'
import { useAuth } from './useAuth'

export function FullPageLoader() {
  const { t } = useTranslation()
  return (
    <div role="status" className="flex min-h-dvh items-center justify-center text-ink-2">
      <span className="figures text-[12px] tracking-[0.2em] uppercase">{t('common.restoringSession')}</span>
    </div>
  )
}

/** Signed-in users with one of `roles`; anonymous → /login (keeping the intended path), wrong portal → /403. */
export function RequireRole({ roles }: { roles: Role[] }) {
  const { status, user } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullPageLoader />
  if (status === 'anonymous' || !user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (!hasRole(user, roles)) return <Navigate to="/403" replace />
  return <Outlet />
}

/** Login/register: already signed in → own home. */
export function PublicOnly() {
  const { status, user } = useAuth()
  if (status === 'loading') return <FullPageLoader />
  if (status === 'authenticated' && user) return <Navigate to={homeFor(user)} replace />
  return <Outlet />
}

export function HomeRedirect() {
  const { status, user } = useAuth()
  if (status === 'loading') return <FullPageLoader />
  return <Navigate to={user ? homeFor(user) : '/login'} replace />
}
