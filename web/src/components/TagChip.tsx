import type { ReactNode } from 'react'
import { Link } from 'react-router'
import type { TagDto } from '../api/types.gen'
import { tagColorClass } from '../lib/tags'

/** A tag in its color (F-16), linking to the tag's page; [children] come after the name. */
export function TagChip({ tag, children }: { tag: TagDto; children?: ReactNode }) {
  return (
    <li className={`chip tag-chip ${tagColorClass(tag.color)} ${tag.archived ? 'chip-muted' : ''}`}>
      <Link to={`/tags/${tag.id}`}>{tag.displayName}</Link>
      {children}
    </li>
  )
}
