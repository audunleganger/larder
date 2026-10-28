import type { TagDto } from '../api/types.gen'
import { compareNames } from './names'

/** The tag colors (F-16), as the server knows them (TagColors); each has a light and a dark style. */
export const TAG_COLORS = ['red', 'orange', 'yellow', 'green', 'teal', 'blue', 'indigo', 'purple', 'pink', 'brown'] as const

/** The class that colors a tag's chip; gray without a color. */
export function tagColorClass(color: string | null | undefined): string {
  return color && (TAG_COLORS as readonly string[]).includes(color) ? `tag-color-${color}` : ''
}

/** The tags with [ids] that are known, by display name (see compareNames). */
export function tagsByName(ids: number[], tags: TagDto[]): TagDto[] {
  return ids
    .map((id) => tags.find((tag) => tag.id === id))
    .filter((tag) => tag !== undefined)
    .sort((a, b) => compareNames(a.displayName, b.displayName))
}
