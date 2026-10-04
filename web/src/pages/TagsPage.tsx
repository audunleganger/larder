import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useTags } from '../api/queries'
import type { TagDto, TagInput } from '../api/types.gen'
import { Badge, Card, Empty, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { TranslationFields } from '../components/TranslationFields'
import { draftTranslations, toTranslations } from '../lib/names'

/** Name and names in other languages, for the create and edit forms (F-16, L-5). */
export function TagFields({ initial, submitLabel, onSubmit, error, busy }: { initial?: TagDto; submitLabel: string; onSubmit: (input: TagInput) => Promise<unknown>; error: unknown; busy: boolean }) {
  const { t } = useTranslation()
  const [name, setName] = useState(initial?.name ?? '')
  const [translations, setTranslations] = useState(() => draftTranslations(initial?.translations))

  async function submit(event: FormEvent) {
    event.preventDefault()
    await onSubmit({ name, translations: toTranslations(translations) })
    if (!initial) {
      setName('')
      setTranslations(draftTranslations([]))
    }
  }

  return (
    <form onSubmit={(e) => void submit(e).catch(() => undefined)}>
      <div className="form-row">
        <Field label={t('common.name')} className="grow">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder={initial ? undefined : t('tags.namePlaceholder')} required />
        </Field>
      </div>
      <TranslationFields draft={translations} onChange={setTranslations} mainName={name} />
      <ErrorText error={error} />
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {submitLabel}
      </button>
    </form>
  )
}

/** The user's tags for grouping foods (F-16). */
export function TagsPage() {
  const { t } = useTranslation()
  const [showArchived, setShowArchived] = useState(false)
  const tags = useTags(showArchived)
  const create = useApiMutation(endpoints.createTag)

  return (
    <div className="page">
      <PageHeader title={t('tags.title')} subtitle={<><Link to="/foods">← {t('foods.title')}</Link> · {t('tags.subtitle')}</>} />
      <Card title={t('tags.new')}>
        <TagFields submitLabel={t('common.create')} onSubmit={(input) => create.mutateAsync(input)} error={create.error} busy={create.isPending} />
      </Card>
      <Card
        title={t('tags.all')}
        actions={
          <label className="checkbox">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> {t('common.showArchived')}
          </label>
        }
      >
        <QueryView query={tags}>
          {(data) =>
            data.length === 0 ? (
              <Empty>{t('tags.empty')}</Empty>
            ) : (
              <div className="table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>{t('common.name')}</th>
                      <th className="num">{t('tags.foodCount')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.map((tag) => (
                      <tr key={tag.id}>
                        <td>
                          <Link to={`/tags/${tag.id}`}>{tag.displayName}</Link> {tag.archived && <Badge>{t('common.archived')}</Badge>}
                        </td>
                        <td className="num">{tag.foodCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          }
        </QueryView>
      </Card>
    </div>
  )
}
