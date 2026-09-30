import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import * as endpoints from '../api/endpoints'
import { useApiMutation, useUsers } from '../api/queries'
import type { AdminUserCreate, AdminUserUpdate, UserDto } from '../api/types.gen'
import { useUser } from '../auth/useAuth'
import { Badge, Card, ErrorText, Field, PageHeader, QueryView } from '../components/ui'
import { LANGUAGES } from '../i18n'
import { currentLocale, formatNumber } from '../lib/format'

function UserRow({ user, self }: { user: UserDto; self: boolean }) {
  const { t } = useTranslation()
  const [resetting, setResetting] = useState(false)
  const [password, setPassword] = useState('')
  const update = useApiMutation((input: AdminUserUpdate) => endpoints.updateUser(user.id, input))

  function reset(event: FormEvent) {
    event.preventDefault()
    update.mutate({ password }, { onSuccess: () => { setResetting(false); setPassword('') } })
  }

  return (
    <tr>
      <td>
        {user.username} {self && <Badge tone="info">{t('admin.you')}</Badge>}
      </td>
      <td>
        {user.isAdmin && <Badge tone="info">{t('admin.admin')}</Badge>} {user.isDisabled && <Badge tone="warning">{t('admin.disabled')}</Badge>}
      </td>
      <td>{new Intl.DateTimeFormat(currentLocale(), { dateStyle: 'medium' }).format(user.createdAt)}</td>
      <td>
        <div className="row-actions">
          <button type="button" className="btn btn-small" onClick={() => update.mutate({ isAdmin: !user.isAdmin })}>
            {user.isAdmin ? t('admin.removeAdmin') : t('admin.makeAdmin')}
          </button>
          <button type="button" className="btn btn-small" onClick={() => update.mutate({ isDisabled: !user.isDisabled })}>
            {user.isDisabled ? t('admin.enable') : t('admin.disable')}
          </button>
          <button type="button" className="btn btn-small" onClick={() => setResetting((r) => !r)}>
            {t('admin.resetPassword')}
          </button>
        </div>
        {resetting && (
          <form className="inline-form" onSubmit={reset}>
            <input className="input" type="password" autoComplete="new-password" minLength={8} placeholder={t('settings.newPassword')} value={password} onChange={(e) => setPassword(e.target.value)} required />
            <button type="submit" className="btn btn-small btn-primary">
              {t('common.save')}
            </button>
          </form>
        )}
        <ErrorText error={update.error} />
      </td>
    </tr>
  )
}

function CreateUser() {
  const { t, i18n } = useTranslation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [isAdmin, setIsAdmin] = useState(false)
  const [locale, setLocale] = useState(i18n.language)
  const create = useApiMutation((input: AdminUserCreate) => endpoints.createUser(input))

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate({ username, password, isAdmin, locale }, { onSuccess: () => { setUsername(''); setPassword(''); setIsAdmin(false) } })
  }

  return (
    <form onSubmit={submit}>
      <div className="form-row">
        <Field label={t('login.username')} className="grow">
          <input className="input" autoComplete="off" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </Field>
        <Field label={t('login.password')} className="grow">
          <input className="input" type="password" autoComplete="new-password" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <Field label={t('settings.language')}>
          <select className="input" value={locale} onChange={(e) => setLocale(e.target.value)}>
            {LANGUAGES.map((l) => (
              <option key={l} value={l}>
                {t(`languages.${l}`)}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <label className="checkbox">
        <input type="checkbox" checked={isAdmin} onChange={(e) => setIsAdmin(e.target.checked)} /> {t('admin.admin')}
      </label>
      <p className="field-hint">{t('admin.createHint')}</p>
      <ErrorText error={create.error} />
      {create.isSuccess && <p className="saved">✓ {t('admin.created', { name: create.data.username })}</p>}
      <button type="submit" className="btn btn-primary" disabled={create.isPending}>
        {t('admin.createUser')}
      </button>
    </form>
  )
}

export function AdminPage() {
  const { t } = useTranslation()
  const me = useUser()
  const users = useUsers()
  const backup = useApiMutation(() => endpoints.backup())

  if (!me.isAdmin) return <p className="error-text">{t('errors.FORBIDDEN')}</p>

  return (
    <div className="page">
      <PageHeader title={t('admin.title')} subtitle={t('admin.subtitle')} />
      <Card title={t('admin.users')}>
        <QueryView query={users}>
          {(list) => (
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>{t('login.username')}</th>
                    <th>{t('admin.role')}</th>
                    <th>{t('admin.createdAt')}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {list.map((user) => (
                    <UserRow key={user.id} user={user} self={user.id === me.id} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </QueryView>
      </Card>
      <Card title={t('admin.newUser')}>
        <CreateUser />
      </Card>
      <Card title={t('admin.backup')}>
        <p className="field-hint">{t('admin.backupHint')}</p>
        <button type="button" className="btn" disabled={backup.isPending} onClick={() => backup.mutate(undefined)}>
          {t('admin.backupNow')}
        </button>
        <ErrorText error={backup.error} />
        {backup.data && (
          <p className="saved">
            ✓ {t('admin.backupDone', { file: backup.data.file, size: formatNumber(backup.data.sizeBytes / 1024, 0) })}
          </p>
        )}
      </Card>
    </div>
  )
}
