# Calorie Companion

A customizable nutrition tracker: web GUI, native Android app and a self-hostable server.
See [REQUIREMENTS.md](REQUIREMENTS.md) for the specification.

## Layout

| Module | What | Tech |
|---|---|---|
| `shared/` | Domain logic, API DTOs, SQLite schema — used by server and Android local mode | Kotlin (JVM), SQLDelight |
| `server/` | REST API (`/api/v1`) + serves the built web GUI | Kotlin, Ktor, SQLite |
| `web/` | Web GUI | React, TypeScript, Vite, react-i18next |
| `android/` | Android app (local or server mode) | Kotlin, Jetpack Compose |

## Prerequisites

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

./gradlew :android:assembleDebug   # android/build/outputs/apk/debug/android-debug.apk
```

## Running the server

Configuration via environment variables:

| Variable | Default | |
|---|---|---|
| `CC_HOST` | `0.0.0.0` | Bind address |
| `CC_PORT` | `8080` | Port |
| `CC_DATA_DIR` | `data` | Directory holding the SQLite database |
| `CC_WEB_DIR` | *(unset)* | Directory with the built web GUI; unset = API only |

**Docker** (API + web GUI in one image):

```sh
docker build -t calorie-companion .
docker run -d -p 8080:8080 -v calorie-companion-data:/data calorie-companion
```

**Plain JAR:**

```sh
./gradlew :server:buildFatJar
(cd web && npm run build)
CC_WEB_DIR=web/dist java -jar server/build/libs/calorie-companion-server.jar
```

## Android release APK

Sideloaded APKs are signed with your own key. Create `android/keystore.properties` (git-ignored):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then `./gradlew :android:assembleRelease`.
