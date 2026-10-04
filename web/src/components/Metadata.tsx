import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useUsers } from '../api/queries'
import type { MetadataInput } from '../api/types.gen'
import { useUser } from '../auth/useAuth'
import { currentLocale } from '../lib/format'
import { Card, ErrorText, Field } from './ui'

export interface MetadataValues {
  createdAt: number
  createdBy: string
  /** Absent for foods, which only record when they were made. */
  updatedAt?: number | null
  updatedBy?: string | null
}

const pad = (n: number) => String(n).padStart(2, '0')

/** A time as the value of a datetime-local input, in local time. */
function toInputValue(time: number): string {
  const d = new Date(time)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromInputValue(value: string): number | null {
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? null : time
}

function formatTime(time: number): string {
  return new Intl.DateTimeFormat(currentLocale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(time))
}

/**
 * Who made an item and when, and when and by whom it was last changed. Admins can correct it: all of it
 * for units and nutrients ([withUpdated]), only the created date for foods.
 */
export function MetadataCard({
  item,
  withUpdated,
  onSave,
}: {
  item: MetadataValues
  withUpdated: boolean
  onSave: (input: MetadataInput) => Promise<unknown>
}) {
  const { t } = useTranslation()
  const user = useUser()
  const [editing, setEditing] = useState(false)
  return (
    <Card
      title={t('metadata.title')}
      actions={
        user.isAdmin &&
        !editing && (
          <button type="button" className="btn btn-small" onClick={() => setEditing(true)}>
            {t('metadata.correct')}
          </button>
        )
      }
    >
      {editing ? (
        <MetadataForm item={item} withUpdated={withUpdated} onSave={onSave} onDone={() => setEditing(false)} />
      ) : (
        <ul className="link-list">
          <li>{t('metadata.created', { date: formatTime(item.createdAt), user: item.createdBy })}</li>
          {withUpdated && (
            <li>
              {item.updatedAt == null
                ? t('metadata.notChanged')
                : item.updatedBy
                  ? t('metadata.updated', { date: formatTime(item.updatedAt), user: item.updatedBy })
                  : t('metadata.updatedUnknown', { date: formatTime(item.updatedAt) })}
            </li>
          )}
        </ul>
      )}
    </Card>
  )
}

function MetadataForm({
  item,
  withUpdated,
  onSave,
  onDone,
}: {
  item: MetadataValues
  withUpdated: boolean
  onSave: (input: MetadataInput) => Promise<unknown>
  onDone: () => void
}) {
  const { t } = useTranslation()
  const users = useUsers()
  const [createdAt, setCreatedAt] = useState(toInputValue(item.createdAt))
  const [createdBy, setCreatedBy] = useState(item.createdBy)
  const [updatedAt, setUpdatedAt] = useState(item.updatedAt == null ? '' : toInputValue(item.updatedAt))
  const [updatedBy, setUpdatedBy] = useState(item.updatedBy ?? '')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const usernames = users.data?.map((u) => u.username) ?? []
  // Keep the current names selectable while the user list loads (or if one is no longer listed).
  const options = (current: string) => (current && !usernames.includes(current) ? [current, ...usernames] : usernames)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const created = fromInputValue(createdAt)
    const updated = updatedAt ? fromInputValue(updatedAt) : null
    if (created === null || (updatedAt && updated === null)) {
      setError(new Error(t('metadata.invalidTime')))
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSave({ createdAt: created, createdBy, updatedAt: updated, updatedBy: updated !== null && updatedBy ? updatedBy : null })
      onDone()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={(e) => void submit(e)}>
      <div className="form-row">
        <Field label={t('metadata.createdAt')}>
          <input className="input" type="datetime-local" value={createdAt} onChange={(e) => setCreatedAt(e.target.value)} required />
        </Field>
        {withUpdated && (
          <Field label={t('metadata.createdBy')}>
            <select className="input" value={createdBy} onChange={(e) => setCreatedBy(e.target.value)}>
              {options(createdBy).map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </Field>
        )}
      </div>
      {withUpdated && (
        <div className="form-row">
          <Field label={t('metadata.updatedAt')} hint={t('metadata.updatedAtHint')}>
            <input className="input" type="datetime-local" value={updatedAt} onChange={(e) => setUpdatedAt(e.target.value)} />
          </Field>
          <Field label={t('metadata.updatedBy')}>
            <select className="input" value={updatedBy} onChange={(e) => setUpdatedBy(e.target.value)} disabled={!updatedAt}>
              <option value="">{t('metadata.unknown')}</option>
              {options(updatedBy).map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </Field>
        </div>
      )}
      <p className="field-hint">{withUpdated ? t('metadata.correctHint') : t('metadata.correctFoodHint')}</p>
      <ErrorText error={error} />
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {t('common.save')}
        </button>
        <button type="button" className="btn" onClick={onDone}>
          {t('common.cancel')}
        </button>
      </div>
    </form>
  )
}
