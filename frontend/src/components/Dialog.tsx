import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

type Props = {
  open: boolean
  onClose: () => void
  title: ReactNode
  description?: ReactNode
  children?: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** Prevents closing while a request is in flight. */
  busy?: boolean
}

const WIDTH = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-2xl' }

/**
 * Modal built on the native <dialog>: showModal() makes the rest of the page inert (focus stays inside),
 * Esc fires "cancel", and focus returns to the opener on close.
 */
export function Dialog({ open, onClose, title, description, children, footer, size = 'md', busy = false }: Props) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDialogElement>(null)
  const opener = useRef<Element | null>(null)
  const titleId = useId()
  const descId = useId()

  useEffect(() => {
    const el = ref.current
    if (!el) return
    if (open && !el.open) {
      opener.current = document.activeElement
      el.showModal()
    } else if (!open && el.open) {
      el.close()
      if (opener.current instanceof HTMLElement) opener.current.focus()
    }
  }, [open])

  // Unmounting while open (parent drops the dialog) must also hand focus back to the opener.
  useEffect(() => {
    const el = ref.current
    const openerRef = opener
    return () => {
      if (el?.open) {
        el.close()
        const target = openerRef.current
        if (target instanceof HTMLElement) requestAnimationFrame(() => target.focus())
      }
    }
  }, [])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      aria-describedby={description ? descId : undefined}
      onCancel={(e) => {
        e.preventDefault()
        if (!busy) onClose()
      }}
      onClick={(e) => {
        if (e.target === ref.current && !busy) onClose()
      }}
      className={`sb-dialog m-auto w-[calc(100%-2rem)] ${WIDTH[size]} rounded-sheet border border-rule bg-sheet p-0 text-ink`}
    >
      <div className="flex items-start justify-between gap-4 border-b border-rule px-5 py-4 sm:px-6">
        <div className="min-w-0">
          <h2 id={titleId} className="font-display text-lg leading-snug font-semibold">
            {title}
          </h2>
          {description && (
            <p id={descId} className="mt-1 text-[13px] text-ink-2">
              {description}
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={onClose}
          disabled={busy}
          aria-label={t('common.close')}
          className="-mr-2 inline-flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-sheet text-ink-2 hover:text-ink disabled:opacity-50"
        >
          <X size={18} strokeWidth={1.5} aria-hidden />
        </button>
      </div>
      {children && <div className="max-h-[70dvh] overflow-y-auto px-5 py-5 sm:px-6">{children}</div>}
      {footer && <div className="flex flex-col-reverse gap-2 border-t border-rule px-5 py-4 sm:flex-row sm:justify-end sm:px-6">{footer}</div>}
    </dialog>
  )
}
