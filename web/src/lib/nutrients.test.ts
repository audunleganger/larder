import { describe, expect, it } from 'vitest'
import { flattenGroups, groupNutrients, moved } from './nutrients'

const n = (id: number, parentId: number | null = null) => ({ id, parentId })

describe('groupNutrients', () => {
  it('puts sub-nutrients under their main nutrient', () => {
    const groups = groupNutrients([n(1), n(2), n(3, 2), n(4), n(5, 4), n(6, 4)])
    expect(groups.map((g) => [g.main.id, g.subs.map((s) => s.id)])).toEqual([
      [1, []],
      [2, [3]],
      [4, [5, 6]],
    ])
  })

  it('treats a sub-nutrient of a missing parent as its own group', () => {
    expect(groupNutrients([n(1), n(3, 2)]).map((g) => g.main.id)).toEqual([1, 3])
  })

  it('flattens back to display order', () => {
    const list = [n(1), n(2), n(3, 2), n(4)]
    expect(flattenGroups(groupNutrients(list))).toEqual(list)
  })
})

describe('moved', () => {
  it('swaps with the neighbour', () => {
    expect(moved([1, 2, 3], 2, -1)).toEqual([1, 3, 2])
  })

  it('refuses to move past the ends', () => {
    expect(moved([1, 2], 0, -1)).toBeNull()
    expect(moved([1, 2], 1, 1)).toBeNull()
  })
})
