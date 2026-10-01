import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Bar, BarChart, CartesianGrid, ComposedChart, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useHistory, useNutrients } from '../api/queries'
import type { HistoryView, NutrientDto, NutrientTotal } from '../api/types.gen'
import { TargetStatusLabel } from '../components/TargetBar'
import { Card, Empty, PageHeader, QueryView } from '../components/ui'
import { addDays, daysBetween, formatDate, isValidIsoDate, rollingAverage, todayIso } from '../lib/dates'
import { currentLocale, formatAmount, formatNumber } from '../lib/format'
import { groupNutrients } from '../lib/nutrients'

const PRESETS = [7, 30, 90, 365] as const

interface Point {
  date: string
  logged: boolean
  amount: number | null
  average: number | null
  min: number | null
  max: number | null
  total: NutrientTotal | null
}

/** Daily values; null when nothing was logged, or when no entry has a value for the nutrient (H-4, C-3). */
function seriesFor(view: HistoryView, nutrientId: number): Point[] {
  const values = view.days.map((day) => {
    const total = day.totals.find((t) => t.nutrientId === nutrientId)
    return day.entryCount > 0 && total && total.missingCount < day.entryCount ? total.amount : null
  })
  const averages = rollingAverage(values, 7)
  return view.days.map((day, i) => {
    const total = day.totals.find((t) => t.nutrientId === nutrientId) ?? null
    return {
      date: day.date,
      logged: day.entryCount > 0,
      amount: values[i],
      average: averages[i],
      min: total?.targetMin ?? null,
      max: total?.targetMax ?? null,
      total: day.entryCount > 0 ? total : null,
    }
  })
}

function tickFormatter(days: number) {
  const locale = currentLocale()
  const format = new Intl.DateTimeFormat(locale, days <= 14 ? { weekday: 'short', day: 'numeric', timeZone: 'UTC' } : { day: 'numeric', month: 'short', timeZone: 'UTC' })
  return (iso: string) => format.format(new Date(`${iso}T00:00:00Z`))
}

interface TooltipProps {
  active?: boolean
  payload?: { payload: Point }[]
  nutrient: NutrientDto
}

function ChartTooltip({ active, payload, nutrient }: TooltipProps) {
  const { t } = useTranslation()
  const point = payload?.[0]?.payload
  if (!active || !point) return null
  return (
    <div className="chart-tooltip">
      <strong>{formatDate(point.date, currentLocale(), 'weekday')}</strong>
      {point.amount === null ? (
        <div className="muted">{point.logged ? t('history.noValue') : t('history.notLogged')}</div>
      ) : (
        <>
          <div>
            <span className="swatch swatch-bar" /> {t('history.daily')}: {formatAmount(point.amount, nutrient)}
          </div>
          {point.average !== null && (
            <div>
              <span className="swatch swatch-line" /> {t('history.average7')}: {formatAmount(point.average, nutrient)}
            </div>
          )}
          {point.total && point.total.status !== 'none' && <TargetStatusLabel status={point.total.status} />}
          {point.total && point.total.missingCount > 0 && <div className="muted">{t('day.missingData', { count: point.total.missingCount })}</div>}
        </>
      )}
    </div>
  )
}

/** A short horizontal mark at the day's target level; reads as a dashed line across days, and stays visible for single days. */
function TargetTick(props: { cx?: number; cy?: number; value?: number | null; index?: number; halfWidth: number }) {
  const { cx, cy, value, index, halfWidth } = props
  if (cx === undefined || cy === undefined || value === null || value === undefined || Number.isNaN(cy)) return <g key={index} />
  return <line key={index} x1={cx - halfWidth} x2={cx + halfWidth} y1={cy} y2={cy} stroke="var(--target-line)" strokeWidth={2} />
}

function MainChart({ view, nutrient, showAverage }: { view: HistoryView; nutrient: NutrientDto; showAverage: boolean }) {
  const { t } = useTranslation()
  const data = seriesFor(view, nutrient.id)
  const days = view.days.length
  const hasTarget = data.some((p) => p.min !== null || p.max !== null)
  const halfWidth = Math.max(2, Math.min(12, 260 / days))
  return (
    <>
      <ul className="chart-legend">
        <li>
          <span className="swatch swatch-bar" /> {t('history.daily')} ({nutrient.measureUnit})
        </li>
        {showAverage && (
          <li>
            <span className="swatch swatch-line" /> {t('history.average7')}
          </li>
        )}
        {hasTarget && (
          <li>
            <span className="swatch swatch-target" /> {t('history.targetRange')}
          </li>
        )}
      </ul>
      <div className="chart" role="img" aria-label={t('history.chartLabel', { nutrient: nutrient.displayName })}>
        <ResponsiveContainer width="100%" height={300}>
          <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
            <CartesianGrid vertical={false} stroke="var(--grid)" />
            <XAxis dataKey="date" tickFormatter={tickFormatter(days)} tick={{ fill: 'var(--text-3)', fontSize: 12 }} tickLine={false} axisLine={{ stroke: 'var(--border)' }} minTickGap={16} />
            <YAxis tick={{ fill: 'var(--text-3)', fontSize: 12 }} tickLine={false} axisLine={false} width={48} tickFormatter={(v: number) => formatNumber(v, 0)} />
            <Tooltip content={<ChartTooltip nutrient={nutrient} />} cursor={{ fill: 'var(--hover)' }} />
            <Bar dataKey="amount" fill="var(--series-1)" radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
            {hasTarget && <Line dataKey="min" stroke="none" dot={(p: object) => <TargetTick {...p} halfWidth={halfWidth} />} activeDot={false} isAnimationActive={false} />}
            {hasTarget && <Line dataKey="max" stroke="none" dot={(p: object) => <TargetTick {...p} halfWidth={halfWidth} />} activeDot={false} isAnimationActive={false} />}
            {showAverage && (
              <Line dataKey="average" type="monotone" stroke="var(--series-2)" strokeWidth={2} dot={false} activeDot={{ r: 4, stroke: 'var(--surface)', strokeWidth: 2 }} connectNulls isAnimationActive={false} />
            )}
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </>
  )
}

function SmallMultiple({ view, nutrient, selected, onSelect }: { view: HistoryView; nutrient: NutrientDto; selected: boolean; onSelect: () => void }) {
  const data = seriesFor(view, nutrient.id)
  const summary = view.summary.find((s) => s.nutrientId === nutrient.id)
  return (
    <button type="button" className={`multiple ${selected ? 'selected' : ''}`} onClick={onSelect} aria-pressed={selected}>
      <span className="multiple-title">{nutrient.displayName}</span>
      <span className="multiple-value">{formatAmount(summary?.average, nutrient)}</span>
      <ResponsiveContainer width="100%" height={56}>
        <BarChart data={data} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
          <Bar dataKey="amount" fill="var(--series-1)" radius={[2, 2, 0, 0]} isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </button>
  )
}

function SummaryTable({ view, nutrients }: { view: HistoryView; nutrients: NutrientDto[] }) {
  const { t } = useTranslation()
  const byId = new Map(nutrients.map((n) => [n.id, n]))
  const rows = view.summary.flatMap((summary) => {
    const nutrient = byId.get(summary.nutrientId)
    return nutrient ? [{ id: nutrient.id, parentId: nutrient.parentId, summary, nutrient }] : []
  })
  return (
    <div className="table-scroll">
      <table className="table">
        <thead>
          <tr>
            <th>{t('history.nutrient')}</th>
            <th className="num">{t('history.avg')}</th>
            <th className="num">{t('history.min')}</th>
            <th className="num">{t('history.max')}</th>
            <th className="num">{t('history.withinTarget')}</th>
          </tr>
        </thead>
        {groupNutrients(rows).map(({ main, subs }) => (
          <tbody key={main.id} className="table-group">
            {[main, ...subs].map(({ summary: s, nutrient: n }) => (
              <tr key={s.nutrientId}>
                <td className={n.id !== main.id ? 'child' : ''}>
                  {n.id !== main.id && <span className="muted of-which">{t('nutrients.ofWhich')} </span>}
                  {n.displayName}
                </td>
                <td className="num">{formatAmount(s.average, n)}</td>
                <td className="num">{formatAmount(s.min, n)}</td>
                <td className="num">{formatAmount(s.max, n)}</td>
                <td className="num">{s.daysWithTarget > 0 ? `${s.daysWithinTarget} / ${s.daysWithTarget}` : '—'}</td>
              </tr>
            ))}
          </tbody>
        ))}
      </table>
    </div>
  )
}

function DataTable({ view, nutrient }: { view: HistoryView; nutrient: NutrientDto }) {
  const { t } = useTranslation()
  const data = seriesFor(view, nutrient.id)
  return (
    <div className="table-scroll">
      <table className="table">
        <thead>
          <tr>
            <th>{t('entry.date')}</th>
            <th className="num">{t('history.daily')}</th>
            <th className="num">{t('history.average7')}</th>
            <th>{t('day.target')}</th>
          </tr>
        </thead>
        <tbody>
          {[...data].reverse().map((p) => (
            <tr key={p.date}>
              <td>{formatDate(p.date, currentLocale(), 'weekday')}</td>
              <td className="num">{p.amount === null ? <span className="muted">{t('history.notLogged')}</span> : formatAmount(p.amount, nutrient)}</td>
              <td className="num">{formatAmount(p.average, nutrient)}</td>
              <td>{p.total && <TargetStatusLabel status={p.total.status} />}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function HistoryPage() {
  const { t } = useTranslation()
  const today = todayIso()
  const [from, setFrom] = useState(addDays(today, -29))
  const [to, setTo] = useState(today)
  const [nutrientId, setNutrientId] = useState<number | null>(null)
  const [showAverage, setShowAverage] = useState(true)
  const [showTable, setShowTable] = useState(false)
  const validRange = isValidIsoDate(from) && isValidIsoDate(to) && from <= to
  const history = useHistory(validRange ? from : today, validRange ? to : today)
  const nutrients = useNutrients(false)
  const displayed = nutrients.data ?? []
  const selected = displayed.find((n) => n.id === nutrientId) ?? displayed[0]
  const span = daysBetween(from, to) + 1

  return (
    <div className="page">
      <PageHeader title={t('history.title')} subtitle={t('history.subtitle')} />
      <Card>
        <div className="filters">
          <div className="segmented" role="group" aria-label={t('history.range')}>
            {PRESETS.map((days) => (
              <button
                key={days}
                type="button"
                className={to === today && span === days ? 'active' : ''}
                onClick={() => {
                  setTo(today)
                  setFrom(addDays(today, -(days - 1)))
                }}
              >
                {t('history.lastDays', { count: days })}
              </button>
            ))}
          </div>
          <label className="inline-field">
            <span className="muted small">{t('history.from')}</span>
            <input className="input" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="inline-field">
            <span className="muted small">{t('history.to')}</span>
            <input className="input" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label className="inline-field">
            <span className="muted small">{t('history.nutrient')}</span>
            <select className="input" value={selected?.id ?? ''} onChange={(e) => setNutrientId(Number(e.target.value))}>
              {displayed.map((n) => (
                <option key={n.id} value={n.id}>
                  {n.displayName}
                </option>
              ))}
            </select>
          </label>
          <label className="checkbox">
            <input type="checkbox" checked={showAverage} onChange={(e) => setShowAverage(e.target.checked)} /> {t('history.average7')}
          </label>
        </div>
        {!validRange && <p className="error-text">{t('history.invalidRange')}</p>}
      </Card>

      <QueryView query={history}>
        {(view) => {
          const logged = view.days.filter((d) => d.entryCount > 0).length
          return (
            <>
              <Card
                title={selected ? selected.displayName : ''}
                actions={
                  <label className="checkbox">
                    <input type="checkbox" checked={showTable} onChange={(e) => setShowTable(e.target.checked)} /> {t('history.showTable')}
                  </label>
                }
              >
                <p className="muted small">{t('history.loggedDays', { count: logged, total: view.days.length })}</p>
                {logged === 0 ? (
                  <Empty>{t('history.noData')}</Empty>
                ) : selected ? (
                  showTable ? <DataTable view={view} nutrient={selected} /> : <MainChart view={view} nutrient={selected} showAverage={showAverage} />
                ) : null}
              </Card>
              {logged > 0 && (
                <Card title={t('history.allNutrients')}>
                  <p className="field-hint">{t('history.multiplesHint')}</p>
                  <div className="multiples">
                    {displayed.map((n) => (
                      <SmallMultiple key={n.id} view={view} nutrient={n} selected={n.id === selected?.id} onSelect={() => setNutrientId(n.id)} />
                    ))}
                  </div>
                </Card>
              )}
              <Card title={t('history.summary')}>
                <SummaryTable view={view} nutrients={displayed} />
                <p className="field-hint">{t('history.summaryHint')}</p>
              </Card>
            </>
          )
        }}
      </QueryView>
    </div>
  )
}
