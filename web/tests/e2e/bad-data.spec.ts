// Bad data skips one document, not the screen (after v1, DESIGN 6.7 and 8.6; review WEB-10, REL-04, REL-13), against
// the emulators. Wrong-typed fields written past the rules (as a buggy client, a console edit or a restore could)
// leave every list showing; a failing listener says "Couldn't load. Retry"; a change the server refuses later is
// reported whichever tab is open.
import { expect, test, type Page } from '@playwright/test';
import {
  closeOtherPhones,
  createHousehold,
  defaultListId,
  deleteDocument,
  householdId,
  newPhone,
  openApp,
  PASSWORD,
  patchFields,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
  inDays,
} from './support';

test.setTimeout(90_000);
test.afterEach(closeOtherPhones);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();

/** Writes a whole document past the rules (every field in the update mask). */
const write = (path: string, fields: Parameters<typeof patchFields>[1]) => patchFields(path, fields);

/** Refuses every server listener with [code] until called with null (development builds only). */
const refuseListens = (page: Page, code: string | null) =>
  page.evaluate((c) => {
    (
      window as unknown as { spoilnotTest: { refuseListens: (code: string | null) => void } }
    ).spoilnotTest.refuseListens(c);
  }, code);

test("review 05's bad-data documents leave every list showing, on this phone and a fresh one", async ({
  page,
  browser,
}) => {
  const email = uniqueEmail('Bea');
  const household = `Bad data ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Bea', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [{ id: 'cheese', name: 'Cheese' }]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();

  const now = new Date();
  const stamp = { createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid };
  const list = await defaultListId(hid);
  // Round 1: one wrong-typed field per collection; then the list fields that threw on the web.
  await write(`households/${hid}/stock/bad-qty`, {
    productId: 'p-b0',
    productName: 'Milk',
    unit: 'pcs',
    category: 'dairy',
    quantity: '2',
    expiryDate: inDays(3),
    dateType: 'use_by',
    location: 'fridge',
    ...stamp,
  });
  await write(`households/${hid}/products/p-bad-list`, {
    name: 'Butter',
    nameKey: 'butter',
    recentShelfLifeDays: '7',
    aliases: 'salted',
    ...stamp,
  });
  await write(`households/${hid}/stock/butter`, {
    productId: 'p-bad-list',
    productName: 'Butter',
    unit: 'pcs',
    quantity: 1,
    expiryDate: inDays(9),
    dateType: 'use_by',
    location: 'fridge',
    ...stamp,
  });
  await write(`households/${hid}/groceryLists/${list}/items/bad-checked`, {
    name: 'Bad checked',
    nameKey: 'bad checked',
    checked: 'yes',
    source: 'manual',
    ...stamp,
  });
  await write(`households/${hid}/recipes/bad-servings`, {
    title: 'Bad servings',
    servings: '4',
    ingredients: 'one string, not a list',
    steps: 'Cook it.',
    tags: 'x',
    ...stamp,
  });
  await write(`households/${hid}/meals/bad-position`, {
    date: inDays(0),
    slot: 'lunch',
    position: '1',
    title: 'Bad position',
    cooks: uid,
    ...stamp,
  });
  await write(`households/${hid}/activity/bad-name`, {
    type: 'stock_added',
    actorUid: uid,
    actorName: 42,
    params: { name: 'Bad name type' },
    createdAt: now,
  });

  const check = async (p: Page) => {
    await tab(p, 'Pantry');
    await expect(p.getByTestId('pantry_item_cheese')).toBeVisible();
    await expect(p.getByTestId('pantry_item_butter')).toBeVisible();
    await expect(p.getByTestId('load_error')).toHaveCount(0);
    await tab(p, 'Groceries');
    await expect(p.getByText('Bad checked', { exact: true })).toBeVisible();
    await tab(p, 'Recipes');
    await expect(p.getByText('Bad servings', { exact: true })).toBeVisible();
    await tab(p, 'Plan');
    await expect(p.getByText('Bad position', { exact: true })).toBeVisible();
    await tab(p, 'Home');
    await expect(p.getByTestId('load_error')).toHaveCount(0);
    await expect(p.locator('.sn-skeleton')).toHaveCount(0);
  };
  await check(page);

  // A fresh phone signed in to the same household reads everything in full, and loads too.
  const other = await (await newPhone(browser)).newPage();
  await openApp(other);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await other.getByLabel('Email').fill(email);
  await other.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await expect(other.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
  await check(other);
});

test("a listener that fails says Couldn't load with Retry, and recovers", async ({ page }) => {
  const email = uniqueEmail('Lou');
  const household = `Load error ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Lou', email);
  await createHousehold(page, household);
  await seedPantry(await householdId(household), await uidOf(email), [{ id: 'cheese', name: 'Cheese' }]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();

  // A refusal that isn't the quota or the network: the list keeps what it had and says so.
  await refuseListens(page, 'internal');
  await expect(page.getByTestId('load_error')).toBeVisible();
  await expect(page.getByTestId('load_error')).toContainText("Couldn't load.");
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();

  // Fixed: Retry listens again at once, and the message goes.
  await refuseListens(page, null);
  await page.getByTestId('load_error_retry').click();
  await expect(page.getByTestId('load_error')).toHaveCount(0, { timeout: 10_000 });
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();
});

test('a change the server refuses later is reported on another tab', async ({ page }) => {
  const email = uniqueEmail('Rae');
  const household = `Refusal ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Rae', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  await seedPantry(hid, await uidOf(email), [{ id: 'cheese', name: 'Cheese' }]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();

  // Offline, Add 1 on the cheese, then the person moves to Recipes.
  await page.context().setOffline(true);
  await page.getByTestId('menu_cheese').click();
  await page.getByRole('menuitem', { name: 'Add 1' }).click();
  await expect(page.getByTestId('pantry_item_cheese')).toContainText('2');
  await tab(page, 'Recipes');
  await expect(page.getByTestId('screen_recipes')).toBeVisible();

  // Meanwhile the purchase was deleted outright, so the +1 is refused once the phone is back: the rules see no
  // document to update.
  await deleteDocument(`households/${hid}/stock/cheese`);
  await page.context().setOffline(false);
  await expect(page.getByRole('status').filter({ hasText: "You don't have permission to do that." })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByTestId('screen_recipes')).toBeVisible();
});
