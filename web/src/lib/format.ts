import i18n from '../i18n'
import type { NutrientDto } from '../api/types.gen'

/** BCP 47 locale for number and date formatting. */
export function currentLocale(): string {
  return i18n.language === 'nb' ? 'nb-NO' : 'en-GB'
}

export function formatNumber(value: number, maxDigits = 2, minDigits = 0): string {
  return new Intl.NumberFormat(currentLocale(), {
    maximumFractionDigits: maxDigits,
    minimumFractionDigits: minDigits,
  }).format(value)
}

/** A nutrient amount with its measurement unit, or an em dash when missing. */
export function formatAmount(value: number | null | undefined, nutrient: Pick<NutrientDto, 'displayPrecision' | 'measureUnit'>): string {
  if (value === null || value === undefined) return '—'
  return `${formatNumber(value, nutrient.displayPrecision)} ${nutrient.measureUnit}`
}

export function formatQuantity(value: number): string {
  return formatNumber(value, 3)
}

/**
 * Parses user-typed decimals, accepting both "," and "." as decimal separator (L-2).
 * Returns null for empty input and NaN for invalid input.
 */
export function parseDecimal(input: string): number | null {
  const cleaned = input.trim().replace(/[\s  ]/g, '').replace(',', '.')
  if (cleaned === '') return null
  if (!/^-?\d*\.?\d+$|^-?\d+\.$/.test(cleaned)) return Number.NaN
  return Number(cleaned)
}

/** Formats a number for an editable input, using the locale's decimal separator and no grouping. */
export function toInputValue(value: number | null | undefined): string {
  if (value === null || value === undefined) return ''
  const text = String(Math.round(value * 1e6) / 1e6)
  return i18n.language === 'nb' ? text.replace('.', ',') : text
}
