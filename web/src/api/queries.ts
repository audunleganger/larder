import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as endpoints from './endpoints'

/** Query hooks. Keys start with the resource name so related data can be refreshed together. */
export const useUnits = (includeHidden = false) =>
  useQuery({ queryKey: ['units', { includeHidden }], queryFn: () => endpoints.listUnits(includeHidden) })

export const useUnitDetail = (id: number) =>
  useQuery({ queryKey: ['units', 'detail', id], queryFn: () => endpoints.unitDetail(id) })

export const useNutrients = (includeHidden = false) =>
  useQuery({ queryKey: ['nutrients', { includeHidden }], queryFn: () => endpoints.listNutrients(includeHidden) })

export const useNutrientDetail = (id: number) =>
  useQuery({ queryKey: ['nutrients', 'detail', id], queryFn: () => endpoints.nutrientDetail(id) })

export const useTags = (includeArchived = false) =>
  useQuery({ queryKey: ['tags', { includeArchived }], queryFn: () => endpoints.listTags(includeArchived) })

export const useTagDetail = (id: number) => useQuery({ queryKey: ['tags', 'detail', id], queryFn: () => endpoints.tagDetail(id) })

export const useFoods = (q = '', includeArchived = false) =>
  useQuery({
    queryKey: ['foods', { q, includeArchived }],
    queryFn: () => endpoints.listFoods(q, includeArchived),
    placeholderData: (previous) => previous,
  })

/** The reference amount/unit new foods start with (F-12). Under 'foods' so food edits refresh it. */
export const useFoodRefDefault = () => useQuery({ queryKey: ['foods', 'ref-default'], queryFn: endpoints.foodRefDefault })

export const useFoodDetail = (id: number | null) =>
  useQuery({
    queryKey: ['foods', 'detail', id],
    queryFn: () => endpoints.foodDetail(id!),
    enabled: id !== null,
  })

/**
 * The food and every composite food that contains it, directly or through other composites. None of
 * them can be its ingredient (F-10): the server refuses such a loop.
 */
export const useFoodAndContainers = (id: number) =>
  useQuery({
    queryKey: ['foods', 'containers', id],
    queryFn: async () => {
      const found = new Set([id])
      for (let queue = [id]; queue.length > 0; ) {
        const detail = await endpoints.foodDetail(queue.shift()!)
        for (const ref of detail.usedIn) {
          if (!found.has(ref.id)) {
            found.add(ref.id)
            queue.push(ref.id)
          }
        }
      }
      return found
    },
  })

export const useDay = (date: string) => useQuery({ queryKey: ['days', date], queryFn: () => endpoints.day(date) })

export const useTargets = () => useQuery({ queryKey: ['targets'], queryFn: endpoints.listTargets })

export const useHistory = (from: string, to: string) =>
  useQuery({
    queryKey: ['history', from, to],
    queryFn: () => endpoints.history(from, to),
    placeholderData: (previous) => previous,
  })

/** Each entry's amount of one nutrient per day, for splitting history bars by food (H-5). */
export const useContributions = (from: string, to: string, nutrientId: number | undefined) =>
  useQuery({
    queryKey: ['history', 'contributions', from, to, nutrientId],
    queryFn: () => endpoints.historyContributions(from, to, nutrientId!),
    enabled: nutrientId !== undefined,
    placeholderData: (previous) => previous,
  })

export const useUsers = () => useQuery({ queryKey: ['users'], queryFn: endpoints.listUsers })

/**
 * A mutation that refreshes all cached data afterwards. Catalog edits change calculated totals
 * everywhere (E-4), so refreshing everything is the simple, correct choice for a personal app.
 * The refresh isn't awaited: callers (e.g. a form resetting itself) continue as soon as the
 * server has confirmed the change.
 */
export function useApiMutation<TArgs, TResult>(fn: (args: TArgs) => Promise<TResult>) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      // Photos are cached by version and never go stale.
      void client.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'images' })
    },
  })
}
