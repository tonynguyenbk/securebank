export type ThemePreference = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'sb.theme'

export function readThemePreference(): ThemePreference {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    if (v === 'light' || v === 'dark') return v
  } catch {
    // storage unavailable
  }
  return 'system'
}

/** "system" removes the attribute so the prefers-color-scheme media query decides. */
export function applyThemePreference(pref: ThemePreference) {
  const root = document.documentElement
  if (pref === 'system') root.removeAttribute('data-theme')
  else root.setAttribute('data-theme', pref)
  try {
    if (pref === 'system') localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, pref)
  } catch {
    // ignore
  }
}
