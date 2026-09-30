import { useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { errorMessage } from '../lib/errors'

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}

export function Card({ title, actions, children, className = '' }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-header">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

export function Field({ label, hint, children, className = '' }: { label: ReactNode; hint?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <div className={`field ${className}`}>
      <label className="field-inner">
        <span className="field-label">{label}</span>
        {children}
      </label>
      {hint && <span className="field-hint">{hint}</span>}
    </div>
  )
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null
  return (
    <p className="error-text" role="alert">
      {errorMessage(error)}
    </p>
  )
}

export function Loading() {
  const { t } = useTranslation()
  return <p className="muted loading">{t('common.loading')}</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>
}

/** Shows loading and error states for a query, and renders children once data is there. */
export function QueryView<T>({ query, children }: { query: { data?: T; error: unknown; isPending: boolean }; children: (data: T) => ReactNode }) {
  if (query.data !== undefined) return <>{children(query.data)}</>
  if (query.error) return <ErrorText error={query.error} />
  return <Loading />
}

/** A button that asks for confirmation with a second click. */
export function ConfirmButton({ onConfirm, children, className = 'btn', disabled }: { onConfirm: () => void; children: ReactNode; className?: string; disabled?: boolean }) {
  const { t } = useTranslation()
  const [asking, setAsking] = useState(false)
  if (!asking) {
    return (
      <button type="button" className={className} disabled={disabled} onClick={() => setAsking(true)}>
        {children}
      </button>
    )
  }
  return (
    <span className="confirm">
      <button
        type="button"
        className="btn btn-danger"
        onClick={() => {
          setAsking(false)
          onConfirm()
        }}
      >
        {t('common.confirm')}
      </button>
      <button type="button" className="btn btn-ghost" onClick={() => setAsking(false)}>
        {t('common.cancel')}
      </button>
    </span>
  )
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'warning' | 'good' | 'info' }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}
