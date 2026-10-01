import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useNutrientDetail, useNutrients } from '../api/queries'
import type { NutrientInput } from '../api/types.gen'
import { Badge, Card, ConfirmButton, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { formatDate } from '../lib/dates'
import { unitLabel } from '../lib/names'
import { currentLocale, formatAmount, formatNumber, formatQuantity } from '../lib/format'
import { NutrientFields } from './NutrientsPage'

export function NutrientDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const id = Number(useParams().id)
  const detail = useNutrientDetail(id)
  const all = useNutrients(true)
  const update = useApiMutation((input: NutrientInput) => endpoints.updateNutrient(id, input))
  const archive = useApiMutation((archived: boolean) => endpoints.archiveNutrient(id, archived))
  const remove = useApiMutation(() => endpoints.deleteNutrient(id))

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
                    {nutrient.displayName} {nutrient.archived && <Badge>{t('common.archived')}</Badge>}
                  </>
                }
                subtitle={
                  <>
                    <Link to="/nutrients">← {t('nutrients.title')}</Link> · {nutrient.measureUnit}
                  </>
                }
              />
              <Card title={t('common.edit')}>
                {all.data && (
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
              <Card title={t('common.manage')}>
                <div className="form-actions">
                  <button type="button" className="btn" onClick={() => archive.mutate(!nutrient.archived)}>
                    {nutrient.archived ? t('common.unarchive') : t('common.archive')}
                  </button>
                  <ConfirmButton onConfirm={() => remove.mutate(undefined, { onSuccess: () => navigate('/nutrients') })}>{t('common.delete')}</ConfirmButton>
                </div>
                <p className="field-hint">{t('nutrients.archiveHint')}</p>
                <ErrorText error={archive.error ?? remove.error} />
              </Card>
            </>
          )
        }}
      </QueryView>
    </div>
  )
}
