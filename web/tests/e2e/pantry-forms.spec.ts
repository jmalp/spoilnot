// Add item and the Edit window on an iPhone (DESIGN 5.2, 18.8 W2b), against the emulators.
import { expect, test } from '@playwright/test';
import { createHousehold, householdId, inDays, openApp, seedPantry, signUp, uidOf, uniqueEmail } from './support';

test('add an item, add the same again (merged), fill from a known product, pick a date, edit to zero with Undo', async ({
  page,
}) => {
  const email = uniqueEmail('Sam');
  const household = `Forms ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();

  // The empty pantry offers its button; the form adds 6 Greek yogurts for a week in the fridge.
  await page.getByTestId('button_add_first_item').click();
  const sheet = page.getByTestId('sheet_add_item');
  await sheet.getByLabel('Name').fill('Greek yogurt');
  await sheet.getByLabel('Quantity').fill('6');
  await sheet.getByTestId(`date_chip_${inDays(7)}`).click();
  await expect(sheet.getByTestId('field_expiry')).toContainText('In 7 days');
  await sheet.getByTestId('button_save_item').click();
  await expect(page.getByRole('status').filter({ hasText: 'Greek yogurt added' })).toBeVisible();
  const yogurt = page
    .getByTestId('pantry_list')
    .locator('[data-testid^="pantry_item_"]')
    .filter({ hasText: 'Greek yogurt' });
  await expect(yogurt).toContainText('× 6');

  // The same product, date and place adds to that purchase.
  await page.getByTestId('fab_add_item').click();
  await sheet.getByLabel('Name').fill('greek YOGURT');
  await sheet.getByTestId(`date_chip_${inDays(7)}`).click();
  await sheet.getByTestId('button_save_item').click();
  await expect(yogurt).toContainText('× 7');

  // A known product fills the form from its name; the date picker sets a date.
  await seedPantry(await householdId(household), await uidOf(email), [
    {
      id: 'rice',
      name: 'Basmati rice',
      quantity: 0,
      min: 1,
      unit: 'g',
      location: 'pantry',
      category: 'pantry_staples',
    },
  ]);
  await page.getByTestId('fab_add_item').click();
  await sheet.getByLabel('Name').fill('basm');
  await page.getByRole('menuitem', { name: 'Basmati rice' }).click();
  await expect(sheet.getByTestId('location_pantry')).toHaveAttribute('aria-checked', 'true');
  await expect(sheet.getByTestId('field_unit')).toHaveText('g');
  await sheet.getByTestId('field_expiry').click();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)/ })
    .nth(20)
    .click();
  await page.getByTestId('date_ok').click();
  await sheet.getByTestId('button_save_item').click();
  await expect(page.getByTestId('pantry_list')).toContainText('Basmati rice');

  // Edit to 0 uses the purchase up, with Undo.
  await yogurt.getByRole('button', { name: /Greek yogurt/ }).click();
  const edit = page.getByTestId('sheet_edit_item');
  await edit.getByLabel('Quantity').fill('0');
  await edit.getByTestId('button_save_item').click();
  await expect(yogurt).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(yogurt).toContainText('× 7');
});
