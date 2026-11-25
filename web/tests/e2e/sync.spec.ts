// Where a changes-only listener starts (DESIGN 9.8, after v1 2026-11-09, WEB-16), against the emulators. The cursor
// is the newest change the server listener received, never the newest one in the cache: before, an iPhone whose
// restock engine wrote Milk to the list from the Pantry tab then skipped what another member had done to the list,
// until the next daily full read. Review 10's run, with a 2-second margin instead of 5 minutes.
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  defaultListId,
  householdId,
  openApp,
  patchFields,
  readFields,
  seedGroceries,
  seedMember,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(60_000);

const MARGIN_MS = 2_000;
const STOP_TIMEOUT_MS = 5_000;
const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const list = (page: Page) => page.getByTestId('grocery_list');
const row = (page: Page, name: string) =>
  list(page)
    .locator('[data-testid^="grocery_item_"]')
    .filter({ has: page.getByText(name, { exact: true }) });
const stored = (page: Page, key: string) => page.evaluate((k) => localStorage.getItem(k), key);

test('a write made while the list is not showing does not skip the other members’ changes', async ({ page }) => {
  await page.addInitScript((ms) => localStorage.setItem('spoilnot.test.cursorMarginMs', ms), String(MARGIN_MS));
  const email = uniqueEmail('Alex');
  const household = `Cursor ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Alex', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  const listId = await defaultListId(hid);
  const items = `households/${hid}/groceryLists/${listId}/items`;
  await seedMember(hid, 'u-blake', 'Blake');
  await seedPantry(hid, uid, [{ id: 'milk', name: 'Milk', quantity: 1, min: 1 }]);
  await seedGroceries(hid, 'u-blake', listId, [{ id: 'early', name: 'early item', createdBy: 'u-blake' }]);

  // Alex opens Groceries once, so the list has been read and its cursor recorded next to the full-read record.
  await tab(page, 'Groceries');
  await expect(row(page, 'early item')).toBeVisible();
  await expect.poll(() => stored(page, `spoilnot.sync.seen:${items}`)).not.toBeNull();
  expect(await stored(page, `spoilnot.sync.${items}`)).not.toBeNull();
  const seen = await stored(page, `spoilnot.sync.seen:${items}`);

  // Alex leaves the list. Meanwhile Blake (another phone) adds eggs and checks the early item off.
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_milk')).toBeVisible();
  // The list's listeners stay 5 s after the last screen leaves (after v1, DESIGN 9.1); then nothing listens to it.
  await page.waitForTimeout(STOP_TIMEOUT_MS + 1_000);
  await seedGroceries(hid, 'u-blake', listId, [{ id: 'eggs', name: 'P01 eggs', createdBy: 'u-blake' }]);
  await patchFields(`${items}/early`, { checked: true, checkedBy: 'u-blake', updatedAt: new Date() });
  await page.waitForTimeout(3 * MARGIN_MS);

  // On Pantry, Alex uses up the last Milk: the restock engine writes Milk to the list, which reaches the server.
  await page.getByTestId('menu_milk').click();
  await page.getByRole('menuitem', { name: 'Used up' }).click();
  await expect.poll(async () => (await readFields(`${items}/low_p-milk`))?.updatedAt ?? null).not.toBeNull();
  expect(await stored(page, `spoilnot.sync.seen:${items}`), 'Alex’s own write does not move the cursor').toBe(seen);

  // Alex opens Groceries: Blake's changes are there, not only Alex's own write.
  await tab(page, 'Groceries');
  await expect(row(page, 'Milk')).toContainText('Added automatically');
  await expect(row(page, 'P01 eggs')).toBeVisible();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (1)');
  await expect(list(page).locator('[data-testid^="grocery_item_"]')).toHaveCount(3);
  await expect
    .poll(async () => Number(await stored(page, `spoilnot.sync.seen:${items}`)))
    .toBeGreaterThan(Number(seen));
});
