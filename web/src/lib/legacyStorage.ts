/**
 * Moves this browser's settings and login from the `cc.` keys used before the rename to Larder, so nobody is
 * signed out or loses their language. Imported first in main.tsx, before anything reads the keys.
 */
const LEGACY_KEYS: Record<string, string> = {
  'cc.token': 'larder.token',
  'cc.lang': 'larder.lang',
  'cc.foodColumns.hidden': 'larder.foodColumns.hidden',
}

try {
  for (const [legacy, current] of Object.entries(LEGACY_KEYS)) {
    const value = localStorage.getItem(legacy)
    if (value === null) continue
    if (localStorage.getItem(current) === null) localStorage.setItem(current, value)
    localStorage.removeItem(legacy)
  }
} catch {
  // ignore unavailable storage
}
