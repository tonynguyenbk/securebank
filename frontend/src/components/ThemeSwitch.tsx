import { Monitor, Moon, Sun } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { applyThemePreference, readThemePreference, type ThemePreference } from '../theme/theme'

const ORDER: ThemePreference[] = ['system', 'light', 'dark']
const ICONS = { system: Monitor, light: Sun, dark: Moon }

/** Cycles system → light → dark. The label names the current mode and the next one. */
export function ThemeSwitch({ tone = 'light' }: { tone?: 'light' | 'console' }) {
  const { t } = useTranslation()
  const [pref, setPref] = useState<ThemePreference>(readThemePreference)
  const next = ORDER[(ORDER.indexOf(pref) + 1) % ORDER.length]
  const Icon = ICONS[pref]

  return (
    <button
      type="button"
      onClick={() => {
        applyThemePreference(next)
        setPref(next)
      }}
      aria-label={t('theme.switch', { current: t(`theme.${pref}`), next: t(`theme.${next}`) })}
      title={t(`theme.${pref}`)}
      className={`inline-flex size-9 cursor-pointer items-center justify-center rounded-sheet border transition-colors duration-150 ${tone === 'console' ? 'border-console-rule text-on-console-2 hover:text-on-console' : 'border-rule text-ink-2 hover:text-ink'}`}
    >
      <Icon size={16} strokeWidth={1.5} aria-hidden />
    </button>
  )
}
