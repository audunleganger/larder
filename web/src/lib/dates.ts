const pad = (n: number) => String(n).padStart(2, '0')

/** Today's local date as YYYY-MM-DD. */
export function todayIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** Current local time as HH:MM. */
export function nowTime(): string {
  const d = new Date()
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** Dates are calendar days without timezone; UTC arithmetic avoids DST surprises. */
function toUtc(iso: string): Date {
  return new Date(`${iso}T00:00:00Z`)
}

export function addDays(iso: string, days: number): string {
  const d = toUtc(iso)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export function daysBetween(from: string, to: string): number {
  return Math.round((toUtc(to).getTime() - toUtc(from).getTime()) / 86_400_000)
}

export function isValidIsoDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(toUtc(value).getTime()) && toUtc(value).toISOString().startsWith(value)
}

export function formatDate(iso: string, locale: string, style: 'long' | 'short' | 'weekday' = 'long'): string {
  const options: Intl.DateTimeFormatOptions =
    style === 'long'
      ? { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }
      : style === 'weekday'
        ? { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }
        : { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }
  return new Intl.DateTimeFormat(locale, options).format(toUtc(iso))
}

/** 7-day rolling average over values where null means "no data" (skipped, not zero). */
export function rollingAverage(values: (number | null)[], window = 7): (number | null)[] {
  return values.map((_, i) => {
    const slice = values.slice(Math.max(0, i - window + 1), i + 1).filter((v): v is number => v !== null)
    return slice.length ? slice.reduce((a, b) => a + b, 0) / slice.length : null
  })
}
