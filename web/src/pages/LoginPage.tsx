import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { ErrorText, Field } from '../components/ui'

export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password)
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <div className="center-screen">
      <form className="card narrow auth-card" onSubmit={submit}>
        <div className="auth-top">
          <h1>{t('appName')}</h1>
          <LanguageSwitch />
        </div>
        <p className="muted">{t('login.intro')}</p>
        <Field label={t('login.username')}>
          <input className="input" autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)} required />
        </Field>
        <Field label={t('login.password')}>
          <input className="input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <ErrorText error={error} />
        <button type="submit" className="btn btn-primary btn-block" disabled={busy}>
          {t('login.submit')}
        </button>
      </form>
    </div>
  )
}

/** First-run setup: creates the first user, who becomes admin (A-2). */
export function SetupPage() {
  const { t, i18n } = useTranslation()
  const { setup } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const mismatch = confirm.length > 0 && confirm !== password

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (mismatch) return
    setBusy(true)
    setError(null)
    try {
      await setup({ username, password, locale: i18n.language })
    } catch (e) {
      setError(e)
      setBusy(false)
    }
  }

  return (
    <div className="center-screen">
      <form className="card narrow auth-card" onSubmit={submit}>
        <div className="auth-top">
          <h1>{t('setup.title')}</h1>
          <LanguageSwitch />
        </div>
        <p className="muted">{t('setup.intro')}</p>
        <Field label={t('login.username')}>
          <input className="input" autoComplete="username" autoFocus value={username} onChange={(e) => setUsername(e.target.value)} required />
        </Field>
        <Field label={t('login.password')} hint={t('setup.passwordHint')}>
          <input className="input" type="password" autoComplete="new-password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <Field label={t('setup.confirmPassword')}>
          <input className="input" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </Field>
        {mismatch && <p className="error-text">{t('setup.mismatch')}</p>}
        <ErrorText error={error} />
        <button type="submit" className="btn btn-primary btn-block" disabled={busy || mismatch}>
          {t('setup.submit')}
        </button>
      </form>
    </div>
  )
}
