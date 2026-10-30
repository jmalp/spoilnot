// Settings on an iPhone (DESIGN 5.6, 13.4, 18.8 W6a), against the emulators: appearance and units, export, Clear
// household data, About.
import { readFile } from 'node:fs/promises';
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  defaultListId,
  householdId,
  inDays,
  openApp,
  PASSWORD,
  seedGroceries,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(60_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const openSettings = (page: Page) =>
  page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });

test('theme, colors and units: chosen in Settings, kept after the app opens again', async ({ page }) => {
  await openApp(page);
  await signUp(page, 'Sam');
  await createHousehold(page, `Looks ${Date.now()}`);
  await openSettings(page);
  const root = page.locator('html');
  await expect(root).toHaveAttribute('data-mode', 'light');

  const appearance = page.getByTestId('card_appearance');
  await appearance.getByRole('radio', { name: 'Dark' }).click();
  await expect(root).toHaveAttribute('data-mode', 'dark');
  const green = await root.evaluate((el) => el.style.getPropertyValue('--md-sys-color-primary'));
  await appearance.getByRole('radio', { name: 'Fiery Palette' }).click();
  await expect(appearance.getByRole('radio', { name: 'Fiery Palette' })).toHaveAttribute('aria-checked', 'true');
  await expect(root).not.toHaveAttribute('style', new RegExp(`--md-sys-color-primary: ${green}`));
  await appearance.getByRole('radio', { name: 'Metric' }).click();
  await expect(appearance.getByRole('radio', { name: 'Metric' })).toHaveAttribute('aria-checked', 'true');

  await page.reload();
  await expect(root).toHaveAttribute('data-mode', 'dark');
  await openSettings(page);
  await expect(appearance.getByRole('radio', { name: 'Fiery Palette' })).toHaveAttribute('aria-checked', 'true');
  // Units are on the profile, so every phone of the user has them.
  await expect(appearance.getByRole('radio', { name: 'Metric' })).toHaveAttribute('aria-checked', 'true');
  await appearance.getByRole('radio', { name: 'System' }).click();
  await expect(root).toHaveAttribute('data-mode', 'light');
});

test('export downloads the household as JSON, then the owner clears its data with the password', async ({ page }) => {
  const email = uniqueEmail('Sam');
  const household = `Clear ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [{ id: 'milk', name: 'Milk', expiry: inDays(3) }]);
  await seedGroceries(hid, uid, await defaultListId(hid), [{ id: 'bread', name: 'Bread' }]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toContainText('Milk');

  await openSettings(page);
  const downloading = page.waitForEvent('download');
  await page.getByTestId('button_export').click();
  const download = await downloading;
  expect(download.suggestedFilename()).toMatch(/^spoilnot-export-\d{4}-\d{2}-\d{2}\.json$/);
  const json = JSON.parse(await readFile((await download.path())!, 'utf8'));
  expect(json).toMatchObject({ format: 'spoilnot-export', formatVersion: 1, appVersion: 'web dev' });
  expect(json.household.name).toBe(household);
  expect(json.products.map((p: { name: string }) => p.name)).toEqual(['Milk']);
  expect(json.groceryLists[0].items.map((i: { name: string }) => i.name)).toEqual(['Bread']);
  await expect(status(page, 'Export saved')).toBeVisible();

  // Are you sure, then the password: a wrong one is refused.
  await page.getByTestId('button_clear_household').click();
  await expect(page.getByRole('dialog')).toContainText(`Everything in ${household} is deleted`);
  await page.getByTestId('clear_household_yes').click();
  await page.getByLabel('Password', { exact: true }).fill('not-the-password');
  await page.getByTestId('clear_household_confirm').click();
  await expect(page.getByRole('dialog')).toContainText(/password/i);
  await expect(page.getByRole('dialog').getByRole('status')).not.toBeEmpty();
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByTestId('clear_household_confirm').click();
  await expect(status(page, 'Household data cleared')).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Everything is gone for every member; the household stays, and Recent activity starts from the reset.
  await tab(page, 'Pantry');
  await expect(page.getByTestId('button_add_first_item')).toBeVisible();
  await tab(page, 'Groceries');
  await expect(page.getByTestId('screen_groceries')).not.toContainText('Bread');
  await tab(page, 'Home');
  await expect(page.getByTestId('card_home_activity')).toContainText("You cleared the household's data");
});

test('About shows the build, the privacy summary and the open source licenses', async ({ page }) => {
  await openApp(page);
  await signUp(page, 'Sam');
  await createHousehold(page, `About ${Date.now()}`);
  await openSettings(page);
  await page.getByTestId('row_about').click();
  await expect(page.getByTestId('about_version')).toHaveText('Spoil Not for iPhone, build dev');
  await expect(page.getByTestId('screen_about')).toContainText('no crash reports');
  const react = page.getByTestId('licenses').getByRole('button', { name: /^react 19/ });
  await expect(react).toContainText('MIT');
  await react.click();
  await expect(page.getByRole('dialog')).toContainText('Permission is hereby granted');
  await page.getByRole('dialog').getByRole('button', { name: 'OK' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  // Leaving the tab closes Settings and About, as in the app.
  await tab(page, 'Pantry');
  await tab(page, 'Home');
  await expect(page.getByTestId('screen_home')).toBeVisible();
});
