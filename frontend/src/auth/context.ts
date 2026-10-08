import { createContext } from 'react'
import type { Role, UserResponse } from '../types/api'
import { STAFF_ROLES } from '../types/api'

export type Status = 'loading' | 'authenticated' | 'anonymous'

export type AuthState = {
  status: Status
  user: UserResponse | null
  login: (username: string, password: string) => Promise<UserResponse>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

export function homeFor(user: Pick<UserResponse, 'roles'>): string {
  return user.roles.some((r) => STAFF_ROLES.includes(r)) ? '/ops/dashboard' : '/dashboard'
}

export function hasRole(user: UserResponse | null, roles: Role[]): boolean {
  return !!user && user.roles.some((r) => roles.includes(r))
}

