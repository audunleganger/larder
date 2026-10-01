import { useId, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { NutrientDto, NutrientTotal } from '../api/types.gen'
import { formatAmount, formatNumber } from '../lib/format'
import { breakdown, colorVar, type Breakdown, type Contribution } from '../lib/segments'
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

/** The foods behind a total, with their colours, amounts and shares (H-5). */
export function BreakdownList({ parts, nutrient, highlight }: { parts: Breakdown; nutrient: NutrientDto; highlight?: number | null }) {
  return (
    <ul className="breakdown-list">
      {parts.foods.map((food) => (
        <li key={food.foodId} className={highlight === food.foodId ? 'highlight' : undefined}>
          <span className="swatch-dot" style={{ background: colorVar(food.color) }} aria-hidden="true" />
          <span className="breakdown-name">
            {food.foodName}
            {food.count > 1 && <span className="muted"> ×{food.count}</span>}
          </span>
          <span className="breakdown-amount">{formatAmount(food.amount, nutrient)}</span>
          <span className="breakdown-share muted">{formatNumber(parts.total > 0 ? (food.amount / parts.total) * 100 : 0, 0)} %</span>
        </li>
      ))}
    </ul>
  )
}

/**
 * A day's total for one nutrient as a bar. Normally it is coloured by target status (T-2); while
 * hovered, focused or tapped it splits into one segment per entry, earliest first, each food in its
 * own colour, with a list of the foods (H-5). Without a target the full bar is the day's total.
 */
export function TotalBar({ total, nutrient, contributions }: { total: NutrientTotal; nutrient: NutrientDto; contributions: Contribution[] }) {
  const { t } = useTranslation()
  const id = useId()
  const [active, setActive] = useState(false)
  const [hovered, setHovered] = useState<number | null>(null)
  const parts = useMemo(() => breakdown(contributions), [contributions])
  const { targetMin: min, targetMax: max, amount, status } = total
  const hasTarget = status !== 'none'
  const scale = hasTarget ? Math.max(amount, max ?? 0, min ?? 0) * 1.1 || 1 : amount || 1
  const pct = (v: number) => `${Math.min(100, (v / scale) * 100)}%`
  const splittable = parts.segments.length > 0
  const split = active && splittable
  const hoveredSegment = parts.segments.find((s) => s.entryId === hovered)
  const label = hasTarget ? `${formatAmount(amount, nutrient)} / ${targetText(total, nutrient)}` : formatAmount(amount, nutrient)

  return (
    <div
      className="total-bar"
      onPointerEnter={(e) => e.pointerType === 'mouse' && setActive(true)}
      onPointerLeave={(e) => {
        if (e.pointerType !== 'mouse') return
        setActive(false)
        setHovered(null)
      }}
    >
      <div
        className={`target-bar ${split ? 'split' : ''}`}
        role={splittable ? 'button' : 'img'}
        tabIndex={splittable ? 0 : undefined}
        aria-label={splittable ? `${label}. ${t('breakdown.show', { nutrient: nutrient.displayName })}` : label}
        aria-expanded={splittable ? split : undefined}
        aria-controls={split ? `${id}-list` : undefined}
        onFocus={() => setActive(true)}
        onBlur={() => setActive(false)}
        // Touch has no hover: a tap opens the split view, tapping elsewhere (blur) closes it.
        onClick={() => setActive(true)}
        onKeyDown={(e) => e.key === 'Escape' && setActive(false)}
      >
        {hasTarget && <div className="target-range" style={{ left: pct(min ?? 0), width: `calc(${pct(max ?? scale)} - ${pct(min ?? 0)})` }} />}
        {split ? (
          <div className="segments" style={{ width: pct(parts.total) }}>
            {parts.segments.map((s) => (
              <div
                key={s.entryId}
                className={`segment ${hovered !== null && hoveredSegment?.foodId !== s.foodId ? 'dimmed' : ''}`}
                style={{ flexGrow: s.amount, background: colorVar(s.color) }}
                onPointerEnter={() => setHovered(s.entryId)}
                onPointerLeave={() => setHovered(null)}
              />
            ))}
          </div>
        ) : (
          <div className={`target-fill fill-${status}`} style={{ width: pct(amount) }} />
        )}
      </div>
      {split && (
        <div className="breakdown-popover" id={`${id}-list`} role="tooltip">
          <p className="breakdown-title">
            {hoveredSegment
              ? `${hoveredSegment.time} · ${hoveredSegment.foodName} · ${formatAmount(hoveredSegment.amount, nutrient)} (${formatNumber((hoveredSegment.amount / parts.total) * 100, 0)} %)`
              : t('breakdown.title', { nutrient: nutrient.displayName })}
          </p>
          <BreakdownList parts={parts} nutrient={nutrient} highlight={hoveredSegment?.foodId} />
        </div>
      )}
    </div>
  )
}
