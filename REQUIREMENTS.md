# Calorie Companion — Requirement Specification

Version 0.9 · 2026-10-04 · Supersedes `requirements.txt`

*0.9: tags for foods (F-16).*

*0.8: each user's own order of their units (U-10).*

*0.7: "ingredient only" foods (F-15).*

*0.6: units, nutrients and foods record who made them and when; units and nutrients also when and by whom they were last changed (U-9, F-14).*

*0.5: units and nutrients are shared by all users of a server (U-9). Each user hides the ones they don't want instead of archiving them; foods, entries and targets stay per user.*

*0.4 adds composite foods (F-10, now in scope), remembered reference amounts (F-12), food photos (F-13), plural endings (U-8), nutrient groups (N-6), totals split by food (H-5) and names per language (L-5). These are implemented in the server and web GUI; the Android app doesn't show them yet (it keeps working against the new server and in local mode).*

Keywords: **MUST** = required for v1, **SHOULD** = v1 if feasible, **FUTURE** = explicitly out of v1 scope but the design must not block it.

---

## 1. Overview

A heavily customizable nutrition tracker. Users maintain their own catalog of **food stuffs** and share the server's **units** and **nutrients**, and log **entries** (a quantity of a food stuff in a given unit at a date/time). The app calculates nutrient totals per entry and per day, compares them to daily targets, and shows history over time.

Clients:
- **Web GUI** — React + TypeScript, talks to a server.
- **Android app** — native Kotlin + Jetpack Compose. Runs either in **local mode** (embedded, on-device backend) or **server mode** (connects to a self-hosted server).

---

## 2. Glossary

| Term | Norwegian UI term (provisional) | Meaning |
|---|---|---|
| Food stuff | Matvare | Anything consumable: ingredient, meal, drink, etc. |
| Unit | Enhet | A way to quantify a food stuff (g, ml, slice, serving, glass). |
| Standard unit | Standardenhet | A unit with a fixed global conversion within a dimension (mass: g, kg, oz, lb; volume: ml, dl, l, tsp, tbsp, cup). |
| Custom unit | Egendefinert enhet | A unit whose size is defined per food stuff (slice, serving, piece, glass). |
| Reference amount | Referansemengde ("per 100 g") | The amount a food stuff's nutrient values are given per, e.g. "per 100 g" or "per 1 piece". |
| Nutrient | Næringsstoff | A measurable property, e.g. energy (kcal), protein (g), vitamin C (mg). |
| Entry | Registrering | One logged consumption: food stuff + unit + quantity + date/time. (Called "food serving / meal" in the original spec.) |
| Catalog | Katalog | A user's set of food stuffs, units and nutrients. |
| Day view | Dagsoversikt | One day's entries and totals. |
| Daily target | Dagsmål | Min/max goal for a nutrient per day. |
| History | Historikk | Week/month views and trends. |
| Archive | Arkiver | Hide a food from pickers while keeping history. |
| Hide | Skjul | Take a shared unit or nutrient out of one's own lists and pickers (U-9). |

The Norwegian terms are a first guess and may be changed; they live only in the localization files.

---

## 3. Functional requirements

### 3.1 Food stuffs

- **F-1 (MUST)** Create a food stuff with only a **name**. Everything else is optional and can be edited later.
- **F-2 (MUST)** The name is unique per user, compared **case-insensitively after trimming whitespace** ("Milk" and " milk " clash). Internally all relations use surrogate IDs, so **renaming is allowed** and all links follow.
- **F-3 (MUST)** A food stuff has an optional **reference amount**: a quantity + unit (e.g. 100 g, 100 ml, 1 piece). Nutrient values are stored per reference amount.
- **F-4 (MUST)** A food stuff has zero or more **nutrient values** (nutrient → amount per reference amount).
- **F-5 (MUST)** A food stuff has zero or more linked **units** (see 3.2). For custom units, the link defines the unit's size for this food (e.g. 1 slice = 35 g).
- **F-6 (MUST)** The food stuff detail view links to:
  - all **entries** it appears in, grouped by date, each showing the unit name (tooltip on web, long-press or secondary text on Android) and quantity
  - all **units** it is linked to, each with its conversion.
- **F-7 (MUST)** Can be **archived**: hidden from pickers, but still shown in history and links. Can be un-archived.
- **F-8 (MUST)** Hard delete is **only allowed when nothing references it**. Otherwise the UI refuses and offers archiving.
- **F-9 (SHOULD)** Optional notes/description field.
- **F-10 (MUST)** **Composite food stuffs**: a food stuff made of other food stuffs (ingredient = food + quantity + unit), e.g. a recipe, or a meal eaten often ("Breakfast" = 2 slices of bread + 1 glass of milk).
  - Its nutrients are the sum of its ingredients' (each calculated like an entry, C-1), **live** (E-4), for its **yield**: how much the ingredients make, as an amount + unit set by the user (a new composite starts as "1 serving" if such a unit exists), or else the ingredients' total weight. The yield is stored apart from the reference amount (F-3); the hand-entered reference amount and values are kept and come back if the food stops being composite.
  - **Logging** a composite food logs **each ingredient as its own entry**, scaled to the amount logged (½ serving → half of each), recursively for composite ingredients, and each entry remembers the composite it came from. Per food, it can instead be set to be logged **as one entry** (e.g. a recipe like lasagne).
  - Ingredients may be composite; a food can't contain itself, directly or indirectly. An ingredient can't be deleted while used (archive instead, F-8); a composite can.
  - No manual override of the derived values (FUTURE).
- **F-12 (MUST)** A new food stuff's reference amount (F-3) is **prefilled with the one last set** on a food (initially 100 g), remembered per user. Imports don't change it.
- **F-11 (FUTURE)** Mass↔volume conversion via optional per-food density.
- **F-14 (MUST)** A food stuff records **when it was made and by whom** (its owner), shown on its page. Admins can correct the date of their own foods (foods are private). An import keeps the date from the file for new foods; existing foods count as made when this was introduced (schema 7).
- **F-15 (MUST)** A food stuff can be marked **ingredient only**: it isn't offered in the food picker when logging, but can be an ingredient of composite foods (F-10). A composite that contains it can still be logged, and logging it as separate items still creates entries for this food. Existing entries of the food stay as they are, and editing one keeps the food. Shown as a badge in the food list and on the food's page; export/import keep it (format version 5).
- **F-16 (MUST)** **Tags** group a user's food stuffs (e.g. "fruit", "breakfast"). A tag has a main name and a name per language (L-5), unique among the user's tags; tags are per user, like food stuffs. A food can have several tags, chosen on its page, where a new tag can also be made. The food list shows each food's tags; a tag's page lists its foods. Tags are archived instead of deleted while a food has them: archived tags aren't offered when tagging, but foods keep them. A tag records when it was made and last changed (its names); admins can correct both for their own tags. Export/import include tags and each food's tags by name (format version 6). Used by the food list filters (planned).
- **F-13 (MUST)** A food stuff can have **one photo**, uploaded from a file or the camera. Clients shrink it before upload (max ~1280 px plus a square thumbnail); the server checks type (JPEG/PNG/WebP) and size and stores it in the database, so backups and export/import include it. The thumbnail is shown in the food picker, food list, entries and ingredients; the photo large on the food page.

### 3.2 Units

- **U-1 (MUST)** Create a unit with only a **name**. Same uniqueness and rename rules as F-2, but unique across the server (U-9).
- **U-2 (MUST)** A unit has a **kind**: `mass`, `volume` or `custom`.
  - `mass` and `volume` units have a **global factor** to the dimension's base (g or ml). E.g. kg = 1000 g.
  - `custom` units have no global size. Their size is defined per food stuff.
- **U-3 (MUST)** The server is **seeded** once with built-in units: g, kg, mg, oz, lb, ml, dl, l, tsp, tbsp, cup, serving, piece. Every user starts with them shown; only admins can change them.
- **U-4 (MUST)** **Automatic conversion**: if a food stuff's reference amount (or any linked unit) is in a standard unit, then **all** standard units of the same dimension are usable for that food without explicit linking.
- **U-5 (MUST)** A food-unit link for a custom unit is expressed as "1 ⟨custom unit⟩ = X ⟨other unit⟩", where the other unit is a standard unit or another unit already linked to the food. The system resolves this to the reference amount. A link without X is allowed (see F-incomplete, 3.5).
- **U-6 (MUST)** The unit detail view links to:
  - all **food stuffs** that have this unit linked (or can use it via U-4 — shown separately as "implicit")
  - all **dates** on which at least one entry used this unit.
- **U-7 (MUST)** Units are hidden instead of archived (U-9). Delete is only allowed when nothing, for any user, references the unit.
- **U-8 (MUST)** A unit name has an editable **plural ending**, added when the quantity isn't exactly 1 ("1 slice", "2 slices", "0.5 slices"). Default: "s"; Norwegian "er" ("r" after a final e); none for standard units (g, ml). Each translation (L-5) has its own. *Kept as a suffix for now; the rule lives in one helper per client so it can become full plural forms later.*
- **U-9 (MUST)** Units and nutrients are **shared by all users of a server**. Names are unique across the server in every language; creating a taken name offers to show the existing one.
  - Each user chooses which ones they **show**; the rest are **hidden** for them and listed in a "Hidden" section on the units and nutrients pages. A new unit or nutrient is shown for its maker and hidden for everyone else.
  - Hiding only declutters lists and pickers. A hidden unit that a food uses stays usable for that food (offered when logging it, shown in its entries); a hidden nutrient keeps its values in foods but leaves the user's totals and history.
  - Only the user who made one, and admins, can change or delete it; only admins the built-in ones (U-3, N-2).
  - Per user: which ones are shown, the unit and nutrient display orders (U-10, N-3) and targets.
  - Each records **who made it and when**, and **when and by whom it was last changed**. Only edits count as changes: hiding, showing and reordering don't. Shown on its page. Admins can correct all four; the new maker becomes the owner. An import keeps the file's dates for new ones (who changed it last is then unknown). Existing ones count as made when this was introduced (schema 7), and never changed.

- **U-10 (MUST)** Each user's own **order of the units they show**, set with move up and down controls on the units page, like nutrients (N-3). Units start in **alphabetical order** (by their name in the user's language), and a **reset** puts them back in it. A unit that is created or shown again goes last, unless the user hasn't set an order. The order applies on the units page and in the unit pickers (logging, a food's units, ingredients); in a picker for a food, the food's own units come first, each group in the user's order. Not exported.

### 3.3 Nutrients

- **N-1 (MUST)** Nutrients are **user-defined** and shared (U-9): name (unique across the server, same rules as F-2) + measurement unit (kcal, kJ, g, mg, µg, IU, or free text).
- **N-2 (MUST)** The server is **seeded** once with built-in nutrients: Energy (kcal), Protein (g), Carbohydrates (g), of which sugars (g), Fat (g), of which saturated (g), Fiber (g), Salt (g). Every user starts with them shown; only admins can change them.
- **N-2a** Energy is tracked in **kcal** by default. kJ is not built in: a user who wants it creates their own nutrient (e.g. "Energy (kJ)") with measurement unit kJ and enters its values separately. There is no automatic kcal↔kJ conversion.
- **N-3 (MUST)** Each user's own **display order** of the nutrients they show. The day view and food editor list nutrients in this order.
- **N-4 (MUST)** The nutrient detail view links to:
  - all **food stuffs** that have a value for this nutrient
  - all **entries** that contribute to it (via their food stuff).
- **N-5 (MUST)** Nutrients are hidden instead of archived (U-9). Delete is only allowed when no food stuff, of any user, has a value for it and no target uses it.
- **N-6 (MUST)** Optional parent, one level (e.g. "of which sugars" under carbohydrates); a group's heading is always a nutrient itself. Groups stay together everywhere: in the display order (moving a main nutrient moves its group; sub-nutrients only move within it), on the nutrients page, in the food editor (nutrition-label style, warning if a sub-value exceeds its main value), in day totals and in history. Parents are not summed automatically.

### 3.4 Entries (main function)

- **E-1 (MUST)** Register an entry with:
  - **food stuff** (required, picker with search; archived foods excluded)
  - **unit** (required; units usable for that food — explicitly linked + implicit standard units per U-4 — are offered first. Any other unit can also be picked: it is then linked to the food without a size and the entry is flagged incomplete until the size is set, see C-3)
  - **quantity** (required, positive decimal, default 1)
  - **date** (required, default today)
  - **time** (required, default now)
  - optional **note**.
- **E-2 (MUST)** While registering, show a live preview of the entry's nutrient totals.
- **E-3 (MUST)** On save, show the entry's nutrient totals and the **day totals** for all entries that day.
- **E-4 (MUST)** Nutrient totals are **calculated live** from the food stuff's current values. Editing a food stuff's nutrients or unit sizes retroactively changes all past entry totals. No snapshots.
- **E-5 (MUST)** Entries can be edited (all fields) and deleted.
- **E-6 (MUST)** Date and time are stored as the **user's local date + local time** (no UTC shifting), so an entry logged at 23:30 stays on that date regardless of later timezone changes.
- **E-7 (FUTURE)** Copy/repeat entries or a whole day. "Recent" and "frequent" sorting in the food picker.

### 3.5 Calculation rules

- **C-1** entry nutrient amount = `quantity × size_of_unit_in_reference_units / reference_amount × nutrient_value_per_reference`.
- **C-2** Unit size resolution follows U-4/U-5, possibly through a chain (slice → g → reference in 100 g).
- **C-3 (Incomplete data)** An entry is **incomplete** if its food has no reference amount, its unit can't be resolved to the reference, or the food lacks a value for a displayed nutrient. Incomplete entries are **allowed**. They contribute 0 for the missing nutrients and are visibly flagged. Day totals show a warning such as "2 entries lack data for Protein".
- **C-4** Values are stored as floating point and **rounded only for display** (per-nutrient display precision; default 0 decimals for kcal and 1 for grams).

### 3.6 Day view & targets

- **D-1 (MUST)** The day view lists the day's entries in **chronological order** by time, with per-entry totals and day totals per nutrient.
- **D-2 (MUST)** Easy day navigation (prev/next, date picker, "today").
- **T-1 (MUST)** **Daily targets** per nutrient: optional **min** and/or **max**. The same target applies to every day (no per-weekday or training/rest-day variation).
- **T-2 (MUST)** The day view shows progress against each target (e.g. bar + "1 650 / 2 000 kcal"), with distinct states for below min, within range and above max.
- **T-3 (SHOULD)** Targets are versioned by an effective-from date, so changing your targets doesn't rewrite how past days are judged.

### 3.7 History & charts

- **H-1 (MUST)** Week and month views: per-day totals for a chosen nutrient, with targets drawn as reference lines/bands.
- **H-2 (MUST)** Trend chart for one or more nutrients over a chosen date range, with daily values and an optional rolling average (7-day).
- **H-3 (MUST)** Summary stats for a range: average per day, days within target, days logged.
- **H-4 (SHOULD)** Days with no entries are shown as gaps, not zeros, and are excluded from averages.
- **H-5 (MUST)** **Totals split by food**: every day total has a bar (scaled to the target if any, coloured by target status; otherwise the full bar is the total). Hovering, tapping or focusing it splits it into one segment per entry, earliest first, each food in its own colour (several entries of the same food: separate segments, same colour), with a list of foods, amounts and shares; leaving restores the status colours. In history, hovering a day's bar stacks it the same way (earliest at the bottom). Colours come from a validated 8-colour palette in fixed order; beyond 8 foods, the smallest share a neutral colour.

### 3.8 Import / export

- **X-1 (MUST)** Export the user's complete data (catalog, entries, targets) to a **versioned JSON file**, from every client and mode.
- **X-2 (MUST)** Import that file into an **empty** catalog (migration: local ↔ server, restore from backup).
- **X-3 (SHOULD)** Import into a non-empty catalog with a merge strategy by name (skip/overwrite on conflict; *rename* is FUTURE). Entries identical to an existing entry are skipped, so re-importing a file is harmless.
- **X-4 (FUTURE)** Barcode scanning and Open Food Facts lookup to prefill food stuffs.

### 3.9 Localization

- **L-1 (MUST)** UI available in **English and Norwegian (bokmål)**, selectable per user or device, defaulting to the system locale.
- **L-2 (MUST)** Numbers and dates are formatted per locale (decimal comma in Norwegian). Decimal input accepts both `,` and `.`.
- **L-3 (MUST)** All UI strings are in resource files (web: i18n JSON; Android: `strings.xml`). No hardcoded strings.
- **L-4** The built-in units and nutrients have English main names and Norwegian translations (L-5).
- **L-5 (MUST)** Food stuffs, tags (F-16), units and nutrients have a main name and an optional **name per UI language**. The name shown is the one in the reader's language if set, else the main name (for a language without translations, English before the main name). Search matches all names; a name belongs to only one item of a kind, in any language (F-2). Web clients send their language with each request; otherwise the user's saved language applies.

---

## 4. Users, auth & modes

- **A-1 (MUST)** A server is **multi-user**. Each user has their own food stuffs, diary and targets; units and nutrients are shared (U-9).
- **A-2 (MUST)** On a fresh server (no users), the web GUI shows a **first-run setup screen** where the first user is created and becomes **admin**. The setup screen and its endpoint are disabled permanently once any user exists.
- **A-3 (MUST)** **No open registration.** Only admins create users, set initial passwords, reset passwords, disable users and grant or revoke admin. **Password reset is admin-only** (no email/self-service reset in v1).
- **A-4 (MUST)** Authentication with username + password (hashed with Argon2id or bcrypt). Clients get a token (session token or short-lived access + refresh token). Users can change their own password.
- **A-5 (MUST)** The **Android local mode** is single-user with no login. Data lives only on the device (plus manual exports).
- **A-6 (MUST)** The Android app chooses its mode at first launch (**Local** or **Server**: URL + login) and can switch later in settings. Switching mode does **not** move data (use export/import, X-1/X-2).
- **A-7 (MUST)** The **web GUI is server-only**. To use the web GUI "locally", run the server locally (e.g. Docker on localhost).
- **A-8 (FUTURE)** Multiple saved server profiles on Android.

### 4.1 Connectivity

- **O-1 (MUST)** Server mode is **online-only**. If the server is unreachable, clients show a clear "server unreachable" state with retry. No offline queue and no sync in v1.
- **O-2** Remote access from outside the LAN is the operator's responsibility (VPN such as Tailscale/WireGuard, or a reverse proxy with TLS).
- **O-3 (MUST)** Android must allow plain `http://` for user-entered LAN addresses (network security config), with a visible warning when not using HTTPS. (Revisit if the app is ever published on Play Store.)

---

## 5. Architecture & technology

```
┌──────────────┐      HTTPS/JSON      ┌──────────────────────────────┐
│ Web (React)  │ ───────────────────▶ │ Server (Kotlin + Ktor)       │
└──────────────┘                      │  ├─ shared domain module     │
┌──────────────┐      HTTPS/JSON      │  └─ SQLite (file on volume)  │
│ Android      │ ───────────────────▶ └──────────────────────────────┘
│ (Compose)    │
│  └─ local mode: shared domain module + on-device SQLite
└──────────────┘
```

| Part | Choice |
|---|---|
| Web GUI | React + TypeScript, Vite. Charts: Recharts (or similar). i18n: react-i18next. |
| Android | Kotlin, Jetpack Compose, Material 3. minSdk 26, compile/target SDK 37. |
| Server | Kotlin + Ktor, REST/JSON under `/api/v1`. |
| Shared code | A **pure-Kotlin `shared` module** (JVM library, bytecode targeting Java 17 so Android can consume it) containing the domain model, validation, unit resolution, nutrient calculation and the persistence schema. It is used by the server **and** by Android local mode, so the logic is written once. |
| Persistence | SQLite for both server and Android, via SQLDelight (one schema, drivers for JDBC and Android). Queries stay within SQLite 3.18 (Android 8). Migrations are versioned. |
| API contract | The Kotlin DTOs in `shared/…/api` are the single source of truth. The web's TypeScript types (`web/src/api/types.gen.ts`) are **generated** from them by `TypeScriptTypesTest`, which fails the build when the file is stale. (An OpenAPI document is FUTURE.) |
| Android data layer | A `Repository` interface with two implementations: `LocalRepository` (shared module + on-device DB) and `RemoteRepository` (HTTP client). The UI is mode-agnostic. |

### 5.1 Deployment

- **DEP-1 (MUST)** A single **Docker image** serving both the API and the built web GUI (static files) on one port.
- **DEP-2 (MUST)** The SQLite database lives on a mounted volume. Configuration comes from env vars (port, data dir, token lifetime).
- **DEP-3 (MUST)** Also runnable without Docker as a plain JAR for "local backend on my PC".
- **DEP-4 (SHOULD)** A built-in consistent backup (SQLite `VACUUM INTO` / online backup), triggerable by an admin endpoint or on a schedule.
- **DEP-5 (SHOULD)** A health endpoint (`/api/health`) and the server version exposed to clients (so clients can warn about API version mismatches).
- **DEP-6 (MUST)** Android is distributed as a **sideloaded APK**, signed with a project release key, built by a Gradle release task. Play Store / F-Droid distribution is FUTURE.
- **DEP-7 (MUST)** Schema changes are versioned migrations (SQLDelight `.sqm`, verified against snapshots of each released schema). Before migrating an existing database the server writes a copy to `data/backups/pre-migration-v<N>-<time>.db`, migrates in one transaction, and refuses to start on a database newer than it knows.

---

## 6. Data model (logical)

Tables of per-user data carry `user_id`; on units and nutrients it is the user who made them. Names are stored as entered, plus a normalized column (`lower(trim(name))`) with a unique index: per user for foods, per server for units and nutrients.

| Entity | Fields |
|---|---|
| user | id, username, password_hash, is_admin, is_disabled, locale, created_at, food_ref_amount, food_ref_unit_id (F-12) |
| quantity_unit | id, user_id (maker), name, name_norm, kind (mass/volume/custom), base_factor (nullable; for mass/volume), plural_suffix, built_in (U-3), created_at, updated_at, updated_by (U-9) |
| shown_unit | user_id, unit_id, sort_order (nullable: alphabetical) — the units a user shows, in their order (U-9, U-10) |
| unit_translation / nutrient_translation / food_translation / tag_translation | item id, locale, name, name_norm (+ plural_suffix for units) (L-5) |
| nutrient | id, user_id (maker), name, name_norm, measure_unit, display_precision, sort_order (default), parent_id (nullable), built_in (N-2), created_at, updated_at, updated_by (U-9) |
| shown_nutrient | user_id, nutrient_id, sort_order — the nutrients a user shows, in their order (U-9, N-3) |
| food | id, user_id, name, name_norm, ref_amount (nullable), ref_unit_id (nullable), notes, archived, yield_amount, yield_unit_id, log_as_whole (F-10), created_at (F-14), ingredient_only (F-15) |
| food_ingredient | food_id, position, ingredient_id, unit_id, quantity (F-10) |
| food_image | food_id, content_type, image, thumbnail, updated_at (F-13) |
| tag | id, user_id, name, name_norm, archived, created_at, updated_at (F-16) |
| food_tag | food_id, tag_id (F-16) |
| food_unit | food_id, unit_id, equals_amount (nullable), equals_unit_id (nullable) — "1 unit = equals_amount equals_unit" |
| food_nutrient | food_id, nutrient_id, amount (per ref amount) |
| entry | id, user_id, food_id, unit_id, quantity, local_date, local_time, note, created_at, updated_at, via_food_id (F-10) |
| target | id, user_id, nutrient_id, min (nullable), max (nullable), effective_from |

---

## 7. Non-functional requirements

- **NF-1** Day view and entry registration respond in < 300 ms on a LAN server with 5 years of daily data.
- **NF-2** Food picker search is incremental and matches substrings, case- and diacritic-insensitively (æøå handled sensibly).
- **NF-3** The web GUI is responsive and usable on phone-width screens.
- **NF-4** Accessibility: keyboard-navigable web UI, sufficient contrast, TalkBack labels on Android. Dark mode on both.
- **NF-5** Server ↔ client API is versioned. A client refuses to talk to an incompatible major version, with a clear message.
- **NF-6** The shared domain module has unit tests for all calculation rules (C-1 … C-4) and unit resolution (U-4, U-5), including incomplete-data cases.
- **NF-7** Android minSdk: 26 (Android 8).

---

## 8. Out of scope for v1 (tracked as FUTURE)

Manual override of composite foods' derived values (F-10) · per-weekday targets · kcal↔kJ conversion · email password reset · Play Store / F-Droid distribution · mass↔volume density (F-11) · copy/repeat entries and recent/frequent sorting (E-7) · barcode + Open Food Facts (X-4) · multiple server profiles (A-8) · offline queue / sync between local and server · sharing food stuffs between users.

---

## 9. Open questions

None at the moment.

---

## Appendix: Decisions from the requirements review (2026-09-30)

| Topic | Decision |
|---|---|
| Nutrient base | Per reference amount + unit conversions |
| Quantity | Positive decimal, default 1 |
| Editing food values | Recalculate history live (no snapshots) |
| Recipes | Future phase; model must allow it |
| Web tech | React + TypeScript |
| Android tech | Kotlin + Jetpack Compose |
| "Local backend" | Embedded on the phone (Android local mode) |
| Offline | Online-only in server mode |
| Users | Multi-user, fully separate catalogs; admin-created accounts |
| Server stack | Kotlin + Ktor, SQLite |
| Local ↔ server | JSON export/import |
| Nutrients | User-defined, seeded defaults |
| Units | Standard (auto-convert) + custom (per food) |
| Deletion | Blocked while referenced; archive instead |
| Names | Case-insensitive + trimmed unique; renamable |
| Entry time | Date + time of day |
| v1 extras | Daily targets, history/charts |
| Language | English + Norwegian |
| Incomplete data | Allowed, contributes 0, flagged |
| Targets per weekday | No; one daily target |
| Energy unit | kcal by default; kJ only as a user-created nutrient |
| Admin bootstrap | First-run setup screen in the web GUI |
| Password reset | Admin-only for now |
| Norwegian terms | Provisional guesses (see glossary), to be revised |
| Android distribution | Sideloaded APK initially |

## Appendix: Decisions from the second review (2026-10-01)

| Topic | Decision |
|---|---|
| Composite foods | Logging one logs each item as its own entry; a composite can also be a meal template ("Lasagna breakfast" = lasagna + wine). Per-food "log as one entry" added for recipes |
| New food reference | Prefilled with the last one used |
| Photos | One per food; small in picker and lists, large on the food page |
| Split totals | Per entry (same food = same colour), chronological; colours only while hovered, target colours otherwise; history bars the same |
| Translations | For every catalog item in every UI language; selected language, else the main name; English preferred for languages without translations |
| Plural | Editable suffix per name, kept separate so it can change later |
| Nutrient groups | One level; headings are nutrients |
| Clients | Web first; Android catches up later |
| Repository | Stays one repository (shared module used by server and Android local mode) |
| iOS | Not now (no Mac available) |
| Name | Not final; keep the visible name in one place, no new name-specific identifiers |

## Appendix: Decisions from the third review (2026-10-04)

These replace the "fully separate catalogs" and "archive instead" decisions above for units and nutrients.

| Topic | Decision |
|---|---|
| Shared units and nutrients | One list per server; each user shows or hides each one. Foods (and tags) stay per user; sharing foods is on hold |
| Archiving | Dropped for units and nutrients; hiding replaces it. Archived ones became hidden for their owner |
| Who may change one | Its maker and admins; built-in ones only admins |
| Built-ins | Created once per server with English and Norwegian names, owned by the first admin |
| Merging existing data | Same name (or a name that is another's translation) = the same unit or nutrient; the oldest one is kept |
| Export | Units and nutrients the data uses or the user shows, with whether they are hidden (format version 3) |
| Metadata | Units and nutrients: made by/at, last changed by/at (edits only, not hiding or order). Foods: made by/at. Admins correct it; tags get the same when they're added. Existing items: made at the upgrade. Export version 4 carries the dates; an import keeps them for new items |

