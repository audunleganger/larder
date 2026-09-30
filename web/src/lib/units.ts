import type { UnitDto, UnitKind } from '../api/types.gen'
import { formatNumber } from './format'

export const KINDS: UnitKind[] = ['mass', 'volume', 'custom']
export const BASE_UNIT: Record<UnitKind, string> = { mass: 'g', volume: 'ml', custom: '' }

export function unitSize(unit: UnitDto): string {
  return unit.baseFactor !== null && unit.kind !== 'custom' ? `${formatNumber(unit.baseFactor, 6)} ${BASE_UNIT[unit.kind]}` : ''
}
