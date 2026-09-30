import { createContext } from 'react'
import type { SetupInput, UserDto } from '../api/types.gen'

export type AuthState =
  | { status: 'loading' }
  | { status: 'unreachable' }
  | { status: 'incompatible'; serverApiVersion: number }
  | { status: 'setup' }
  | { status: 'anonymous' }
  | { status: 'authenticated'; user: UserDto }

export interface AuthApi {
  state: AuthState
  login(username: string, password: string): Promise<void>
  setup(input: SetupInput): Promise<void>
  logout(): Promise<void>
  updateUser(user: UserDto): void
  retry(): void
}

export const AuthContext = createContext<AuthApi | null>(null)
