import { ApiError } from '../api/client'

/** A unit or nutrient: shared by everyone on the server, hidden or shown per user. */
export interface SharedItem {
  id: number
  displayName: string
  hidden: boolean
  builtIn: boolean
  createdBy: string
  canEdit: boolean
}

/** The item a NAME_TAKEN error points to, if the reader has it hidden; creating it again then means showing it. */
export function takenHidden<T extends SharedItem>(error: unknown, items: T[] | undefined): T | undefined {
  if (!(error instanceof ApiError) || error.code !== 'NAME_TAKEN') return undefined
  return items?.find((item) => item.id === error.details.id && item.hidden)
}
