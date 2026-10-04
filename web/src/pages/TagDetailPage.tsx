import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useTagDetail } from '../api/queries'
import type { TagInput } from '../api/types.gen'
import { MetadataCard } from '../components/Metadata'
import { Badge, Card, ConfirmButton, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { TagFields } from './TagsPage'

export function TagDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const id = Number(useParams().id)
  const detail = useTagDetail(id)
  const update = useApiMutation((input: TagInput) => endpoints.updateTag(id, input))
  const archive = useApiMutation((archived: boolean) => endpoints.archiveTag(id, archived))
  const remove = useApiMutation(() => endpoints.deleteTag(id))
  const correct = useApiMutation((input: { createdAt: number; updatedAt?: number | null }) => endpoints.setTagMetadata(id, input))

  return (
    <div className="page">
      <QueryView query={detail}>
        {(data) => (
          <>
            <PageHeader
              title={
                <>
                  {data.tag.displayName} {data.tag.archived && <Badge>{t('common.archived')}</Badge>}
                </>
              }
              subtitle={<Link to="/tags">← {t('tags.title')}</Link>}
            />
            <Card title={t('common.edit')}>
              <TagFields key={JSON.stringify(data.tag)} initial={data.tag} submitLabel={t('common.save')} onSubmit={(input) => update.mutateAsync(input)} error={update.error} busy={update.isPending} />
            </Card>
            <Card title={t('tags.foods')}>
              {data.foods.length === 0 ? (
                <Empty>{t('tags.noFoods')}</Empty>
              ) : (
                <ul className="link-list">
                  {data.foods.map((food) => (
                    <li key={food.id}>
                      <Link to={`/foods/${food.id}`}>{food.name}</Link> {food.archived && <Badge>{t('common.archived')}</Badge>}
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            {/* Tags are private: the one who changed it last is always its owner. */}
            <MetadataCard
              item={{ ...data.tag, updatedBy: data.tag.updatedAt === null ? null : data.tag.createdBy }}
              withUpdated
              withUsers={false}
              onSave={(input) => correct.mutateAsync({ createdAt: input.createdAt, updatedAt: input.updatedAt })}
            />
            <Card title={t('common.manage')}>
              <div className="form-actions">
                <button type="button" className="btn" onClick={() => archive.mutate(!data.tag.archived)}>
                  {data.tag.archived ? t('common.unarchive') : t('common.archive')}
                </button>
                <ConfirmButton onConfirm={() => remove.mutate(undefined, { onSuccess: () => navigate('/tags') })}>{t('common.delete')}</ConfirmButton>
              </div>
              <p className="field-hint">{t('tags.archiveHint')}</p>
              <ErrorText error={archive.error ?? remove.error} />
            </Card>
          </>
        )}
      </QueryView>
    </div>
  )
}
