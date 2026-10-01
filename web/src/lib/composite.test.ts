import { describe, expect, it } from 'vitest'
import type { FoodDto, UnitDto } from '../api/types.gen'
import { compositeDraft, toCompositeInput } from './composite'

const serving = { id: 7, name: 'serving', kind: 'custom', archived: false, translations: [{ locale: 'nb', name: 'porsjon', pluralSuffix: 'er' }] } as unknown as UnitDto
const plain = { composite: null } as unknown as FoodDto

describe('composite drafts', () => {
  it('start as "makes 1 serving" when there is a serving unit', () => {
    expect(compositeDraft(plain, null, [serving])).toMatchObject({ rows: [], yieldMode: 'set', yieldAmount: '1', yieldUnitId: 7 })
    expect(compositeDraft(plain, null, [])).toMatchObject({ yieldMode: 'auto' })
  })

  it('convert to input, or say what is missing', () => {
    const draft = { rows: [{ key: 1, food: { id: 3, name: 'Bread' }, quantity: '2,5', unitId: 4 as const }], yieldMode: 'auto' as const, yieldAmount: '', yieldUnitId: '' as const, logAsWhole: false }
    expect(toCompositeInput(draft)).toEqual({ ingredients: [{ foodId: 3, unitId: 4, quantity: 2.5 }], yieldAmount: null, yieldUnitId: null, logAsWhole: false })
    expect(toCompositeInput({ ...draft, yieldMode: 'set' })).toEqual({ error: 'composite.errors.yield' })
    expect(toCompositeInput({ ...draft, rows: [{ ...draft.rows[0], unitId: '' }] })).toEqual({ error: 'composite.errors.ingredient' })
  })
})
