import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useDay, useNutrients } from '../api/queries'
import type { DayView, EntryView, NutrientDto } from '../api/types.gen'
import { TargetBar, TargetStatusLabel } from '../components/TargetBar'
import { targetText } from '../lib/targets'
import { Badge, Card, ConfirmButton, Empty, ErrorText, PageHeader, QueryView } from '../components/ui'
import { addDays, formatDate, isValidIsoDate, todayIso } from '../lib/dates'
import { currentLocale, formatAmount, formatQuantity } from '../lib/format'
import { EntryForm } from './EntryForm'

function DayNavigation({ date }: { date: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const today = todayIso()
  return (
    <div className="day-nav">
      <Link className="btn btn-icon" to={`/day/${addDays(date, -1)}`} aria-label={t('day.previous')} title={t('day.previous')}>
        ‹
      </Link>
      <input
        className="input"
        type="date"
        aria-label={t('day.pickDate')}
        value={date}
        onChange={(e) => isValidIsoDate(e.target.value) && navigate(`/day/${e.target.value}`)}
      />
      <Link className="btn btn-icon" to={`/day/${addDays(date, 1)}`} aria-label={t('day.next')} title={t('day.next')}>
        ›
      </Link>
      {date !== today && (
        <Link className="btn" to="/">
          {t('day.today')}
        </Link>
      )}
    </div>
  )
}

function EntryItem({ entry, nutrients, onEdit }: { entry: EntryView; nutrients: Map<number, NutrientDto>; onEdit: () => void }) {
  const { t } = useTranslation()
  const remove = useApiMutation(() => endpoints.deleteEntry(entry.id))
  const amounts = entry.nutrients.map((a) => ({ nutrient: nutrients.get(a.nutrientId), amount: a.amount })).filter((a) => a.nutrient)
  const [primary, ...rest] = amounts
  return (
    <li className={`entry ${entry.unresolved ? 'entry-unresolved' : ''}`}>
      <span className="entry-time">{entry.time}</span>
      <div className="entry-main">
        <div className="entry-title">
          <Link to={`/foods/${entry.foodId}`}>{entry.foodName}</Link>
          <span className="muted">
            {formatQuantity(entry.quantity)} {entry.unitName}
          </span>
          {entry.unresolved && <Badge tone="warning">⚠ {t('day.incomplete')}</Badge>}
        </div>
        {entry.note && <p className="entry-note">{entry.note}</p>}
        {rest.some((a) => a.amount !== null) && (
          <p className="entry-nutrients">
            {rest.filter((a) => a.amount !== null).map(({ nutrient, amount }) => (
              <span key={nutrient!.id}>
                {nutrient!.name} {formatAmount(amount, nutrient!)}
              </span>
            ))}
          </p>
        )}
        <ErrorText error={remove.error} />
      </div>
      <div className="entry-primary">{primary?.nutrient ? formatAmount(primary.amount, primary.nutrient) : ''}</div>
      <div className="entry-actions">
        <button type="button" className="btn btn-ghost btn-small" onClick={onEdit}>
          {t('common.edit')}
        </button>
        <ConfirmButton className="btn btn-ghost btn-small" onConfirm={() => remove.mutate(undefined)}>
          {t('common.delete')}
        </ConfirmButton>
      </div>
    </li>
  )
}

function Totals({ day, nutrients }: { day: DayView; nutrients: Map<number, NutrientDto> }) {
  const { t } = useTranslation()
  const unresolved = day.entries.filter((e) => e.unresolved).length
  return (
    <>
      {unresolved > 0 && (
        <p className="warning-text">
          <span aria-hidden="true">⚠ </span>
          {t('day.unresolvedEntries', { count: unresolved })}
        </p>
      )}
      <ul className="totals">
        {day.totals.map((total) => {
          const nutrient = nutrients.get(total.nutrientId)
          if (!nutrient) return null
          // With no entry contributing a value, "0" would be misleading (C-3).
          const noData = day.entries.length > 0 && total.missingCount === day.entries.length
          return (
            <li key={total.nutrientId} className={`total ${nutrient.parentId ? 'total-child' : ''}`}>
              <div className="total-row">
                <Link to={`/nutrients/${nutrient.id}`} className="total-name">
                  {nutrient.name}
                </Link>
                <span className="total-amount">{noData ? '—' : formatAmount(total.amount, nutrient)}</span>
              </div>
              {total.status !== 'none' && (
                <>
                  <TargetBar total={total} nutrient={nutrient} />
                  <div className="total-row small">
                    <TargetStatusLabel status={total.status} />
                    <span className="muted">
                      {t('day.target')}: {targetText(total, nutrient)}
                    </span>
                  </div>
                </>
              )}
              {/* Unresolved entries are explained above; only mention other missing values. */}
              {total.missingCount > unresolved && (
                <p className="total-missing">{noData ? t('day.noData') : t('day.missingData', { count: total.missingCount })}</p>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}

export function DayPage() {
  const { t } = useTranslation()
  const params = useParams()
  const date = params.date && isValidIsoDate(params.date) ? params.date : todayIso()
  const day = useDay(date)
  const nutrientList = useNutrients(true)
  const nutrients = new Map((nutrientList.data ?? []).map((n) => [n.id, n]))
  const [editing, setEditing] = useState<EntryView | null>(null)
  const isToday = date === todayIso()

  return (
    <div className="page">
      <PageHeader
        title={formatDate(date, currentLocale())}
        subtitle={isToday ? t('day.today') : undefined}
        actions={<DayNavigation date={date} />}
      />
      <div className="day-grid">
        <div className="day-main">
          <Card title={editing ? t('entry.editTitle') : t('entry.addTitle')}>
            <EntryForm key={editing ? `edit-${editing.id}` : `new-${date}`} date={date} entry={editing} onDone={() => setEditing(null)} />
          </Card>
          <Card title={t('day.entries')}>
            <QueryView query={day}>
              {(data) =>
                data.entries.length === 0 ? (
                  <Empty>{t('day.noEntries')}</Empty>
                ) : (
                  <ul className="entries">
                    {data.entries.map((entry) => (
                      <EntryItem
                        key={entry.id}
                        entry={entry}
                        nutrients={nutrients}
                        onEdit={() => {
                          setEditing(entry)
                          window.scrollTo({ top: 0, behavior: 'smooth' })
                        }}
                      />
                    ))}
                  </ul>
                )
              }
            </QueryView>
          </Card>
        </div>
        <aside className="day-side">
          <Card title={t('day.totals')}>
            <QueryView query={day}>{(data) => <Totals day={data} nutrients={nutrients} />}</QueryView>
            <p className="small">
              <Link to="/targets">{t('day.editTargets')}</Link>
            </p>
          </Card>
        </aside>
      </div>
    </div>
  )
}
