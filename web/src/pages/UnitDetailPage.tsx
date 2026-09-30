import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useUnitDetail } from '../api/queries'
import type { FoodRef, UnitInput } from '../api/types.gen'
import { Badge, Card, ConfirmButton, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { formatDate } from '../lib/dates'
import { currentLocale } from '../lib/format'
import { unitSize } from '../lib/units'
import { UnitFields } from './UnitsPage'

function FoodLinks({ foods }: { foods: FoodRef[] }) {
  const { t } = useTranslation()
  return (
    <ul className="link-list">
      {foods.map((food) => (
        <li key={food.id}>
          <Link to={`/foods/${food.id}`}>{food.name}</Link> {food.archived && <Badge>{t('common.archived')}</Badge>}
        </li>
      ))}
    </ul>
  )
}

export function UnitDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const id = Number(useParams().id)
  const detail = useUnitDetail(id)
  const update = useApiMutation((input: UnitInput) => endpoints.updateUnit(id, input))
  const archive = useApiMutation((archived: boolean) => endpoints.archiveUnit(id, archived))
  const remove = useApiMutation(() => endpoints.deleteUnit(id))

  return (
    <div className="page">
      <QueryView query={detail}>
        {(data) => (
          <>
            <PageHeader
              title={
                <>
                  {data.unit.name} {data.unit.archived && <Badge>{t('common.archived')}</Badge>}
                </>
              }
              subtitle={
                <>
                  <Link to="/units">← {t('units.title')}</Link> · {t(`units.kinds.${data.unit.kind}`)} {unitSize(data.unit) && `· ${unitSize(data.unit)}`}
                </>
              }
            />
            <Card title={t('common.edit')}>
              <UnitFields
                key={JSON.stringify(data.unit)}
                initial={data.unit}
                submitLabel={t('common.save')}
                onSubmit={(input) => update.mutateAsync(input)}
                error={update.error}
                busy={update.isPending}
              />
            </Card>
            <div className="two-col">
              <Card title={t('units.foods')}>
                {data.foods.length === 0 ? <Empty>{t('units.noFoods')}</Empty> : <FoodLinks foods={data.foods} />}
                {data.implicitFoods.length > 0 && (
                  <>
                    <h3>{t('units.implicitFoods')}</h3>
                    <p className="field-hint">{t('units.implicitHint')}</p>
                    <FoodLinks foods={data.implicitFoods} />
                  </>
                )}
              </Card>
              <Card title={t('units.dates')}>
                {data.dates.length === 0 ? (
                  <Empty>{t('units.noDates')}</Empty>
                ) : (
                  <ul className="link-list">
                    {data.dates.map((date) => (
                      <li key={date}>
                        <Link to={`/day/${date}`}>{formatDate(date, currentLocale(), 'long')}</Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
            <Card title={t('common.manage')}>
              <div className="form-actions">
                <button type="button" className="btn" onClick={() => archive.mutate(!data.unit.archived)}>
                  {data.unit.archived ? t('common.unarchive') : t('common.archive')}
                </button>
                <ConfirmButton onConfirm={() => remove.mutate(undefined, { onSuccess: () => navigate('/units') })}>{t('common.delete')}</ConfirmButton>
              </div>
              <p className="field-hint">{t('units.archiveHint')}</p>
              <ErrorText error={archive.error ?? remove.error} />
            </Card>
          </>
        )}
      </QueryView>
    </div>
  )
}
