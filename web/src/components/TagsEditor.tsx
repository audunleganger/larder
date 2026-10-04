import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import * as endpoints from '../api/endpoints'
import { useTags } from '../api/queries'
import { errorMessage } from '../lib/errors'

/**
 * A food's tags (F-16): the chosen ones as chips, a list to add an existing tag, and a field to make a
 * new one. Archived tags stay on foods that have them, but aren't offered.
 */
export function TagsEditor({ value, onChange }: { value: number[]; onChange: (ids: number[]) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const tags = useTags(true)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const all = tags.data ?? []
  const chosen = value.map((id) => all.find((tag) => tag.id === id)).filter((tag) => tag !== undefined)
  const offered = all.filter((tag) => !tag.archived && !value.includes(tag.id))

  async function create() {
    const clean = name.trim()
    if (!clean) return
    setError(null)
    setBusy(true)
    try {
      // An existing tag with that name (in any language) is added instead.
      const existing = all.find((tag) => [tag.name, ...tag.translations.map((tr) => tr.name)].some((n) => n.toLowerCase() === clean.toLowerCase()))
      const id = existing?.id ?? (await endpoints.createTag({ name: clean })).id
      if (!existing) await queryClient.invalidateQueries({ queryKey: ['tags'] })
      if (!value.includes(id)) onChange([...value, id])
      setName('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="tags-editor">
      {chosen.length === 0 ? (
        <p className="muted">{t('tags.none')}</p>
      ) : (
        <ul className="chips">
          {chosen.map((tag) => (
            <li key={tag.id} className={`chip ${tag.archived ? 'chip-muted' : ''}`}>
              <Link to={`/tags/${tag.id}`}>{tag.displayName}</Link>
              <button type="button" className="chip-remove" aria-label={t('tags.remove', { name: tag.displayName })} onClick={() => onChange(value.filter((id) => id !== tag.id))}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="form-row">
        {offered.length > 0 && (
          <select
            className="input"
            aria-label={t('tags.add')}
            value=""
            onChange={(e) => {
              if (e.target.value) onChange([...value, Number(e.target.value)])
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
              void create()
            }
          }}
        />
        <button type="button" className="btn" disabled={busy || !name.trim()} onClick={() => void create()}>
          {t('tags.create')}
        </button>
      </div>
      {error && <p className="error-text">{error}</p>}
    </div>
  )
}
