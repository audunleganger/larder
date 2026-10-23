# To do

Changes to stored data also need a schema migration, a new export format version and regenerated API types
(see [README → Development](README.md#development)).

## Features

- **Scan a nutrition label** when editing a food: take or upload a photo of the label and fill in the nutrient
  values from it. It must be free and work offline, so the text is read in the browser (for example with
  Tesseract.js), not by an online service. It only fills nutrients whose name (or a translation) matches a line on
  the label and that don't have a value yet, and shows what it filled so it can be checked before saving. To
  decide: what to do when the label's amount (per 100 g, per serving) differs from the food's reference amount.
- **Arithmetic in unit sizes**, such as "1 piece = 1/8 pizza" or "1 slice = 450/12 g" instead of working out the
  number by hand. Questions to answer:
  - Only in a food's unit sizes, or also in other number fields: the quantity when logging, ingredient amounts,
    nutrient values?
  - Which operators: + − × ÷ and parentheses? Decimal commas, as in Norwegian ("1,5/3")?
  - Is the expression kept and shown again when editing, or only the number it gives? Keeping it needs a new column
    and export field.
- **Loading animations and transitions** in the web GUI, with a setting to turn them off. Questions to answer:
  - Which ones: spinners or placeholders while pages and charts load, a busy state on buttons while saving, the
    photo upload, transitions between pages, bars growing into place?
  - Is the setting saved per user (on the server, follows them between devices) or per browser?
  - Should it be off by default for systems set to reduce motion?
- **Install the web GUI as an app (PWA)** on phones, with its own icon and window. Start with a manifest only:
  the app still loads from the server on each launch, so a new version shows the next time it's opened, and it
  doesn't work without a connection. Questions to answer:
  - Also add a service worker that keeps a copy of the app on the phone? It starts faster, but a new version then
    takes effect one launch late, or right away through an "Update available — reload" prompt. Real offline use
    (logging without a connection and syncing later) is a separate, much larger feature.
  - Should an app left open for days notice a new deploy, by checking when it comes back to the foreground, and
    ask to reload? `/api/health` reports the release version, or `main-<commit>` for images built from main
    (`LARDER_VERSION`), so a change in it means a new deploy.
  - The icon: wait for the Larder rename, which needs a new icon anyway?

## Changes

- **Only a name when adding a food.** The "new food" form on the foods page asks just for the name; the reference
  amount and unit are set on the food's page, which opens right after. The new food still starts with the
  remembered reference amount (F-12): the form sends it without showing it, or the food's page prefills it. Same
  change in the Android app.
- **Amounts in "Used in".** On a food's page, each composite food in the "Used in" list also shows how much of
  this food it contains, in the ingredient's unit, such as "Breakfast · 2 slices". If a composite has the food
  more than once, show each amount.

## Needs design: sizes for custom units

Imprecise custom units such as "portion" or "slice" could take modifiers like "small" and "large", each with its
own size. Wait with the implementation until a design is settled. Questions to answer:

- Is a modifier's size set per food (a large slice of this bread is 50 g) or as a general factor (large = 1.5×)?
- Is "large slice" its own unit, or a modifier stored on the entry next to the unit?
- How do modifiers work with translations and plural forms?

## On hold

- **Sharing foods between users**, with private foods as the exception. Shelved for now as too large for the
  gain: it raises questions about who may edit a shared food, edits changing other users' history, composite
  foods with private ingredients, and telling apart foods with the same name.
- **The logged amount in breakdown lists.** Each row in a total's list of foods (day view and history) could show
  the amount logged, such as "2 slices", next to the nutrient amount. For now the rows keep showing only the
  nutrient amount.

## Project

### Larder icon

Replace the default Vite favicon (`web/public/favicon.svg`) and the Android launcher icon with an icon for Larder.
