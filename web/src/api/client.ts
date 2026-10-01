import i18n from '../i18n'
import type { ErrorResponse } from './types.gen'

/** Error code used when the server can't be reached at all (O-1). */
export const NETWORK_ERROR = 'NETWORK'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: Record<string, number>

  constructor(status: number, code: string, message: string, details: Record<string, number> = {}) {
    super(message)
    this.status = status
    this.code = code
    this.details = details
  }
}

const TOKEN_KEY = 'cc.token'

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

let token: string | null = readToken()
let unauthorizedHandler: (() => void) | null = null

export function getToken(): string | null {
  return token
}

export function setToken(value: string | null) {
  token = value
  try {
    if (value) localStorage.setItem(TOKEN_KEY, value)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Storage unavailable (private mode): the session lasts until reload.
  }
}

/** Called when the server rejects the session, so the app can return to the login screen. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

type Query = Record<string, string | number | boolean | undefined | null>

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  query?: Query
}

function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${path}?${qs}` : path
}

/** Low-level request to any server path. */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  // The server returns item names in this language (L-5).
  const headers: Record<string, string> = { Accept: 'application/json', 'Accept-Language': i18n.language }
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, NETWORK_ERROR, 'Server unreachable')
  }

  if (response.status === 204) return undefined as T
  const text = await response.text()
  let data: unknown = undefined
  try {
    data = text ? JSON.parse(text) : undefined
  } catch {
    data = undefined
  }

  if (!response.ok) {
    const error = data as Partial<ErrorResponse> | undefined
    if (!error?.error && [502, 503, 504].includes(response.status)) {
      // A proxy in front of a stopped server.
      throw new ApiError(response.status, NETWORK_ERROR, 'Server unreachable')
    }
    if (response.status === 401 && error?.error === 'UNAUTHORIZED') unauthorizedHandler?.()
    throw new ApiError(
      response.status,
      error?.error ?? `HTTP_${response.status}`,
      error?.message ?? response.statusText,
      error?.details ?? {},
    )
  }
  return data as T
}

/** Request to the versioned API (/api/v1). */
export function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(`/api/v1${path}`, options)
}
