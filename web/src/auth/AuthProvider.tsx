import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, getToken, NETWORK_ERROR, setToken, setUnauthorizedHandler } from '../api/client'
import * as endpoints from '../api/endpoints'
import { API_VERSION, type SetupInput, type UserDto } from '../api/types.gen'
import { changeLanguage, toLanguage } from '../i18n'
import { AuthContext, type AuthApi, type AuthState } from './context'

function applyUserLanguage(user: UserDto) {
  const language = toLanguage(user.locale)
  if (language) changeLanguage(language)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading' })

  const load = useCallback(async () => {
    try {
      const health = await endpoints.health()
      if (health.apiVersion !== API_VERSION) {
        setState({ status: 'incompatible', serverApiVersion: health.apiVersion })
        return
      }
      const setup = await endpoints.setupStatus()
      if (setup.needsSetup) {
        setToken(null)
        setState({ status: 'setup' })
        return
      }
      if (!getToken()) {
        setState({ status: 'anonymous' })
        return
      }
      const user = await endpoints.me()
      applyUserLanguage(user)
      setState({ status: 'authenticated', user })
    } catch (error) {
      if (error instanceof ApiError && error.code !== NETWORK_ERROR && error.status === 401) {
        setToken(null)
        setState({ status: 'anonymous' })
      } else {
        setState({ status: 'unreachable' })
      }
    }
  }, [])

  useEffect(() => {
    // load() is async: its state updates happen after the server answers, not synchronously.
    // eslint-disable-next-line react/set-state-in-effect
    void load()
  }, [load])

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setToken(null)
      queryClient.clear()
      setState({ status: 'anonymous' })
    })
    return () => setUnauthorizedHandler(null)
  }, [queryClient])

  const api = useMemo<AuthApi>(
    () => ({
      state,
      async login(username, password) {
        const result = await endpoints.login(username, password)
        setToken(result.token)
        applyUserLanguage(result.user)
        setState({ status: 'authenticated', user: result.user })
      },
      async setup(input: SetupInput) {
        const result = await endpoints.setup(input)
        setToken(result.token)
        setState({ status: 'authenticated', user: result.user })
      },
      async logout() {
        try {
          await endpoints.logout()
        } catch {
          // Logging out locally is enough if the server is gone.
        }
        setToken(null)
        queryClient.clear()
        setState({ status: 'anonymous' })
      },
      updateUser(user) {
        // Ignore late responses that arrive after logging out.
        setState((current) => (current.status === 'authenticated' && current.user.id === user.id ? { status: 'authenticated', user } : current))
      },
      retry() {
        setState({ status: 'loading' })
        void load()
      },
    }),
    [state, load, queryClient],
  )

  return <AuthContext.Provider value={api}>{children}</AuthContext.Provider>
}
