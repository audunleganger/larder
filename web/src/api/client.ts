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

  if (!response.ok) throw failure(response, data)
  return data as T
}

/** The error for a failed [response] whose body parsed as [data]. */
function failure(response: Response, data: unknown): ApiError {
  const error = data as Partial<ErrorResponse> | undefined
  if (!error?.error && [502, 503, 504].includes(response.status)) {
    // A proxy in front of a stopped server.
    return new ApiError(response.status, NETWORK_ERROR, 'Server unreachable')
  }
  if (response.status === 401 && error?.error === 'UNAUTHORIZED') unauthorizedHandler?.()
  return new ApiError(response.status, error?.error ?? `HTTP_${response.status}`, error?.message ?? response.statusText, error?.details ?? {})
}

/** POSTs [body] to the versioned API and returns the binary answer, e.g. a downloaded photo. */
export async function apiBlob(path: string, body: unknown): Promise<Blob> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', 'Accept-Language': i18n.language }
  if (token) headers.Authorization = `Bearer ${token}`
  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, { method: 'POST', headers, body: JSON.stringify(body) })
  } catch {
    throw new ApiError(0, NETWORK_ERROR, 'Server unreachable')
  }
  if (response.ok) return response.blob()
  let data: unknown = undefined
  try {
    data = await response.json()
  } catch {
    data = undefined
  }
  throw failure(response, data)
}

/** A binary resource from the versioned API as a data: URL, e.g. a photo for an <img>, which can't send the token itself. */
export async function apiDataUrl(path: string, query?: Query): Promise<string> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let response: Response
  try {
    response = await fetch(buildUrl(`/api/v1${path}`, query), { headers })
  } catch {
    throw new ApiError(0, NETWORK_ERROR, 'Server unreachable')
  }
  if (!response.ok) throw new ApiError(response.status, `HTTP_${response.status}`, response.statusText)
  const blob = await response.blob()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

/** Request to the versioned API (/api/v1). */
export function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return request<T>(`/api/v1${path}`, options)
}
