import { describe, expect, it } from 'vitest'
import type { TagDto } from '../api/types.gen'
import { tagColorClass, tagsByName } from './tags'

const tag = (id: number, displayName: string): TagDto => ({
  id,
  name: displayName,
  translations: [],
  displayName,
  archived: false,
  foodCount: 1,
  createdAt: 0,
  createdBy: 'me',
  updatedAt: null,
  color: null,
})

describe('tagsByName', () => {
  it('sorts by display name in the language, ignoring case, and skips unknown ids', () => {
    const tags = [tag(1, 'ost'), tag(2, 'Ærfugl'), tag(3, 'Brød'), tag(4, 'øl')]
    expect(tagsByName([1, 2, 3, 4, 9], tags, 'nb-NO').map((t) => t.displayName)).toEqual(['Brød', 'ost', 'Ærfugl', 'øl'])
  })
})

describe('tagColorClass', () => {
  it('knows the palette, and gives gray otherwise', () => {
    expect(tagColorClass('teal')).toBe('tag-color-teal')
    expect(tagColorClass(null)).toBe('')
    expect(tagColorClass('chartreuse')).toBe('')
  })
})
