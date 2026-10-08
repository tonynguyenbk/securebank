import type { UserResponse } from '../types/api'
import { useAuth } from './useAuth'

/** UI-only mirror of the server's @PreAuthorize rules: hides controls, never replaces server checks. */
export function permissionsFor(user: UserResponse | null) {
  const roles = user?.roles ?? []
  const admin = roles.includes('ADMIN')
  const staff = roles.includes('BANK_STAFF')
  const auditor = roles.includes('AUDITOR')
  return {
    /** Freeze/unfreeze, limits, fraud review. AUDITOR never mutates. */
    canMutate: admin || staff,
    canReconcile: admin || auditor,
    seesIpAddress: admin || auditor,
    isAuditorOnly: auditor && !admin && !staff,
  }
}

export function usePermissions() {
  return permissionsFor(useAuth().user)
}
