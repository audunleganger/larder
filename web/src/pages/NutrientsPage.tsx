import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useNutrients } from '../api/queries'
import type { NutrientDto, NutrientInput } from '../api/types.gen'
import { Card, Empty, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { MadeBy, TakenHiddenNotice } from '../components/Shared'
import { takenHidden } from '../lib/shared'
import { flattenGroups, groupNutrients, moved, type NutrientGroup } from '../lib/nutrients'
import { draftTranslations, toTranslations } from '../lib/names'
import { TranslationFields } from '../components/TranslationFields'

/** Fields shared by the create and edit nutrient forms (N-1, N-6). */
export function NutrientFields({ initial, all, submitLabel, onSubmit, error, busy }: { initial?: NutrientDto; all: NutrientDto[]; submitLabel: string; onSubmit: (input: NutrientInput) => Promise<unknown>; error: unknown; busy: boolean }) {
  const { t } = useTranslation()
  const [name, setName] = useState(initial?.name ?? '')
  const [measureUnit, setMeasureUnit] = useState(initial?.measureUnit ?? 'g')
  const [precision, setPrecision] = useState(initial?.displayPrecision ?? 1)
  const [parentId, setParentId] = useState<number | ''>(initial?.parentId ?? '')
  const [translations, setTranslations] = useState(() => draftTranslations(initial?.translations))
  const hasChildren = initial !== undefined && all.some((n) => n.parentId === initial.id)
  const parents = all.filter((n) => n.parentId === null && n.id !== initial?.id && (!n.hidden || n.id === initial?.parentId))

  async function submit(event: FormEvent) {
    event.preventDefault()
    await onSubmit({ name, measureUnit, displayPrecision: precision, parentId: parentId === '' ? null : parentId, translations: toTranslations(translations) })
    if (!initial) {
      setName('')
      setParentId('')
      setTranslations(draftTranslations([]))
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
                {n.displayName}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <TranslationFields draft={translations} onChange={setTranslations} mainName={name} />
      <ErrorText error={error} />
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {submitLabel}
      </button>
    </form>
  )
}

/**
 * Nutrients are shared by everyone on the server. The page lists the ones the user shows, in their
 * own order, and the hidden ones (theirs, and other users') on request.
 */
export function NutrientsPage() {
  const { t } = useTranslation()
  const [showHidden, setShowHidden] = useState(false)
  // Remounts the create form, emptying it, once a hidden item is shown instead of created.
  const [formKey, setFormKey] = useState(0)
  const nutrients = useNutrients(true)
  const create = useApiMutation(endpoints.createNutrient)
  const reorder = useApiMutation(endpoints.reorderNutrients)
  const toggle = useApiMutation((n: NutrientDto) => endpoints.hideNutrient(n.id, !n.hidden))
  const taken = takenHidden(create.error, nutrients.data)
  const hidden = nutrients.data?.filter((n) => n.hidden) ?? []

  // Reorders the shown groups (or one group's sub-nutrients).
  function send(groups: NutrientGroup<NutrientDto>[]) {
    reorder.mutate(flattenGroups(groups).map((n) => n.id))
  }

  return (
    <div className="page">
      <PageHeader title={t('nutrients.title')} subtitle={t('nutrients.subtitle')} />
      <Card title={t('nutrients.new')}>
        {nutrients.data && (
          <NutrientFields key={formKey} all={nutrients.data} submitLabel={t('common.create')} onSubmit={(input) => create.mutateAsync(input)} error={taken ? null : create.error} busy={create.isPending} />
        )}
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
        title={t('nutrients.order')}
        actions={
          <button type="button" className="btn btn-small" aria-expanded={showHidden} onClick={() => setShowHidden((v) => !v)}>
            {t('nutrients.showHidden', { count: hidden.length })}
          </button>
        }
      >
        <p className="field-hint">{t('nutrients.orderHint')}</p>
        <QueryView query={nutrients}>
          {(data) => {
            const groups = groupNutrients(data.filter((n) => !n.hidden))
            const row = (n: NutrientDto, up: (() => void) | null, down: (() => void) | null) => (
              <div className="order-row">
                <span className="order-name">
                  {n.parentId !== null && <span className="muted of-which">{t('nutrients.ofWhich')} </span>}
                  <Link to={`/nutrients/${n.id}`}>{n.displayName}</Link> <span className="muted">({n.measureUnit})</span>
                </span>
                <span className="order-buttons">
                  <button type="button" className="btn btn-ghost btn-small" disabled={toggle.isPending} onClick={() => toggle.mutate(n)}>
                    {t('common.hide')}
                  </button>
                  <button type="button" className="btn btn-icon btn-small" aria-label={t('nutrients.moveUp', { name: n.displayName })} disabled={!up || reorder.isPending} onClick={up ?? undefined}>
                    ↑
                  </button>
                  <button type="button" className="btn btn-icon btn-small" aria-label={t('nutrients.moveDown', { name: n.displayName })} disabled={!down || reorder.isPending} onClick={down ?? undefined}>
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
                      <ol className="order-subs" aria-label={t('nutrients.subsOf', { name: group.main.displayName })}>
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
        <ErrorText error={reorder.error ?? toggle.error} />
      </Card>
      {showHidden && (
        <Card title={t('nutrients.hiddenTitle')}>
          <p className="field-hint">{t('nutrients.hiddenHint')}</p>
          {hidden.length === 0 ? (
            <Empty>{t('nutrients.noHidden')}</Empty>
          ) : (
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('nutrients.measureUnit')}</th>
                    <th>{t('common.createdBy')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {hidden.map((n) => (
                    <tr key={n.id}>
                      <td>
                        <Link to={`/nutrients/${n.id}`}>{n.displayName}</Link>
                      </td>
                      <td>{n.measureUnit}</td>
                      <td>
                        <MadeBy item={n} />
                      </td>
                      <td className="num">
                        <button type="button" className="btn btn-ghost btn-small" disabled={toggle.isPending} onClick={() => toggle.mutate(n)}>
                          {t('common.show')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </div>
  )
}
