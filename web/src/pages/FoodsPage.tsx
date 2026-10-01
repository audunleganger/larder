import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useFoodRefDefault, useFoods, useUnits } from '../api/queries'
import { DecimalInput } from '../components/DecimalInput'
import { FoodThumb } from '../components/FoodPhoto'
import { unitLabel } from '../lib/names'
import { Badge, Card, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { formatNumber, parseDecimal, toInputValue } from '../lib/format'
import { useDebounced } from '../lib/useDebounced'

export function FoodsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  const [newName, setNewName] = useState('')
  // Null until the user edits it: then the remembered default (F-12) is shown.
  const [newRef, setNewRef] = useState<{ amount: string; unitId: number | '' } | null>(null)
  const refDefault = useFoodRefDefault()
  const ref = newRef ?? { amount: toInputValue(refDefault.data?.refAmount ?? null), unitId: refDefault.data?.refUnitId ?? ('' as const) }
  const foods = useFoods(useDebounced(search.trim(), 150), showArchived)
  const units = useUnits(true)
  const unitName = (id: number | null, amount: number) => {
    const unit = units.data?.find((u) => u.id === id)
    return unit ? unitLabel(unit.displayName, unit.displayPluralSuffix, amount) : ''
  }
  const create = useApiMutation(endpoints.createFood)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const amount = parseDecimal(ref.amount)
    const withRef = amount !== null && !Number.isNaN(amount) && amount > 0 && ref.unitId !== ''
    const food = await create.mutateAsync({ name: newName, refAmount: withRef ? amount : null, refUnitId: withRef ? Number(ref.unitId) : null })
    setNewName('')
    setNewRef(null)
    navigate(`/foods/${food.id}`)
  }

  return (
    <div className="page">
      <PageHeader title={t('foods.title')} subtitle={t('foods.subtitle')} />
      <Card title={t('foods.new')}>
        <form className="inline-form" onSubmit={(e) => void submit(e).catch(() => undefined)}>
          <input className="input grow" placeholder={t('foods.namePlaceholder')} aria-label={t('common.name')} value={newName} onChange={(e) => setNewName(e.target.value)} required />
          <span className="muted">{t('foods.per')}</span>
          <DecimalInput className="qty-input" aria-label={t('foods.refAmount')} value={ref.amount} onChange={(amount) => setNewRef({ ...ref, amount })} />
          <select className="input unit-select" aria-label={t('foods.refUnit')} value={ref.unitId} onChange={(e) => setNewRef({ ...ref, unitId: e.target.value ? Number(e.target.value) : '' })}>
            <option value="">{t('foods.notSet')}</option>
            {(units.data ?? [])
              .filter((u) => !u.archived || u.id === ref.unitId)
              .map((u) => (
                <option key={u.id} value={u.id}>
                  {u.displayName}
                </option>
              ))}
          </select>
          <button type="submit" className="btn btn-primary" disabled={create.isPending}>
            {t('common.create')}
          </button>
        </form>
        <p className="field-hint">{t('foods.newHint')}</p>
        <ErrorText error={create.error} />
      </Card>
      <Card
        title={t('foods.all')}
        actions={
          <label className="checkbox">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> {t('common.showArchived')}
          </label>
        }
      >
        <input className="input" type="search" placeholder={t('foods.search')} value={search} onChange={(e) => setSearch(e.target.value)} aria-label={t('foods.search')} />
        <QueryView query={foods}>
          {(data) =>
            data.length === 0 ? (
              <Empty>{search ? t('foods.noMatches') : t('foods.empty')}</Empty>
            ) : (
              <table className="table">
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('foods.reference')}</th>
                    <th className="num">{t('foods.nutrientCount')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.map((food) => (
                    <tr key={food.id}>
                      <td>
                        <Link to={`/foods/${food.id}`} className="food-link">
                          <FoodThumb foodId={food.id} version={food.imageVersion} name={food.name} />
                          {food.name}
                        </Link> {food.composite && <Badge tone="info">{t('composite.badge')}</Badge>} {food.archived && <Badge>{t('common.archived')}</Badge>}
                      </td>
                      <td>
                        {food.refAmount !== null ? (
                          food.composite ? (
                            t('composite.makesShort', { amount: formatNumber(food.refAmount, 3), unit: unitName(food.refUnitId, food.refAmount) })
                          ) : (
                            `${t('foods.per')} ${formatNumber(food.refAmount, 3)} ${unitName(food.refUnitId, food.refAmount)}`
                          )
                        ) : (
                          <Badge tone="warning">{t('foods.noReference')}</Badge>
                        )}
                      </td>
                      <td className="num">{food.nutrientCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          }
        </QueryView>
      </Card>
    </div>
  )
}
