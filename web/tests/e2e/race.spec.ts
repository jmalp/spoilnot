// One phone wins (DESIGN 8.5 invariant 10, 9.4; after v1 2026-11-09, WEB-01), against the emulators: the iPhone, offline
// in the store, finishes something another member's phone finished first. The rules refuse the iPhone's whole write
// when it reconnects, and it says why with the other member's name. Blake's phone is played by writes to the
// emulator, as the Android app makes them (the Android side is OnePhoneWinsFlowTest).
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  defaultListId,
  deleteDocument,
  documentIds,
  householdId,
  inDays,
  listIdNamed,
  openApp,
  patchFields,
  readFields,
  seedGroceries,
  seedMeal,
  seedMember,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(60_000);

const BLAKE = 'u-blake';
const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });
const list = (page: Page) => page.getByTestId('grocery_list');
const row = (page: Page, name: string) =>
  list(page)
    .locator('[data-testid^="grocery_item_"]')
    .filter({ has: page.getByText(name, { exact: true }) });
const pantryRows = (page: Page) => page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]');

test('Add to pantry offline loses to a phone that added the same items first, and says who', async ({ page }) => {
  const email = uniqueEmail('Alex');
  const name = `Race pantry ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Alex', email);
  await createHousehold(page, name);
  const hid = await householdId(name);
  const uid = await uidOf(email);
  const listId = await defaultListId(hid);
  const items = `households/${hid}/groceryLists/${listId}/items`;
  await seedMember(hid, BLAKE, 'Blake');
  await seedGroceries(hid, uid, listId, [
    { id: 'milk', name: 'Droid milk', checked: true },
    { id: 'rice', name: 'web rice', checked: true },
  ]);
  await tab(page, 'Groceries');
  await expect(page.getByTestId('cart_header')).toContainText('In cart (2)');

  // In the store with no signal: the cart goes into the pantry on this phone.
  await page.context().setOffline(true);
  await page.getByTestId('fab_add_to_pantry').click();
  await page.getByTestId('sheet_add_to_pantry').getByTestId('button_confirm_add_to_pantry').click();
  await expect(status(page, 'Added to the pantry')).toBeVisible();

  // Meanwhile Blake presses Add all on Android: one purchase of each, the items tombstoned by Blake.
  await seedPantry(hid, BLAKE, [
    { id: 'blake-milk', name: 'Droid milk' },
    { id: 'blake-rice', name: 'web rice' },
  ]);
  for (const id of ['milk', 'rice']) {
    await patchFields(`${items}/${id}`, { deletedAt: new Date(), updatedAt: new Date(), updatedBy: BLAKE });
  }

  // Back online: the iPhone's whole write is refused, and it says why.
  await page.context().setOffline(false);
  await expect(status(page, 'Blake already took these off the list. Nothing was added to the pantry.')).toBeVisible({
    timeout: 20_000,
  });
  await tab(page, 'Pantry');
  await expect(pantryRows(page).filter({ hasText: 'Droid milk' })).toHaveCount(1);
  await expect(pantryRows(page).filter({ hasText: 'web rice' })).toHaveCount(1);
  // On the server: Blake's purchases only, and the items still Blake's tombstones.
  expect((await readFields(`${items}/milk`))?.updatedBy).toBe(BLAKE);
});

test('Cooked offline loses to a phone that marked the meal cooked first, and says who', async ({ page }) => {
  const email = uniqueEmail('Alex');
  const name = `Race cooked ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Alex', email);
  await createHousehold(page, name);
  const hid = await householdId(name);
  const uid = await uidOf(email);
  await seedMember(hid, BLAKE, 'Blake');
  const today = inDays(0);
  await seedMeal(hid, uid, { id: 'm1', date: today, slot: 'dinner', title: 'Leftovers' });
  await tab(page, 'Plan');
  const meal = page.getByTestId(`plan_day_${today}`).getByRole('button', { name: /Leftovers/ });
  await expect(meal).toBeVisible();

  await page.context().setOffline(true);
  await meal.click();
  await page.getByTestId('plan_cook_meal').click();
  // The sheet closes; the mark itself shows once the server time is known.
  await expect(page.getByTestId('plan_cook_meal')).toHaveCount(0);
  // Blake marks the same dinner cooked first.
  await patchFields(`households/${hid}/meals/m1`, {
    cookedAt: new Date(),
    cookedBy: BLAKE,
    updatedAt: new Date(),
    updatedBy: BLAKE,
  });

  await page.context().setOffline(false);
  await expect(status(page, 'Blake already marked this meal cooked. Nothing was taken from the pantry.')).toBeVisible({
    timeout: 20_000,
  });
  expect((await readFields(`households/${hid}/meals/m1`))?.cookedBy).toBe(BLAKE);
});

test('an item added offline to a list another member deletes is refused, and says so', async ({ page }) => {
  const email = uniqueEmail('Alex');
  const name = `Race list ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Alex', email);
  await createHousehold(page, name);
  const hid = await householdId(name);
  await tab(page, 'Groceries');
  await page.getByTestId('grocery_list_switcher').click();
  await page.getByRole('menuitem', { name: 'New list' }).click();
  await page.getByTestId('field_list_name').fill('Party');
  await page.getByTestId('button_save_list').click();
  await expect(page.getByTestId('grocery_list_switcher')).toHaveAccessibleName('List: Party');
  // The list is on the server, and this phone has heard back (else it sends the create again when it reconnects).
  const party = await listIdNamed(hid, 'Party');
  await page.evaluate(() => new Promise((resolve) => setTimeout(resolve, 2_000)));

  await page.context().setOffline(true);
  await page.getByTestId('grocery_quick_add').fill('Chips');
  await page.getByTestId('grocery_quick_add').press('Enter');
  await expect(row(page, 'Chips')).toBeVisible();
  // Another member deletes Party (the list goes first, then its items: none on the server yet).
  await deleteDocument(`households/${hid}/groceryLists/${party}`);

  await page.context().setOffline(false);
  await expect(status(page, "That list was deleted, so the change wasn't saved.")).toBeVisible({ timeout: 20_000 });
  expect(await documentIds(`households/${hid}/groceryLists/${party}/items`)).toEqual([]);
});
