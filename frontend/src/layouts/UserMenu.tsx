import { ChevronDown, LogOut, UserRound } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ResetPreviewButton } from '../components/PreviewTools'
import { initials } from '../utils/format'

/** Disclosure menu (button + list of links), closes on Esc / outside click. */
export function UserMenu() {
  const { t } = useTranslation()
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const menuId = useId()

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!ref.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!user) return null

  async function signOut() {
    setBusy(true)
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex h-9 cursor-pointer items-center gap-1.5 rounded-sheet border border-rule pr-2 pl-1 hover:border-ink-2/60"
      >
        <span className="figures inline-flex size-7 items-center justify-center rounded-[4px] bg-vault-tint text-[11px] font-medium text-vault">{initials(user.fullName)}</span>
        <span className="sr-only">{t('nav.accountMenu', { name: user.fullName })}</span>
        <ChevronDown size={14} strokeWidth={1.5} aria-hidden className="text-ink-2" />
      </button>
      {open && (
        <div id={menuId} className="absolute right-0 z-40 mt-2 w-64 rounded-sheet border border-rule bg-sheet py-2 shadow-[0_8px_24px_-12px_rgb(19_32_30/0.35)]">
          <div className="border-b border-rule px-4 pt-1 pb-3">
            <p className="truncate font-medium">{user.fullName}</p>
            <p className="figures truncate text-[12px] text-ink-2">{user.username}</p>
          </div>
          <Link to="/profile" onClick={() => setOpen(false)} className="flex min-h-10 items-center gap-2 px-4 hover:bg-vault-tint">
            <UserRound size={15} strokeWidth={1.5} aria-hidden />
            {t('nav.profile')}
          </Link>
          <button type="button" onClick={signOut} disabled={busy} className="flex min-h-10 w-full cursor-pointer items-center gap-2 px-4 text-left hover:bg-vault-tint disabled:opacity-60">
            <LogOut size={15} strokeWidth={1.5} aria-hidden />
            {busy ? t('nav.signingOut') : t('nav.signOut')}
          </button>
          <div className="mt-1 border-t border-rule px-4 pt-1">
            <ResetPreviewButton />
          </div>
        </div>
      )}
    </div>
  )
}
