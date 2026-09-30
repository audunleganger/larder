// End-to-end walkthrough of the web GUI against a fresh server (see e2e/run.sh).
// Screenshots go to $E2E_SHOTS (default: e2e/screenshots). Exits non-zero if any step fails.
import { mkdirSync, readFileSync } from 'node:fs'
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
  await page.locator('.entry-form select').selectOption({ label: 'serving' })
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

await step('units pages', async () => {
  await page.getByRole('link', { name: 'Units' }).click()
  await page.getByRole('link', { name: 'serving' }).click()
  await page.getByText('Rye bread').first().waitFor()
  await shot('08-unit-detail')
  await page.getByRole('button', { name: 'Delete' }).click()
  await page.getByRole('button', { name: 'Yes, do it' }).click()
  await page.getByText(/still used by/).waitFor()
})

await step('nutrients pages', async () => {
  await page.getByRole('link', { name: 'Nutrients' }).click()
  await page.getByRole('button', { name: 'Move Protein up' }).click()
  await page.waitForTimeout(500)
  const first = await page.locator('.order-list li').first().innerText()
  if (!first.includes('Protein')) throw new Error('reorder failed: ' + first)
  await page.getByRole('button', { name: 'Move Protein down' }).click()
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

await step('norwegian', async () => {
  await page.getByRole('link', { name: 'Settings' }).click()
  await page.getByRole('button', { name: 'Norsk' }).click()
  await page.getByRole('heading', { name: 'Innstillinger' }).waitFor()
  await page.getByRole('link', { name: 'Dag', exact: true }).click()
  await page.getByRole('heading', { name: 'Registrer mat' }).waitFor()
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
  await page.locator('input[type=file]').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from('{"format":"nope"}') })
  await page.getByRole('button', { name: 'Import', exact: true }).click()
  await page.getByText('That file isn’t a Calorie Companion export.').waitFor()
})

console.log('\nConsole problems:', problems.length ? '\n' + problems.join('\n') : 'none')
await browser.close()
process.exit(failures ? 1 : 0)
