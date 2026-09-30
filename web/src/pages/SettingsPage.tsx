import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import * as endpoints from '../api/endpoints'
import { useApiMutation } from '../api/queries'
import type { ConflictStrategy, ExportFile, ImportResult } from '../api/types.gen'
import { useAuth } from '../auth/useAuth'
import { LanguageSwitch } from '../components/LanguageSwitch'
import { Card, ErrorText, Field, PageHeader } from '../components/ui'
import { todayIso } from '../lib/dates'

function PasswordForm() {
  const { t } = useTranslation()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const change = useApiMutation(({ current, next }: { current: string; next: string }) => endpoints.changePassword(current, next))
  const mismatch = confirm.length > 0 && confirm !== next

  function submit(event: FormEvent) {
    event.preventDefault()
    if (mismatch) return
    change.mutate(
      { current, next },
      {
        onSuccess: () => {
          setCurrent('')
          setNext('')
          setConfirm('')
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="stack">
      <Field label={t('settings.currentPassword')}>
        <input className="input" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
      </Field>
      <Field label={t('settings.newPassword')} hint={t('setup.passwordHint')}>
        <input className="input" type="password" autoComplete="new-password" minLength={8} value={next} onChange={(e) => setNext(e.target.value)} required />
      </Field>
      <Field label={t('setup.confirmPassword')}>
        <input className="input" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
      </Field>
      {mismatch && <p className="error-text">{t('setup.mismatch')}</p>}
      <ErrorText error={change.error} />
      {change.isSuccess && <p className="saved">✓ {t('settings.passwordChanged')}</p>}
      <div>
        <button type="submit" className="btn btn-primary" disabled={change.isPending || mismatch}>
          {t('settings.changePassword')}
        </button>
      </div>
    </form>
  )
}

function ImportResultView({ result }: { result: ImportResult }) {
  const { t } = useTranslation()
  const rows = [
    ['units', result.units],
    ['nutrients', result.nutrients],
    ['foods', result.foods],
    ['entries', result.entries],
    ['targets', result.targets],
  ] as const
  return (
    <table className="table">
      <thead>
        <tr>
          <th />
          <th className="num">{t('settings.created')}</th>
          <th className="num">{t('settings.updated')}</th>
          <th className="num">{t('settings.skipped')}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map(([key, counts]) => (
          <tr key={key}>
            <td>{t(`settings.kinds.${key}`)}</td>
            <td className="num">{counts.created ?? 0}</td>
            <td className="num">{counts.updated ?? 0}</td>
            <td className="num">{counts.skipped ?? 0}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function DataTransfer() {
  const { t } = useTranslation()
  const [exportError, setExportError] = useState<unknown>(null)
  const [file, setFile] = useState<File | null>(null)
  const [strategy, setStrategy] = useState<ConflictStrategy>('skip')
  const [parseError, setParseError] = useState<string | null>(null)
  const importMutation = useApiMutation(({ data, strategy }: { data: ExportFile; strategy: ConflictStrategy }) => endpoints.importData(data, strategy))

  async function download() {
    setExportError(null)
    try {
      const data = await endpoints.exportData()
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `calorie-companion-${todayIso()}.json`
      link.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setExportError(e)
    }
  }

  async function upload(event: FormEvent) {
    event.preventDefault()
    setParseError(null)
    if (!file) return
    let data: ExportFile
    try {
      data = JSON.parse(await file.text()) as ExportFile
    } catch {
      data = {} as ExportFile
    }
    if (data?.format !== 'calorie-companion-export') {
      setParseError(t('settings.invalidFile'))
      return
    }
    importMutation.mutate({ data, strategy })
  }

  return (
    <>
      <Card title={t('settings.export')}>
        <p className="field-hint">{t('settings.exportHint')}</p>
        <button type="button" className="btn" onClick={() => void download()}>
          {t('settings.download')}
        </button>
        <ErrorText error={exportError} />
      </Card>
      <Card title={t('settings.import')}>
        <p className="field-hint">{t('settings.importHint')}</p>
        <form onSubmit={(e) => void upload(e)} className="stack">
          <input className="input" type="file" accept="application/json,.json" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <fieldset className="radio-group">
            <legend>{t('settings.onConflict')}</legend>
            {(['skip', 'overwrite'] as const).map((s) => (
              <label key={s} className="checkbox">
                <input type="radio" name="strategy" value={s} checked={strategy === s} onChange={() => setStrategy(s)} /> {t(`settings.strategies.${s}`)}
              </label>
            ))}
          </fieldset>
          {parseError && <p className="error-text">{parseError}</p>}
          <ErrorText error={importMutation.error} />
          <div>
            <button type="submit" className="btn btn-primary" disabled={!file || importMutation.isPending}>
              {t('settings.importButton')}
            </button>
          </div>
        </form>
        {importMutation.data && <ImportResultView result={importMutation.data} />}
      </Card>
    </>
  )
}

export function SettingsPage() {
  const { t } = useTranslation()
  const { updateUser } = useAuth()
  const [languageError, setLanguageError] = useState<unknown>(null)
  const health = useQuery({ queryKey: ['health'], queryFn: endpoints.health })

  return (
    <div className="page">
      <PageHeader title={t('settings.title')} />
      <div className="two-col">
        <Card title={t('settings.language')}>
          <LanguageSwitch
            onChange={(language) => {
              setLanguageError(null)
              endpoints.setLocale(language).then(updateUser, setLanguageError)
            }}
          />
          <p className="field-hint">{t('settings.languageHint')}</p>
          <ErrorText error={languageError} />
        </Card>
        <Card title={t('settings.password')}>
          <PasswordForm />
        </Card>
      </div>
      <DataTransfer />
      {health.data && (
        <p className="muted small">
          {t('settings.version', { version: health.data.version, api: health.data.apiVersion })}
        </p>
      )}
    </div>
  )
}
