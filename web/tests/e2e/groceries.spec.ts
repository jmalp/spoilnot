// The Groceries tab on an iPhone (DESIGN 5.3, 9.6, 18.8 W3), against the emulators.
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, householdId, openApp, PASSWORD, seedPantry, signUp, uidOf, uniqueEmail } from './support';

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const list = (page: Page) => page.getByTestId('grocery_list');
const row = (page: Page, name: string) =>
  list(page)
    .locator('[data-testid^="grocery_item_"]')
    .filter({ has: page.getByText(name, { exact: true }) });

async function add(page: Page, text: string) {
  await page.getByTestId('grocery_quick_add').fill(text);
  await page.getByTestId('grocery_quick_add').press('Enter');
  await expect(page.getByTestId('grocery_quick_add')).toHaveValue('');
}

async function groceriesOfNewHousehold(page: Page) {
  const email = uniqueEmail('Sam');
  const household = `Groceries ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  await tab(page, 'Groceries');
  // The list has loaded (the switcher says Groceries even before).
  await expect(page.getByText('Your list is empty.', { exact: false })).toBeVisible();
  return { email, household };
}

test('quick add sorts into aisles, check-offs learn the store order, and the cart goes to the pantry', async ({
  page,
}) => {
  await groceriesOfNewHousehold(page);

  // Typed names get a guessed aisle; a quantity in front is read as one.
  await add(page, '2 bananas');
  await add(page, 'Greek yogurt');
  await add(page, 'batteries');
  await expect(list(page).getByRole('heading')).toHaveText(['Produce', 'Dairy and eggs', 'Other']);
  await expect(row(page, 'bananas')).toContainText('× 2');

  // The same name again asks first; Increase adds to it.
  await add(page, 'BANANAS').catch(() => undefined);
  await expect(page.getByRole('dialog')).toContainText('bananas is already on the list.');
  await page.getByTestId('button_increase').click();
  await expect(row(page, 'bananas')).toContainText('× 3');

  // Checking off moves items to the cart; yogurt before bananas teaches this store's order.
  await row(page, 'Greek yogurt').getByRole('checkbox').click();
  await row(page, 'bananas').getByRole('checkbox').click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (2)');
  await add(page, 'milk');
  await add(page, 'apples');
  await expect(list(page).getByRole('heading')).toHaveText(['Dairy and eggs', 'Produce', 'Other']);

  // Add to pantry: one line each; skip the batteries.
  await row(page, 'batteries').getByRole('checkbox').click();
  await page.getByTestId('fab_add_to_pantry').click();
  const sheet = page.getByTestId('sheet_add_to_pantry');
  await expect(sheet.locator('[data-testid^="entry_"]').filter({ hasText: 'bananas' })).toContainText(
    '3 · Fridge · No expiry date',
  );
  const batteries = sheet.locator('[data-testid^="entry_"]').filter({ hasText: 'batteries' });
  await batteries.getByRole('button').first().click();
  await batteries.getByRole('switch').check();
  await expect(sheet.getByTestId('button_confirm_add_to_pantry')).toHaveText('Add 2 to pantry');
  await sheet.getByTestId('button_confirm_add_to_pantry').click();
  await expect(page.getByRole('status').filter({ hasText: 'Added to the pantry' })).toBeVisible();
  await expect(page.getByTestId('cart_header')).toHaveCount(0);
  await expect(row(page, 'batteries')).toHaveCount(0);

  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toContainText('Greek yogurt');
  await expect(page.getByTestId('pantry_list')).toContainText('bananas');

  // Home counts what is left to buy.
  await tab(page, 'Home');
  await expect(page.getByTestId('card_home_groceries')).toContainText('2 items to buy');
});

test('lists, edit, swipe and clear with Undo, low-stock restock, and another phone adding', async ({
  page,
  browser,
}) => {
  const { email, household } = await groceriesOfNewHousehold(page);

  // A second list for another store.
  await page.getByTestId('grocery_list_switcher').click();
  await page.getByRole('menuitem', { name: 'New list' }).click();
  await page.getByTestId('field_list_name').fill('Costco');
  await page.getByTestId('button_save_list').click();
  await expect(page.getByTestId('grocery_list_switcher')).toHaveAccessibleName('List: Costco');
  await add(page, 'paper towels');
  await page.getByTestId('grocery_list_switcher').click();
  await page.getByRole('menuitem', { name: 'Groceries' }).click();
  await expect(page.getByText('Your list is empty.', { exact: false })).toBeVisible();

  // Edit renames and sets a note; swipe deletes with Undo; Clear checked has Undo too.
  await add(page, 'beans');
  await row(page, 'beans').getByRole('button', { name: 'Options for beans' }).click();
  await page.getByRole('menuitem', { name: 'Edit' }).click();
  const edit = page.getByTestId('sheet_edit_grocery_item');
  await edit.getByLabel('Name').fill('Black beans');
  await edit.getByLabel('Notes').fill('the big can');
  await edit.getByTestId('button_save_grocery_item').click();
  await expect(row(page, 'Black beans')).toContainText('the big can');

  const beans = row(page, 'Black beans');
  const box = (await beans.boundingBox())!;
  await page.mouse.move(box.x + box.width * 0.8, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + 10, box.y + box.height / 2, { steps: 8 });
  await page.mouse.up();
  await expect(page.getByRole('status').filter({ hasText: 'Black beans removed' })).toBeVisible();
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(row(page, 'Black beans')).toBeVisible();

  await row(page, 'Black beans').getByRole('checkbox').click();
  await page.getByTestId('button_clear_checked').click();
  await expect(row(page, 'Black beans')).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (1)');

  // A staple below Keep at least lands on the default list once (the restock engine, DESIGN 9.6).
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [{ id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' }]);
  await expect(row(page, 'Olive oil')).toContainText('Low stock');
  // Stock coming back shows Back in stock with a one-tap remove.
  await seedPantry(hid, uid, [{ id: 'oil', name: 'Olive oil', quantity: 2, min: 1, location: 'pantry' }]);
  await expect(row(page, 'Olive oil')).toContainText('Back in stock');
  await row(page, 'Olive oil').getByRole('button', { name: 'Remove' }).click();
  await expect(row(page, 'Olive oil')).toHaveCount(0);

  // Another phone on the account adds an item: it shows here with a short highlight.
  const other = await (await browser.newContext()).newPage();
  await openApp(other);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await other.getByLabel('Email').fill(email);
  await other.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await tab(other, 'Groceries');
  await expect(other.getByTestId('cart_header')).toContainText('In cart (1)');
  await add(other, 'eggs');
  await expect(row(page, 'eggs')).toHaveAttribute('data-remote', 'true');
  await expect(row(page, 'eggs')).not.toHaveAttribute('data-remote', 'true', { timeout: 4000 });

  // Deleting the second list leaves the default one.
  await page.getByTestId('grocery_list_switcher').click();
  await page.getByRole('menuitem', { name: 'Costco' }).click();
  await page.getByTestId('grocery_list_switcher').click();
  await page.getByRole('menuitem', { name: 'Delete list' }).click();
  await page.getByTestId('confirm').click();
  await expect(page.getByTestId('grocery_list_switcher')).toHaveAccessibleName('List: Groceries');
});
