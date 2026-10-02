# Calorie Companion

A self-hosted nutrition tracker where you define the foods, units and nutrients yourself. It has a web app, an
Android app, and a small Kotlin server that keeps everything in one SQLite file.

Most trackers come with a fixed food database. This one starts nearly empty: you add the foods you actually eat,
in the units you actually use ("1 slice", "1 glass", "100 g"), and track whichever nutrients you care about. You
can log a food before you know its nutrition. Fill in the numbers later, and every past day is recalculated.

## Features

- **Your own catalog.** Foods, units and nutrients are user-defined, with sensible defaults to start from. A new
  food needs only a name.
- **Units that convert.** Nutrient values are stored per reference amount (for example per 100 g). Weight and
  volume units convert automatically. Custom units like slice, glass or serving get their size per food.
- **Composite foods.** Build a food out of other foods, like a recipe or a meal you often eat. Its nutrients are
  calculated from the ingredients. Logging it adds each ingredient as its own entry, or one entry if you set it to.
- **Live totals.** Correct a food's values, and every day it was eaten is recalculated. Entries with missing data
  are allowed and flagged.
- **Day view with targets.** A live preview while logging, and day totals against min/max targets. Hover or tap
  a total to see which foods it came from.
- **History.** Daily charts with a 7-day average and targets, plus summaries per nutrient.
- **Photos.** One per food, shown in the picker, the lists and the day view.
- **English and Norwegian (bokmål).** Foods, units and nutrients can have a name in each language, with plural
  endings ("2 slices", "2 skiver").
- **Multi-user.** First-run setup, accounts managed by an admin, a separate catalog and diary per user, JSON
  export/import and built-in backups.
- Light and dark mode, and a layout that works on phones.

## Running it

### Docker

```sh
git clone https://github.com/audunleganger/larder.git
cd larder
docker build -t calorie-companion .
docker run -d --name calorie-companion -p 8080:8080 -v calorie-companion-data:/data calorie-companion
```

Open http://localhost:8080. The first visit shows a setup screen that creates the administrator account. The
admin can add more users under **Admin**; each user has a completely separate catalog and diary.

With Docker Compose:

```yaml
services:
  calorie-companion:
    build: https://github.com/audunleganger/larder.git#main
    image: calorie-companion
    ports:
      - "8080:8080"
    volumes:
      - data:/data
    restart: unless-stopped

volumes:
  data:
```

The image runs as a non-root user. To use a host directory instead of a named volume (`./data:/data`), add
`user: "<uid>:<gid>"` with the owner of that directory.

### Without Docker

Needs Java 21 or newer and Node.js 22.12 or newer.

```sh
./gradlew :server:buildFatJar
(cd web && npm ci && npm run build)
CC_WEB_DIR=web/dist java -jar server/build/libs/calorie-companion-server.jar
```

### Configuration

The server reads these environment variables:

| Variable | Default | |
|---|---|---|
| `CC_HOST` | `0.0.0.0` | Bind address |
| `CC_PORT` | `8080` | Port |
| `CC_DATA_DIR` | `data` (`/data` in the Docker image) | Directory for the SQLite database and `backups/` |
| `CC_WEB_DIR` | *(unset; set in the Docker image)* | Directory with the built web GUI. Unset means API only |
| `CC_TOKEN_LIFETIME_DAYS` | `30` | How long a login lasts without use |

### Backups

An admin can make a consistent copy of the database under **Admin → Backup**. It's saved in
`$CC_DATA_DIR/backups/`. Copying the database file while the server is stopped works too. Food photos are stored
in the database, so backups include them.

Each user can also download all their data as a JSON file under **Settings**, and import it on another server or
in the Android app.

### Upgrading

Build the new version and restart. A newer server upgrades the database on start: it first saves a copy as
`$CC_DATA_DIR/backups/pre-migration-v<N>-<time>.db`, then migrates in one transaction, so a failed upgrade
changes nothing. An older server refuses to open a newer database, so going back to an older version means
restoring that copy.

### Remote access

The server speaks plain HTTP. To reach it from outside your network, put it behind a VPN (WireGuard, Tailscale)
or a reverse proxy with TLS (Caddy, nginx, Traefik).

## Android app

> The Android app is behind the web GUI: it doesn't show composite foods, photos, translations, plural endings or
> split totals yet. It still works with data that uses them.

On first launch the app asks how to run:

- **Use on this device.** Everything is stored on the phone, with no account. It runs the same calculation code
  as the server.
- **Connect to a server.** Enter the server address (for example `http://192.168.1.10:8080`, or
  `http://10.0.2.2:8080` from the Android emulator) and your username and password. This mode needs a connection
  to the server.

**More → Settings** switches between the two (data isn't moved; use export and import for that), changes your
password in server mode, and exports or imports JSON files. The language follows the phone; Android 13 and newer
also let you pick it per app.

There are no prebuilt APKs yet. To build one, see [Development](#development). A release APK is signed with your
own key: create `android/keystore.properties` (it's git-ignored)

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

and run `./gradlew :android:assembleRelease`.

## Development

| Module | What | Tech |
|---|---|---|
| `shared/` | Domain logic, calculations, services, API types and the database schema. Used by the server **and** the Android app's on-device mode | Kotlin (JVM), SQLDelight |
| `server/` | REST API under `/api/v1`, and serves the built web GUI | Kotlin, Ktor, SQLite |
| `web/` | Web GUI | React, TypeScript, Vite, TanStack Query, Recharts, react-i18next |
| `android/` | Android app | Kotlin, Jetpack Compose, Material 3 |

### Prerequisites

- **Java** of any recent version, to launch `./gradlew`. Gradle downloads JDK 21 itself
  (`gradle/gradle-daemon-jvm.properties`), so a JRE is enough; a full JDK 21 makes IDE setup easier.
- **Node.js** 22.12 or newer, for `web/`.
- **Android SDK** with platform 37, for `android/`. Install it with Android Studio or the command-line tools, then
  set `ANDROID_HOME` or `sdk.dir` in `local.properties`. Without an SDK the Android module is skipped and
  everything else still builds.

### Commands

```sh
./gradlew build                    # build and test shared and server (and android, if an SDK is set up)
./gradlew :server:run              # API on http://localhost:8080, data in ./data

cd web
npm install
npm run dev                        # web GUI on http://localhost:5173, sends /api to :8080
npm test                           # unit tests (Vitest)
npm run lint                       # oxlint
npm run e2e                        # browser walkthrough against a fresh server (needs Chromium; set CHROMIUM_PATH to override)

./gradlew :android:assembleDebug   # android/build/outputs/apk/debug/android-debug.apk
```

### API types

The Kotlin classes in `shared/src/main/kotlin/com/caloriecompanion/shared/api` define the API.
`web/src/api/types.gen.ts` is generated from them. After changing one, regenerate it with

```sh
./gradlew :shared:test -PupdateTsTypes=true
```

`./gradlew build` fails if the generated file is out of date.

### Database migrations

The `.sq` files in `shared/src/main/sqldelight` hold the current schema. Every change also needs a migration,
`migrations/<N>.sqm`, which upgrades version N to N+1. After a release, snapshot the new schema with
`./gradlew :shared:generateMainCalorieCompanionDatabaseSchema` (it writes `databases/<N+1>.db`).
`./gradlew check` verifies that the snapshots plus the migrations match the `.sq` schema, and `MigrationTest`
upgrades a version 1 database full of data.

### API

All endpoints except health, setup and login need `Authorization: Bearer <token>`. Errors are JSON:
`{"error": "NAME_TAKEN", "message": "...", "details": {}}`.

<details>
<summary>Endpoints</summary>

| Endpoint | |
|---|---|
| `GET /api/health` | Status, server version, API version |
| `GET/POST /api/v1/setup` | First-run status; create the first (admin) user |
| `POST /api/v1/auth/login`, `/auth/logout` | Sessions |
| `GET /api/v1/me`, `PUT /me/password`, `PUT /me/locale` | Own account |
| `GET/POST /api/v1/admin/users`, `PATCH /admin/users/{id}`, `POST /admin/backup` | Admin |
| `/api/v1/units`, `/nutrients`, `/foods` | List (`?includeArchived`; foods also `?q=`), create, `GET /{id}` (with links), `PUT /{id}`, `POST /{id}/archive`, `/unarchive`, `DELETE /{id}` (409 `REFERENCED` if in use). Names come in the language of `Accept-Language`, else the user's saved language |
| `GET /api/v1/foods/ref-default` | The reference amount a new food starts with |
| `GET/PUT/DELETE /api/v1/foods/{id}/image` | A food's photo (`?size=thumbnail`; `?v=<imageVersion>` for permanent caching). PUT takes base64 JSON |
| `PUT /api/v1/nutrients/order` | Display order |
| `GET /api/v1/days/{date}` | Day view: entries with calculated nutrients, totals, target status |
| `POST /api/v1/entries`, `POST /entries/preview`, `GET/PUT/DELETE /entries/{id}` | Entries. Logging a composite food creates one entry per ingredient and returns the first |
| `GET/POST /api/v1/targets`, `DELETE /targets/{id}` | Daily targets, each valid from a date |
| `GET /api/v1/history?from=&to=` | Totals per day, and a summary |
| `GET /api/v1/history/contributions?from=&to=&nutrientId=` | Each entry's amount of one nutrient per day (for splitting bars by food) |
| `GET /api/v1/export`, `POST /api/v1/import?onConflict=skip\|overwrite` | JSON export and import |

</details>

### Specification and plans

[REQUIREMENTS.md](REQUIREMENTS.md) is the full specification, including what's deliberately left for later.
[TODO.md](TODO.md) lists planned work.

## License

Copyright © 2026 Audun Leganger.

Licensed under the [GNU Affero General Public License v3.0](LICENSE). You may use, change and share it. If you run
a modified version as a service for others, you must make your source code available to its users.
