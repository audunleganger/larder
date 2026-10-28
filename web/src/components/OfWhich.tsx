import { useTranslation } from 'react-i18next'

/** "of which", before the name of a sub-nutrient (N-6). */
export function OfWhich() {
  const { t } = useTranslation()
  return <span className="muted of-which">{t('nutrients.ofWhich')} </span>
}
