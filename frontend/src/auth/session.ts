import type { TokenResponse, UserResponse } from '../types/api'

/*
 * Token storage (spec §24).
 * - The access token (15 min JWT) lives only in this module's memory: it is never written to
 *   any Web Storage, so an XSS payload can't simply read it out of localStorage.
 * - The refresh token is kept in sessionStorage so a page reload can restore the session through
 *   POST /auth/refresh. Trade-off: sessionStorage is still readable by script running on the page,
 *   but it is scoped to the tab and cleared when the tab closes, and refresh tokens are rotated on
 *   every use (reuse of a revoked one revokes the whole family server-side). The stronger option, an
 *   HttpOnly SameSite cookie set by the gateway, needs backend/gateway changes outside v1 of the
 *   contract — noted as a production follow-up.
 */

const REFRESH_KEY = 'sb.rt'

type Session = { accessToken: string; expiresAt: number; user: UserResponse }

let current: Session | null = null
const listeners = new Set<(user: UserResponse | null) => void>()

export function getAccessToken(): string | null {
  return current?.accessToken ?? null
}

export function getSessionUser(): UserResponse | null {
  return current?.user ?? null
}

export function getAccessExpiry(): number | null {
  return current?.expiresAt ?? null
}

export function getRefreshToken(): string | null {
  try {
    return sessionStorage.getItem(REFRESH_KEY)
  } catch {
    return null
  }
}

export function storeTokens(res: TokenResponse) {
  current = { accessToken: res.accessToken, expiresAt: Date.now() + res.expiresIn * 1000, user: res.user }
  try {
    sessionStorage.setItem(REFRESH_KEY, res.refreshToken)
  } catch {
    // storage blocked: session survives until reload only
  }
  listeners.forEach((l) => l(res.user))
}

export function updateUser(user: UserResponse) {
  if (current) current = { ...current, user }
  listeners.forEach((l) => l(user))
}

export function clearSession() {
  current = null
  try {
    sessionStorage.removeItem(REFRESH_KEY)
  } catch {
    // ignore
  }
  listeners.forEach((l) => l(null))
}

export function onSessionChange(listener: (user: UserResponse | null) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
