import { describe, expect, it } from 'vitest'
import { defaultPlural, draftTranslations, toTranslations, unitLabel } from './names'

describe('unitLabel', () => {
  it('uses the plural form unless the quantity is exactly 1', () => {
    expect(unitLabel('slice', 'slices', 1)).toBe('slice')
    expect(unitLabel('slice', 'slices', 2)).toBe('slices')
    expect(unitLabel('slice', 'slices', 0.5)).toBe('slices')
    expect(unitLabel('goose', 'geese', 3)).toBe('geese')
    expect(unitLabel('skive', 'skiver', null)).toBe('skive')
  })

  it('uses the name when there is no plural', () => {
    expect(unitLabel('g', '', 250)).toBe('g')
  })
})

describe('defaultPlural', () => {
  it('depends on kind and language', () => {
    expect(defaultPlural('slice', 'custom', 'en')).toBe('slices')
    expect(defaultPlural('pinch', 'custom', 'en')).toBe('pinches')
    expect(defaultPlural('glass', 'custom', 'en')).toBe('glasses')
    expect(defaultPlural('berry', 'custom', 'en')).toBe('berries')
    expect(defaultPlural('tray', 'custom', 'en')).toBe('trays')
    expect(defaultPlural('skive', 'custom', 'nb')).toBe('skiver')
    expect(defaultPlural('bit', 'custom', 'nb')).toBe('biter')
    expect(defaultPlural('oz', 'mass', 'en')).toBe('')
    expect(defaultPlural('  ', 'custom', 'en')).toBe('')
  })
})

describe('translations', () => {
  it('round-trips, dropping blank names and defaulting untouched plurals', () => {
    const draft = draftTranslations([{ locale: 'nb', name: 'gås', plural: 'gjess' }])
    expect(draft.nb).toEqual({ name: 'gås', plural: 'gjess', pluralEdited: true })
    expect(draft.en.name).toBe('')
    expect(toTranslations(draft, 'custom')).toEqual([{ locale: 'nb', name: 'gås', plural: 'gjess' }])
    const fresh = draftTranslations([])
    fresh.nb.name = 'bit'
    expect(toTranslations(fresh, 'custom')).toEqual([{ locale: 'nb', name: 'bit', plural: 'biter' }])
    expect(toTranslations(fresh)).toEqual([{ locale: 'nb', name: 'bit', plural: '' }])
  })
})
