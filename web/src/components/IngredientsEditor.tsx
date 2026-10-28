import { useQueryClient } from '@tanstack/react-query'
import type { Dispatch, SetStateAction } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useFoodAndContainers, useFoodDetail } from '../api/queries'
import type { CompositeDetail, UnitDto } from '../api/types.gen'
import { formatNumber, parseDecimal } from '../lib/format'
import { defaultUnitId } from '../lib/units'
import { nextRowKey, type CompositeDraft, type IngredientRow } from '../lib/composite'
import { DecimalInput } from './DecimalInput'
import { FoodPicker } from './FoodPicker'
import { FoodUnitSelect, UnitSelect } from './UnitSelect'

/** One ingredient: food, amount and unit (the units that food can be measured in come first). */
function IngredientRowEditor({ row, units, saved, exclude, onPatch, onRemove }: { row: IngredientRow; units: UnitDto[]; saved?: CompositeDetail['ingredients'][number]; exclude?: ReadonlySet<number>; onPatch: (patch: Partial<IngredientRow>) => void; onRemove: () => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const detail = useFoodDetail(row.food?.id ?? null)
  const qty = parseDecimal(row.quantity)
  // The saved calculation only describes this row while it's unchanged.
  const unresolved = saved && saved.foodId === row.food?.id && saved.unitId === row.unitId && saved.unresolved

  return (
    <div className="ingredient-row">
      <div className="ingredient-food">
        <FoodPicker
          value={row.food}
          ariaLabel={t('composite.ingredient')}
          exclude={exclude}
          onChange={(food) => {
            onPatch({ food, unitId: '' })
            if (!food) return
            // Preselect the unit the food is usually measured in, like the log form does.
            void queryClient
              .fetchQuery({ queryKey: ['foods', 'detail', food.id], queryFn: () => endpoints.foodDetail(food.id) })
              .then((d) => onPatch({ unitId: defaultUnitId(d) ?? '' }))
          }}
        />
      </div>
      <DecimalInput className="qty-input" value={row.quantity} onChange={(quantity) => onPatch({ quantity })} aria-label={t('composite.amount')} />
      <FoodUnitSelect
        className="input unit-select"
        aria-label={t('composite.unit')}
        usable={detail.data?.usableUnits ?? []}
        units={units}
        value={row.unitId}
        onChange={(unitId) => onPatch({ unitId })}
        placeholder={t('foods.chooseUnit')}
        quantity={qty}
        disabled={!row.food}
      />
      <button type="button" className="btn btn-ghost btn-small" onClick={onRemove}>
        {t('common.remove')}
      </button>
      {unresolved && row.food && (
        <p className="ingredient-warning">
          <span aria-hidden="true">⚠ </span>
          {t('composite.unresolved')} <Link to={`/foods/${row.food.id}`}>{t('composite.openFood', { name: row.food.name })}</Link>
        </p>
      )}
    </div>
  )
}

/**
 * What a composite food is made of (F-10): its ingredients, how much they make, and whether logging
 * it adds each ingredient as its own entry or one entry.
 */
export function IngredientsEditor({ foodId, draft, onChange, units, saved }: { foodId: number; draft: CompositeDraft; onChange: Dispatch<SetStateAction<CompositeDraft>>; units: UnitDto[]; saved: CompositeDetail | null }) {
  const { t } = useTranslation()
  // The food itself and the foods that contain it can't be its ingredients.
  const containers = useFoodAndContainers(foodId)
  // Functional updates: a row's unit is set asynchronously after its food is picked.
  const update = (patch: Partial<CompositeDraft>) => onChange((d) => ({ ...d, ...patch }))
  const patchRow = (key: number, patch: Partial<IngredientRow>) =>
    onChange((d) => ({ ...d, rows: d.rows.map((row) => (row.key === key ? { ...row, ...patch } : row)) }))
  const totalGrams = saved?.totalGrams
  const yieldUnits = units.filter((u) => !u.hidden || u.id === draft.yieldUnitId)
  const yieldQty = parseDecimal(draft.yieldAmount)

  return (
    <div className="ingredients-editor">
      {draft.rows.length === 0 ? (
        <p className="field-hint">{t('composite.intro')}</p>
      ) : (
        <div className="ingredient-rows">
          {draft.rows.map((row, index) => (
            <IngredientRowEditor
              key={row.key}
              row={row}
              units={units}
              saved={saved?.ingredients[index]}
              exclude={containers.data}
              onPatch={(patch) => patchRow(row.key, patch)}
              onRemove={() => onChange((d) => ({ ...d, rows: d.rows.filter((r) => r.key !== row.key) }))}
            />
          ))}
        </div>
      )}
      <button type="button" className="btn" onClick={() => onChange((d) => ({ ...d, rows: [...d.rows, { key: nextRowKey(), food: null, quantity: '1', unitId: '' }] }))}>
        {t('composite.addIngredient')}
      </button>

      {draft.rows.length > 0 && (
        <>
          <fieldset className="choice-group">
            <legend>{t('composite.makes')}</legend>
            <label className="radio">
              <input type="radio" name="yield" checked={draft.yieldMode === 'auto'} onChange={() => update({ yieldMode: 'auto' })} />
              <span>
                {t('composite.totalWeight')}{' '}
                <span className="muted">
                  {totalGrams !== null && totalGrams !== undefined ? `(${formatNumber(totalGrams, 0)} g)` : saved ? `(${t('composite.noTotalWeight')})` : ''}
                </span>
              </span>
            </label>
            <label className="radio yield-set">
              <input type="radio" name="yield" checked={draft.yieldMode === 'set'} onChange={() => update({ yieldMode: 'set' })} />
              <DecimalInput
                className="qty-input"
                value={draft.yieldAmount}
                aria-label={t('composite.yieldAmount')}
                onFocus={() => update({ yieldMode: 'set' })}
                onChange={(yieldAmount) => update({ yieldMode: 'set', yieldAmount })}
              />
              <UnitSelect
                className="input unit-select"
                aria-label={t('composite.yieldUnit')}
                units={yieldUnits}
                value={draft.yieldUnitId}
                onChange={(yieldUnitId) => update({ yieldMode: 'set', yieldUnitId })}
                placeholder={t('foods.chooseUnit')}
                quantity={yieldQty}
              />
            </label>
            <p className="field-hint">{t('composite.makesHint')}</p>
          </fieldset>

          <fieldset className="choice-group">
            <legend>{t('composite.logging')}</legend>
            <label className="radio">
              <input type="radio" name="logAs" checked={!draft.logAsWhole} onChange={() => update({ logAsWhole: false })} />
              <span>
                {t('composite.logItems')} <span className="muted">— {t('composite.logItemsHint')}</span>
              </span>
            </label>
            <label className="radio">
              <input type="radio" name="logAs" checked={draft.logAsWhole} onChange={() => update({ logAsWhole: true })} />
              <span>
                {t('composite.logWhole')} <span className="muted">— {t('composite.logWholeHint')}</span>
              </span>
            </label>
          </fieldset>
        </>
      )}
    </div>
  )
}
