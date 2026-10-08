import { useTranslation } from 'react-i18next'

/** Shown on builds without a real backend (GitHub Pages preview) so nobody mistakes the data for real. */
export function PreviewBadge() {
  const { t } = useTranslation()
  if (import.meta.env.VITE_API_MODE !== 'mock') return null
  return (
    <span title={t('preview.note')} className="figures hidden rounded-sheet border border-brass/40 px-2 py-1 text-[11px] text-brass sm:inline">
      {t('preview.badge')}
    </span>
  )
}
