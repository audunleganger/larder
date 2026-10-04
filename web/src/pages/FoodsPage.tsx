import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useFoodRefDefault, useFoods, useNutrients, useTags, useUnits } from '../api/queries'
import type { NutrientDto } from '../api/types.gen'
import { DecimalInput } from '../components/DecimalInput'
import { FoodThumb } from '../components/FoodPhoto'
import { unitLabel } from '../lib/names'
import { Badge, Card, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { currentLocale, formatNumber, parseDecimal, toInputValue } from '../lib/format'
import { tagsByName } from '../lib/tags'
import { TagChip } from '../components/TagChip'
import { useDebounced } from '../lib/useDebounced'
import { filterParams, isFiltered, matchesFilter, NO_FILTER, parseFilter, type FoodFilter } from '../lib/foodFilters'

const HIDDEN_COLUMNS_KEY = 'cc.foodColumns.hidden'

/**
 * The nutrient columns the reader has turned off in the food table (F-17), kept in this browser. New
 * nutrients start as shown.
 */
function useHiddenColumns(): [Set<number>, (id: number) => void] {
  const [hidden, setHidden] = useState<Set<number>>(() => {
    try {
      const stored: unknown = JSON.parse(localStorage.getItem(HIDDEN_COLUMNS_KEY) ?? '[]')
      return new Set(Array.isArray(stored) ? stored.filter((id): id is number => typeof id === 'number') : [])
    } catch {
      return new Set()
    }
  })
  function toggle(id: number) {
    const next = new Set(hidden)
    if (!next.delete(id)) next.add(id)
    setHidden(next)
    try {
      localStorage.setItem(HIDDEN_COLUMNS_KEY, JSON.stringify([...next]))
    } catch {
      // Not saved; the choice still applies until the page is left.
    }
  }
  return [hidden, toggle]
}

/** A nutrient value in the food table: per the food's reference amount, or a red dash without one. */
function NutrientCell({ value, nutrient }: { value: number | undefined; nutrient: NutrientDto }) {
  const { t } = useTranslation()
  return (
    <td className="num">
      {value === undefined ? (
        <span className="missing-value" title={t('foods.noValue')} aria-label={t('foods.noValue')}>
          –
        </span>
      ) : (
        formatNumber(value, nutrient.displayPrecision)
      )}
    </td>
  )
}

export function FoodsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [showArchived, setShowArchived] = useState(false)
  // The filter is mirrored in the page address, so it's still there after going to a food and back.
  const [searchParams, setSearchParams] = useSearchParams()
  const [filter, setFilterState] = useState(() => parseFilter(searchParams))
  const setFilter = (patch: Partial<FoodFilter>) => {
    const next = { ...filter, ...patch }
    setFilterState(next)
    setSearchParams(filterParams(next), { replace: true })
  }
  const [hiddenColumns, toggleColumn] = useHiddenColumns()
  const [newName, setNewName] = useState('')
  // Null until the user edits it: then the remembered default (F-12) is shown.
  const [newRef, setNewRef] = useState<{ amount: string; unitId: number | '' } | null>(null)
  const refDefault = useFoodRefDefault()
  const ref = newRef ?? { amount: toInputValue(refDefault.data?.refAmount ?? null), unitId: refDefault.data?.refUnitId ?? ('' as const) }
  const foods = useFoods(useDebounced(search.trim(), 150), showArchived)
  const units = useUnits(true)
  const tags = useTags(true)
  const nutrients = useNutrients()
  const shownNutrients = (nutrients.data ?? []).filter((n) => !n.hidden)
  const columns = shownNutrients.filter((n) => !hiddenColumns.has(n.id))
  const unitName = (id: number | null, amount: number) => {
    const unit = units.data?.find((u) => u.id === id)
    return unit ? unitLabel(unit.displayName, unit.displayPlural, amount) : ''
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
      <PageHeader
        title={t('foods.title')}
        subtitle={t('foods.subtitle')}
        actions={
          <Link to="/tags" className="btn btn-small">
            {t('tags.title')}
          </Link>
        }
      />
      <Card title={t('foods.new')}>
        <form className="inline-form" onSubmit={(e) => void submit(e).catch(() => undefined)}>
          <input className="input grow" placeholder={t('foods.namePlaceholder')} aria-label={t('common.name')} value={newName} onChange={(e) => setNewName(e.target.value)} required />
          <span className="muted">{t('foods.per')}</span>
          <DecimalInput className="qty-input" aria-label={t('foods.refAmount')} value={ref.amount} onChange={(amount) => setNewRef({ ...ref, amount })} />
          <select className="input unit-select" aria-label={t('foods.refUnit')} value={ref.unitId} onChange={(e) => setNewRef({ ...ref, unitId: e.target.value ? Number(e.target.value) : '' })}>
            <option value="">{t('foods.notSet')}</option>
            {(units.data ?? [])
              .filter((u) => !u.hidden || u.id === ref.unitId)
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
        <div className="filters food-filters">
          <label className="inline-field">
            <span className="muted small">{t('foods.filters.tag')}</span>
            <select className="input" aria-label={t('foods.filters.tag')} value={filter.tagId ?? ''} onChange={(e) => setFilter({ tagId: e.target.value ? Number(e.target.value) : null })}>
              <option value="">{t('foods.filters.anyTag')}</option>
              {(tags.data ?? []).map((tag) => (
                <option key={tag.id} value={tag.id}>
                  {tag.archived ? t('foods.filters.archivedTag', { name: tag.displayName }) : tag.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="inline-field">
            <span className="muted small">{t('foods.filters.nutrient')}</span>
            <span className="inline-controls">
              <select className="input" aria-label={t('foods.filters.nutrient')} value={filter.nutrientId ?? ''} onChange={(e) => setFilter({ nutrientId: e.target.value ? Number(e.target.value) : null })}>
                <option value="">{t('foods.filters.anyNutrient')}</option>
                {shownNutrients.map((n) => (
                  <option key={n.id} value={n.id}>
                    {n.displayName}
                  </option>
                ))}
              </select>
              {filter.nutrientId !== null && (
                <select className="input" aria-label={t('foods.filters.valueLabel')} value={filter.hasValue ? 'has' : 'lacks'} onChange={(e) => setFilter({ hasValue: e.target.value === 'has' })}>
                  <option value="has">{t('foods.filters.hasValue')}</option>
                  <option value="lacks">{t('foods.filters.lacksValue')}</option>
                </select>
              )}
            </span>
          </label>
          <label className="checkbox">
            <input type="checkbox" checked={filter.composite} onChange={(e) => setFilter({ composite: e.target.checked })} /> {t('foods.filters.composite')}
          </label>
          <label className="checkbox">
            <input type="checkbox" checked={filter.ingredientOnly} onChange={(e) => setFilter({ ingredientOnly: e.target.checked })} /> {t('foods.filters.ingredientOnly')}
          </label>
          {isFiltered(filter) && (
            <button type="button" className="btn btn-small" onClick={() => setFilter(NO_FILTER)}>
              {t('foods.filters.clear')}
            </button>
          )}
        </div>
        {shownNutrients.length > 0 && (
          <div className="column-toggles" role="group" aria-label={t('foods.columns')}>
            <span className="muted small">{t('foods.columns')}</span>
            {shownNutrients.map((n) => (
              <button key={n.id} type="button" className="toggle-chip" aria-pressed={!hiddenColumns.has(n.id)} onClick={() => toggleColumn(n.id)}>
                {n.displayName}
              </button>
            ))}
          </div>
        )}
        <QueryView query={foods}>
          {(data) => {
            const shown = data.filter((food) => matchesFilter(food, filter))
            if (data.length === 0) return <Empty>{search ? t('foods.noMatches') : t('foods.empty')}</Empty>
            if (shown.length === 0) return <Empty>{t('foods.filters.noMatches')}</Empty>
            return (
              <>
                {isFiltered(filter) && <p className="muted small">{t('foods.filters.showing', { shown: shown.length, total: data.length })}</p>}
                <div className="table-scroll">
                  <table className="table food-table">
                    <thead>
                      <tr>
                        <th>{t('common.name')}</th>
                        <th>{t('foods.reference')}</th>
                        {columns.length === 0 && <th className="num">{t('foods.nutrientCount')}</th>}
                        {columns.map((n) => (
                          <th key={n.id} className="num">
                            {n.displayName} <span className="measure-unit">{n.measureUnit}</span>
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {shown.map((food) => (
                        <tr key={food.id}>
                          <td>
                            <Link to={`/foods/${food.id}`} className="food-link">
                              <FoodThumb foodId={food.id} version={food.imageVersion} name={food.name} />
                              {food.name}
                            </Link> {food.composite && <Badge tone="info">{t('composite.badge')}</Badge>} {food.ingredientOnly && <Badge tone="info">{t('foods.ingredientOnly')}</Badge>} {food.archived && <Badge>{t('common.archived')}</Badge>}
                            {food.tagIds.length > 0 && (
                              <ul className="chips food-tags">
                                {tagsByName(food.tagIds, tags.data ?? [], currentLocale()).map((tag) => (
                                  <TagChip key={tag.id} tag={tag} />
                                ))}
                              </ul>
                            )}
                          </td>
                          <td className="nowrap">
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
                          {columns.length === 0 && <td className="num">{food.nutrientCount}</td>}
                          {columns.map((n) => (
                            <NutrientCell key={n.id} value={food.nutrients[n.id]} nutrient={n} />
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )
          }}
        </QueryView>
      </Card>
    </div>
  )
}
