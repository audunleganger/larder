import { ApiError, NETWORK_ERROR } from '../api/client'
import i18n from '../i18n'

/** Human-readable, translated message for an API error. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const key = `errors.${error.code}`
    if (error.code === 'REFERENCED') {
      const parts = Object.entries(error.details)
        .filter(([, count]) => count > 0)
        .map(([what, count]) => i18n.t(`references.${what}` as 'references.entries', { count }))
      return i18n.t('errors.REFERENCED', { what: parts.join(', ') })
    }
    if (error.code === NETWORK_ERROR) return i18n.t('errors.NETWORK')
    // Validation messages come from the server in English; show them as-is.
    if (error.code !== 'VALIDATION' && i18n.exists(key)) return i18n.t(key as 'errors.NOT_FOUND')
    return error.message
  }
  return error instanceof Error ? error.message : String(error)
}
