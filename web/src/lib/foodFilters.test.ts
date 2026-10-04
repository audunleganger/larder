import { describe, expect, it } from 'vitest'
import type { FoodSummary } from '../api/types.gen'
import { filterParams, isFiltered, matchesFilter, NO_FILTER, parseFilter } from './foodFilters'

const food = (patch: Partial<FoodSummary>): FoodSummary => ({
  id: 1,
  name: 'Bread',
  archived: false,
  refAmount: 100,
  refUnitId: 1,
  nutrientCount: 1,
  imageVersion: null,
  composite: false,
  ingredientOnly: false,
  tagIds: [],
  nutrients: {},
  ...patch,
})

describe('matchesFilter', () => {
  it('lets everything through without filters', () => {
    expect(matchesFilter(food({}), NO_FILTER)).toBe(true)
    expect(isFiltered(NO_FILTER)).toBe(false)
  })

  it('combines tag, kind and nutrient filters', () => {
    const soup = food({ composite: true, tagIds: [3], nutrients: { 1: 120, 2: 0 } })
    expect(matchesFilter(soup, { ...NO_FILTER, tagId: 3, composite: true })).toBe(true)
    expect(matchesFilter(soup, { ...NO_FILTER, tagId: 4 })).toBe(false)
    expect(matchesFilter(soup, { ...NO_FILTER, ingredientOnly: true })).toBe(false)
    expect(matchesFilter(food({ ingredientOnly: true }), { ...NO_FILTER, ingredientOnly: true })).toBe(true)
  })

  it('tells a value of 0 apart from no value', () => {
    const soup = food({ nutrients: { 1: 120, 2: 0 } })
    expect(matchesFilter(soup, { ...NO_FILTER, nutrientId: 2 })).toBe(true)
    expect(matchesFilter(soup, { ...NO_FILTER, nutrientId: 3 })).toBe(false)
    expect(matchesFilter(soup, { ...NO_FILTER, nutrientId: 3, hasValue: false })).toBe(true)
    expect(matchesFilter(soup, { ...NO_FILTER, nutrientId: 1, hasValue: false })).toBe(false)
  })
})

describe('filter parameters', () => {
  it('round-trip, leaving out filters that are off', () => {
    const filter = { tagId: 3, composite: true, ingredientOnly: false, nutrientId: 7, hasValue: false }
    const params = filterParams(filter)
    expect(params).toEqual({ tag: '3', composite: '1', nutrient: '7', value: 'lacks' })
    expect(parseFilter(new URLSearchParams(params))).toEqual(filter)
    expect(filterParams(NO_FILTER)).toEqual({})
    expect(parseFilter(new URLSearchParams('tag=abc&nutrient=-1'))).toEqual(NO_FILTER)
  })
})
