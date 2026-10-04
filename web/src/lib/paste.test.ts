import { describe, expect, it } from 'vitest'
import { linkIn } from './paste'

describe('linkIn', () => {
  it('accepts http and https links, trimmed', () => {
    expect(linkIn('  https://example.com/a.jpg\n')).toBe('https://example.com/a.jpg')
    expect(linkIn('http://example.com/a b.png')).toBeNull()
    expect(linkIn('HTTP://Example.com/x.webp')).toBe('http://example.com/x.webp')
  })

  it('refuses other text', () => {
    expect(linkIn('file:///etc/passwd')).toBeNull()
    expect(linkIn('javascript:alert(1)')).toBeNull()
    expect(linkIn('Rye bread')).toBeNull()
    expect(linkIn('')).toBeNull()
  })
})
