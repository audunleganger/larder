import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useNutrientDetail, useNutrients } from '../api/queries'
import type { MetadataInput, NutrientInput } from '../api/types.gen'
import { MetadataCard } from '../components/Metadata'
import { Badge, Card, ConfirmButton, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { formatDate } from '../lib/dates'
import { unitLabel } from '../lib/names'
import { currentLocale, formatAmount, formatNumber, formatQuantity } from '../lib/format'
import { ReadOnlyNote } from '../components/Shared'
import { NutrientFields } from './NutrientsPage'

export function NutrientDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const id = Number(useParams().id)
  const detail = useNutrientDetail(id)
  const all = useNutrients(true)
  const update = useApiMutation((input: NutrientInput) => endpoints.updateNutrient(id, input))
  const hide = useApiMutation((hidden: boolean) => endpoints.hideNutrient(id, hidden))
  const remove = useApiMutation(() => endpoints.deleteNutrient(id))
  const correct = useApiMutation((input: MetadataInput) => endpoints.setNutrientMetadata(id, input))

  return (
    <div className="page">
      <QueryView query={detail}>
        {(data) => {
          const nutrient = data.nutrient
          return (
            <>
              <PageHeader
                title={
                  <>
                    {nutrient.displayName} {nutrient.hidden && <Badge>{t('common.hidden')}</Badge>}
                  </>
                }
                subtitle={
                  <>
                    <Link to="/nutrients">← {t('nutrients.title')}</Link> · {nutrient.measureUnit} · {t('common.createdBy')} {nutrient.createdBy}
                  </>
                }
              />
              <Card title={t('common.edit')}>
                {!nutrient.canEdit && <ReadOnlyNote item={nutrient} />}
                {nutrient.canEdit && all.data && (
                  <NutrientFields
                    key={JSON.stringify(nutrient)}
                    initial={nutrient}
                    all={all.data}
                    submitLabel={t('common.save')}
                    onSubmit={(input) => update.mutateAsync(input)}
                    error={update.error}
                    busy={update.isPending}
                  />
                )}
              </Card>
              <div className="two-col">
                <Card title={t('nutrients.foods')}>
                  {data.foods.length === 0 ? (
                    <Empty>{t('nutrients.noFoods')}</Empty>
                  ) : (
                    <table className="table">
                      <thead>
                        <tr>
                          <th>{t('nutrients.food')}</th>
                          <th className="num">{t('nutrients.amount')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.foods.map((f) => (
                          <tr key={f.foodId}>
                            <td>
                              <Link to={`/foods/${f.foodId}`}>{f.foodName}</Link> {f.foodArchived && <Badge>{t('common.archived')}</Badge>}
                            </td>
                            <td className="num">
                              {formatAmount(f.amount, nutrient)}
                              {f.refAmount !== null && (
                                <span className="muted">
                                  {' '}
                                  / {formatNumber(f.refAmount, 3)} {f.refUnitName}
                                </span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </Card>
                <Card title={t('nutrients.entries')}>
                  {data.entries.length === 0 ? (
                    <Empty>{t('nutrients.noEntries')}</Empty>
                  ) : (
                    <table className="table">
                      <thead>
                        <tr>
                          <th>{t('entry.date')}</th>
                          <th>{t('nutrients.food')}</th>
                          <th className="num">{t('nutrients.amount')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.entries.map((e) => (
                          <tr key={e.entryId}>
                            <td>
                              <Link to={`/day/${e.date}`}>{formatDate(e.date, currentLocale(), 'short')}</Link>
                            </td>
                            <td>
                              {e.foodName}{' '}
                              <span className="muted">
                                {formatQuantity(e.quantity)} {unitLabel(e.unitName, e.unitPluralSuffix, e.quantity)}
                              </span>
                            </td>
                            <td className="num">{formatAmount(e.amount, nutrient)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                  {data.entriesTruncated && <p className="field-hint">{t('nutrients.truncated', { count: data.entries.length })}</p>}
                </Card>
              </div>
              <MetadataCard item={nutrient} withUpdated onSave={(input) => correct.mutateAsync(input)} />
              <Card title={nutrient.canEdit ? t('common.hideOrDelete') : t('common.hide')}>
                <div className="form-actions">
                  <button type="button" className="btn" onClick={() => hide.mutate(!nutrient.hidden)}>
                    {nutrient.hidden ? t('common.show') : t('common.hide')}
                  </button>
                  {nutrient.canEdit && <ConfirmButton onConfirm={() => remove.mutate(undefined, { onSuccess: () => navigate('/nutrients') })}>{t('common.delete')}</ConfirmButton>}
                </div>
                <p className="field-hint">{t('nutrients.hideHint')}</p>
                <ErrorText error={hide.error ?? remove.error} />
              </Card>
            </>
          )
        }}
      </QueryView>
    </div>
  )
}
