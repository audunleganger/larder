import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en'
import nb from './nb'

declare module 'i18next' {
  interface CustomTypeOptions {
    resources: { translation: typeof en }
  }
}

export type Language = 'en' | 'nb'
export const LANGUAGES: Language[] = ['en', 'nb']
const STORAGE_KEY = 'cc.lang'

/** Maps any locale string to a supported language; Norwegian variants use bokmål (L-1). */
export function toLanguage(locale: string | null | undefined): Language | null {
  if (!locale) return null
  const lower = locale.toLowerCase()
  if (lower.startsWith('nb') || lower.startsWith('no') || lower.startsWith('nn')) return 'nb'
  if (lower.startsWith('en')) return 'en'
  return null
}

function initialLanguage(): Language {
  try {
    const stored = toLanguage(localStorage.getItem(STORAGE_KEY))
    if (stored) return stored
  } catch {
    // ignore unavailable storage
  }
  return (typeof navigator !== 'undefined' && toLanguage(navigator.language)) || 'en'
}

export function changeLanguage(language: Language) {
  try {
    localStorage.setItem(STORAGE_KEY, language)
  } catch {
    // ignore unavailable storage
  }
  if (typeof document !== 'undefined') document.documentElement.lang = language
  void i18n.changeLanguage(language)
}

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, nb: { translation: nb } },
  lng: initialLanguage(),
  fallbackLng: 'en',
  supportedLngs: LANGUAGES,
  interpolation: { escapeValue: false },
})
if (typeof document !== 'undefined') {
  document.documentElement.lang = i18n.language
  document.title = i18n.t('appName')
}

export default i18n
