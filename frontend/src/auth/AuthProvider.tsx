import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { refreshSession } from '../api/client'
import { authApi } from '../api/endpoints'
import type { UserResponse } from '../types/api'
import { AuthContext, type AuthState, type Status } from './context'
import { clearSession, getRefreshToken, onSessionChange, storeTokens, updateUser } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<UserResponse | null>(null)
  const [status, setStatus] = useState<Status>(() => (getRefreshToken() ? 'loading' : 'anonymous'))

  // Reload bootstrap: the access token only lived in memory, so restore the session via the refresh token.
  useEffect(() => {
    if (!getRefreshToken()) return
    let cancelled = false
    refreshSession()
      .then(() => authApi.me())
      .then((me) => {
        if (cancelled) return
        updateUser(me)
        setUser(me)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        clearSession()
        setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [])

  // A failed refresh anywhere in the app ends the session; guards then redirect to /login.
  useEffect(
    () =>
      onSessionChange((u) => {
        if (u) {
          setUser(u)
          setStatus('authenticated')
        } else {
          setUser(null)
          setStatus('anonymous')
          queryClient.clear()
        }
      }),
    [queryClient],
  )

  const login = useCallback(async (username: string, password: string) => {
    const res = await authApi.login({ username, password })
    storeTokens(res)
    return res.user
  }, [])

  const logout = useCallback(async () => {
    const rt = getRefreshToken()
    try {
      if (rt) await authApi.logout(rt)
    } catch {
      // Server-side revoke failed (offline/expired): still clear local state.
    } finally {
      clearSession()
    }
  }, [])

  const value = useMemo<AuthState>(() => ({ status, user, login, logout }), [status, user, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
