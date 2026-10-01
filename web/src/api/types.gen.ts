// Generated from the Kotlin API types in shared/ by TypeScriptTypesTest. Do not edit.
// Regenerate: ./gradlew :shared:test -PupdateTsTypes=true

export const API_VERSION = 1

export const ErrorCodes = {
  VALIDATION: 'VALIDATION',
  NAME_REQUIRED: 'NAME_REQUIRED',
  NAME_TAKEN: 'NAME_TAKEN',
  NOT_FOUND: 'NOT_FOUND',
  REFERENCED: 'REFERENCED',
  UNAUTHORIZED: 'UNAUTHORIZED',
  FORBIDDEN: 'FORBIDDEN',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  SETUP_DONE: 'SETUP_DONE',
  LAST_ADMIN: 'LAST_ADMIN',
  WEAK_PASSWORD: 'WEAK_PASSWORD',
  INVALID_IMPORT: 'INVALID_IMPORT',
} as const

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes]

export interface AdminUserCreate {
  username: string
  password: string
  isAdmin?: boolean
  locale?: string | null
}

export interface AdminUserUpdate {
  isAdmin?: boolean | null
  isDisabled?: boolean | null
  password?: string | null
}

export interface BackupResult {
  file: string
  sizeBytes: number
}

export interface CompositeDetail {
  ingredients: IngredientView[]
  yieldAmount: number | null
  yieldUnitId: number | null
  yieldAutomatic: boolean
  totalGrams: number | null
  nutrients: FoodNutrientValue[]
  logAsWhole: boolean
}

export interface CompositeInput {
  ingredients: Ingredient[]
  yieldAmount?: number | null
  yieldUnitId?: number | null
  logAsWhole?: boolean
}

export type ConflictStrategy = 'skip' | 'overwrite'

export interface DayContributions {
  date: string
  entries: EntryContribution[]
}

export interface DayView {
  date: string
  entries: EntryView[]
  totals: NutrientTotal[]
}

export interface EntryContribution {
  entryId: number
  foodId: number
  foodName: string
  time: string
  amount: number | null
}

export interface EntryInput {
  foodId: number
  unitId: number
  quantity: number
  date: string
  time: string
  note?: string | null
}

export interface EntryView {
  id: number
  foodId: number
  foodName: string
  foodImageVersion: number | null
  unitId: number
  unitName: string
  unitPluralSuffix: string
  quantity: number
  date: string
  time: string
  note: string | null
  nutrients: NutrientAmount[]
  unresolved: boolean
  viaFoodId: number | null
  viaFoodName: string | null
}

export interface ErrorResponse {
  error: string
  message: string
  details?: Record<string, number>
}

export interface ExportEntry {
  food: string
  unit: string
  quantity: number
  date: string
  time: string
  note?: string | null
  via?: string | null
}

export interface ExportFile {
  format?: string
  version?: number
  exportedAt: string
  units: ExportUnit[]
  nutrients: ExportNutrient[]
  foods: ExportFood[]
  entries: ExportEntry[]
  targets: ExportTarget[]
}

export interface ExportFood {
  name: string
  refAmount?: number | null
  refUnit?: string | null
  notes?: string | null
  archived?: boolean
  nutrients?: Record<string, number>
  units?: ExportFoodUnit[]
  translations?: NameTranslation[]
  image?: FoodImageData | null
  ingredients?: ExportIngredient[]
  yieldAmount?: number | null
  yieldUnit?: string | null
  logAsWhole?: boolean
}

export interface ExportFoodUnit {
  unit: string
  equalsAmount?: number | null
  equalsUnit?: string | null
}

export interface ExportIngredient {
  food: string
  unit: string
  quantity: number
}

export interface ExportNutrient {
  name: string
  measureUnit: string
  displayPrecision?: number
  parent?: string | null
  archived?: boolean
  translations?: NameTranslation[]
}

export interface ExportTarget {
  nutrient: string
  min?: number | null
  max?: number | null
  effectiveFrom: string
}

export interface ExportUnit {
  name: string
  kind: UnitKind
  baseFactor?: number | null
  archived?: boolean
  pluralSuffix?: string
  translations?: NameTranslation[]
}

export interface FoodDetail {
  food: FoodDto
  usableUnits: UsableUnit[]
  entries: FoodEntryRef[]
  composite: CompositeDetail | null
  usedIn: FoodRef[]
  loggedAsItemsOn: string[]
}

export interface FoodDto {
  id: number
  name: string
  refAmount: number | null
  refUnitId: number | null
  notes: string | null
  archived: boolean
  nutrients: FoodNutrientValue[]
  units: FoodUnitLink[]
  translations: NameTranslation[]
  displayName: string
  imageVersion: number | null
  composite: CompositeInput | null
}

export interface FoodEntryRef {
  entryId: number
  date: string
  time: string
  quantity: number
  unitId: number
  unitName: string
  unitPluralSuffix: string
}

export interface FoodImageData {
  contentType: string
  image: string
  thumbnail: string
}

export interface FoodInput {
  name: string
  refAmount?: number | null
  refUnitId?: number | null
  notes?: string | null
  nutrients?: FoodNutrientValue[]
  units?: FoodUnitLink[]
  translations?: NameTranslation[] | null
  composite?: CompositeInput | null
}

export interface FoodNutrientValue {
  nutrientId: number
  amount: number
}

export interface FoodRef {
  id: number
  name: string
  archived: boolean
}

export interface FoodRefDefault {
  refAmount: number | null
  refUnitId: number | null
}

export interface FoodSummary {
  id: number
  name: string
  archived: boolean
  refAmount: number | null
  refUnitId: number | null
  nutrientCount: number
  imageVersion: number | null
  composite: boolean
}

export interface FoodUnitLink {
  unitId: number
  equalsAmount?: number | null
  equalsUnitId?: number | null
}

export interface HealthResponse {
  status: string
  version: string
  apiVersion: number
}

export interface HistoryDay {
  date: string
  entryCount: number
  totals: NutrientTotal[]
}

export interface HistoryView {
  from: string
  to: string
  days: HistoryDay[]
  summary: NutrientSummary[]
}

export interface ImportCounts {
  created?: number
  updated?: number
  skipped?: number
}

export interface ImportResult {
  units: ImportCounts
  nutrients: ImportCounts
  foods: ImportCounts
  entries: ImportCounts
  targets: ImportCounts
}

export interface Ingredient {
  foodId: number
  unitId: number
  quantity: number
}

export interface IngredientView {
  foodId: number
  foodName: string
  foodImageVersion: number | null
  unitId: number
  unitName: string
  unitPluralSuffix: string
  quantity: number
  unresolved: boolean
  grams: number | null
}

export interface LocaleInput {
  locale: string
}

export interface LoginInput {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  user: UserDto
}

export interface NameTranslation {
  locale: string
  name: string
  pluralSuffix?: string
}

export interface NutrientAmount {
  nutrientId: number
  amount: number | null
}

export interface NutrientContributions {
  nutrientId: number
  days: DayContributions[]
}

export interface NutrientDetail {
  nutrient: NutrientDto
  foods: NutrientFoodValue[]
  entries: NutrientEntryRef[]
  entriesTruncated: boolean
}

export interface NutrientDto {
  id: number
  name: string
  measureUnit: string
  displayPrecision: number
  sortOrder: number
  parentId: number | null
  archived: boolean
  translations: NameTranslation[]
  displayName: string
}

export interface NutrientEntryRef {
  entryId: number
  date: string
  time: string
  foodId: number
  foodName: string
  quantity: number
  unitName: string
  amount: number | null
  unitPluralSuffix: string
}

export interface NutrientFoodValue {
  foodId: number
  foodName: string
  foodArchived: boolean
  amount: number
  refAmount: number | null
  refUnitName: string | null
}

export interface NutrientInput {
  name: string
  measureUnit: string
  displayPrecision?: number
  parentId?: number | null
  translations?: NameTranslation[] | null
}

export interface NutrientOrderInput {
  ids: number[]
}

export interface NutrientSummary {
  nutrientId: number
  average: number | null
  min: number | null
  max: number | null
  loggedDays: number
  daysWithTarget: number
  daysWithinTarget: number
}

export interface NutrientTotal {
  nutrientId: number
  amount: number
  missingCount: number
  targetMin: number | null
  targetMax: number | null
  status: TargetStatus
}

export interface PasswordChangeInput {
  currentPassword: string
  newPassword: string
}

export interface PreviewInput {
  foodId: number
  unitId: number
  quantity: number
}

export interface PreviewResult {
  nutrients: NutrientAmount[]
  unresolved: boolean
}

export interface SetupInput {
  username: string
  password: string
  locale?: string | null
}

export interface SetupStatus {
  needsSetup: boolean
}

export interface TargetDto {
  id: number
  nutrientId: number
  min: number | null
  max: number | null
  effectiveFrom: string
}

export interface TargetInput {
  nutrientId: number
  min?: number | null
  max?: number | null
  effectiveFrom: string
}

export type TargetStatus = 'none' | 'below' | 'within' | 'above'

export interface UnitDetail {
  unit: UnitDto
  foods: FoodRef[]
  implicitFoods: FoodRef[]
  dates: string[]
}

export interface UnitDto {
  id: number
  name: string
  kind: UnitKind
  baseFactor: number | null
  archived: boolean
  pluralSuffix: string
  translations: NameTranslation[]
  displayName: string
  displayPluralSuffix: string
}

export interface UnitInput {
  name: string
  kind: UnitKind
  baseFactor?: number | null
  pluralSuffix?: string | null
  translations?: NameTranslation[] | null
}

export type UnitKind = 'mass' | 'volume' | 'custom'

export interface UsableUnit {
  unitId: number
  name: string
  kind: UnitKind
  explicit: boolean
  amountInRefUnit: number | null
  pluralSuffix: string
}

export interface UserDto {
  id: number
  username: string
  isAdmin: boolean
  isDisabled: boolean
  locale: string | null
  createdAt: number
}
