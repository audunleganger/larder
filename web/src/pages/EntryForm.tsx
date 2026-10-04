import { useQuery } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useFoodDetail, useNutrients, useUnits } from '../api/queries'
import type { CompositeDetail, EntryInput, EntryView } from '../api/types.gen'
import { DecimalInput } from '../components/DecimalInput'
import { FoodPicker, type PickedFood } from '../components/FoodPicker'
import { ErrorText, Field } from '../components/ui'
import { nowTime } from '../lib/dates'
import { formatAmount, formatQuantity, parseDecimal, toInputValue } from '../lib/format'
import { defaultUnitId } from '../lib/units'
import { unitLabel } from '../lib/names'
import { useDebounced } from '../lib/useDebounced'

/** The share of a composite food's whole yield that [quantity] of a unit is; null if unknown. */
function splitShare(composite: CompositeDetail, amountInRefUnit: number | null | undefined, quantity: number): number | null {
  if (!composite.yieldAmount || amountInRefUnit === null || amountInRefUnit === undefined) return null
  return (quantity * amountInRefUnit) / composite.yieldAmount
}

/** What logging a composite food will add (F-10). */
function CompositeNote({ composite, share }: { composite: CompositeDetail; share: number | null }) {
  const { t } = useTranslation()
  if (composite.logAsWhole) return <p className="field-hint">{t('composite.willLogWhole')}</p>
  return (
    <div className="composite-note">
      <p className="field-hint">{t('composite.willLogItems')}</p>
      {share !== null && (
        <ul className="chips">
          {composite.ingredients.map((ingredient, index) => {
            const quantity = ingredient.quantity * share
            return (
              <li key={index} className="chip">
                {ingredient.foodName} · {formatQuantity(quantity)} {unitLabel(ingredient.unitName, ingredient.unitPluralSuffix, quantity)}
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

/** Registers or edits an entry (E-1), with a live nutrient preview (E-2). */
export function EntryForm({ date, entry, onDone }: { date: string; entry: EntryView | null; onDone: () => void }) {
  const { t } = useTranslation()
  const [food, setFood] = useState<PickedFood | null>(entry ? { id: entry.foodId, name: entry.foodName } : null)
  const [unitId, setUnitId] = useState<number | null>(entry?.unitId ?? null)
  const [quantity, setQuantity] = useState(entry ? toInputValue(entry.quantity) : '1')
  // Until the user types a quantity, a composite food measured by weight defaults to all of it.
  const [quantityEdited, setQuantityEdited] = useState(entry !== null)
  const [entryDate, setEntryDate] = useState(entry?.date ?? date)
  const [time, setTime] = useState(entry?.time ?? nowTime())
  const [note, setNote] = useState(entry?.note ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [pickerKey, setPickerKey] = useState(0)
  const formRef = useRef<HTMLFormElement>(null)

  const detail = useFoodDetail(food?.id ?? null)
  const units = useUnits(true)
  const nutrients = useNutrients(false)
  const composite = food !== null && detail.data?.food.id === food.id ? detail.data.composite : null
  // A composite food defaults to the unit of how much it makes (F-10).
  const effectiveUnitId = unitId ?? (entry ? null : composite?.yieldUnitId) ?? defaultUnitId(detail.data)
  const wholeByWeight = composite?.yieldAutomatic && composite.yieldAmount !== null && effectiveUnitId === composite.yieldUnitId
  const shownQuantity = !quantityEdited && wholeByWeight ? toInputValue(Math.round(composite.yieldAmount!)) : quantity
  const qty = parseDecimal(shownQuantity)
  const validQty = qty !== null && !Number.isNaN(qty) && qty > 0 ? qty : null
  const debouncedQty = useDebounced(validQty, 250)

  const preview = useQuery({
    queryKey: ['preview', food?.id, effectiveUnitId, debouncedQty],
    queryFn: () => endpoints.previewEntry({ foodId: food!.id, unitId: effectiveUnitId!, quantity: debouncedQty! }),
    enabled: food !== null && effectiveUnitId !== null && debouncedQty !== null,
    placeholderData: (previous) => previous,
  })

  const save = useApiMutation((input: EntryInput) => (entry ? endpoints.updateEntry(entry.id, input) : endpoints.createEntry(input)))

  const usable = detail.data?.usableUnits ?? []
  const usableIds = new Set(usable.map((u) => u.unitId))
  // Hidden units only when the entry already uses one.
  const otherUnits = (units.data ?? []).filter((u) => !usableIds.has(u.id) && (!u.hidden || u.id === effectiveUnitId))
  const selectedUsable = usable.find((u) => u.unitId === effectiveUnitId)
  const unitUnresolved = food !== null && effectiveUnitId !== null && (!selectedUsable || selectedUsable.amountInRefUnit === null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    if (!food) return setFormError(t('entry.errors.food'))
    if (effectiveUnitId === null) return setFormError(t('entry.errors.unit'))
    if (validQty === null) return setFormError(t('entry.errors.quantity'))
    await save.mutateAsync({ foodId: food.id, unitId: effectiveUnitId, quantity: validQty, date: entryDate, time, note: note.trim() || null })
    if (entry) {
      onDone()
    } else {
      setFood(null)
      setUnitId(null)
      setQuantity('1')
      setQuantityEdited(false)
      setNote('')
      setTime(nowTime())
      setPickerKey((k) => k + 1)
    }
  }

  const shown = (nutrients.data ?? []).slice(0, 6)

  return (
    <form ref={formRef} className="entry-form" onSubmit={(e) => void submit(e).catch(() => undefined)}>
      <div className="form-row">
        <Field label={t('entry.food')} className="grow">
          <FoodPicker
            key={pickerKey}
            value={food}
            forLogging
            autoFocus={!entry && pickerKey > 0}
            onChange={(picked) => {
              setFood(picked)
              setUnitId(null)
              if (!entry) setQuantityEdited(false)
            }}
          />
        </Field>
      </div>
      <div className="form-row">
        <Field label={t('entry.quantity')} className="qty">
          <DecimalInput
            value={shownQuantity}
            onChange={(value) => {
              setQuantity(value)
              setQuantityEdited(true)
            }}
            aria-label={t('entry.quantity')}
          />
        </Field>
        <Field label={t('entry.unit')} className="grow">
          <select
            className="input"
            value={effectiveUnitId ?? ''}
            disabled={!food}
            onChange={(e) => setUnitId(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="" disabled>
              {food ? t('entry.chooseUnit') : '—'}
            </option>
            {usable.length > 0 && (
              <optgroup label={t('entry.unitsForFood')}>
                {usable.map((u) => (
                  <option key={u.unitId} value={u.unitId}>
                    {unitLabel(u.name, u.pluralSuffix, validQty)}
                    {u.amountInRefUnit === null ? ' ⚠' : ''}
                  </option>
                ))}
              </optgroup>
            )}
            {otherUnits.length > 0 && (
              <optgroup label={t('entry.otherUnits')}>
                {otherUnits.map((u) => (
                  <option key={u.id} value={u.id}>
                    {unitLabel(u.displayName, u.displayPluralSuffix, validQty)}
                  </option>
                ))}
              </optgroup>
            )}
          </select>
        </Field>
      </div>
      <div className="form-row">
        <Field label={t('entry.date')}>
          <input className="input" type="date" value={entryDate} onChange={(e) => setEntryDate(e.target.value)} required />
        </Field>
        <Field label={t('entry.time')}>
          <input className="input" type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
        </Field>
        <Field label={t('entry.note')} className="grow">
          <input className="input" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} />
        </Field>
      </div>

      {composite && !entry && validQty !== null && <CompositeNote composite={composite} share={splitShare(composite, selectedUsable?.amountInRefUnit, validQty)} />}

      {food && validQty !== null && effectiveUnitId !== null && (
        <div className="preview" aria-live="polite">
          {unitUnresolved || preview.data?.unresolved ? (
            <p className="warning-text">
              <span aria-hidden="true">⚠ </span>
              {t('entry.unresolved')} <Link to={`/foods/${food.id}`}>{t('entry.fixOnFoodPage')}</Link>
            </p>
          ) : (
            <ul className="chips">
              {shown.map((n) => {
                const amount = preview.data?.nutrients.find((a) => a.nutrientId === n.id)?.amount
                return (
                  <li key={n.id} className="chip">
                    <span className="chip-label">{n.displayName}</span> {formatAmount(amount, n)}
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}

      {formError && <p className="error-text">{formError}</p>}
      <ErrorText error={save.error} />
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {entry ? t('common.save') : t('entry.add')}
        </button>
        {entry && (
          <button type="button" className="btn btn-ghost" onClick={onDone}>
            {t('common.cancel')}
          </button>
        )}
      </div>
    </form>
  )
}
