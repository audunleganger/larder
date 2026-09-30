import type { NutrientDto, NutrientTotal } from '../api/types.gen'
import { formatNumber } from './format'

export function targetText(total: NutrientTotal, nutrient: NutrientDto): string {
  const digits = nutrient.displayPrecision
  const { targetMin: min, targetMax: max } = total
  if (min !== null && max !== null) return `${formatNumber(min, digits)}–${formatNumber(max, digits)} ${nutrient.measureUnit}`
  if (min !== null) return `≥ ${formatNumber(min, digits)} ${nutrient.measureUnit}`
  if (max !== null) return `≤ ${formatNumber(max, digits)} ${nutrient.measureUnit}`
  return ''
}
