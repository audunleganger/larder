import type { FoodDetail, UnitDto, UnitKind } from '../api/types.gen'
import { formatNumber } from './format'

export const KINDS: UnitKind[] = ['mass', 'volume', 'custom']
export const BASE_UNIT: Record<UnitKind, string> = { mass: 'g', volume: 'ml', custom: '' }

export function unitSize(unit: UnitDto): string {
  return unit.baseFactor !== null && unit.kind !== 'custom' ? `${formatNumber(unit.baseFactor, 6)} ${BASE_UNIT[unit.kind]}` : ''
}

/** Unit to preselect for a food: the last one used, else the first resolvable explicit unit. */
export function defaultUnitId(detail: FoodDetail | undefined): number | null {
  if (!detail) return null
  const last = detail.entries[0]?.unitId
  if (last !== undefined) return last
  const usable = detail.usableUnits
  return (usable.find((u) => u.explicit && u.amountInRefUnit !== null) ?? usable.find((u) => u.amountInRefUnit !== null) ?? usable[0])?.unitId ?? null
}
