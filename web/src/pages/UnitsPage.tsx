import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useUnits } from '../api/queries'
import type { UnitDto, UnitInput, UnitKind } from '../api/types.gen'
import { BASE_UNIT, KINDS, unitSize } from '../lib/units'
import { DecimalInput } from '../components/DecimalInput'
import { Badge, Card, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { parseDecimal, toInputValue } from '../lib/format'
import { draftTranslations, effectiveSuffix, toTranslations } from '../lib/names'
import { PluralSuffixField, TranslationFields } from '../components/TranslationFields'

/** Name, kind and size fields shared by the create and edit forms (U-1, U-2). */
export function UnitFields({ initial, submitLabel, onSubmit, error, busy }: { initial?: UnitDto; submitLabel: string; onSubmit: (input: UnitInput) => Promise<unknown>; error: unknown; busy: boolean }) {
  const { t, i18n } = useTranslation()
  const [name, setName] = useState(initial?.name ?? '')
  const [kind, setKind] = useState<UnitKind>(initial?.kind ?? 'custom')
  const [factor, setFactor] = useState(toInputValue(initial?.baseFactor))
  // A new unit's plural ending follows the default for its name until the user edits it (U-8).
  const [suffix, setSuffix] = useState(initial?.pluralSuffix ?? '')
  const [suffixEdited, setSuffixEdited] = useState(initial !== undefined)
  const [translations, setTranslations] = useState(() => draftTranslations(initial?.translations))
  const [formError, setFormError] = useState<string | null>(null)
  const pluralSuffix = effectiveSuffix(name, suffix, suffixEdited, kind, i18n.language)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const value = parseDecimal(factor)
    if (kind !== 'custom' && (value === null || Number.isNaN(value) || value <= 0)) {
      setFormError(t('units.errors.size', { base: BASE_UNIT[kind] }))
      return
    }
    await onSubmit({ name, kind, baseFactor: kind === 'custom' ? null : value, pluralSuffix, translations: toTranslations(translations, kind) })
    if (!initial) {
      setName('')
      setFactor('')
      setSuffix('')
      setSuffixEdited(false)
      setTranslations(draftTranslations([]))
    }
  }

  return (
    <form onSubmit={(e) => void submit(e).catch(() => undefined)}>
      <div className="form-row">
        <Field label={t('common.name')} className="grow">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <PluralSuffixField
          name={name}
          value={pluralSuffix}
          onChange={(value) => {
            setSuffix(value)
            setSuffixEdited(true)
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

export function UnitsPage() {
  const { t } = useTranslation()
  const [showArchived, setShowArchived] = useState(false)
  const units = useUnits(showArchived)
  const create = useApiMutation(endpoints.createUnit)

  return (
    <div className="page">
      <PageHeader title={t('units.title')} subtitle={t('units.subtitle')} />
      <Card title={t('units.new')}>
        <UnitFields submitLabel={t('common.create')} onSubmit={(input) => create.mutateAsync(input)} error={create.error} busy={create.isPending} />
      </Card>
      <Card
        title={t('units.all')}
        actions={
          <label className="checkbox">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> {t('common.showArchived')}
          </label>
        }
      >
        <QueryView query={units}>
          {(data) => (
            <table className="table">
              <thead>
                <tr>
                  <th>{t('common.name')}</th>
                  <th>{t('units.kind')}</th>
                  <th className="num">{t('units.sizeColumn')}</th>
                </tr>
              </thead>
              <tbody>
                {data.map((unit) => (
                  <tr key={unit.id}>
                    <td>
                      <Link to={`/units/${unit.id}`}>{unit.displayName}</Link> {unit.archived && <Badge>{t('common.archived')}</Badge>}
                    </td>
                    <td>{t(`units.kinds.${unit.kind}`)}</td>
                    <td className="num">{unitSize(unit) || <span className="muted">{t('units.perFood')}</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </QueryView>
      </Card>
    </div>
  )
}
