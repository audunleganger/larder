import { useContext } from 'react'
import type { UserDto } from '../api/types.gen'
import { AuthContext, type AuthApi } from './context'

export function useAuth(): AuthApi {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth outside AuthProvider')
  return context
}

/** The logged-in user; only use inside authenticated pages. */
export function useUser(): UserDto {
  const { state } = useAuth()
  if (state.status !== 'authenticated') throw new Error('useUser without a logged-in user')
  return state.user
}
