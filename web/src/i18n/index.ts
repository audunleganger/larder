import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import nb from './nb.json'

const supported = ['en', 'nb'] as const

// Norwegian variants (no, nn, nb) fall back to bokmål; everything else to English.
function detectLanguage(): string {
  const lang = navigator.language.toLowerCase()
  if (lang.startsWith('nb') || lang.startsWith('no') || lang.startsWith('nn')) return 'nb'
  return 'en'
}

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, nb: { translation: nb } },
  lng: detectLanguage(),
  fallbackLng: 'en',
  supportedLngs: supported,
  interpolation: { escapeValue: false },
})

export default i18n
