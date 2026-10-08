import { RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { clearSession } from '../auth/session'
import { Button } from './Button'
import { Dialog } from './Dialog'

export const IS_MOCK = import.meta.env.VITE_API_MODE === 'mock'

/** "Reset preview data": only rendered in mock mode; reseeds the in-browser store and signs out. */
export function ResetPreviewButton({ tone = 'light' }: { tone?: 'light' | 'console' }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  if (!IS_MOCK) return null

  async function reset() {
    setBusy(true)
    if (import.meta.env.VITE_API_MODE === 'mock') {
      const { resetMockData } = await import('../mocks/browser')
      resetMockData()
    }
    clearSession()
    window.location.assign(`${import.meta.env.BASE_URL}login`)
  }

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={`inline-flex min-h-9 cursor-pointer items-center gap-1.5 text-[12px] ${tone === 'console' ? 'text-on-console-2 hover:text-on-console' : 'text-ink-2 hover:text-ink'}`}
      >
        <RotateCcw size={13} strokeWidth={1.5} aria-hidden />
        {t('preview.reset')}
      </button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        busy={busy}
        size="sm"
        title={t('preview.resetTitle')}
        description={t('preview.resetBody')}
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={busy}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" onClick={reset} loading={busy}>
              {t('preview.resetConfirm')}
            </Button>
          </>
        }
      />
    </>
  )
}
