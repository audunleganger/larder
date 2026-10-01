import { describe, expect, it } from 'vitest'
import en from './en'
import nb from './nb'

function keys(value: unknown, prefix = ''): string[] {
  if (typeof value !== 'object' || value === null) return [prefix]
  return Object.entries(value).flatMap(([k, v]) => keys(v, prefix ? `${prefix}.${k}` : k))
}

describe('translations', () => {
  it('Norwegian has every English string and no extra ones', () => {
    expect(keys(nb).sort()).toEqual(keys(en).sort())
  })
})
