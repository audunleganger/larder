import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useFoodDetail, useNutrients, useUnits } from '../api/queries'
import type { FoodDetail, FoodInput, NutrientDto, UnitDto } from '../api/types.gen'
import { DecimalInput } from '../components/DecimalInput'
import { Badge, Card, ConfirmButton, Empty, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { formatDate } from '../lib/dates'
import { currentLocale, formatNumber, formatQuantity, parseDecimal, toInputValue } from '../lib/format'

interface LinkRow {
  key: number
  unitId: number | ''
  equalsAmount: string
  equalsUnitId: number | ''
}

let rowKey = 0

function unitLabel(unit: UnitDto, t: (key: 'common.archived') => string) {
  return unit.archived ? `${unit.name} (${t('common.archived')})` : unit.name
}

function FoodEditor({ detail, units, nutrients }: { detail: FoodDetail; units: UnitDto[]; nutrients: NutrientDto[] }) {
  const { t } = useTranslation()
  const food = detail.food
  const [name, setName] = useState(food.name)
  const [refAmount, setRefAmount] = useState(toInputValue(food.refAmount))
  const [refUnitId, setRefUnitId] = useState<number | ''>(food.refUnitId ?? '')
  const [notes, setNotes] = useState(food.notes ?? '')
  const [values, setValues] = useState<Record<number, string>>(() =>
    Object.fromEntries(food.nutrients.map((v) => [v.nutrientId, toInputValue(v.amount)])),
  )
  const [links, setLinks] = useState<LinkRow[]>(() =>
    food.units.map((l) => ({ key: rowKey++, unitId: l.unitId, equalsAmount: toInputValue(l.equalsAmount), equalsUnitId: l.equalsUnitId ?? '' })),
  )
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const save = useApiMutation((input: FoodInput) => endpoints.updateFood(food.id, input))

  // Archived nutrients are only shown when this food has a value for them.
  const shownNutrients = nutrients.filter((n) => !n.archived || values[n.id] !== undefined)
  const refUnitName = units.find((u) => u.id === refUnitId)?.name ?? ''

  function build(): FoodInput | null {
    const amount = parseDecimal(refAmount)
    if (amount !== null && (Number.isNaN(amount) || amount <= 0)) {
      setFormError(t('foods.errors.refAmount'))
      return null
    }
    if ((amount === null) !== (refUnitId === '')) {
      setFormError(t('foods.errors.refBoth'))
      return null
    }
    const nutrientValues = []
    for (const [id, text] of Object.entries(values)) {
      const value = parseDecimal(text)
      if (value === null) continue
      if (Number.isNaN(value) || value < 0) {
        setFormError(t('foods.errors.nutrient', { name: nutrients.find((n) => n.id === Number(id))?.name ?? id }))
        return null
      }
      nutrientValues.push({ nutrientId: Number(id), amount: value })
    }
    const unitLinks = []
    for (const row of links) {
      if (row.unitId === '') continue
      const equals = parseDecimal(row.equalsAmount)
      if (equals !== null && (Number.isNaN(equals) || equals <= 0)) {
        setFormError(t('foods.errors.unitSize'))
        return null
      }
      const sized = equals !== null && row.equalsUnitId !== ''
      unitLinks.push({ unitId: row.unitId, equalsAmount: sized ? equals : null, equalsUnitId: sized ? Number(row.equalsUnitId) : null })
    }
    return {
      name,
      refAmount: amount,
      refUnitId: refUnitId === '' ? null : refUnitId,
      notes: notes.trim() || null,
      nutrients: nutrientValues,
      units: unitLinks,
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    setSaved(false)
    const input = build()
    if (!input) return
    await save.mutateAsync(input)
    setSaved(true)
  }

  const updateLink = (key: number, patch: Partial<LinkRow>) =>
    setLinks((rows) => rows.map((row) => (row.key === key ? { ...row, ...patch } : row)))

  return (
    <form onSubmit={(e) => void submit(e).catch(() => undefined)} onChange={() => setSaved(false)}>
      <Card title={t('foods.details')}>
        <div className="form-row">
          <Field label={t('common.name')} className="grow">
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
          </Field>
        </div>
        <div className="form-row">
          <Field label={t('foods.refAmount')} className="qty-wide">
            <DecimalInput value={refAmount} onChange={setRefAmount} placeholder="100" />
          </Field>
          <Field label={t('foods.refUnit')} className="grow">
            <select className="input" value={refUnitId} onChange={(e) => setRefUnitId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">{t('foods.notSet')}</option>
              {units.map((u) => (
                <option key={u.id} value={u.id}>
                  {unitLabel(u, t)}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <p className="field-hint">{t('foods.refHint')}</p>
        <Field label={t('foods.notes')}>
          <textarea className="input" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </Card>

      <Card title={refUnitName && parseDecimal(refAmount) ? t('foods.nutrientsPer', { amount: refAmount, unit: refUnitName }) : t('foods.nutrients')}>
        {!refUnitName && <p className="warning-text">{t('foods.setReferenceFirst')}</p>}
        <div className="nutrient-grid">
          {shownNutrients.map((n) => (
            <label key={n.id} className={`nutrient-input ${n.parentId ? 'child' : ''}`}>
              <span>{n.name}</span>
              <DecimalInput
                value={values[n.id] ?? ''}
                onChange={(text) => setValues((v) => ({ ...v, [n.id]: text }))}
                aria-label={n.name}
              />
              <span className="muted">{n.measureUnit}</span>
            </label>
          ))}
        </div>
      </Card>

      <Card title={t('foods.units')}>
        <p className="field-hint">{t('foods.unitsHint')}</p>
        {links.length > 0 && (
          <div className="link-rows">
            {links.map((row) => (
              <div key={row.key} className="link-row">
                <span className="muted">1</span>
                <select className="input" aria-label={t('foods.unit')} value={row.unitId} onChange={(e) => updateLink(row.key, { unitId: e.target.value ? Number(e.target.value) : '' })}>
                  <option value="">{t('foods.chooseUnit')}</option>
                  {units.map((u) => (
                    <option key={u.id} value={u.id}>
                      {unitLabel(u, t)}
                    </option>
                  ))}
                </select>
                <span className="muted">=</span>
                <DecimalInput className="qty-input" value={row.equalsAmount} onChange={(text) => updateLink(row.key, { equalsAmount: text })} aria-label={t('foods.equalsAmount')} placeholder="?" />
                <select className="input" aria-label={t('foods.equalsUnit')} value={row.equalsUnitId} onChange={(e) => updateLink(row.key, { equalsUnitId: e.target.value ? Number(e.target.value) : '' })}>
                  <option value="">{t('foods.chooseUnit')}</option>
                  {units
                    .filter((u) => u.id !== row.unitId)
                    .map((u) => (
                      <option key={u.id} value={u.id}>
                        {unitLabel(u, t)}
                      </option>
                    ))}
                </select>
                <button type="button" className="btn btn-ghost btn-small" onClick={() => setLinks((rows) => rows.filter((r) => r.key !== row.key))}>
                  {t('common.remove')}
                </button>
              </div>
            ))}
          </div>
        )}
        <button type="button" className="btn" onClick={() => setLinks((rows) => [...rows, { key: rowKey++, unitId: '', equalsAmount: '', equalsUnitId: refUnitId }])}>
          {t('foods.addUnit')}
        </button>

        <h3>{t('foods.usableUnits')}</h3>
        {detail.usableUnits.length === 0 ? (
          <Empty>{t('foods.noUsableUnits')}</Empty>
        ) : (
          <ul className="usable-units">
            {detail.usableUnits.map((u) => {
              const ref = units.find((x) => x.id === food.refUnitId)?.name
              return (
                <li key={u.unitId}>
                  <Link to={`/units/${u.unitId}`}>{u.name}</Link>{' '}
                  {u.amountInRefUnit !== null ? (
                    <span className="muted">
                      = {formatNumber(u.amountInRefUnit, 3)} {ref}
                    </span>
                  ) : (
                    <Badge tone="warning">⚠ {t('foods.noSize')}</Badge>
                  )}{' '}
                  {!u.explicit && <Badge tone="info">{t('foods.automatic')}</Badge>}
                </li>
              )
            })}
          </ul>
        )}
        <p className="field-hint">{t('foods.usableHint')}</p>
      </Card>

      <div className="save-bar">
        {formError && <p className="error-text">{formError}</p>}
        <ErrorText error={save.error} />
        {saved && <span className="saved">✓ {t('common.saved')}</span>}
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {t('common.save')}
        </button>
      </div>
    </form>
  )
}

function EntriesByDate({ detail }: { detail: FoodDetail }) {
  const { t } = useTranslation()
  if (detail.entries.length === 0) return <Empty>{t('foods.noEntries')}</Empty>
  const byDate = new Map<string, FoodDetail['entries']>()
  for (const entry of detail.entries) byDate.set(entry.date, [...(byDate.get(entry.date) ?? []), entry])
  return (
    <ul className="date-list">
      {[...byDate.entries()].map(([date, entries]) => (
        <li key={date}>
          <Link to={`/day/${date}`}>{formatDate(date, currentLocale(), 'short')}</Link>
          <span className="date-list-items">
            {entries.map((e) => (
              <span key={e.entryId} className="chip" title={`${formatQuantity(e.quantity)} × ${e.unitName} · ${e.time}`}>
                {detail.food.name} · {formatQuantity(e.quantity)} {e.unitName}
              </span>
            ))}
          </span>
        </li>
      ))}
    </ul>
  )
}

export function FoodDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const id = Number(useParams().id)
  const detail = useFoodDetail(id)
  const units = useUnits(true)
  const nutrients = useNutrients(true)
  const archive = useApiMutation((archived: boolean) => endpoints.archiveFood(id, archived))
  const remove = useApiMutation(() => endpoints.deleteFood(id))

  return (
    <div className="page">
      <QueryView query={detail}>
        {(data) => (
          <>
            <PageHeader
              title={
                <>
                  {data.food.name} {data.food.archived && <Badge>{t('common.archived')}</Badge>}
                </>
              }
              subtitle={<Link to="/foods">← {t('foods.title')}</Link>}
            />
            {units.data && nutrients.data && (
              <FoodEditor key={JSON.stringify(data.food)} detail={data} units={units.data} nutrients={nutrients.data} />
            )}
            <Card title={t('foods.entries')}>
              <EntriesByDate detail={data} />
            </Card>
            <Card title={t('common.manage')}>
              <div className="form-actions">
                <button type="button" className="btn" onClick={() => archive.mutate(!data.food.archived)}>
                  {data.food.archived ? t('common.unarchive') : t('common.archive')}
                </button>
                <ConfirmButton onConfirm={() => remove.mutate(undefined, { onSuccess: () => navigate('/foods') })}>{t('common.delete')}</ConfirmButton>
              </div>
              <p className="field-hint">{t('foods.archiveHint')}</p>
              <ErrorText error={archive.error ?? remove.error} />
            </Card>
          </>
        )}
      </QueryView>
    </div>
  )
}
