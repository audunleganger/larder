import { describe, expect, it } from 'vitest'
import { defaultPluralSuffix, draftTranslations, toTranslations, unitLabel } from './names'

describe('unitLabel', () => {
  it('adds the plural ending unless the quantity is exactly 1', () => {
    expect(unitLabel('slice', 's', 1)).toBe('slice')
    expect(unitLabel('slice', 's', 2)).toBe('slices')
    expect(unitLabel('slice', 's', 0.5)).toBe('slices')
    expect(unitLabel('g', '', 250)).toBe('g')
    expect(unitLabel('skive', 'r', null)).toBe('skive')
  })
})

describe('defaultPluralSuffix', () => {
  it('depends on kind and language', () => {
    expect(defaultPluralSuffix('slice', 'custom', 'en')).toBe('s')
    expect(defaultPluralSuffix('skive', 'custom', 'nb')).toBe('r')
    expect(defaultPluralSuffix('bit', 'custom', 'nb')).toBe('er')
    expect(defaultPluralSuffix('oz', 'mass', 'en')).toBe('')
  })
})

describe('translations', () => {
  it('round-trips, dropping blank names and defaulting untouched endings', () => {
    const draft = draftTranslations([{ locale: 'nb', name: 'skive', pluralSuffix: 'r' }])
    expect(draft.nb).toEqual({ name: 'skive', pluralSuffix: 'r', suffixEdited: true })
    expect(draft.en.name).toBe('')
    expect(toTranslations(draft, 'custom')).toEqual([{ locale: 'nb', name: 'skive', pluralSuffix: 'r' }])
    const fresh = draftTranslations([])
    fresh.nb.name = 'bit'
    expect(toTranslations(fresh, 'custom')).toEqual([{ locale: 'nb', name: 'bit', pluralSuffix: 'er' }])
    expect(toTranslations(fresh)).toEqual([{ locale: 'nb', name: 'bit', pluralSuffix: '' }])
  })
})
