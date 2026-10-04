import type { FoodSummary } from '../api/types.gen'

/** Filters for the food list (F-17); they combine with each other and with the search. */
export interface FoodFilter {
  tagId: number | null
  composite: boolean
  ingredientOnly: boolean
  nutrientId: number | null
  /** With [nutrientId]: foods that have a value for it, or that lack one. */
  hasValue: boolean
}

export const NO_FILTER: FoodFilter = { tagId: null, composite: false, ingredientOnly: false, nutrientId: null, hasValue: true }

function id(value: string | null): number | null {
  const n = Number(value)
  return value && Number.isInteger(n) && n > 0 ? n : null
}

/** Reads the filter from the page address, so it survives going to a food and back. */
export function parseFilter(params: URLSearchParams): FoodFilter {
  return {
    tagId: id(params.get('tag')),
    composite: params.get('composite') === '1',
    ingredientOnly: params.get('ingredientOnly') === '1',
    nutrientId: id(params.get('nutrient')),
    hasValue: params.get('value') !== 'lacks',
  }
}

/** The filter as address parameters; filters that are off are left out. */
export function filterParams(filter: FoodFilter): Record<string, string> {
  const params: Record<string, string> = {}
  if (filter.tagId !== null) params.tag = String(filter.tagId)
  if (filter.composite) params.composite = '1'
  if (filter.ingredientOnly) params.ingredientOnly = '1'
  if (filter.nutrientId !== null) {
    params.nutrient = String(filter.nutrientId)
    if (!filter.hasValue) params.value = 'lacks'
  }
  return params
}

export function isFiltered(filter: FoodFilter): boolean {
  return filter.tagId !== null || filter.composite || filter.ingredientOnly || filter.nutrientId !== null
}

export function matchesFilter(food: FoodSummary, filter: FoodFilter): boolean {
  if (filter.tagId !== null && !food.tagIds.includes(filter.tagId)) return false
  if (filter.composite && !food.composite) return false
  if (filter.ingredientOnly && !food.ingredientOnly) return false
  if (filter.nutrientId !== null && (food.nutrients[filter.nutrientId] !== undefined) !== filter.hasValue) return false
  return true
}
