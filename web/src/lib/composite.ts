import type { CompositeDetail, CompositeInput, FoodDto, UnitDto } from '../api/types.gen'
import type { PickedFood } from '../components/FoodPicker'
import { parseDecimal, toInputValue } from './format'

export interface IngredientRow {
  key: number
  food: PickedFood | null
  quantity: string
  unitId: number | ''
}

/** The composite food editor's state (F-10). */
export interface CompositeDraft {
  rows: IngredientRow[]
  /** 'auto': the ingredients' total weight. */
  yieldMode: 'auto' | 'set'
  yieldAmount: string
  yieldUnitId: number | ''
  logAsWhole: boolean
}

let key = 0
export const nextRowKey = () => key++

export function compositeDraft(food: FoodDto, saved: CompositeDetail | null, units: UnitDto[]): CompositeDraft {
  const composite = food.composite
  if (!composite) {
    // A food that becomes composite starts as "makes 1 serving", if there's such a unit.
    const serving = units.find((u) => !u.archived && u.kind === 'custom' && [u.name, ...u.translations.map((t) => t.name)].some((n) => ['serving', 'porsjon'].includes(n.toLowerCase())))
    return { rows: [], yieldMode: serving ? 'set' : 'auto', yieldAmount: serving ? '1' : '', yieldUnitId: serving?.id ?? '', logAsWhole: false }
  }
  return {
    rows: composite.ingredients.map((ingredient, index) => ({
      key: nextRowKey(),
      food: { id: ingredient.foodId, name: saved?.ingredients[index]?.foodName ?? '' },
      quantity: toInputValue(ingredient.quantity),
      unitId: ingredient.unitId,
    })),
    yieldMode: composite.yieldAmount == null ? 'auto' : 'set',
    yieldAmount: toInputValue(composite.yieldAmount),
    yieldUnitId: composite.yieldUnitId ?? '',
    logAsWhole: composite.logAsWhole ?? false,
  }
}

/** The input to save, or the i18n key of what's wrong. */
export function toCompositeInput(draft: CompositeDraft): CompositeInput | { error: 'composite.errors.ingredient' | 'composite.errors.yield' } {
  const ingredients = []
  for (const row of draft.rows) {
    const quantity = parseDecimal(row.quantity)
    if (!row.food || row.unitId === '' || quantity === null || Number.isNaN(quantity) || quantity <= 0) return { error: 'composite.errors.ingredient' }
    ingredients.push({ foodId: row.food.id, unitId: row.unitId, quantity })
  }
  if (draft.yieldMode === 'auto') return { ingredients, yieldAmount: null, yieldUnitId: null, logAsWhole: draft.logAsWhole }
  const amount = parseDecimal(draft.yieldAmount)
  if (amount === null || Number.isNaN(amount) || amount <= 0 || draft.yieldUnitId === '') return { error: 'composite.errors.yield' }
  return { ingredients, yieldAmount: amount, yieldUnitId: draft.yieldUnitId, logAsWhole: draft.logAsWhole }
}
