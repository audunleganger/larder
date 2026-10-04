# To do

Changes to stored data also need a schema migration, a new export format version and regenerated API types
(see [README → Development](README.md#development)).

## Bug fixes

- **A composite food is offered as its own ingredient.** The ingredient picker (`IngredientsEditor.tsx`) lists
  every food. Leave out the food being edited, and also the foods that already contain it, since the server
  rejects both (`FoodService.kt`, "can't be an ingredient of itself").
- **Non-square photos look zoomed in.** The thumbnail is a centre square cropped from the photo
  (`web/src/lib/images.ts`), and the large photo is cropped to its box (`object-fit: cover` on `.food-photo`).
  Show the whole image instead, padded to a square with white or with a blurred copy of the image's edges. The blur
  looks better in dark mode. Thumbnails already uploaded were cropped, so regenerate them from the stored photo.
- **Hovering a day total's segment replaces the popover heading** with that entry's time, food and amount
  (`TargetBar.tsx`). Keep the heading as it is; only the food's row in the list should be highlighted.

## Features

- **Tags for foods.** A tag has a main name and a name per language, like foods, units and nutrients (L-5), with
  the same uniqueness and archive/delete rules. A food can have several tags. Used by the food list filters below.
- **Photo from a URL**, besides file upload and the camera. The server downloads the image once and stores it like
  an upload (shrunk, with a thumbnail), so backups and exports include it and it survives the link breaking. It
  must refuse addresses on the local network (loopback, private and link-local, checked again after each
  redirect), limit size and download time, and accept only JPEG, PNG and WebP.
- **"Ingredient only" foods.** A flag on a food: it doesn't appear in the picker when logging, but can be used as
  an ingredient in composite foods. A composite that contains it can still be logged, and when it's logged as
  separate items it still creates an entry for this food. Existing entries of a food that gets the flag stay as
  they are, and editing one keeps the food selected.
- **Scan a nutrition label** when editing a food: take or upload a photo of the label and fill in the nutrient
  values from it. It must be free and work offline, so the text is read in the browser (for example with
  Tesseract.js), not by an online service. It only fills nutrients whose name (or a translation) matches a line on
  the label and that don't have a value yet, and shows what it filled so it can be checked before saving. To
  decide: what to do when the label's amount (per 100 g, per serving) differs from the food's reference amount.
- **The food list as a table.** One row per food and a column per nutrient, showing the values per reference
  amount (with the reference amount in its own column). Toggles above the table choose the nutrient columns. A
  missing value shows as a red dash (–). Composite foods show their calculated values. On phones the table
  scrolls sideways.
- **Filters for the food list:** by tag, composite only, ingredient only, and has or lacks a value for a given
  nutrient. They combine with the search.
- **Metadata on catalog items:** created date and user, last updated date and user, on foods, units, nutrients
  and tags. Read-only for normal users, editable by admins. This is groundwork for shared catalogs (REQUIREMENTS.md
  §8); until those exist, the user is always the catalog's owner. An import keeps the dates from the file.

## Changes

- **Full plural forms instead of a suffix**, so irregular plurals work (goose → geese). Each name and each
  translation gets its own plural form. Existing units migrate to name + suffix. The rule lives in `unitLabel`,
  in `web/src/lib/names.ts` and in `Names.kt` in the shared module, and new units get their default ending from
  `defaultPluralSuffix` (U-8).

## Quality of life

- **History: highlight the hovered segment.** Hovering a part of a day's stacked bar should dim the other parts
  and highlight the food's row in the tooltip list, like the day view does. Now only the whole bar reacts
  (`SplitBar` and `ChartTooltip` in `HistoryPage.tsx`).
- **Thicker total bars in the day view.** They're 8 px high, with 4 px segments when split (`.target-bar` and
  `.segments` in `web/src/index.css`).

## Needs design: sizes for custom units

Imprecise custom units such as "portion" or "slice" could take modifiers like "small" and "large", each with its
own size. Wait with the implementation until a design is settled. Questions to answer:

- Is a modifier's size set per food (a large slice of this bread is 50 g) or as a general factor (large = 1.5×)?
- Is "large slice" its own unit, or a modifier stored on the entry next to the unit?
- How do modifiers work with translations and plural forms?

## On hold

- **The logged amount in breakdown lists.** Each row in a total's list of foods (day view and history) could show
  the amount logged, such as "2 slices", next to the nutrient amount. For now the rows keep showing only the
  nutrient amount.

## Project

- **CI and a published Docker image.** A GitHub Actions workflow runs `./gradlew build` and the web tests and
  lint on every push, and publishes an image to ghcr.io for each version tag. Deploying then means pulling a tag
  instead of building from a checkout.

### Rename to Larder

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
