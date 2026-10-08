import { useTranslation } from 'react-i18next'

/** Shown on builds without a real backend (GitHub Pages preview) so nobody mistakes the data for real. */
export function PreviewBadge() {
  const { t } = useTranslation()
  if (import.meta.env.VITE_API_MODE !== 'mock') return null
  return (
    <span
      title={t('preview.note')}
      className="figures inline-flex shrink-0 items-center rounded-sheet border border-brass/50 px-2 py-1 text-[11px] leading-none whitespace-nowrap text-brass"
    >
      <span className="lg:hidden">{t('preview.badgeShort')}</span>
      <span className="hidden lg:inline">{t('preview.badge')}</span>
    </span>
  )
}
