import { useTranslation } from 'react-i18next'
import type { UnitKind } from '../api/types.gen'
import { LANGUAGES } from '../i18n'
import { effectiveSuffix, unitLabel, type TranslationDraft } from '../lib/names'
import { Field } from './ui'

/** Plural ending input with a live example ("2 slices"). */
export function PluralSuffixField({ name, value, onChange, disabled }: { name: string; value: string; onChange: (value: string) => void; disabled?: boolean }) {
  const { t } = useTranslation()
  return (
    <Field label={t('names.pluralSuffix')} hint={name.trim() ? t('names.pluralExample', { example: unitLabel(name.trim(), value, 2) }) : undefined} className="suffix">
      <input className="input" value={value} disabled={disabled} maxLength={20} onChange={(e) => onChange(e.target.value)} />
    </Field>
  )
}

/**
 * Optional names per language for a food, unit or nutrient (L-5); for units also their plural
 * endings (U-8). Collapsed unless the item already has a translation.
 */
export function TranslationFields({ draft, onChange, mainName, kind }: { draft: TranslationDraft; onChange: (draft: TranslationDraft) => void; mainName: string; kind?: UnitKind }) {
  const { t } = useTranslation()
  const hasAny = LANGUAGES.some((language) => draft[language].name.trim() !== '')
  return (
    <details className="translations" open={hasAny}>
      <summary>{t('names.title')}</summary>
      <p className="field-hint">{t('names.hint')}</p>
      {LANGUAGES.map((language) => {
        const row = draft[language]
        const set = (patch: Partial<TranslationDraft[typeof language]>) => onChange({ ...draft, [language]: { ...row, ...patch } })
        return (
          <div key={language} className="form-row">
            <Field label={t(`names.in.${language}`)} className="grow">
              <input className="input" lang={language} value={row.name} placeholder={mainName} onChange={(e) => set({ name: e.target.value })} />
            </Field>
            {kind && (
              <PluralSuffixField
                name={row.name}
                value={effectiveSuffix(row.name, row.pluralSuffix, row.suffixEdited, kind, language)}
                disabled={!row.name.trim()}
                onChange={(pluralSuffix) => set({ pluralSuffix, suffixEdited: true })}
              />
            )}
          </div>
        )
      })}
    </details>
  )
}
