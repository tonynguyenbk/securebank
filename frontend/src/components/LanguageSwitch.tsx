import { useTranslation } from 'react-i18next'
import { LANGUAGES } from '../i18n'

export function LanguageSwitch({ tone = 'light' }: { tone?: 'light' | 'console' }) {
  const { t, i18n } = useTranslation()
  const idle = tone === 'console' ? 'text-on-console-2 hover:text-on-console' : 'text-ink-2 hover:text-ink'
  const active = tone === 'console' ? 'bg-on-console text-console' : 'bg-ink text-paper'
  return (
    <div role="group" aria-label={t('lang.label')} className={`inline-flex h-9 items-center rounded-sheet border p-0.5 text-xs ${tone === 'console' ? 'border-console-rule' : 'border-rule'}`}>
      {LANGUAGES.map((lng) => {
        const isActive = i18n.resolvedLanguage === lng
        return (
          <button
            key={lng}
            type="button"
            aria-pressed={isActive}
            onClick={() => i18n.changeLanguage(lng)}
            className={`figures min-h-7 cursor-pointer rounded-[4px] px-2.5 font-medium transition-colors duration-150 ${isActive ? active : idle}`}
          >
            {t(`lang.${lng}`)}
          </button>
        )
      })}
    </div>
  )
}
