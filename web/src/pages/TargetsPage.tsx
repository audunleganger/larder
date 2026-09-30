import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useNutrients, useTargets } from '../api/queries'
import type { NutrientDto, TargetDto, TargetInput } from '../api/types.gen'
import { DecimalInput } from '../components/DecimalInput'
import { Card, ConfirmButton, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { formatDate, todayIso } from '../lib/dates'
import { currentLocale, formatNumber, parseDecimal, toInputValue } from '../lib/format'

function rangeText(target: Pick<TargetDto, 'min' | 'max'>, nutrient: NutrientDto, none: string): string {
  const f = (v: number) => formatNumber(v, nutrient.displayPrecision)
  if (target.min !== null && target.max !== null) return `${f(target.min)}–${f(target.max)} ${nutrient.measureUnit}`
  if (target.min !== null) return `≥ ${f(target.min)} ${nutrient.measureUnit}`
  if (target.max !== null) return `≤ ${f(target.max)} ${nutrient.measureUnit}`
  return none
}

function TargetRow({ nutrient, current }: { nutrient: NutrientDto; current: TargetDto | undefined }) {
  const { t } = useTranslation()
  const [min, setMin] = useState(toInputValue(current?.min))
  const [max, setMax] = useState(toInputValue(current?.max))
  const [from, setFrom] = useState(todayIso())
  const [formError, setFormError] = useState<string | null>(null)
  const save = useApiMutation((input: TargetInput) => endpoints.setTarget(input))

  function submit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const minValue = parseDecimal(min)
    const maxValue = parseDecimal(max)
    if ([minValue, maxValue].some((v) => v !== null && (Number.isNaN(v) || v < 0))) return setFormError(t('targets.errors.number'))
    if (minValue !== null && maxValue !== null && minValue > maxValue) return setFormError(t('targets.errors.order'))
    save.mutate({ nutrientId: nutrient.id, min: minValue, max: maxValue, effectiveFrom: from })
  }

  return (
    <tr>
      <th scope="row" className={nutrient.parentId ? 'child' : ''}>
        {nutrient.name}
        <div className="muted small">{current ? rangeText(current, nutrient, t('targets.none')) : t('targets.none')}</div>
      </th>
      <td colSpan={4}>
        <form className="target-form" onSubmit={submit}>
          <label>
            <span className="sr-only">{t('targets.min')}</span>
            <DecimalInput value={min} onChange={setMin} placeholder={t('targets.min')} aria-label={`${nutrient.name} ${t('targets.min')}`} />
          </label>
          <label>
            <span className="sr-only">{t('targets.max')}</span>
            <DecimalInput value={max} onChange={setMax} placeholder={t('targets.max')} aria-label={`${nutrient.name} ${t('targets.max')}`} />
          </label>
          <span className="muted">{nutrient.measureUnit}</span>
          <label className="target-from">
            <span className="muted small">{t('targets.from')}</span>
            <input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} required aria-label={`${nutrient.name} ${t('targets.from')}`} />
          </label>
          <button type="submit" className="btn btn-small btn-primary" disabled={save.isPending}>
            {t('common.save')}
          </button>
          {save.isSuccess && <span className="saved">✓</span>}
        </form>
        {formError && <p className="error-text">{formError}</p>}
        <ErrorText error={save.error} />
      </td>
    </tr>
  )
}

function Version({ target, nutrient }: { target: TargetDto; nutrient: NutrientDto }) {
  const { t } = useTranslation()
  const remove = useApiMutation(() => endpoints.deleteTarget(target.id))
  return (
    <li>
      <span>
        <strong>{nutrient.name}</strong> {rangeText(target, nutrient, t('targets.cleared'))}{' '}
        <span className="muted">
          {t('targets.fromDate', { date: formatDate(target.effectiveFrom, currentLocale(), 'short') })}
          {target.effectiveFrom > todayIso() && ` · ${t('targets.upcoming')}`}
        </span>
      </span>
      <ConfirmButton className="btn btn-ghost btn-small" onConfirm={() => remove.mutate(undefined)}>
        {t('common.delete')}
      </ConfirmButton>
    </li>
  )
}

export function TargetsPage() {
  const { t } = useTranslation()
  const nutrients = useNutrients(true)
  const targets = useTargets()
  const today = todayIso()

  return (
    <div className="page">
      <PageHeader title={t('targets.title')} subtitle={t('targets.subtitle')} />
      <Card title={t('targets.current')}>
        <QueryView query={targets}>
          {(list) =>
            nutrients.data ? (
              <div className="table-scroll">
                <table className="table targets-table">
                  <tbody>
                    {nutrients.data
                      .filter((n) => !n.archived)
                      .map((n) => {
                        const current = list.filter((v) => v.nutrientId === n.id && v.effectiveFrom <= today).at(-1)
                        return <TargetRow key={`${n.id}-${current?.id ?? 'none'}-${current?.min}-${current?.max}`} nutrient={n} current={current} />
                      })}
                  </tbody>
                </table>
              </div>
            ) : null
          }
        </QueryView>
        <p className="field-hint">{t('targets.hint')}</p>
      </Card>
      <Card title={t('targets.history')}>
        <QueryView query={targets}>
          {(list) => {
            const byId = new Map((nutrients.data ?? []).map((n) => [n.id, n]))
            const versions = [...list].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom))
            return versions.length === 0 ? (
              <Empty>{t('targets.noHistory')}</Empty>
            ) : (
              <ul className="version-list">
                {versions.map((v) => {
                  const nutrient = byId.get(v.nutrientId)
                  return nutrient ? <Version key={v.id} target={v} nutrient={nutrient} /> : null
                })}
              </ul>
            )
          }}
        </QueryView>
      </Card>
    </div>
  )
}
