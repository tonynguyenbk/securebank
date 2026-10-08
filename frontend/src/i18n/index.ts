import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import vi from './vi.json'

export const LANGUAGES = ['en', 'vi'] as const
export type Language = (typeof LANGUAGES)[number]

const STORAGE_KEY = 'sb.lang'

function initialLanguage(): Language {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'en' || saved === 'vi') return saved
  } catch {
    // storage unavailable (private mode) — fall through to browser language
  }
  return navigator.language.toLowerCase().startsWith('vi') ? 'vi' : 'en'
}

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, vi: { translation: vi } },
  lng: initialLanguage(),
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
  try {
    localStorage.setItem(STORAGE_KEY, lng)
  } catch {
    // ignore
  }
})
document.documentElement.lang = i18n.language

export default i18n
