import { api, request } from './client'
import type {
  AdminUserCreate,
  AdminUserUpdate,
  BackupResult,
  ConflictStrategy,
  DayView,
  EntryInput,
  EntryView,
  ExportFile,
  FoodDetail,
  FoodDto,
  FoodInput,
  FoodRefDefault,
  FoodSummary,
  HealthResponse,
  HistoryView,
  ImportResult,
  LoginResult,
  NutrientDetail,
  NutrientDto,
  NutrientInput,
  PreviewInput,
  PreviewResult,
  SetupInput,
  SetupStatus,
  TargetDto,
  TargetInput,
  UnitDetail,
  UnitDto,
  UnitInput,
  UserDto,
} from './types.gen'

export const health = () => request<HealthResponse>('/api/health')

// Account
export const setupStatus = () => api<SetupStatus>('/setup')
export const setup = (input: SetupInput) => api<LoginResult>('/setup', { method: 'POST', body: input })
export const login = (username: string, password: string) =>
  api<LoginResult>('/auth/login', { method: 'POST', body: { username, password } })
export const logout = () => api<void>('/auth/logout', { method: 'POST' })
export const me = () => api<UserDto>('/me')
export const changePassword = (currentPassword: string, newPassword: string) =>
  api<void>('/me/password', { method: 'PUT', body: { currentPassword, newPassword } })
export const setLocale = (locale: string) => api<UserDto>('/me/locale', { method: 'PUT', body: { locale } })

// Admin
export const listUsers = () => api<UserDto[]>('/admin/users')
export const createUser = (input: AdminUserCreate) => api<UserDto>('/admin/users', { method: 'POST', body: input })
export const updateUser = (id: number, input: AdminUserUpdate) =>
  api<UserDto>(`/admin/users/${id}`, { method: 'PATCH', body: input })
export const backup = () => api<BackupResult>('/admin/backup', { method: 'POST' })

// Units
export const listUnits = (includeArchived = false) => api<UnitDto[]>('/units', { query: { includeArchived } })
export const unitDetail = (id: number) => api<UnitDetail>(`/units/${id}`)
export const createUnit = (input: UnitInput) => api<UnitDto>('/units', { method: 'POST', body: input })
export const updateUnit = (id: number, input: UnitInput) => api<UnitDto>(`/units/${id}`, { method: 'PUT', body: input })
export const archiveUnit = (id: number, archived: boolean) =>
  api<UnitDto>(`/units/${id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' })
export const deleteUnit = (id: number) => api<void>(`/units/${id}`, { method: 'DELETE' })

// Nutrients
export const listNutrients = (includeArchived = false) =>
  api<NutrientDto[]>('/nutrients', { query: { includeArchived } })
export const nutrientDetail = (id: number) => api<NutrientDetail>(`/nutrients/${id}`)
export const createNutrient = (input: NutrientInput) => api<NutrientDto>('/nutrients', { method: 'POST', body: input })
export const updateNutrient = (id: number, input: NutrientInput) =>
  api<NutrientDto>(`/nutrients/${id}`, { method: 'PUT', body: input })
export const reorderNutrients = (ids: number[]) => api<NutrientDto[]>('/nutrients/order', { method: 'PUT', body: { ids } })
export const archiveNutrient = (id: number, archived: boolean) =>
  api<NutrientDto>(`/nutrients/${id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' })
export const deleteNutrient = (id: number) => api<void>(`/nutrients/${id}`, { method: 'DELETE' })

// Foods
export const listFoods = (q?: string, includeArchived = false) =>
  api<FoodSummary[]>('/foods', { query: { q, includeArchived } })
export const foodRefDefault = () => api<FoodRefDefault>('/foods/ref-default')
export const foodDetail = (id: number) => api<FoodDetail>(`/foods/${id}`)
export const createFood = (input: FoodInput) => api<FoodDto>('/foods', { method: 'POST', body: input })
export const updateFood = (id: number, input: FoodInput) => api<FoodDto>(`/foods/${id}`, { method: 'PUT', body: input })
export const archiveFood = (id: number, archived: boolean) =>
  api<FoodDto>(`/foods/${id}/${archived ? 'archive' : 'unarchive'}`, { method: 'POST' })
export const deleteFood = (id: number) => api<void>(`/foods/${id}`, { method: 'DELETE' })

// Diary
export const day = (date: string) => api<DayView>(`/days/${date}`)
export const createEntry = (input: EntryInput) => api<EntryView>('/entries', { method: 'POST', body: input })
export const updateEntry = (id: number, input: EntryInput) =>
  api<EntryView>(`/entries/${id}`, { method: 'PUT', body: input })
export const deleteEntry = (id: number) => api<void>(`/entries/${id}`, { method: 'DELETE' })
export const previewEntry = (input: PreviewInput) => api<PreviewResult>('/entries/preview', { method: 'POST', body: input })

// Targets & history
export const listTargets = () => api<TargetDto[]>('/targets')
export const setTarget = (input: TargetInput) => api<TargetDto>('/targets', { method: 'POST', body: input })
export const deleteTarget = (id: number) => api<void>(`/targets/${id}`, { method: 'DELETE' })
export const history = (from: string, to: string) => api<HistoryView>('/history', { query: { from, to } })

// Import / export
export const exportData = () => api<ExportFile>('/export')
export const importData = (file: ExportFile, onConflict: ConflictStrategy) =>
  api<ImportResult>('/import', { method: 'POST', body: file, query: { onConflict } })
