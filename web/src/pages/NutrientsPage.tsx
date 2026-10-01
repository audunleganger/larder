import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useNutrients } from '../api/queries'
import type { NutrientDto, NutrientInput } from '../api/types.gen'
import { Badge, Card, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { flattenGroups, groupNutrients, moved, type NutrientGroup } from '../lib/nutrients'

/** Fields shared by the create and edit nutrient forms (N-1, N-6). */
export function NutrientFields({ initial, all, submitLabel, onSubmit, error, busy }: { initial?: NutrientDto; all: NutrientDto[]; submitLabel: string; onSubmit: (input: NutrientInput) => Promise<unknown>; error: unknown; busy: boolean }) {
  const { t } = useTranslation()
  const [name, setName] = useState(initial?.name ?? '')
  const [measureUnit, setMeasureUnit] = useState(initial?.measureUnit ?? 'g')
  const [precision, setPrecision] = useState(initial?.displayPrecision ?? 1)
  const [parentId, setParentId] = useState<number | ''>(initial?.parentId ?? '')
  const hasChildren = initial !== undefined && all.some((n) => n.parentId === initial.id)
  const parents = all.filter((n) => n.parentId === null && n.id !== initial?.id && !n.archived)

  async function submit(event: FormEvent) {
    event.preventDefault()
    await onSubmit({ name, measureUnit, displayPrecision: precision, parentId: parentId === '' ? null : parentId })
    if (!initial) {
      setName('')
      setParentId('')
    }
  }

  return (
    <form onSubmit={(e) => void submit(e).catch(() => undefined)}>
      <div className="form-row">
        <Field label={t('common.name')} className="grow">
          <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <Field label={t('nutrients.measureUnit')} hint={t('nutrients.measureUnitHint')}>
          <input className="input" list="measure-units" value={measureUnit} onChange={(e) => setMeasureUnit(e.target.value)} required maxLength={20} />
          <datalist id="measure-units">
            {['kcal', 'kJ', 'g', 'mg', 'µg', 'IU'].map((u) => (
              <option key={u} value={u} />
            ))}
          </datalist>
        </Field>
        <Field label={t('nutrients.precision')}>
          <select className="input" value={precision} onChange={(e) => setPrecision(Number(e.target.value))}>
            {[0, 1, 2, 3].map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </Field>
        <Field label={t('nutrients.parent')}>
          <select className="input" value={parentId} disabled={hasChildren} onChange={(e) => setParentId(e.target.value ? Number(e.target.value) : '')}>
            <option value="">{t('nutrients.noParent')}</option>
            {parents.map((n) => (
              <option key={n.id} value={n.id}>
                {n.name}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <ErrorText error={error} />
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {submitLabel}
      </button>
    </form>
  )
}

export function NutrientsPage() {
  const { t } = useTranslation()
  const [showArchived, setShowArchived] = useState(false)
  const nutrients = useNutrients(true)
  const create = useApiMutation(endpoints.createNutrient)
  const reorder = useApiMutation(endpoints.reorderNutrients)

  // Reorders the visible groups (or one group's sub-nutrients); hidden archived nutrients keep their place.
  function send(groups: NutrientGroup<NutrientDto>[]) {
    reorder.mutate(flattenGroups(groups).map((n) => n.id))
  }

  return (
    <div className="page">
      <PageHeader title={t('nutrients.title')} subtitle={t('nutrients.subtitle')} />
      <Card title={t('nutrients.new')}>
        {nutrients.data && (
          <NutrientFields all={nutrients.data} submitLabel={t('common.create')} onSubmit={(input) => create.mutateAsync(input)} error={create.error} busy={create.isPending} />
        )}
      </Card>
      <Card
        title={t('nutrients.order')}
        actions={
          <label className="checkbox">
            <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> {t('common.showArchived')}
          </label>
        }
      >
        <p className="field-hint">{t('nutrients.orderHint')}</p>
        <QueryView query={nutrients}>
          {(data) => {
            const groups = groupNutrients(showArchived ? data : data.filter((n) => !n.archived))
            const row = (n: NutrientDto, up: (() => void) | null, down: (() => void) | null) => (
              <div className="order-row">
                <span className="order-name">
                  {n.parentId !== null && <span className="muted of-which">{t('nutrients.ofWhich')} </span>}
                  <Link to={`/nutrients/${n.id}`}>{n.name}</Link> <span className="muted">({n.measureUnit})</span>{' '}
                  {n.archived && <Badge>{t('common.archived')}</Badge>}
                </span>
                <span className="order-buttons">
                  <button type="button" className="btn btn-icon btn-small" aria-label={t('nutrients.moveUp', { name: n.name })} disabled={!up || reorder.isPending} onClick={up ?? undefined}>
                    ↑
                  </button>
                  <button type="button" className="btn btn-icon btn-small" aria-label={t('nutrients.moveDown', { name: n.name })} disabled={!down || reorder.isPending} onClick={down ?? undefined}>
                    ↓
                  </button>
                </span>
              </div>
            )
            const moveGroup = (index: number, delta: number) => {
              const next = moved(groups, index, delta)
              return next ? () => send(next) : null
            }
            const moveSub = (index: number, subIndex: number, delta: number) => {
              const subs = moved(groups[index].subs, subIndex, delta)
              return subs ? () => send(groups.map((g, i) => (i === index ? { ...g, subs } : g))) : null
            }
            return (
              <ol className="order-list">
                {groups.map((group, index) => (
                  <li key={group.main.id} className={`order-group ${group.subs.length > 0 ? 'has-subs' : ''}`}>
                    {row(group.main, moveGroup(index, -1), moveGroup(index, 1))}
                    {group.subs.length > 0 && (
                      <ol className="order-subs" aria-label={t('nutrients.subsOf', { name: group.main.name })}>
                        {group.subs.map((sub, subIndex) => (
                          <li key={sub.id}>{row(sub, moveSub(index, subIndex, -1), moveSub(index, subIndex, 1))}</li>
                        ))}
                      </ol>
                    )}
                  </li>
                ))}
              </ol>
            )
          }}
        </QueryView>
        <ErrorText error={reorder.error} />
      </Card>
    </div>
  )
}
