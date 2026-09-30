import { useTranslation } from 'react-i18next'
import type { NutrientDto, NutrientTotal } from '../api/types.gen'
import { formatAmount } from '../lib/format'
import { targetText } from '../lib/targets'

const STATUS_ICON = { below: '▼', within: '✓', above: '▲', none: '' } as const

/** Status label with icon; target state is never conveyed by color alone (T-2). */
export function TargetStatusLabel({ status }: { status: NutrientTotal['status'] }) {
  const { t } = useTranslation()
  if (status === 'none') return null
  return (
    <span className={`status status-${status}`}>
      <span aria-hidden="true">{STATUS_ICON[status]}</span> {t(`targets.status.${status}`)}
    </span>
  )
}

/** Progress of a day's total against its target range. */
export function TargetBar({ total, nutrient }: { total: NutrientTotal; nutrient: NutrientDto }) {
  const { targetMin: min, targetMax: max, amount, status } = total
  if (status === 'none') return null
  const scale = Math.max(amount, max ?? 0, min ?? 0) * 1.1 || 1
  const pct = (v: number) => `${Math.min(100, (v / scale) * 100)}%`
  const rangeStart = min ?? 0
  const rangeEnd = max ?? scale
  return (
    <div className="target-bar" role="img" aria-label={`${formatAmount(amount, nutrient)} / ${targetText(total, nutrient)}`}>
      <div className="target-range" style={{ left: pct(rangeStart), width: `calc(${pct(rangeEnd)} - ${pct(rangeStart)})` }} />
      <div className={`target-fill fill-${status}`} style={{ width: pct(amount) }} />
    </div>
  )
}
