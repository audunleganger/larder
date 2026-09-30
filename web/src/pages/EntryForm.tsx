import { useQuery } from '@tanstack/react-query'
import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useFoodDetail, useNutrients, useUnits } from '../api/queries'
import type { EntryInput, EntryView, FoodDetail } from '../api/types.gen'
import { DecimalInput } from '../components/DecimalInput'
import { FoodPicker, type PickedFood } from '../components/FoodPicker'
import { ErrorText, Field } from '../components/ui'
import { nowTime } from '../lib/dates'
import { formatAmount, parseDecimal, toInputValue } from '../lib/format'
import { useDebounced } from '../lib/useDebounced'

/** Unit to preselect for a food: the last one used, else the first resolvable explicit unit. */
function defaultUnitId(detail: FoodDetail | undefined): number | null {
  if (!detail) return null
  const last = detail.entries[0]?.unitId
  if (last !== undefined) return last
  const usable = detail.usableUnits
  return (usable.find((u) => u.explicit && u.amountInRefUnit !== null) ?? usable.find((u) => u.amountInRefUnit !== null) ?? usable[0])?.unitId ?? null
}

/** Registers or edits an entry (E-1), with a live nutrient preview (E-2). */
export function EntryForm({ date, entry, onDone }: { date: string; entry: EntryView | null; onDone: () => void }) {
  const { t } = useTranslation()
  const [food, setFood] = useState<PickedFood | null>(entry ? { id: entry.foodId, name: entry.foodName } : null)
  const [unitId, setUnitId] = useState<number | null>(entry?.unitId ?? null)
  const [quantity, setQuantity] = useState(entry ? toInputValue(entry.quantity) : '1')
  const [entryDate, setEntryDate] = useState(entry?.date ?? date)
  const [time, setTime] = useState(entry?.time ?? nowTime())
  const [note, setNote] = useState(entry?.note ?? '')
  const [formError, setFormError] = useState<string | null>(null)
  const [pickerKey, setPickerKey] = useState(0)
  const formRef = useRef<HTMLFormElement>(null)

  const detail = useFoodDetail(food?.id ?? null)
  const units = useUnits(false)
  const nutrients = useNutrients(false)
  const effectiveUnitId = unitId ?? defaultUnitId(detail.data)
  const qty = parseDecimal(quantity)
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
  const otherUnits = (units.data ?? []).filter((u) => !usableIds.has(u.id))
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
            autoFocus={!entry && pickerKey > 0}
            onChange={(picked) => {
              setFood(picked)
              setUnitId(null)
            }}
          />
        </Field>
      </div>
      <div className="form-row">
        <Field label={t('entry.quantity')} className="qty">
          <DecimalInput value={quantity} onChange={setQuantity} aria-label={t('entry.quantity')} />
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
                    {u.name}
                    {u.amountInRefUnit === null ? ' ⚠' : ''}
                  </option>
                ))}
              </optgroup>
            )}
            {otherUnits.length > 0 && (
              <optgroup label={t('entry.otherUnits')}>
                {otherUnits.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name}
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
                    <span className="chip-label">{n.name}</span> {formatAmount(amount, n)}
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
