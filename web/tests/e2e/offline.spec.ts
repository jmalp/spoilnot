// Offline on an iPhone (DESIGN 9.5, 9.8), against the emulators: what is deleted leaves the screen at once. A
// tombstone whose deletedAt is still waiting for the server counts as deleted; before, it read as null, so the cart
// kept its items and Add to pantry could be pressed again (WEB-02).
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, householdId, openApp, seedPantry, signUp, uidOf, uniqueEmail } from './support';

test.setTimeout(60_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const list = (page: Page) => page.getByTestId('grocery_list');
const row = (page: Page, name: string) =>
  list(page)
    .locator('[data-testid^="grocery_item_"]')
    .filter({ has: page.getByText(name, { exact: true }) });
const pantryRows = (page: Page) => page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]');
const waiting = (page: Page) => page.getByRole('img', { name: 'Waiting to sync' });

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
  for (const name of ['onions', 'bananas', 'coffee']) await add(page, name);
  await expect(list(page).locator('[data-testid^="grocery_item_"]')).toHaveCount(3);
  await expect(waiting(page)).toHaveCount(0);

  await page.context().setOffline(true);

  // Removing an item: gone at once, and Undo brings it back, also offline.
  await row(page, 'onions').getByRole('button', { name: 'Options for onions' }).click();
  await page.getByRole('menuitem', { name: 'Delete' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'onions removed' })).toBeVisible();
  await expect(row(page, 'onions')).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(row(page, 'onions')).toBeVisible();

  // Add to pantry: the cart empties at once, so it cannot be pressed a second time.
  await row(page, 'bananas').getByRole('checkbox').click();
  await row(page, 'coffee').getByRole('checkbox').click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (2)');
  await page.getByTestId('fab_add_to_pantry').click();
  await page.getByTestId('sheet_add_to_pantry').getByTestId('button_confirm_add_to_pantry').click();
  await expect(page.getByRole('status').filter({ hasText: 'Added to the pantry' })).toBeVisible();
  await expect(page.getByTestId('cart_header')).toHaveCount(0);
  await expect(page.getByTestId('fab_add_to_pantry')).toHaveCount(0);
  await expect(row(page, 'bananas')).toHaveCount(0);
  await expect(row(page, 'onions')).toBeVisible();

  // Used up: the purchase leaves the pantry while the delete waits for the server.
  await tab(page, 'Pantry');
  await page.getByTestId('menu_cheese').click();
  await page.getByRole('menuitem', { name: 'Used up' }).click();
  await expect(page.getByTestId('pantry_item_cheese')).toHaveCount(0);
  await expect(pantryRows(page).filter({ hasText: 'bananas' })).toHaveCount(1);
  await expect(pantryRows(page).filter({ hasText: 'coffee' })).toHaveCount(1);

  // Back online: everything reaches the server, and one bunch of bananas stayed one.
  await page.context().setOffline(false);
  await expect(waiting(page)).toHaveCount(0, { timeout: 20_000 });
  await expect(pantryRows(page).filter({ hasText: 'bananas' })).toHaveCount(1);
  await expect(pantryRows(page).filter({ hasText: 'bananas' })).not.toContainText('× 2');
  await tab(page, 'Groceries');
  await expect(list(page).locator('[data-testid^="grocery_item_"]')).toHaveCount(1);
});
