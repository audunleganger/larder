import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fetchHealth, type HealthResponse } from './api/health'

type ServerState =
  | { kind: 'checking' }
  | { kind: 'ok'; health: HealthResponse }
  | { kind: 'unreachable' }

function App() {
  const { t } = useTranslation()
  const [server, setServer] = useState<ServerState>({ kind: 'checking' })

  const load = useCallback(() => {
    fetchHealth()
      .then((health) => setServer({ kind: 'ok', health }))
      .catch(() => setServer({ kind: 'unreachable' }))
  }, [])

  useEffect(load, [load])

  const retry = () => {
    setServer({ kind: 'checking' })
    load()
  }

  return (
    <main>
      <h1>{t('appName')}</h1>
      <p className={`server-status ${server.kind}`}>
        {server.kind === 'checking' && t('server.checking')}
        {server.kind === 'ok' &&
          t('server.ok', { version: server.health.version, apiVersion: server.health.apiVersion })}
        {server.kind === 'unreachable' && (
          <>
            {t('server.unreachable')} <button onClick={retry}>{t('server.retry')}</button>
          </>
        )}
      </p>
    </main>
  )
}

export default App
