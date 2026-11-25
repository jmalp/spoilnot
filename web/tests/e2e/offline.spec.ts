// Offline on an iPhone (DESIGN 9.5, 9.8), against the emulators: what is deleted leaves the screen at once. A
// tombstone whose deletedAt is still waiting for the server counts as deleted; before, it read as null, so the cart
// kept its items and Add to pantry could be pressed again (WEB-02). The same holds for a meal's Cooked (DESIGN 5.9).
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  readFields,
  seedMeal,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(60_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const list = (page: Page) => page.getByTestId('grocery_list');
const row = (page: Page, name: string) =>
  list(page)
    .locator('[data-testid^="grocery_item_"]')
    .filter({ has: page.getByText(name, { exact: true }) });
const pantryRows = (page: Page) => page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]');
const waiting = (page: Page) => page.getByRole('img', { name: 'Waiting to sync' });
const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });

async function add(page: Page, text: string) {
  await page.getByTestId('grocery_quick_add').fill(text);
  await page.getByTestId('grocery_quick_add').press('Enter');
  await expect(page.getByTestId('grocery_quick_add')).toHaveValue('');
}

test('offline, deletes and Add to pantry leave the screen at once, and sync once back', async ({ page }) => {
  const email = uniqueEmail('Sam');
  const household = `Offline ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  await seedPantry(await householdId(household), await uidOf(email), [{ id: 'cheese', name: 'Cheese' }]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();

  // Online: three items on the list, all on the server.
  await tab(page, 'Groceries');
  await expect(page.getByText('Your list is empty.', { exact: false })).toBeVisible();
  for (const name of ['onions', 'bananas', 'coffee']) await add(page, name); // saved as "Onions", "Bananas", "Coffee" (DESIGN 8.1)
  await expect(list(page).locator('[data-testid^="grocery_item_"]')).toHaveCount(3);
  await expect(waiting(page)).toHaveCount(0);

  await page.context().setOffline(true);

  // Removing an item: gone at once, and Undo brings it back, also offline.
  await row(page, 'Onions').getByRole('button', { name: 'Options for Onions' }).click();
  await page.getByRole('menuitem', { name: 'Delete' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Onions removed' })).toBeVisible();
  await expect(row(page, 'Onions')).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(row(page, 'Onions')).toBeVisible();

  // Add to pantry: the cart empties at once, so it cannot be pressed a second time.
  await row(page, 'Bananas').getByRole('checkbox').click();
  await row(page, 'Coffee').getByRole('checkbox').click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (2)');
  await page.getByTestId('fab_add_to_pantry').click();
  await page.getByTestId('sheet_add_to_pantry').getByTestId('button_confirm_add_to_pantry').click();
  await expect(page.getByRole('status').filter({ hasText: 'Added to the pantry' })).toBeVisible();
  await expect(page.getByTestId('cart_header')).toHaveCount(0);
  await expect(page.getByTestId('fab_add_to_pantry')).toHaveCount(0);
  await expect(row(page, 'Bananas')).toHaveCount(0);
  await expect(row(page, 'Onions')).toBeVisible();

  // Used up: the purchase leaves the pantry while the delete waits for the server.
  await tab(page, 'Pantry');
  await page.getByTestId('menu_cheese').click();
  await page.getByRole('menuitem', { name: 'Used up' }).click();
  await expect(page.getByTestId('pantry_item_cheese')).toHaveCount(0);
  await expect(pantryRows(page).filter({ hasText: 'Bananas' })).toHaveCount(1);
  await expect(pantryRows(page).filter({ hasText: 'Coffee' })).toHaveCount(1);

  // Back online: everything reaches the server, and one bunch of bananas stayed one.
  await page.context().setOffline(false);
  await expect(waiting(page)).toHaveCount(0, { timeout: 20_000 });
  await expect(pantryRows(page).filter({ hasText: 'Bananas' })).toHaveCount(1);
  await expect(pantryRows(page).filter({ hasText: 'Bananas' })).not.toContainText('× 2');
  await tab(page, 'Groceries');
  await expect(list(page).locator('[data-testid^="grocery_item_"]')).toHaveCount(1);
});

test('a cooked meal removed and brought back with Undo stays cooked, online and offline', async ({ page }) => {
  const email = uniqueEmail('Sam');
  const household = `Offline cooked ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  const today = inDays(0);
  const path = `households/${hid}/meals/m1`;
  await seedMeal(hid, uid, { id: 'm1', date: today, slot: 'dinner', title: 'Leftovers' });
  await tab(page, 'Plan');
  const meal = page.getByTestId(`plan_day_${today}`).getByRole('button', { name: /Leftovers/ });
  await expect(meal).toBeVisible();
  const removeAndUndo = async () => {
    await meal.click();
    await page.getByTestId('plan_remove_meal').click();
    await page.mouse.move(1, 1); // a finger lifts; a pointer left on a snackbar holds its timer
    await expect(meal).toHaveCount(0);
    await status(page, 'Removed Leftovers from the plan').getByRole('button', { name: 'Undo' }).click();
    await page.mouse.move(1, 1);
    await expect(meal).toContainText('Cooked');
  };

  // Online: Cooked reaches the server at its time, and Undo of Remove leaves that time as it was.
  await meal.click();
  await page.getByTestId('plan_cook_meal').click();
  await page.mouse.move(1, 1);
  await expect.poll(async () => (await readFields(path))?.cookedBy, { timeout: 10_000 }).toBe(uid);
  const cookedAt = (await readFields(path))?.cookedAt;
  await removeAndUndo();
  await expect.poll(async () => 'deletedAt' in ((await readFields(path)) ?? {}), { timeout: 10_000 }).toBe(false);
  expect((await readFields(path))?.cookedAt).toBe(cookedAt);

  // Offline: Cooked, Remove and Undo with no signal; back online the meal is on the server, cooked.
  await meal.click();
  await page.getByTestId('plan_not_cooked').click();
  await expect.poll(async () => (await readFields(path))?.cookedAt ?? null, { timeout: 10_000 }).toBeNull();
  await page.context().setOffline(true);
  await meal.click();
  await page.getByTestId('plan_cook_meal').click();
  await page.mouse.move(1, 1);
  await expect(meal).toContainText('Cooked');
  await removeAndUndo();
  await page.context().setOffline(false);
  await expect.poll(async () => (await readFields(path))?.cookedBy ?? null, { timeout: 20_000 }).toBe(uid);
  // The Undo was refused if the meal is still a tombstone: wait past the writer's commit, then look.
  await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 2_000)));
  const synced = await readFields(path);
  expect(synced && 'deletedAt' in synced).toBe(false);
  expect(synced?.cookedAt).toBeTruthy();
  await expect(meal).toContainText('Cooked');
});
