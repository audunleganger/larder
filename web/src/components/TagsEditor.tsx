import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useTags } from '../api/queries'
import type { FoodDetail, FoodDto, TagInput } from '../api/types.gen'
import { currentLocale } from '../lib/format'
import { tagsByName } from '../lib/tags'
import { ErrorText } from './ui'
import { TagChip } from './TagChip'

/**
 * A food's tags (F-16): the chosen ones as chips, a list to add an existing tag, and a field to make a
 * new one. Changes are saved at once, without the food's Save button; a tag no food has any more goes.
 * Archived tags stay on foods that have them, but aren't offered. [value]: the food's tags as loaded,
 * not a copy. Changes start from the tags that still exist, so a tag deleted elsewhere is never sent
 * back, even while the food shows a cached copy from before.
 */
export function TagsEditor({ foodId, value }: { foodId: number; value: number[] }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const tags = useTags(true)
  const [name, setName] = useState('')
  const set = useApiMutation((ids: number[]) => endpoints.setFoodTags(foodId, ids))
  const add = useApiMutation((input: TagInput) => endpoints.addFoodTag(foodId, input))
  const busy = set.isPending || add.isPending
  const all = tags.data ?? []
  const chosen = tagsByName(value, all, currentLocale())
  const current = chosen.map((tag) => tag.id)
  const offered = tagsByName(
    all.filter((tag) => !tag.archived && !value.includes(tag.id)).map((tag) => tag.id),
    all,
    currentLocale(),
  )

  // Shown at once; the refresh that follows every change brings the rest.
  function saved(food: FoodDto) {
    queryClient.setQueryData<FoodDetail>(['foods', 'detail', foodId], (detail) => detail && { ...detail, food })
  }

  async function save(ids: number[]) {
    add.reset()
    saved(await set.mutateAsync(ids))
  }

  async function create() {
    const clean = name.trim()
    if (!clean) return
    set.reset()
    saved(await add.mutateAsync({ name: clean }))
    setName('')
  }

  return (
    <div className="tags-editor">
      {chosen.length === 0 ? (
        <p className="muted">{t('tags.none')}</p>
      ) : (
        <ul className="chips">
          {chosen.map((tag) => (
            <TagChip key={tag.id} tag={tag}>
              <button
                type="button"
                className="chip-remove"
                aria-label={t('tags.remove', { name: tag.displayName })}
                disabled={busy}
                onClick={() => void save(current.filter((id) => id !== tag.id)).catch(() => undefined)}
              >
                ×
              </button>
            </TagChip>
          ))}
        </ul>
      )}
      <div className="form-row">
        {offered.length > 0 && (
          <select
            className="input"
            aria-label={t('tags.add')}
            value=""
            disabled={busy}
            onChange={(e) => {
              if (e.target.value) void save([...current, Number(e.target.value)]).catch(() => undefined)
            }}
          >
            <option value="">{t('tags.add')}</option>
            {offered.map((tag) => (
              <option key={tag.id} value={tag.id}>
                {tag.displayName}
              </option>
            ))}
          </select>
        )}
        <input
          className="input grow"
          aria-label={t('tags.newName')}
          placeholder={t('tags.newName')}
          value={name}
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => {
            // Enter makes the tag instead of saving the food.
            if (e.key === 'Enter') {
              e.preventDefault()
              void create().catch(() => undefined)
            }
          }}
        />
        <button type="button" className="btn" disabled={busy || !name.trim()} onClick={() => void create().catch(() => undefined)}>
          {t('tags.create')}
        </button>
      </div>
      <p className="field-hint">{t('tags.savedAtOnce')}</p>
      <ErrorText error={set.error ?? add.error} />
    </div>
  )
}
