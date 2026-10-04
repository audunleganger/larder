import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useUnits } from '../api/queries'
import type { UnitDto, UnitInput, UnitKind } from '../api/types.gen'
import { BASE_UNIT, KINDS, unitSize } from '../lib/units'
import { DecimalInput } from '../components/DecimalInput'
import { Card, Empty, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { MadeBy, TakenHiddenNotice } from '../components/Shared'
import { takenHidden } from '../lib/shared'
import { moved } from '../lib/nutrients'
import { parseDecimal, toInputValue } from '../lib/format'
import { draftTranslations, effectivePlural, toTranslations } from '../lib/names'
import { PluralField, TranslationFields } from '../components/TranslationFields'

/** Name, kind and size fields shared by the create and edit forms (U-1, U-2). */
export function UnitFields({ initial, submitLabel, onSubmit, error, busy }: { initial?: UnitDto; submitLabel: string; onSubmit: (input: UnitInput) => Promise<unknown>; error: unknown; busy: boolean }) {
  const { t, i18n } = useTranslation()
  const [name, setName] = useState(initial?.name ?? '')
  const [kind, setKind] = useState<UnitKind>(initial?.kind ?? 'custom')
  const [factor, setFactor] = useState(toInputValue(initial?.baseFactor))
  // A new unit's plural follows the default for its name until the user edits it (U-8).
  const [pluralDraft, setPluralDraft] = useState(initial?.plural ?? '')
  const [pluralEdited, setPluralEdited] = useState(initial !== undefined)
  const [translations, setTranslations] = useState(() => draftTranslations(initial?.translations))
  const [formError, setFormError] = useState<string | null>(null)
  const plural = effectivePlural(name, pluralDraft, pluralEdited, kind, i18n.language)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const value = parseDecimal(factor)
    if (kind !== 'custom' && (value === null || Number.isNaN(value) || value <= 0)) {
      setFormError(t('units.errors.size', { base: BASE_UNIT[kind] }))
      return
    }
    await onSubmit({ name, kind, baseFactor: kind === 'custom' ? null : value, plural: plural.trim(), translations: toTranslations(translations, kind) })
    if (!initial) {
      setName('')
      setFactor('')
      setPluralDraft('')
      setPluralEdited(false)
      setTranslations(draftTranslations([]))
    }
  }

  return (
    <form onSubmit={(e) => void submit(e).catch(() => undefined)}>
      <div className="form-row">
        <Field label={t('common.name')} className="grow">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <PluralField
          name={name}
          value={plural}
          onChange={(value) => {
            setPluralDraft(value)
            setPluralEdited(true)
          }}
        />
        <Field label={t('units.kind')}>
          <select className="input" value={kind} onChange={(e) => setKind(e.target.value as UnitKind)}>
            {KINDS.map((k) => (
              <option key={k} value={k}>
                {t(`units.kinds.${k}`)}
              </option>
            ))}
          </select>
        </Field>
        {kind !== 'custom' && (
          <Field label={t('units.size', { base: BASE_UNIT[kind] })} className="qty">
            <DecimalInput value={factor} onChange={setFactor} />
          </Field>
        )}
      </div>
      <p className="field-hint">{t(`units.kindHints.${kind}`)}</p>
      <TranslationFields draft={translations} onChange={setTranslations} mainName={name} kind={kind} />
      {formError && <p className="error-text">{formError}</p>}
      <ErrorText error={error} />
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {submitLabel}
      </button>
    </form>
  )
}

/** One unit in the units list, with a button to hide or show it, and to move it up or down ([onMove]). */
function UnitRow({ unit, onToggle, busy, onMove }: { unit: UnitDto; onToggle: () => void; busy: boolean; onMove?: { up: (() => void) | null; down: (() => void) | null } }) {
  const { t } = useTranslation()
  return (
    <tr>
      <td>
        <Link to={`/units/${unit.id}`}>{unit.displayName}</Link>
      </td>
      <td>{t(`units.kinds.${unit.kind}`)}</td>
      <td className="num">{unitSize(unit) || <span className="muted">{t('units.perFood')}</span>}</td>
      <td>
        <MadeBy item={unit} />
      </td>
      <td className="num">
        <span className="order-buttons">
          <button type="button" className="btn btn-ghost btn-small" disabled={busy} onClick={onToggle}>
            {unit.hidden ? t('common.show') : t('common.hide')}
          </button>
          {onMove && (
            <>
              <button type="button" className="btn btn-icon btn-small" aria-label={t('units.moveUp', { name: unit.displayName })} disabled={!onMove.up || busy} onClick={onMove.up ?? undefined}>
                ↑
              </button>
              <button type="button" className="btn btn-icon btn-small" aria-label={t('units.moveDown', { name: unit.displayName })} disabled={!onMove.down || busy} onClick={onMove.down ?? undefined}>
                ↓
              </button>
            </>
          )}
        </span>
      </td>
    </tr>
  )
}

/** [onReorder]: lets the user move the units, sending the new order. */
function UnitTable({ units, onToggle, busy, onReorder }: { units: UnitDto[]; onToggle: (unit: UnitDto) => void; busy: boolean; onReorder?: (ids: number[]) => void }) {
  const { t } = useTranslation()
  return (
    <div className="table-scroll">
      <table className="table">
        <thead>
          <tr>
            <th>{t('common.name')}</th>
            <th>{t('units.kind')}</th>
            <th className="num">{t('units.sizeColumn')}</th>
            <th>{t('common.createdBy')}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {units.map((unit, index) => {
            const move = (delta: number) => {
              const next = moved(units, index, delta)
              return next && onReorder ? () => onReorder(next.map((u) => u.id)) : null
            }
            return <UnitRow key={unit.id} unit={unit} busy={busy} onToggle={() => onToggle(unit)} onMove={onReorder && { up: move(-1), down: move(1) }} />
          })}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Units are shared by everyone on the server. The page lists the ones the user shows, and the
 * hidden ones (theirs, and other users') on request.
 */
export function UnitsPage() {
  const { t } = useTranslation()
  const [showHidden, setShowHidden] = useState(false)
  // Remounts the create form, emptying it, once a hidden item is shown instead of created.
  const [formKey, setFormKey] = useState(0)
  const units = useUnits(true)
  const create = useApiMutation(endpoints.createUnit)
  const toggle = useApiMutation((unit: UnitDto) => endpoints.hideUnit(unit.id, !unit.hidden))
  const reorder = useApiMutation(endpoints.reorderUnits)
  const reset = useApiMutation(endpoints.resetUnitOrder)
  const taken = takenHidden(create.error, units.data)
  const hidden = units.data?.filter((u) => u.hidden) ?? []
  const customOrder = units.data?.some((u) => u.sortOrder !== null) ?? false
  const busy = toggle.isPending || reorder.isPending || reset.isPending

  return (
    <div className="page">
      <PageHeader title={t('units.title')} subtitle={t('units.subtitle')} />
      <Card title={t('units.new')}>
        <UnitFields key={formKey} submitLabel={t('common.create')} onSubmit={(input) => create.mutateAsync(input)} error={taken ? null : create.error} busy={create.isPending} />
        {taken && <TakenHiddenNotice item={taken} onShow={() =>
              toggle.mutate(taken, {
                onSuccess: () => {
                  create.reset()
                  setFormKey((k) => k + 1)
                },
              })
            } />}
      </Card>
      <Card
        title={t('units.all')}
        actions={
          <>
            {customOrder && (
              <button type="button" className="btn btn-small" disabled={busy} onClick={() => reset.mutate()}>
                {t('units.resetOrder')}
              </button>
            )}
            <button type="button" className="btn btn-small" aria-expanded={showHidden} onClick={() => setShowHidden((v) => !v)}>
              {t('units.showHidden', { count: hidden.length })}
            </button>
          </>
        }
      >
        <p className="field-hint">{t('units.orderHint')}</p>
        <QueryView query={units}>
          {(data) => <UnitTable units={data.filter((u) => !u.hidden)} onToggle={(unit) => toggle.mutate(unit)} busy={busy} onReorder={(ids) => reorder.mutate(ids)} />}
        </QueryView>
        <ErrorText error={toggle.error ?? reorder.error ?? reset.error} />
      </Card>
      {showHidden && (
        <Card title={t('units.hiddenTitle')}>
          <p className="field-hint">{t('units.hiddenHint')}</p>
          {hidden.length === 0 ? <Empty>{t('units.noHidden')}</Empty> : <UnitTable units={hidden} onToggle={(unit) => toggle.mutate(unit)} busy={toggle.isPending} />}
        </Card>
      )}
    </div>
  )
}
