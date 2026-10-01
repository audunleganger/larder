import type { NameTranslation, UnitKind } from '../api/types.gen'
import { LANGUAGES, type Language } from '../i18n'

/**
 * A unit's label for a quantity (U-8): the plural ending is added unless the quantity is exactly 1
 * ("1 slice", "2 slices", "0.5 slices"). The only place plurals are formed, so the rule can change
 * (e.g. to full plural forms) without touching the callers.
 */
export function unitLabel(name: string, pluralSuffix: string, quantity: number | null | undefined): string {
  return quantity === null || quantity === undefined || quantity === 1 ? name : name + pluralSuffix
}

/**
 * The plural ending a new unit name starts with; mirrors defaultPluralSuffix in the shared Kotlin
 * module. Standard-unit abbreviations get none, Norwegian names "er" ("r" after a final e), others "s".
 */
export function defaultPluralSuffix(name: string, kind: UnitKind, language: string): string {
  if (kind !== 'custom') return ''
  if (language === 'nb') return name.trim().toLowerCase().endsWith('e') ? 'r' : 'er'
  return 's'
}

/** The plural ending to show for a name: what the user typed, or the default until they do (U-8). */
export function effectiveSuffix(name: string, suffix: string, edited: boolean, kind: UnitKind, language: string): string {
  return edited ? suffix : defaultPluralSuffix(name, kind, language)
}

/** One editable name per supported language; empty means "use the main name" (L-5). */
export type TranslationDraft = Record<Language, { name: string; pluralSuffix: string; suffixEdited: boolean }>

export function draftTranslations(translations: NameTranslation[] | undefined): TranslationDraft {
  return Object.fromEntries(
    LANGUAGES.map((language) => {
      const existing = translations?.find((t) => t.locale === language)
      return [language, { name: existing?.name ?? '', pluralSuffix: existing?.pluralSuffix ?? '', suffixEdited: existing !== undefined }]
    }),
  ) as TranslationDraft
}

/** The translations to send; blank names are dropped. Plural endings are only kept for units. */
export function toTranslations(draft: TranslationDraft, kind?: UnitKind): NameTranslation[] {
  return LANGUAGES.flatMap((language) => {
    const { name, pluralSuffix, suffixEdited } = draft[language]
    if (!name.trim()) return []
    const suffix = kind === undefined ? '' : effectiveSuffix(name, pluralSuffix, suffixEdited, kind, language).trim()
    return [{ locale: language, name: name.trim(), pluralSuffix: suffix }]
  })
}
