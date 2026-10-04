// End-to-end walkthrough of the web GUI against a fresh server (see e2e/run.sh).
// Screenshots go to $E2E_SHOTS (default: e2e/screenshots). Exits non-zero if any step fails.
import { mkdirSync, readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { chromium } from 'playwright-core'

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:18090'
const SHOTS = process.env.E2E_SHOTS ?? new URL('./screenshots', import.meta.url).pathname
mkdirSync(SHOTS, { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH ?? '/usr/bin/chromium' })
let failures = 0
const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'en-US', acceptDownloads: true })
context.setDefaultTimeout(6000)
const page = await context.newPage()
const problems = []
page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') problems.push(`[${m.type()}] ${m.text()}`) })
page.on('pageerror', (e) => problems.push(`[pageerror] ${e.message}`))
// A small PNG; the app shrinks and re-encodes photos in the browser before uploading.
const PHOTO_PNG = 'iVBORw0KGgoAAAANSUhEUgAAAHgAAABQCAIAAABd+SbeAAAAzElEQVR4nO3QQRHAIADAMEDXNKEOgVOx8liioNd59jP43rod8BdGR4yOGB0xOmJ0xOiI0RGjI0ZHjI4YHTE6YnTE6IjREaMjRkeMjhgdMTpidMToiNERoyNGR4yOGB0xOmJ0xOiI0RGjI0ZHjI4YHTE6YnTE6IjREaMjRkeMjhgdMTpidMToiNERoyNGR4yOGB0xOmJ0xOiI0RGjI0ZHjI4YHTE6YnTE6IjREaMjRkeMjhgdMTpidMToiNERoyNGR4yOGB0xOmJ0xOjIC/b/Af6iJ0AQAAAAAElFTkSuQmCC'
const shot = (name) => page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true })
const step = async (name, fn) => {
  try { await fn(); console.log('ok  ', name) }
  catch (e) { failures++; console.log('FAIL', name, e.message.split('\n')[0]); await shot(`fail-${name.replace(/\W+/g, '-')}`) }
}

await step('setup screen', async () => {
  await page.goto(BASE)
  await page.getByRole('heading', { name: 'Welcome' }).waitFor()
  await shot('01-setup')
  await page.getByLabel('Username').fill('audun')
  await page.getByLabel('Password', { exact: true }).fill('secret123')
  await page.getByLabel('Repeat password').fill('secret123')
  await page.getByRole('button', { name: 'Create account' }).click()
  await page.getByRole('heading', { name: 'Log food' }).waitFor()
  await shot('02-day-empty')
})

await step('create food via foods page', async () => {
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByPlaceholder('Name, e.g. Rye bread').fill('Rye bread')
  await page.getByRole('button', { name: 'Create' }).click()
  await page.getByRole('heading', { name: 'Rye bread' }).waitFor()
  await page.getByLabel('Reference amount').fill('100')
  await page.getByLabel('Reference unit').selectOption({ label: 'g' })
  await page.getByLabel('Energy', { exact: true }).fill('250')
  await page.getByLabel('Protein', { exact: true }).fill('9,5')
  await page.getByLabel('Carbohydrates', { exact: true }).fill('45')
  await page.getByRole('button', { name: 'Add unit' }).click()
  const row = page.locator('.link-row').first()
  await row.getByLabel('Unit', { exact: true }).selectOption({ label: 'serving' })
  await row.getByLabel('Amount').fill('35')
  await page.getByRole('button', { name: 'Save' }).click()
  await page.getByText('= 35 g').waitFor()
  await shot('03-food-detail')
})

await step('log entry from day view', async () => {
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  const picker = page.getByPlaceholder('Search foods…')
  await picker.fill('rye')
  await page.getByRole('option', { name: 'Rye bread' }).click()
  await page.getByLabel('Quantity').first().fill('2')
  // Unit names take their plural form when the quantity isn't 1.
  await page.locator('.entry-form select').selectOption({ label: 'servings' })
  await page.locator('.preview .chip').first().waitFor()
  await shot('04-day-preview')
  await page.getByRole('button', { name: 'Add', exact: true }).click()
  await page.locator('.entry').first().waitFor()
  const total = await page.locator('.total').first().innerText()
  if (!total.includes('175')) throw new Error(`energy total was: ${total}`)
})

await step('quick-create food from picker and log incomplete', async () => {
  await page.getByPlaceholder('Search foods…').fill('Mystery soup')
  await page.getByRole('option', { name: /Create/ }).click()
  await page.locator('.entry-form select').selectOption({ label: 'serving' })
  await page.getByText(/no size for this food/).waitFor()
  await page.getByRole('button', { name: 'Add', exact: true }).click()
  await page.getByText('incomplete').first().waitFor()
  await page.getByText(/can’t be calculated yet/).waitFor()
  await shot('05-day-with-entries')
})

await step('new foods start with the last used reference amount', async () => {
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  const amount = page.getByRole('textbox', { name: 'Reference amount' })
  const unit = page.getByRole('combobox', { name: 'Reference unit' })
  if ((await amount.inputValue()) !== '100') throw new Error('expected 100, got ' + (await amount.inputValue()))
  await amount.fill('1')
  await unit.selectOption({ label: 'dl' })
  await page.getByPlaceholder('Name, e.g. Rye bread').fill('Orange juice')
  await page.getByRole('button', { name: 'Create' }).click()
  await page.getByRole('heading', { name: 'Orange juice' }).waitFor()
  if ((await page.getByLabel('Reference amount').inputValue()) !== '1') throw new Error('juice not created per 1 dl')
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByRole('link', { name: 'Orange juice' }).waitFor()
  if ((await amount.inputValue()) !== '1') throw new Error('default not remembered')
  if ((await unit.locator('option:checked').innerText()) !== 'dl') throw new Error('default unit not remembered')
  await page.getByRole('link', { name: 'Day', exact: true }).click()
})

await step('targets', async () => {
  await page.getByRole('link', { name: 'Targets', exact: true }).click()
  const row = page.getByRole('row', { name: /Energy/ })
  await row.getByLabel('Energy Min').fill('1800')
  await row.getByLabel('Energy Max').fill('2200')
  await row.getByRole('button', { name: 'Save' }).click()
  await page.getByText(/1[,.]?800–2[,.]?200 kcal/).first().waitFor()
  await shot('06-targets')
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await page.getByText('Below target').first().waitFor()
  await shot('07-day-target')
})

await step('edit entry', async () => {
  await page.locator('.entry').first().getByRole('button', { name: 'Edit' }).click()
  await page.getByRole('heading', { name: 'Edit entry' }).waitFor()
  await page.getByLabel('Quantity').first().fill('4')
  await page.getByRole('button', { name: 'Save' }).click()
  await page.getByRole('heading', { name: 'Log food' }).waitFor()
  await page.locator('.total-amount', { hasText: '350' }).first().waitFor()
})

await step('day totals split by food on hover', async () => {
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await page.locator('.total .target-bar').first().hover()
  await page.locator('.breakdown-popover').getByText('Rye bread').waitFor()
  if ((await page.locator('.segment').count()) < 1) throw new Error('no segments')
  await shot('07b-day-split')
  await page.mouse.move(0, 0)
  await page.locator('.breakdown-popover').waitFor({ state: 'detached' })
  // Keyboard: focusing a bar splits it too.
  await page.locator('.total .target-bar').first().focus()
  await page.locator('.breakdown-popover').waitFor()
  await page.keyboard.press('Escape')
})

await step('units pages', async () => {
  await page.getByRole('link', { name: 'Units' }).click()
  await page.getByRole('link', { name: 'serving' }).click()
  await page.getByText('Rye bread').first().waitFor()
  await shot('08-unit-detail')
  await page.getByRole('button', { name: 'Delete' }).click()
  await page.getByRole('button', { name: 'Yes, do it' }).click()
  await page.getByText(/still used by/).waitFor()
})

await step('unit order', async () => {
  await page.getByRole('link', { name: 'Units', exact: true }).click()
  const rows = page.locator('table').first().locator('tbody tr td:first-child')
  await rows.first().waitFor()
  const before = await rows.allInnerTexts()
  if (before.join() !== [...before].sort((a, b) => a.localeCompare(b, 'en')).join()) throw new Error(`not alphabetical: ${before}`)
  await page.getByRole('button', { name: `Move ${before[1]} up` }).click()
  await page.getByRole('button', { name: 'Reset to alphabetical' }).waitFor()
  await page.waitForFunction(([first]) => document.querySelector('table tbody tr td')?.textContent === first, [before[1]])
  await shot('08b-unit-order')
  await page.getByRole('button', { name: 'Reset to alphabetical' }).click()
  await page.getByRole('button', { name: 'Reset to alphabetical' }).waitFor({ state: 'detached' })
  if ((await rows.allInnerTexts()).join() !== before.join()) throw new Error('reset did not restore alphabetical order')
})

await step('plural forms', async () => {
  // A new unit's plural follows the default for its name until it's edited; irregular plurals work.
  await page.getByRole('link', { name: 'Units', exact: true }).click()
  await page.getByLabel('Name', { exact: true }).fill('loaf')
  const plural = page.getByLabel('Plural', { exact: true }).first()
  if ((await plural.inputValue()) !== 'loafs') throw new Error(`default plural was: ${await plural.inputValue()}`)
  await plural.fill('loaves')
  await page.getByText('e.g. “2 loaves”').waitFor()
  await page.getByRole('button', { name: 'Create' }).click()
  await page.getByRole('link', { name: 'loaf', exact: true }).click()
  await page.getByRole('heading', { name: 'loaf', exact: true }).waitFor()
  const saved = page.getByLabel('Plural', { exact: true }).first()
  if ((await saved.inputValue()) !== 'loaves') throw new Error(`saved plural was: ${await saved.inputValue()}`)
  await shot('08c-unit-plural')
})

await step('nutrients pages', async () => {
  await page.getByRole('link', { name: 'Nutrients' }).click()
  await page.getByRole('button', { name: 'Move Protein up' }).click()
  await page.waitForTimeout(500)
  const first = await page.locator('.order-list li').first().innerText()
  if (!first.includes('Protein')) throw new Error('reorder failed: ' + first)
  await page.getByRole('button', { name: 'Move Protein down' }).click()
  await page.waitForTimeout(300)
  // Moving a main nutrient moves its whole group ("of which" nutrients come along).
  await page.getByRole('button', { name: 'Move Fat up' }).click()
  await page.waitForTimeout(500)
  const names = (await page.locator('.order-list .order-name a').allInnerTexts()).map((s) => s.trim())
  const fat = names.indexOf('Fat')
  if (!(fat < names.indexOf('Carbohydrates') && names[fat + 1] === 'Saturated fat')) throw new Error('group move failed: ' + names.join(', '))
  await page.getByRole('button', { name: 'Move Fat down' }).click()
  await page.waitForTimeout(300)
  await shot('09-nutrients')
  await page.getByRole('link', { name: 'Energy' }).click()
  await page.getByText('Rye bread').first().waitFor()
  await shot('10-nutrient-detail')
})

await step('history', async () => {
  await page.getByRole('link', { name: 'History' }).click()
  await page.locator('.recharts-surface').first().waitFor()
  await page.waitForTimeout(400)
  // Hovering a day's bar splits it by food, with the foods listed in the tooltip.
  await page.locator('.chart .recharts-bar-rectangle').last().hover()
  await page.locator('.chart-tooltip .breakdown-list').getByText('Rye bread').waitFor()
  await shot('11-history')
})

await step('settings and admin', async () => {
  await page.getByRole('link', { name: 'Settings' }).click()
  await page.getByRole('heading', { name: 'Settings' }).waitFor()
  await shot('12-settings')
  await page.getByRole('link', { name: 'Admin' }).click()
  await page.getByLabel('Username').fill('kari')
  await page.getByLabel('Password').fill('passord123')
  await page.getByRole('button', { name: 'Create user' }).click()
  await page.getByText('Created kari.').waitFor()
  await page.getByRole('button', { name: 'Back up now' }).click()
  await page.getByText(/Saved calorie-companion-/).waitFor()
  await shot('13-admin')
})

await step('shared units and nutrients', async () => {
  // Kari (made above) shares the units and nutrients, but only admins can change the built-in ones.
  const kariContext = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'en-US' })
  kariContext.setDefaultTimeout(6000)
  const kari = await kariContext.newPage()
  kari.on('pageerror', (e) => problems.push(`[pageerror kari] ${e.message}`))
  await kari.goto(BASE)
  await kari.getByLabel('Username').fill('kari')
  await kari.getByLabel('Password').fill('passord123')
  await kari.getByRole('button', { name: 'Log in' }).click()
  await kari.getByRole('link', { name: 'Units' }).click()
  await kari.getByRole('link', { name: 'serving', exact: true }).click()
  await kari.getByText('Only an administrator can change it.').waitFor()
  await kari.getByRole('button', { name: 'Hide', exact: true }).click()
  await kari.getByText('hidden', { exact: true }).waitFor()

  // Her own unit is hers to change; for others it's hidden until they show it.
  await kari.getByRole('link', { name: '← Units' }).click()
  await kari.getByLabel('Name', { exact: true }).fill('glass')
  await kari.getByRole('button', { name: 'Create' }).click()
  await kari.getByRole('link', { name: 'glass', exact: true }).waitFor()
  // Creating a unit she has hidden offers to show it instead.
  await kari.getByLabel('Name', { exact: true }).fill('Serving')
  await kari.getByRole('button', { name: 'Create' }).click()
  await kari.getByText('“serving” already exists, but it’s hidden for you.').waitFor()
  await kari.getByRole('button', { name: 'Show it' }).click()
  await kari.locator('.card', { hasText: 'Your units' }).getByRole('link', { name: 'serving', exact: true }).waitFor()
  await kari.screenshot({ path: `${SHOTS}/13b-units-kari.png`, fullPage: true })
  await kariContext.close()

  await page.getByRole('link', { name: 'Units' }).click()
  await page.getByRole('button', { name: /Hidden units \(1\)/ }).click()
  const hiddenCard = page.locator('.card', { hasText: 'Hidden units' }).last()
  await hiddenCard.getByRole('cell', { name: 'kari' }).waitFor()
  await shot('13c-units-hidden')
  await hiddenCard.getByRole('button', { name: 'Show' }).click()
  await page.locator('.card', { hasText: 'Your units' }).getByRole('link', { name: 'glass', exact: true }).waitFor()
})

await step('who made it and when', async () => {
  await page.locator('.card', { hasText: 'Your units' }).getByRole('link', { name: 'glass', exact: true }).click()
  const details = page.locator('.card', { hasText: 'Details' })
  await details.getByText(/^Made .* by kari\.$/).waitFor()
  await details.getByText('Not changed since.').waitFor()
  // An admin's edit is recorded as a change.
  await page.locator('.card', { hasText: 'Edit' }).first().getByRole('button', { name: 'Save' }).click()
  await details.getByText(/^Last changed .* by audun\.$/).waitFor()
  // Admins can correct it; the new maker owns the unit.
  await details.getByRole('button', { name: 'Correct' }).click()
  await details.getByLabel('Made by').selectOption('audun')
  await details.getByRole('button', { name: 'Save' }).click()
  await details.getByText(/^Made .* by audun\.$/).waitFor()
  await shot('13d-unit-details')
})

await step('food photo', async () => {
  const png = Buffer.from(PHOTO_PNG, 'base64')
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByRole('link', { name: 'Rye bread' }).click()
  await page.locator('input[type=file]').setInputFiles({ name: 'bread.png', mimeType: 'image/png', buffer: png })
  // Shrinking the photo can take a while on a slow CI machine.
  await page.getByRole('img', { name: 'Photo of Rye bread' }).waitFor({ timeout: 20000 })
  await page.getByRole('button', { name: 'Replace photo' }).waitFor()
  // The thumbnail shows in the food list and next to the food's entries.
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.locator('a.food-link', { hasText: 'Rye bread' }).locator('img.food-thumb').waitFor()
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await page.locator('.entry a.food-link', { hasText: 'Rye bread' }).locator('img.food-thumb').waitFor()
})

await step('photo from a paste or a link', async () => {
  // A stand-in for a web site with an image and a page, on localhost (the server is told to allow that).
  const image = Buffer.from(PHOTO_PNG, 'base64')
  const site = createServer((req, res) => {
    if (req.url === '/bread.png') res.writeHead(200, { 'Content-Type': 'image/png' }).end(image)
    else if (req.url === '/redirect') res.writeHead(302, { Location: '/bread.png' }).end()
    else res.writeHead(200, { 'Content-Type': 'text/html' }).end('<!doctype html><title>Shop</title>')
  })
  await new Promise((resolve) => site.listen(0, '127.0.0.1', resolve))
  const siteUrl = `http://127.0.0.1:${site.address().port}`
  const paste = (selector, { text, png }) =>
    page.evaluate(
      ({ selector, text, png }) => {
        const data = new DataTransfer()
        if (text) data.setData('text/plain', text)
        if (png) data.items.add(new File([Uint8Array.from(atob(png), (c) => c.charCodeAt(0))], 'pasted.png', { type: 'image/png' }))
        document.querySelector(selector).dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }))
      },
      { selector, text, png },
    )
  try {
    await page.getByRole('link', { name: 'Foods', exact: true }).click()
    await page.locator('.food-table a.food-link', { hasText: 'Mystery soup' }).click()
    await page.getByRole('heading', { name: 'Mystery soup' }).waitFor()
    const field = page.getByLabel('Paste an image or a link to one')
    // A link to a web page isn't an image.
    await field.fill(`${siteUrl}/shop`)
    await page.getByRole('button', { name: 'Get photo' }).click()
    await page.getByText('That link isn’t to an image.', { exact: false }).waitFor()
    // A pasted link is downloaded at once, following redirects.
    await field.fill('')
    await paste('.photo-link input', { text: `${siteUrl}/redirect` })
    await page.getByRole('img', { name: 'Photo of Mystery soup' }).waitFor()
    await page.getByText('That link isn’t to an image.', { exact: false }).waitFor({ state: 'detached' })
    await shot('23-photo-link')

    // A pasted image works anywhere on the page outside a field.
    await page.getByRole('button', { name: 'Remove photo' }).click()
    await page.getByRole('button', { name: 'Yes, do it' }).click()
    await page.getByText('No photo yet').waitFor()
    await paste('body', { png: PHOTO_PNG })
    await page.getByRole('img', { name: 'Photo of Mystery soup' }).waitFor()
  } finally {
    site.close()
  }
})

await step('names in other languages', async () => {
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByRole('link', { name: 'Rye bread' }).click()
  await page.getByText('Names in other languages').click()
  await page.getByLabel('Norwegian name').fill('Rugbrød')
  await page.getByRole('button', { name: 'Save' }).click()
  await page.getByText('Saved').waitFor()
  // Search finds a food by any of its names.
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await page.getByPlaceholder('Search foods…').fill('rugbr')
  await page.getByRole('option', { name: 'Rye bread' }).waitFor()
  await page.getByPlaceholder('Search foods…').fill('')
})

await step('norwegian', async () => {
  await page.getByRole('link', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Norsk' }).click()
  await page.getByRole('heading', { name: 'Innstillinger' }).waitFor()
  await page.getByRole('link', { name: 'Dag', exact: true }).click()
  await page.getByRole('heading', { name: 'Registrer mat' }).waitFor()
  // Names follow the language: the food's Norwegian name, built-in names, and Norwegian plurals.
  await page.getByRole('link', { name: 'Rugbrød' }).waitFor()
  await page.getByText('4 porsjoner').waitFor()
  await page.getByText('Energi', { exact: true }).first().waitFor()
  await shot('14-day-nb')
  await page.getByRole('link', { name: 'Innstillinger' }).click()
  await page.getByRole('button', { name: 'English' }).click()
})

await step('dark mode + mobile', async () => {
  const dark = await browser.newContext({ viewport: { width: 390, height: 844 }, colorScheme: 'dark', locale: 'en-US' })
  const token = await page.evaluate(() => localStorage.getItem('cc.token'))
  dark.setDefaultTimeout(6000)
  const p2 = await dark.newPage()
  p2.on('pageerror', (e) => problems.push(`[pageerror mobile] ${e.message}`))
  await p2.goto(BASE)
  await p2.evaluate((t) => localStorage.setItem('cc.token', t), token)
  await p2.goto(BASE)
  await p2.getByRole('heading', { name: 'Log food' }).waitFor()
  await p2.screenshot({ path: `${SHOTS}/15-mobile-dark-day.png`, fullPage: true })
  await p2.goto(`${BASE}/history`)
  await p2.locator('.recharts-surface').first().waitFor()
  await p2.waitForTimeout(400)
  await p2.screenshot({ path: `${SHOTS}/16-mobile-dark-history.png`, fullPage: true })
  const overflow = await p2.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  if (overflow) problems.push('horizontal overflow on mobile history')
  await p2.goto(`${BASE}/foods/1`)
  await p2.getByRole('heading', { name: 'Rye bread' }).waitFor()
  await p2.screenshot({ path: `${SHOTS}/17-mobile-dark-food.png`, fullPage: true })
  const overflow2 = await p2.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
  if (overflow2) problems.push('horizontal overflow on mobile food page')
})

await step('logout and login', async () => {
  await page.getByRole('button', { name: 'Log out' }).click()
  await page.getByRole('button', { name: 'Log in' }).waitFor()
  await page.getByLabel('Username').fill('audun')
  await page.getByLabel('Password').fill('wrong')
  await page.getByRole('button', { name: 'Log in' }).click()
  await page.getByText('Wrong username or password.').waitFor()
  await page.getByLabel('Password').fill('secret123')
  await page.getByRole('button', { name: 'Log in' }).click()
  await page.getByRole('button', { name: 'Log out' }).waitFor()
})

await step('composite food', async () => {
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByPlaceholder('Name, e.g. Rye bread').fill('Juice breakfast')
  await page.getByRole('button', { name: 'Create' }).click()
  await page.getByRole('heading', { name: 'Juice breakfast' }).waitFor()
  const add = async (food, option, quantity, unit) => {
    await page.getByRole('button', { name: 'Add ingredient' }).click()
    const row = page.locator('.ingredient-row').last()
    await row.getByRole('combobox', { name: 'Ingredient' }).fill(food)
    await page.getByRole('option', { name: option }).click()
    await row.getByLabel('Amount').fill(quantity)
    await row.getByLabel('Unit', { exact: true }).selectOption({ label: unit })
  }
  await add('rye', 'Rye bread', '2', 'servings')
  await add('juice', 'Orange juice', '2', 'dl')
  await page.getByRole('button', { name: 'Save' }).click()
  // 2 servings of 35 g rye bread at 250 kcal / 100 g; the juice has no values.
  await page.getByText('Nutrients in all of it (1 serving)').waitFor()
  await page.locator('.nutrient-value', { hasText: 'Energy' }).getByText('175 kcal').waitFor()
  await shot('18-composite')

  // Logging it adds each item as its own entry.
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await page.getByRole('heading', { name: 'Log food' }).waitFor()
  await page.locator('.entry').first().waitFor()
  const before = await page.locator('.entry').count()
  await page.getByPlaceholder('Search foods…').fill('juice b')
  await page.getByRole('option', { name: /Juice breakfast/ }).click()
  await page.getByText('Adds each item as its own entry:').waitFor()
  await page.getByRole('button', { name: 'Add', exact: true }).click()
  await page.locator('.entry-via').first().waitFor()
  const after = await page.locator('.entry').count()
  if (after !== before + 2) throw new Error(`expected 2 new entries, got ${after - before}`)
  if ((await page.locator('.entry-via', { hasText: 'Juice breakfast' }).count()) !== 2) throw new Error('entries not marked as from the composite')
})

await step('ingredient-only food', async () => {
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByRole('link', { name: 'Orange juice' }).click()
  await page.getByRole('heading', { name: 'Orange juice' }).waitFor()
  await page.getByLabel('Ingredient only').check()
  await page.getByRole('button', { name: 'Save' }).click()
  await page.locator('h1 .badge', { hasText: 'Ingredient only' }).waitFor()
  await shot('19-ingredient-only')
  // Not offered when logging, but the composite that contains it still is.
  await page.getByRole('link', { name: 'Day', exact: true }).click()
  await page.getByPlaceholder('Search foods…').fill('juice')
  await page.getByRole('option', { name: /Juice breakfast/ }).waitFor()
  // The list can briefly show search results cached from before the change; wait for the fresh ones.
  await page.getByRole('option', { name: 'Orange juice' }).waitFor({ state: 'detached' })
  await page.getByPlaceholder('Search foods…').fill('')
})

await step('tags', async () => {
  // Tags are made on a food's page and saved at once, without the food's Save button.
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.locator('.food-table a.food-link', { hasText: 'Rye bread' }).click()
  await page.getByRole('heading', { name: 'Rye bread' }).waitFor()
  const editor = page.locator('.tags-editor')
  await page.getByLabel('New tag').fill('Breakfast')
  await page.getByLabel('New tag').press('Enter')
  await editor.locator('.chip', { hasText: 'Breakfast' }).waitFor()
  await page.getByLabel('New tag').fill('Bread')
  await page.getByRole('button', { name: 'Create tag' }).click()
  await editor.locator('.chip', { hasText: 'Bread' }).first().waitFor()
  await page.getByLabel('New tag').fill('Snack')
  await page.getByLabel('New tag').press('Enter')
  await editor.locator('.chip', { hasText: 'Snack' }).waitFor()
  // Still there after leaving the page unsaved, by name.
  await page.reload()
  await page.getByRole('heading', { name: 'Rye bread' }).waitFor()
  await editor.locator('.chip', { hasText: 'Snack' }).waitFor()
  const order = (await editor.locator('.chip a').allInnerTexts()).join()
  if (order !== 'Bread,Breakfast,Snack') throw new Error(`food tags in order: ${order}`)
  await page.locator('.card', { has: editor }).screenshot({ path: `${SHOTS}/20-food-tags.png` })

  // The food list shows them, by name; a tag links to its page, where it gets a color.
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  const row = page.locator('tr', { hasText: 'Rye bread' })
  await row.locator('.food-tags .chip', { hasText: 'Snack' }).waitFor()
  const listed = (await row.locator('.food-tags .chip').allInnerTexts()).join()
  if (listed !== 'Bread,Breakfast,Snack') throw new Error(`listed tags in order: ${listed}`)
  await row.locator('.food-tags').getByRole('link', { name: 'Breakfast' }).click()
  await page.getByRole('heading', { name: 'Breakfast' }).waitFor()
  await page.getByRole('link', { name: 'Rye bread' }).waitFor()
  await page.getByRole('button', { name: 'Green' }).click()
  await page.getByRole('button', { name: 'Green', pressed: true }).waitFor()
  await page.locator('.tag-title.tag-color-green').waitFor()
  await shot('22-tag-color')
  await page.getByRole('link', { name: '← Tags' }).click()
  await page.locator('.tag-chip.tag-color-green', { hasText: 'Breakfast' }).waitFor()
  // Tags aren't made here any more.
  if (await page.getByRole('button', { name: 'Create' }).count()) throw new Error('Tags page can still create tags')

  // Deleting a tag takes it off its foods.
  await page.getByRole('link', { name: 'Snack', exact: true }).click()
  await page.getByRole('heading', { name: 'Snack' }).waitFor()
  await page.getByRole('button', { name: 'Delete' }).click()
  await page.getByRole('button', { name: 'Yes, do it' }).click()
  await page.getByRole('heading', { name: 'Tags', exact: true }).waitFor()
  await page.getByRole('link', { name: 'Breakfast', exact: true }).waitFor()
  // The list may show its cached copy first; wait for the fresh one.
  await page.getByRole('link', { name: 'Snack', exact: true }).waitFor({ state: 'detached' })

  // A tag no food has any more goes.
  await page.getByRole('link', { name: '← Foods' }).click()
  await page.locator('.food-table a.food-link', { hasText: 'Rye bread' }).click()
  await page.getByRole('heading', { name: 'Rye bread' }).waitFor()
  await editor.locator('.chip', { hasText: 'Snack' }).waitFor({ state: 'detached' })
  await page.getByRole('button', { name: 'Remove Bread' }).click()
  await editor.locator('.chip', { hasText: 'Bread' }).filter({ hasNotText: 'Breakfast' }).waitFor({ state: 'detached' })
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  await page.getByRole('main').getByRole('link', { name: 'Tags', exact: true }).click()
  await page.getByRole('link', { name: 'Breakfast', exact: true }).waitFor()
  await page.getByRole('link', { name: 'Bread', exact: true }).waitFor({ state: 'detached' })
})

await step('food list filters and table', async () => {
  await page.getByRole('link', { name: 'Foods', exact: true }).click()
  const table = page.locator('.food-table')
  const rows = table.locator('tbody tr')
  // The link holds the photo (or its initial) and the name; the name is the last line.
  const names = async () => (await rows.locator('a.food-link').allInnerTexts()).map((n) => n.trim().split('\n').pop().trim())
  // A column per nutrient, values per reference amount, and a red dash where a value is missing.
  await table.getByRole('columnheader', { name: /Energy/ }).waitFor()
  await rows.filter({ hasText: 'Rye bread' }).getByRole('cell', { name: '250', exact: true }).waitFor()
  await rows.filter({ hasText: 'Orange juice' }).locator('.missing-value').first().waitFor()
  await shot('21-food-table')

  await page.getByLabel('Tag', { exact: true }).selectOption({ label: 'Breakfast' })
  await page.getByText(/Showing 1 of \d+ foods/).waitFor()
  if ((await names()).join() !== 'Rye bread') throw new Error(`tag filter showed: ${await names()}`)
  await page.getByRole('button', { name: 'Clear filters' }).click()
  await page.getByLabel('Composite', { exact: true }).check()
  await page.getByText(/Showing 1 of/).waitFor()
  if ((await names()).join() !== 'Juice breakfast') throw new Error(`composite filter showed: ${await names()}`)
  await page.getByRole('button', { name: 'Clear filters' }).click()
  await page.getByLabel('Nutrient', { exact: true }).selectOption({ label: 'Energy' })
  await page.getByLabel('Has or lacks a value').selectOption({ label: 'lacks a value' })
  await page.getByText(/Showing \d+ of/).waitFor()
  const lacking = await names()
  if (!lacking.includes('Orange juice') || lacking.includes('Rye bread')) throw new Error(`lacks filter showed: ${lacking}`)
  // The filters are in the address, so they survive going to a food and back.
  await page.getByRole('link', { name: 'Orange juice' }).click()
  await page.getByRole('heading', { name: 'Orange juice' }).waitFor()
  await page.goBack()
  await page.getByText(/Showing \d+ of/).waitFor()
  await page.getByRole('button', { name: 'Clear filters' }).click()

  // Column toggles are remembered in this browser.
  await page.getByRole('button', { name: 'Energy', exact: true, pressed: true }).click()
  await table.getByRole('columnheader', { name: /Energy/ }).waitFor({ state: 'detached' })
  await page.reload()
  await page.getByRole('button', { name: 'Energy', exact: true, pressed: false }).click()
  await table.getByRole('columnheader', { name: /Energy/ }).waitFor()
})

await step('export and import', async () => {
  await page.getByRole('link', { name: 'Settings' }).click()
  await page.getByRole('heading', { name: 'Settings' }).waitFor()
  const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Download export' }).click()])
  const path = await download.path()
  const data = JSON.parse(readFileSync(path, 'utf8'))
  if (!data.entries.length) throw new Error('export has no entries')
  await page.locator('input[type=file]').setInputFiles(path)
  await page.getByRole('button', { name: 'Import', exact: true }).click()
  await page.getByRole('cell', { name: 'Entries' }).waitFor()
  if (!data.tags.length || !data.foods.some((f) => f.tags.includes('Breakfast'))) throw new Error('export has no tags')
  await page.locator('input[type=file]').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from('{"format":"nope"}') })
  await page.getByRole('button', { name: 'Import', exact: true }).click()
  await page.getByText('That file isn’t an export from Calorie Companion.').waitFor()
})

console.log('\nConsole problems:', problems.length ? '\n' + problems.join('\n') : 'none')
await browser.close()
process.exit(failures ? 1 : 0)
