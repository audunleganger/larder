import { describe, expect, it } from 'vitest'
import { parseDecimal } from './format'
import { addDays, daysBetween, isValidIsoDate, rollingAverage } from './dates'

describe('parseDecimal', () => {
  it('accepts comma and dot', () => {
    expect(parseDecimal('1,5')).toBe(1.5)
    expect(parseDecimal('1.5')).toBe(1.5)
    expect(parseDecimal(' 2 ')).toBe(2)
    expect(parseDecimal('.5')).toBe(0.5)
    expect(parseDecimal('3.')).toBe(3)
  })
  it('returns null for empty and NaN for garbage', () => {
    expect(parseDecimal('')).toBeNull()
    expect(parseDecimal('abc')).toBeNaN()
    expect(parseDecimal('1,2,3')).toBeNaN()
  })
  it('ignores spaces used as thousand separators', () => {
    expect(parseDecimal('1 500')).toBe(1500)
  })
})

describe('dates', () => {
  it('adds days across month and DST boundaries', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-03-29', -1)).toBe('2026-03-28')
    expect(daysBetween('2026-01-01', '2026-12-31')).toBe(364)
  })
  it('validates ISO dates', () => {
    expect(isValidIsoDate('2026-02-28')).toBe(true)
    expect(isValidIsoDate('2026-02-30')).toBe(false)
    expect(isValidIsoDate('30.09.2026')).toBe(false)
  })
  it('computes rolling averages skipping gaps', () => {
    expect(rollingAverage([1, null, 3], 7)).toEqual([1, 1, 2])
    expect(rollingAverage([null, null], 7)).toEqual([null, null])
  })
})
