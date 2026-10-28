# Larder

A self-hosted food diary: foods, units, nutrients, daily entries, targets and history. One Kotlin server with a
SQLite database, a React web GUI (the main client) and an Android app (secondary: a subset of the features).
[README.md](README.md) has the commands, the API and the release steps; [REQUIREMENTS.md](REQUIREMENTS.md) is the
specification, with requirement IDs such as F-10 or L-5; [TODO.md](TODO.md) lists planned work.

## Where things go

| What | Where |
|---|---|
| Rules, calculations, validation | `shared/.../domain` (pure, no database) |
| Use cases on the database | `shared/.../service`, one `XService` per resource |
| API types (requests and responses) | `shared/.../api`; the web types are generated from these |
| Database schema | `shared/src/main/sqldelight` (`.sq` files, plus a migration per change) |
| HTTP routes | `server/`: thin, they parse the request and call a service |
| Web data access | `web/src/api`: `endpoints.ts` (calls), `queries.ts` (query hooks); pages never call `fetch` |
| Web pages and parts | `web/src/pages`, `web/src/components`, helpers in `web/src/lib` |
| Texts in the web GUI | `web/src/i18n/en.ts` and `nb.ts`, always both |

Logic that more than one client needs goes in `shared`, not in the web GUI or the Android app.

## Before writing a helper, check these

- **shared/domain:** `cleanName`, `cleanOptionalText`, `requirePositive`, `requireNonNegative`, `parseDate`,
  `normalizeTime`, `validation()`, `notFound()` (Validation.kt); `pickTranslation`, `Languages`, `defaultPlural`,
  `unitLabel` (Names.kt); `unitOrder`, `inDisplayOrder`, `groupedOrder` (Catalog.kt); `UnitResolver`,
  `NutritionCalculator`, `Composites`, `TargetTimeline`, `ImageRules`.
- **shared/service:** `loadCatalog`, `loadUnits`, `loadNutrients`, `loadTags` and the `toDto` mappers
  (Mapping.kt); `NameRules` (name and translation checks); `MetadataRules`; `foldForSearch` (Search.kt).
- **web/src/components/ui.tsx:** `PageHeader`, `Card`, `Field`, `ErrorText`, `Loading`, `Empty`, `QueryView`,
  `ConfirmButton`, `Badge`. Also `DecimalInput`, `FoodPicker`, `TranslationFields`, `MetadataCard`,
  `TakenHiddenNotice`, `MadeBy`.
- **web/src/lib:** `format.ts` (`formatNumber`, `formatAmount`, `formatQuantity`, `parseDecimal`,
  `toInputValue`), `dates.ts`, `names.ts`, `nutrients.ts` (`groupNutrients`), `units.ts`, `tags.ts`, `errors.ts`.

If something close exists, extend it instead of writing a second one.

## Patterns to copy

When adding something new, copy the newest complete example of its kind. Tags were added last and follow every
convention:

- Service: `TagService.kt`. Routes: the `/tags` block in `CatalogRoutes.kt`.
- List page: `TagsPage.tsx`. Detail page: `TagDetailPage.tsx`. A list page with a create form, and the notice
  for a name that's taken by a hidden item: `UnitsPage.tsx`.

## Conventions

- **Names sort the Norwegian way** (æ, ø, å last), whatever the reader's language. Not every list follows this
  yet.
- **Search** is case- and diacritic-insensitive over every name of an item, with `foldForSearch`.
- **Names in several languages (L-5):** an item has a main name plus optional translations; show
  `displayName(language)`. Names are unique per kind across all languages (`NameRules.ensureFree`).
- **Update inputs:** a null field means "leave unchanged". Older clients (the Android app) rely on this to keep
  data they don't know about, so new optional fields on an update input must be nullable.
- **Services:** constructed per request as `XService(db, userId, language)`. A change runs in
  `db.transactionWithResult`, validates, writes, and returns the item read back.
- **Errors:** `validation()`, `notFound()` or `AppException` with a code from `ErrorCodes`. Server messages are in
  English; the web GUI translates by error code.
- **Web mutations:** use `useApiMutation`; it refreshes all cached data afterwards, deliberately.
- **Comments:** short, saying why rather than what, with the requirement ID where one applies (`(F-16)`).
  Match the density of the file you're in.

## Changing stored data

Every change to what's stored needs all of:

1. The schema in the `.sq` files and a migration `migrations/<N>.sqm`.
2. A new export format: bump `ExportFile.VERSION` (`api/Transfer.kt`), and have the import handle older files
   (`file.version >= N` in `TransferService`).
3. Regenerated web types: `./gradlew :shared:test -PupdateTsTypes=true`.
4. The README's API table and REQUIREMENTS.md, if behaviour or the API changed.

## Checks

```sh
./gradlew build                          # shared and server: tests, schema check, generated types up to date
cd web && npm run lint && npm test && npm run build
cd web && npm run e2e                    # browser walkthrough; update web/e2e/flow.mjs when a flow changes
```

CI runs all of these on every pull request.

## Workflow

- Work on a branch and open a pull request with `gh`; never commit to or push `main`. The owner reviews and
  merges, and deploys by hand.
- Stage files by name. The working tree often holds the owner's own uncommitted experiments; never commit those.
- A feature PR also updates the docs (README, REQUIREMENTS.md with a version note, TODO.md), often as its own
  "Document …" commit.
- Keep refactors and features in separate pull requests.
