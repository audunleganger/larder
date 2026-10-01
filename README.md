# Calorie Companion

A customizable nutrition tracker: web GUI, native Android app and a self-hostable server.
See [REQUIREMENTS.md](REQUIREMENTS.md) for the specification.

- **Your own catalog** — food stuffs, units and nutrients are all user-defined (with sensible defaults).
  Only a name is needed to start; nutrient values and unit sizes can be filled in later.
- **Units that convert** — nutrient values are stored per reference amount (e.g. per 100 g). Standard
  weight/volume units convert automatically; custom units (slice, glass, serving) are sized per food.
- **Composite foods** — make a food out of other foods: a recipe, or a meal you often eat. Nutrients
  are calculated from the ingredients; logging it adds each item as its own entry (or one entry, per food).
- **Photos** — one per food, shown next to it in the picker, lists and entries.
- **Day view** — log entries with a live nutrient preview, see day totals against min/max targets, and
  hover (or tap) a total to see which foods it came from.
- **History** — daily charts with 7-day average and targets, split by food on hover, summaries per nutrient.
- **Names in both languages** — foods, units and nutrients can have a Norwegian and an English name;
  units have plural endings ("2 slices", "2 skiver"). Nutrients group as "of which …" under their main nutrient.
- **Multi-user server** with first-run setup, admin-managed accounts, export/import and backups.
- English and Norwegian (bokmål), light and dark mode, works on phones.

## Layout

| Module | What | Tech |
|---|---|---|
| `shared/` | Domain logic, calculation engine, services, API DTOs, SQLite schema — used by the server **and** Android local mode | Kotlin (JVM), SQLDelight |
| `server/` | REST API (`/api/v1`) + serves the built web GUI | Kotlin, Ktor, SQLite |
| `web/` | Web GUI | React, TypeScript, Vite, TanStack Query, Recharts, react-i18next |
| `android/` | Android app (local or server mode) | Kotlin, Jetpack Compose |

## Quick start (Docker)

```sh
docker build -t calorie-companion .
docker run -d -p 8080:8080 -v calorie-companion-data:/data calorie-companion
```

Open http://localhost:8080. The first visit shows a setup screen that creates the administrator account.
The admin can add more users under **Admin**; each user has a completely separate catalog and diary.

## Prerequisites for development

- **Java runtime** of any recent version to launch `./gradlew`. Gradle provisions JDK 21 itself
  (see `gradle/gradle-daemon-jvm.properties`) — a JRE is enough, but a full JDK 21 install
  (`pacman -S jdk21-openjdk`) makes IDE setup easier.
- **Node.js** ≥ 22.12 for `web/`.
- **Android SDK** (platform `android-37.0`) for `android/` — install via Android Studio or the
  command-line tools, then set `ANDROID_HOME` or `sdk.dir` in `local.properties`.
  Without an SDK the `:android` module is skipped and everything else still builds.

## Development

```sh
./gradlew build                 # build + test shared and server (and android if an SDK is configured)
./gradlew :server:run           # API on http://localhost:8080, data in ./data

cd web && npm install
npm run dev                     # web GUI on http://localhost:5173, proxies /api to :8080
npm test                        # unit tests (vitest)
npm run lint
npm run e2e                     # browser walkthrough against a fresh server (needs Chromium; CHROMIUM_PATH to override)

./gradlew :android:assembleDebug   # android/build/outputs/apk/debug/android-debug.apk
```

### API types

The Kotlin DTOs in `shared/src/main/kotlin/com/caloriecompanion/shared/api` are the API contract.
`web/src/api/types.gen.ts` is generated from them; after changing a DTO run:

```sh
./gradlew :shared:test -PupdateTsTypes=true
```

`./gradlew build` fails if the generated file is out of date.

## Running the server

Configuration via environment variables:

| Variable | Default | |
|---|---|---|
| `CC_HOST` | `0.0.0.0` | Bind address |
| `CC_PORT` | `8080` | Port |
| `CC_DATA_DIR` | `data` | Directory holding the SQLite database (and `backups/`) |
| `CC_WEB_DIR` | *(unset)* | Directory with the built web GUI; unset = API only |
| `CC_TOKEN_LIFETIME_DAYS` | `30` | Session lifetime (sliding) |

**Plain JAR** (no Docker):

```sh
./gradlew :server:buildFatJar
(cd web && npm ci && npm run build)
CC_WEB_DIR=web/dist java -jar server/build/libs/calorie-companion-server.jar
```

**Backups:** admins can trigger a consistent copy (`VACUUM INTO`) under **Admin → Backup**; it lands in
`$CC_DATA_DIR/backups/`. Copying the database file while the server is stopped works too. Food photos are
stored in the database, so they're included.

**Upgrades:** a newer server upgrades the database schema on start. It first saves the old database as
`$CC_DATA_DIR/backups/pre-migration-v<N>-<time>.db` and migrates in one transaction. To go back to an older
server version, restore that copy: an older server refuses to open a newer database.

### Database migrations

The `.sq` files hold the current schema; each change also gets a migration
`shared/src/main/sqldelight/migrations/<N>.sqm` (from version N to N+1). After a release, snapshot the new
schema with `./gradlew :shared:generateMainCalorieCompanionDatabaseSchema` (writes `databases/<N+1>.db`).
`./gradlew check` verifies that the snapshots plus migrations equal the `.sq` schema, and `MigrationTest`
upgrades a version 1 database full of data.

**Remote access:** the server speaks plain HTTP. To reach it from outside your LAN, put it behind a VPN
(Tailscale, WireGuard) or a reverse proxy with TLS (Caddy, nginx, Traefik).

## API overview

All endpoints except health, setup and login need `Authorization: Bearer <token>`.
Errors are JSON: `{"error": "NAME_TAKEN", "message": "...", "details": {}}`.

| Endpoint | |
|---|---|
| `GET /api/health` | Status, server version, API version |
| `GET/POST /api/v1/setup` | First-run status / create the first (admin) user |
| `POST /api/v1/auth/login`, `/auth/logout` | Sessions |
| `GET /api/v1/me`, `PUT /me/password`, `PUT /me/locale` | Own account |
| `GET/POST /api/v1/admin/users`, `PATCH /admin/users/{id}`, `POST /admin/backup` | Admin |
| `/api/v1/units`, `/nutrients`, `/foods` | List (`?includeArchived`, foods also `?q=`), create, `GET /{id}` (detail with links), `PUT /{id}`, `POST /{id}/archive`, `/unarchive`, `DELETE /{id}` (409 `REFERENCED` if in use). Names are returned in the language of `Accept-Language` (else the user's saved language) |
| `GET /api/v1/foods/ref-default` | The reference amount a new food starts with |
| `GET/PUT/DELETE /api/v1/foods/{id}/image` | A food's photo (`?size=thumbnail`, `?v=<imageVersion>` for permanent caching); PUT takes base64 JSON |
| `PUT /api/v1/nutrients/order` | Display order |
| `GET /api/v1/days/{date}` | Day view: entries with calculated nutrients, totals, target status |
| `POST /api/v1/entries`, `POST /entries/preview`, `GET/PUT/DELETE /entries/{id}` | Entries. Logging a composite food creates one entry per ingredient and returns the first |
| `GET/POST /api/v1/targets`, `DELETE /targets/{id}` | Versioned daily targets |
| `GET /api/v1/history?from=&to=` | Per-day totals and summary |
| `GET /api/v1/history/contributions?from=&to=&nutrientId=` | Each entry's amount of one nutrient per day (for splitting bars by food) |
| `GET /api/v1/export`, `POST /api/v1/import?onConflict=skip\|overwrite` | Portable JSON export/import |

## Android app

*The Android app doesn't show composite foods, photos, translations, plural endings or split totals yet;
it keeps working with them (composite foods logged from it are split by the server or the shared code).*

On first launch the app asks how to run:

- **Use on this device** — everything is stored in an on-device database (no account). The same
  calculation code as the server runs on the phone.
- **Connect to a server** — enter the server address (e.g. `http://192.168.1.10:8080`, or
  `http://10.0.2.2:8080` from the Android emulator) and your username and password. Server mode is
  online-only.

**More → Settings** switches mode (data is not moved; use **Export**/**Import** to move it), changes the
password in server mode, and exports/imports JSON files through the system file picker. The language
follows the phone; Android 13+ also lets you pick it per app.

## Android release APK

Sideloaded APKs are signed with your own key. Create `android/keystore.properties` (git-ignored):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then `./gradlew :android:assembleRelease`.
