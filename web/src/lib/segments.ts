/** How much one entry contributed to a nutrient total. */
export interface Contribution {
  entryId: number
  foodId: number
  foodName: string
  time: string
  amount: number | null
}

/** The categorical colour slots (CSS --cat-1 … --cat-8). */
export const COLOR_SLOTS = 8

/** A colour slot 1…8, or 'other' for foods folded into a neutral "other" colour. */
export type SegmentColor = number | 'other'

export interface Segment extends Contribution {
  amount: number
  color: SegmentColor
}

/** One food's share of the total; a food eaten twice is one share with count 2. */
export interface FoodShare {
  foodId: number
  foodName: string
  amount: number
  count: number
  color: SegmentColor
}

export interface Breakdown {
  /** One per contributing entry, earliest first (H-5). */
  segments: Segment[]
  /** One per food, in order of first appearance. */
  foods: FoodShare[]
  total: number
}

/**
 * Splits a nutrient total into one segment per entry, earliest first. Each food gets its own colour,
 * assigned in order of first appearance, and keeps it for every entry of that food. With more foods
 * than colour slots, the largest contributors keep a colour and the rest share a neutral "other"
 * colour, so no colour is ever generated or reused.
 */
export function breakdown(contributions: Contribution[]): Breakdown {
  const segments = contributions
    .filter((c): c is Contribution & { amount: number } => c.amount !== null && c.amount > 0)
    .sort((a, b) => a.time.localeCompare(b.time) || a.entryId - b.entryId)
  const foods = new Map<number, FoodShare>()
  for (const s of segments) {
    const food = foods.get(s.foodId)
    if (food) {
      food.amount += s.amount
      food.count += 1
    } else {
      foods.set(s.foodId, { foodId: s.foodId, foodName: s.foodName, amount: s.amount, count: 1, color: 'other' })
    }
  }
  const shares = [...foods.values()]
  const colored = shares.length <= COLOR_SLOTS ? shares : [...shares].sort((a, b) => b.amount - a.amount).slice(0, COLOR_SLOTS - 1)
  const keep = new Set(colored.map((f) => f.foodId))
  let slot = 0
  for (const share of shares) if (keep.has(share.foodId)) share.color = ++slot
  return {
    segments: segments.map((s) => ({ ...s, color: foods.get(s.foodId)!.color })),
    foods: shares,
    total: segments.reduce((sum, s) => sum + s.amount, 0),
  }
}

export function colorVar(color: SegmentColor): string {
  return color === 'other' ? 'var(--cat-other)' : `var(--cat-${color})`
}
