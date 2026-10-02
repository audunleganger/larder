# To do

## Rename to Larder

The GitHub repository is called `larder`, but the project itself is still named Calorie Companion. Rename it in
one go:

- **Visible name:** `appName` in `web/src/i18n/en.ts` and `nb.ts`, `<title>` in `web/index.html`, Android
  `app_name` and the strings that mention the name (`values/strings.xml`, `values-nb/strings.xml`), README.md,
  REQUIREMENTS.md, the e2e checks in `web/e2e/flow.mjs`.
- **Build names:** `rootProject.name` in `settings.gradle.kts`, `calorie-companion-server.jar`
  (`server/build.gradle.kts`, `Dockerfile`, `web/e2e/run.sh`), `calorie-companion-web` in `web/package.json`,
  the Docker image name in the README.
- **Code identifiers:** Kotlin packages `com.caloriecompanion.*`, the SQLDelight database `CalorieCompanionDatabase`
  (its schema task in the README is named after it), the Android `namespace`.
- **Names that existing installs depend on.** Each needs a fallback, or should stay as it is:
  - The database file `calorie-companion.db` (`Database.FILE_NAME` on the server, `LocalRepository.DATABASE_NAME`
    on Android). Open the old file if it exists.
  - The export format marker `calorie-companion-export` (`Transfer.FORMAT`). Keep importing the old one.
  - The Android `applicationId` `com.caloriecompanion`. Changing it installs a separate app and leaves the old
    app's local data behind.
  - The `CC_*` environment variables, used in existing compose files. Accept both names, or keep them.
  - The web GUI's `cc.token` and `cc.lang` localStorage keys. Changing them signs everyone out and resets the
    language.
- **Harmless to change:** the export file name `calorie-companion-<date>.json` (server and Android), the backup
  file prefix.
- Replace the default Vite favicon (`web/public/favicon.svg`) with an icon for the new name.
