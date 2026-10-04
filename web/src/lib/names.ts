import type { NameTranslation, UnitKind } from '../api/types.gen'
import { LANGUAGES, type Language } from '../i18n'

/**
 * A unit's label for a quantity (U-8): its plural form unless the quantity is exactly 1 ("1 slice",
 * "2 slices", "0.5 slices"); an empty plural is the same as the name. The only place plurals are
 * chosen, so the rule can change without touching the callers. Mirrors unitLabel in the shared Kotlin module.
 */
export function unitLabel(name: string, plural: string, quantity: number | null | undefined): string {
  return quantity === null || quantity === undefined || quantity === 1 || plural === '' ? name : plural
}

/**
 * The plural form a new unit name starts with; mirrors defaultPlural in the shared Kotlin module.
 * Standard-unit abbreviations get none (the same as the name), Norwegian names "er" ("r" after a
 * final e), English names "s", "es" after s, x, z, ch and sh, and "ies" for a consonant before a final y.
 */
export function defaultPlural(name: string, kind: UnitKind, language: string): string {
  const clean = name.trim()
  if (kind !== 'custom' || clean === '') return ''
  const lower = clean.toLowerCase()
  if (language === 'nb') return clean + (lower.endsWith('e') ? 'r' : 'er')
  if (lower.length > 1 && lower.endsWith('y') && !'aeiou'.includes(lower[lower.length - 2])) return clean.slice(0, -1) + 'ies'
  if (['s', 'x', 'z', 'ch', 'sh'].some((end) => lower.endsWith(end))) return clean + 'es'
  return clean + 's'
}

/** The plural to show for a name: what the user typed, or the default until they do (U-8). */
export function effectivePlural(name: string, plural: string, edited: boolean, kind: UnitKind, language: string): string {
  return edited ? plural : defaultPlural(name, kind, language)
}

/** One editable name per supported language; empty means "use the main name" (L-5). */
export type TranslationDraft = Record<Language, { name: string; plural: string; pluralEdited: boolean }>

export function draftTranslations(translations: NameTranslation[] | undefined): TranslationDraft {
  return Object.fromEntries(
    LANGUAGES.map((language) => {
      const existing = translations?.find((t) => t.locale === language)
      return [language, { name: existing?.name ?? '', plural: existing?.plural ?? '', pluralEdited: existing !== undefined }]
    }),
  ) as TranslationDraft
}

/** The translations to send; blank names are dropped. Plurals are only kept for units. */
export function toTranslations(draft: TranslationDraft, kind?: UnitKind): NameTranslation[] {
  return LANGUAGES.flatMap((language) => {
    const { name, plural, pluralEdited } = draft[language]
    if (!name.trim()) return []
    const value = kind === undefined ? '' : effectivePlural(name, plural, pluralEdited, kind, language).trim()
    return [{ locale: language, name: name.trim(), plural: value }]
  })
}
