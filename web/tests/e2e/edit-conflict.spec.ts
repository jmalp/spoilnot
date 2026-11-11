// Edits don't undo someone else's change (after v1, DESIGN 9.2 and 9.4; WEB-03, WEB-07), against the emulators: the
// iPhone's Edit window is open while Blake's phone, played by writes to the emulator as the Android app makes them,
// uses or removes the item. The Android side is EditConflictFlowTest.
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  patchFields,
  readFields,
  removeFields,
  seedMember,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(60_000);

const BLAKE = 'u-blake';
const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });
const eggsRow = (page: Page) =>
  page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]').filter({ hasText: 'Eggs' });

async function pantryWithEggs(page: Page): Promise<{ hid: string; uid: string; path: string }> {
  const email = uniqueEmail('Alex');
  const name = `Edit conflict ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Alex', email);
  await createHousehold(page, name);
  const hid = await householdId(name);
  const uid = await uidOf(email);
  await seedMember(hid, BLAKE, 'Blake');
  await seedPantry(hid, uid, [{ id: 'eggs', name: 'Eggs', quantity: 8, expiry: inDays(5) }]);
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await expect(eggsRow(page)).toContainText('× 8', { timeout: 15_000 });
  return { hid, uid, path: `households/${hid}/stock/eggs` };
}

test("the Edit window keeps Blake's use, says so, and Review shows it", async ({ page }) => {
  // WEB-03: Alex opens Eggs (8); Blake uses 2; Alex changes only the date and saves.
  const { path } = await pantryWithEggs(page);
  await eggsRow(page).getByRole('button', { name: /Eggs/ }).click();
  const sheet = page.getByTestId('sheet_edit_item');
  await expect(sheet.getByLabel('Quantity')).toHaveValue('8');

  await patchFields(path, { quantity: 6, updatedAt: new Date(), updatedBy: BLAKE });
  const notice = sheet.getByTestId('notice_item_changed');
  await expect(notice).toHaveText(/Blake changed this item\./);
  // The draft is kept until Review.
  await expect(sheet.getByLabel('Quantity')).toHaveValue('8');
  await notice.getByTestId('button_review_changes').click();
  await expect(sheet.getByLabel('Quantity')).toHaveValue('6');
  await expect(notice).toHaveCount(0);

  await sheet.getByTestId('field_expiry').click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)/ })
    .nth(20)
    .click();
  await page.getByTestId('date_ok').click();
  await sheet.getByTestId('button_save_item').click();
  await expect(eggsRow(page)).toContainText('× 6');
  // Only the date was written: Blake's use stays.
  await expect.poll(async () => (await readFields(path))?.expiryDate).not.toBe(inDays(5));
  expect((await readFields(path))?.quantity).toBe(6);
});

test('the Edit window closes when Blake removes the item, and Undo of a delete keeps the creator', async ({ page }) => {
  const { path, uid } = await pantryWithEggs(page);
  await eggsRow(page).getByRole('button', { name: /Eggs/ }).click();
  await expect(page.getByTestId('sheet_edit_item')).toBeVisible();

  await patchFields(path, { deletedAt: new Date(), updatedAt: new Date(), updatedBy: BLAKE });
  await expect(status(page, 'Blake removed this item')).toBeVisible();
  await expect(page.getByTestId('sheet_edit_item')).toHaveCount(0);
  await expect(eggsRow(page)).toHaveCount(0);

  // Blake's Undo, as the apps now write it: the tombstone goes, nothing else changes. Then Alex uses it up and
  // undoes: the creator stays. (A field from a newer app is covered by the mapper round trips: the rules allow only
  // known fields, so this browser can't write to a document that has one.)
  await patchFields(path, { updatedAt: new Date(), updatedBy: BLAKE });
  await removeFields(path, ['deletedAt']);
  await expect(eggsRow(page)).toContainText('× 8');
  await page.getByTestId('menu_eggs').click();
  await page.getByRole('menuitem', { name: 'Used up' }).click();
  await expect(eggsRow(page)).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(eggsRow(page)).toContainText('× 8');
  await expect.poll(async () => (await readFields(path))?.deletedAt).toBeUndefined();
  const back = await readFields(path);
  expect(back?.createdBy).toBe(uid);
  expect(back?.updatedBy).toBe(uid);
});
