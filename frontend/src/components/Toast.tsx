import { X } from 'lucide-react'
import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

type Tone = 'success' | 'error' | 'info'
type Item = { id: number; tone: Tone; text: string }
type ToastApi = { success: (t: string) => void; error: (t: string) => void; info: (t: string) => void }

const ToastContext = createContext<ToastApi | null>(null)

const RULE: Record<Tone, string> = { success: 'border-l-credit', error: 'border-l-debit', info: 'border-l-vault' }

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const [items, setItems] = useState<Item[]>([])
  const seq = useRef(0)

  const dismiss = useCallback((id: number) => setItems((xs) => xs.filter((x) => x.id !== id)), [])
  const push = useCallback(
    (tone: Tone, text: string) => {
      const id = ++seq.current
      setItems((xs) => [...xs.slice(-2), { id, tone, text }])
      window.setTimeout(() => dismiss(id), tone === 'error' ? 7000 : 4500)
    },
    [dismiss],
  )
  const api = useMemo<ToastApi>(
    () => ({ success: (x) => push('success', x), error: (x) => push('error', x), info: (x) => push('info', x) }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" role="status" className="no-print pointer-events-none fixed inset-x-4 bottom-4 z-50 flex flex-col items-end gap-2 sm:inset-x-auto sm:right-6 sm:bottom-6">
        {items.map((i) => (
          <div
            key={i.id}
            className={`animate-toast pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-sheet border border-l-4 border-rule bg-sheet py-3 pr-2 pl-4 text-[14px] shadow-[0_1px_0_var(--rule)] ${RULE[i.tone]}`}
          >
            <p className="flex-1 pt-0.5">{i.text}</p>
            <button
              type="button"
              onClick={() => dismiss(i.id)}
              aria-label={t('common.dismiss')}
              className="inline-flex size-7 shrink-0 cursor-pointer items-center justify-center text-ink-2 hover:text-ink"
            >
              <X size={15} strokeWidth={1.5} aria-hidden />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

// eslint-disable-next-line react/only-export-components
export function useToast(): ToastApi {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast outside ToastProvider')
  return ctx
}
