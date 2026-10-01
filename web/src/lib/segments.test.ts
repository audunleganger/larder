import { describe, expect, it } from 'vitest'
import { breakdown, type Contribution } from './segments'

let id = 0
const c = (foodId: number, time: string, amount: number | null): Contribution => ({ entryId: ++id, foodId, foodName: `food ${foodId}`, time, amount })

describe('breakdown', () => {
  it('orders segments chronologically and colours foods by first appearance', () => {
    const result = breakdown([c(2, '12:00', 100), c(1, '08:00', 50), c(2, '18:00', 30)])
    expect(result.segments.map((s) => [s.foodId, s.time, s.color])).toEqual([
      [1, '08:00', 1],
      [2, '12:00', 2],
      [2, '18:00', 2],
    ])
    expect(result.foods.map((f) => [f.foodId, f.amount, f.count])).toEqual([
      [1, 50, 1],
      [2, 130, 2],
    ])
    expect(result.total).toBe(180)
  })

  it('leaves out entries without a value', () => {
    const result = breakdown([c(1, '08:00', null), c(2, '09:00', 0), c(3, '10:00', 5)])
    expect(result.segments.map((s) => s.foodId)).toEqual([3])
  })

  it('folds the smallest foods into "other" when there are more foods than colours', () => {
    const many = Array.from({ length: 10 }, (_, i) => c(i + 1, `0${i}:00`, i === 0 ? 1 : 10 + i))
    const result = breakdown(many)
    const colors = result.foods.map((f) => f.color)
    // Food 1 is the smallest; the 7 largest keep colours 1…7 in order of appearance.
    expect(colors.filter((x) => x === 'other')).toHaveLength(3)
    expect(result.foods[0].color).toBe('other')
    expect(colors.filter((x) => x !== 'other')).toEqual([1, 2, 3, 4, 5, 6, 7])
  })
})
