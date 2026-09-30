import { useTranslation } from 'react-i18next'
import { changeLanguage, LANGUAGES, type Language } from '../i18n'

export function LanguageSwitch({ onChange }: { onChange?: (language: Language) => void }) {
  const { t, i18n } = useTranslation()
  return (
    <div className="segmented" role="group" aria-label={t('settings.language')}>
      {LANGUAGES.map((language) => (
        <button
          key={language}
          type="button"
          className={i18n.language === language ? 'active' : ''}
          aria-pressed={i18n.language === language}
          onClick={() => {
            changeLanguage(language)
            onChange?.(language)
          }}
        >
          {t(`languages.${language}`)}
        </button>
      ))}
    </div>
  )
}
