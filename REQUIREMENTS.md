# Calorie Companion — Requirement Specification

Version 0.3 · 2026-09-30 · Supersedes `requirements.txt`

Keywords: **MUST** = required for v1, **SHOULD** = v1 if feasible, **FUTURE** = explicitly out of v1 scope but the design must not block it.

---

## 1. Overview

A heavily customizable nutrition tracker. Users maintain their own catalog of **food stuffs**, **units** and **nutrients**, and log **entries** (a quantity of a food stuff in a given unit at a date/time). The app calculates nutrient totals per entry and per day, compares them to daily targets, and shows history over time.

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
| Archive | Arkiver | Hide from pickers while keeping history. |

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
- **F-10 (FUTURE)** **Composite food stuffs (recipes)**: a food stuff made of ingredients (food + unit + quantity) with a yield, and nutrients derived from the ingredients (manual override possible). *The v1 data model must leave room for an ingredient table without breaking changes.*
- **F-11 (FUTURE)** Mass↔volume conversion via optional per-food density.

### 3.2 Units

- **U-1 (MUST)** Create a unit with only a **name**. Same uniqueness and rename rules as F-2.
- **U-2 (MUST)** A unit has a **kind**: `mass`, `volume` or `custom`.
  - `mass` and `volume` units have a **global factor** to the dimension's base (g or ml). E.g. kg = 1000 g.
  - `custom` units have no global size. Their size is defined per food stuff.
- **U-3 (MUST)** Every user's catalog is **seeded** with standard units: g, kg, mg, oz, lb, ml, dl, l, tsp, tbsp, cup. They are editable and archivable like any other unit.
- **U-4 (MUST)** **Automatic conversion**: if a food stuff's reference amount (or any linked unit) is in a standard unit, then **all** standard units of the same dimension are usable for that food without explicit linking.
- **U-5 (MUST)** A food-unit link for a custom unit is expressed as "1 ⟨custom unit⟩ = X ⟨other unit⟩", where the other unit is a standard unit or another unit already linked to the food. The system resolves this to the reference amount. A link without X is allowed (see F-incomplete, 3.5).
- **U-6 (MUST)** The unit detail view links to:
  - all **food stuffs** that have this unit linked (or can use it via U-4 — shown separately as "implicit")
  - all **dates** on which at least one entry used this unit.
- **U-7 (MUST)** Archive/delete rules identical to F-7/F-8.

### 3.3 Nutrients

- **N-1 (MUST)** Nutrients are **user-defined**: name (unique, same rules as F-2) + measurement unit (kcal, kJ, g, mg, µg, IU, or free text).
- **N-2 (MUST)** Every user's catalog is **seeded** with: Energy (kcal), Protein (g), Carbohydrates (g), of which sugars (g), Fat (g), of which saturated (g), Fiber (g), Salt (g). Editable, archivable.
- **N-2a** Energy is tracked in **kcal** by default. kJ is not built in: a user who wants it creates their own nutrient (e.g. "Energy (kJ)") with measurement unit kJ and enters its values separately. There is no automatic kcal↔kJ conversion.
- **N-3 (MUST)** User-defined **display order**. The day view and food editor list nutrients in this order.
- **N-4 (MUST)** The nutrient detail view links to:
  - all **food stuffs** that have a value for this nutrient
  - all **entries** that contribute to it (via their food stuff).
- **N-5 (MUST)** Archive/delete rules identical to F-7/F-8 (a nutrient is "referenced" if any food stuff has a value for it or a target exists).
- **N-6 (SHOULD)** Optional grouping or parent (e.g. sugars under carbohydrates) for display indentation only. Parents are not summed automatically.

### 3.4 Entries (main function)

- **E-1 (MUST)** Register an entry with:
  - **food stuff** (required, picker with search; archived foods excluded)
  - **unit** (required; restricted to units usable for that food: explicitly linked + implicit standard units per U-4)
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

### 3.8 Import / export

- **X-1 (MUST)** Export the user's complete data (catalog, entries, targets) to a **versioned JSON file**, from every client and mode.
- **X-2 (MUST)** Import that file into an **empty** catalog (migration: local ↔ server, restore from backup).
- **X-3 (SHOULD)** Import into a non-empty catalog with a merge strategy by name (skip/overwrite/rename on conflict).
- **X-4 (FUTURE)** Barcode scanning and Open Food Facts lookup to prefill food stuffs.

### 3.9 Localization

- **L-1 (MUST)** UI available in **English and Norwegian (bokmål)**, selectable per user or device, defaulting to the system locale.
- **L-2 (MUST)** Numbers and dates are formatted per locale (decimal comma in Norwegian). Decimal input accepts both `,` and `.`.
- **L-3 (MUST)** All UI strings are in resource files (web: i18n JSON; Android: `strings.xml`). No hardcoded strings.
- **L-4** User data (food names, custom units, nutrients) is **not** translated. The seeded defaults are created in the user's language at account creation.

---

## 4. Users, auth & modes

- **A-1 (MUST)** A server is **multi-user**, and each user has a **fully separate** catalog, diary and targets. No shared data between users.
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
| Persistence | SQLite for both server and Android. Proposed: SQLDelight (one schema, drivers for JDBC and Android). Migrations are versioned. |
| API contract | An OpenAPI spec generated from or maintained alongside the server. The TypeScript client for the web is **generated** from it. |
| Android data layer | A `Repository` interface with two implementations: `LocalRepository` (shared module + on-device DB) and `RemoteRepository` (HTTP client). The UI is mode-agnostic. |

### 5.1 Deployment

- **DEP-1 (MUST)** A single **Docker image** serving both the API and the built web GUI (static files) on one port.
- **DEP-2 (MUST)** The SQLite database lives on a mounted volume. Configuration comes from env vars (port, data dir, token lifetime).
- **DEP-3 (MUST)** Also runnable without Docker as a plain JAR for "local backend on my PC".
- **DEP-4 (SHOULD)** A built-in consistent backup (SQLite `VACUUM INTO` / online backup), triggerable by an admin endpoint or on a schedule.
- **DEP-5 (SHOULD)** A health endpoint (`/api/health`) and the server version exposed to clients (so clients can warn about API version mismatches).
- **DEP-6 (MUST)** Android is distributed as a **sideloaded APK**, signed with a project release key, built by a Gradle release task. Play Store / F-Droid distribution is FUTURE.

---

## 6. Data model (logical)

All tables except `user` carry `user_id`. Names are stored as entered, plus a normalized column (`lower(trim(name))`) with a unique index per user.

| Entity | Fields |
|---|---|
| user | id, username, password_hash, is_admin, is_disabled, locale, created_at |
| quantity_unit | id, user_id, name, name_norm, kind (mass/volume/custom), base_factor (nullable; for mass/volume), archived |
| nutrient | id, user_id, name, name_norm, measure_unit, display_precision, sort_order, parent_id (nullable), archived |
| food | id, user_id, name, name_norm, ref_amount (nullable), ref_unit_id (nullable), notes, archived |
| food_unit | food_id, unit_id, equals_amount (nullable), equals_unit_id (nullable) — "1 unit = equals_amount equals_unit" |
| food_nutrient | food_id, nutrient_id, amount (per ref amount) |
| entry | id, user_id, food_id, unit_id, quantity, local_date, local_time, note, created_at, updated_at |
| target | id, user_id, nutrient_id, min (nullable), max (nullable), effective_from |
| *(future)* food_ingredient | food_id, ingredient_food_id, unit_id, quantity; + food.yield |

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

Recipes/composite foods (F-10) · per-weekday targets · kcal↔kJ conversion · email password reset · Play Store / F-Droid distribution · mass↔volume density (F-11) · copy/repeat entries and recent/frequent sorting (E-7) · barcode + Open Food Facts (X-4) · multiple server profiles (A-8) · offline queue / sync between local and server · shared catalogs between users.

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
