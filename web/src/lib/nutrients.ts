/** A main nutrient and its sub-nutrients ("of which …"), one level deep (N-6). */
export interface NutrientGroup<T> {
  main: T
  subs: T[]
}

/**
 * Groups a list that is already in display order (the server keeps sub-nutrients directly after
 * their parent). A sub-nutrient whose parent isn't in the list, e.g. because the parent is archived
 * and filtered out, becomes a group of its own.
 */
export function groupNutrients<T extends { id: number; parentId: number | null }>(list: T[]): NutrientGroup<T>[] {
  const ids = new Set(list.map((n) => n.id))
  const groups: NutrientGroup<T>[] = []
  const byMain = new Map<number, NutrientGroup<T>>()
  for (const item of list) {
    const parent = item.parentId !== null && ids.has(item.parentId) ? byMain.get(item.parentId) : undefined
    if (parent) {
      parent.subs.push(item)
    } else {
      const group = { main: item, subs: [] }
      groups.push(group)
      byMain.set(item.id, group)
    }
  }
  return groups
}

/** The flat display order of [groups], e.g. to send as a new nutrient order. */
export function flattenGroups<T>(groups: NutrientGroup<T>[]): T[] {
  return groups.flatMap((g) => [g.main, ...g.subs])
}

/** Returns a copy of [list] with the item at [index] moved by [delta], or null if it can't move. */
export function moved<T>(list: T[], index: number, delta: number): T[] | null {
  const target = index + delta
  if (target < 0 || target >= list.length) return null
  const copy = [...list]
  ;[copy[index], copy[target]] = [copy[target], copy[index]]
  return copy
}
