// The Groceries tab on an iPhone (DESIGN 5.3, 9.6, 18.8 W3), against the emulators.
import { expect, test, type Page } from '@playwright/test';
import {
  closeOtherPhones,
  createHousehold,
  householdId,
  newPhone,
  openApp,
  PASSWORD,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.afterEach(closeOtherPhones);

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

// A long flow: room for slow machines.
test.setTimeout(60_000);

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

  // The same name again asks first, saying how much (after v1, UX-18); Add more adds to it.
  await add(page, 'BANANAS').catch(() => undefined);
  await expect(page.getByRole('dialog')).toContainText(
    'Add 1 more to the 2 on the list, or add it as a separate item?',
  );
  await page.getByTestId('button_increase').click();
  await expect(row(page, 'bananas')).toContainText('× 3');

  // Checking off moves items to the cart; yogurt before bananas teaches this store's order.
  await row(page, 'Greek yogurt').getByRole('checkbox').click();
  await row(page, 'bananas').getByRole('checkbox').click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (2)');
  await add(page, 'milk');
  await add(page, 'apples');
  // Aisle headers in sentence case, as written (after v1, UX-18).
  await expect(list(page).getByRole('heading')).toHaveText(['Dairy and eggs', 'Produce', 'Other']);
  await expect(list(page).getByRole('heading').first()).toHaveCSS('text-transform', 'none');
  await expect(page.getByTestId('cart_header').locator('.sn-cart-header__label')).toHaveCSS('text-transform', 'none');

  // The list has learned an order: once, under the first aisle, why the aisles move (after v1, UX-08).
  const hint = page.getByTestId('grocery_aisle_hint');
  await expect(hint).toContainText('Aisles follow the order you shop in.');
  await expect(list(page).locator('section').first().getByTestId('grocery_aisle_hint')).toBeVisible();
  await page.getByTestId('grocery_aisle_hint_ok').click();
  await expect(hint).toHaveCount(0);
  await page.reload();
  await expect(row(page, 'milk')).toBeVisible();
  await expect(hint).toHaveCount(0);

  // Add to pantry: one line each; skip the batteries.
  await row(page, 'batteries').getByRole('checkbox').click();
  // The sheet follows the cart while open (after v1, REL-08); opening it once the cart shows the batteries keeps this
  // test's order of events plain.
  await expect(page.getByTestId('cart_header')).toContainText('In cart (3)');
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

  // Undo puts the three items back in the cart and takes the purchases off (after v1, LOGIC-10); then again.
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (3)');
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toHaveCount(0);
  await tab(page, 'Groceries');
  await expect(page.getByTestId('cart_header')).toContainText('In cart (3)');
  await page.getByTestId('fab_add_to_pantry').click();
  const again = sheet.locator('[data-testid^="entry_"]').filter({ hasText: 'batteries' });
  await again.getByRole('button').first().click();
  await again.getByRole('switch').check();
  await sheet.getByTestId('button_confirm_add_to_pantry').click();
  await expect(page.getByTestId('cart_header')).toHaveCount(0);
  await expect(row(page, 'batteries')).toHaveCount(0);

  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toContainText('Greek yogurt');
  await expect(page.getByTestId('pantry_list')).toContainText('bananas');

  // Home counts what is left to buy.
  await tab(page, 'Home');
  await expect(page.getByTestId('card_home_groceries')).toContainText('2 items to buy');

  // Increase quantity only adds in the same unit (after v1, LOGIC-08).
  await tab(page, 'Groceries');
  // Quick add needs the list loaded.
  await expect(row(page, 'milk')).toBeVisible();
  await add(page, '500g rice');
  await expect(row(page, 'rice')).toContainText('500 g');
  await add(page, '2 kg rice').catch(() => undefined);
  await expect(page.getByRole('dialog')).toContainText('rice is already on the list as 500 g.');
  await expect(page.getByTestId('button_increase')).toHaveCount(0);
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
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

  // Edit renames and sets a note; a left swipe deletes with Undo; Clear cart has Undo too.
  await add(page, 'beans');
  await row(page, 'beans').getByRole('button', { name: 'Options for beans' }).click();
  await page.getByRole('menuitem', { name: 'Edit' }).click();
  const edit = page.getByTestId('sheet_edit_grocery_item');
  await edit.getByLabel('Name').fill('Black beans');
  await edit.getByLabel('Notes').fill('the big can');
  await edit.getByTestId('button_save_grocery_item').click();
  await expect(row(page, 'Black beans')).toContainText('the big can');

  // Swipes (after v1, UX-10): what each does shows under the row while it moves.
  const swipe = async (name: string, from: number, to: number, label: string) => {
    const box = (await row(page, name).boundingBox())!;
    await page.mouse.move(box.x + box.width * from, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width * to, box.y + box.height / 2, { steps: 8 });
    await expect(row(page, name).getByTestId('grocery_swipe')).toHaveText(label);
    await page.mouse.up();
  };
  // Left deletes, with Undo.
  await swipe('Black beans', 0.8, 0.05, 'Delete');
  const removed = page.getByRole('status').filter({ hasText: 'Black beans removed' });
  await expect(removed).toBeVisible();
  // The 8-second Undo waits while the snackbar has focus (after v1, A11Y-02), as for someone tabbing to it.
  await removed.getByRole('button', { name: 'Undo' }).focus();
  await page.waitForTimeout(9_000);
  await expect(removed).toBeVisible();
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(row(page, 'Black beans')).toBeVisible();
  // Right checks off, and in the cart puts it back.
  await swipe('Black beans', 0.15, 0.9, 'Check off');
  await expect(page.getByTestId('cart_header')).toContainText('In cart (1)');
  await expect(row(page, 'Black beans').getByRole('checkbox')).toBeChecked();
  await swipe('Black beans', 0.15, 0.9, 'Back to list');
  await expect(page.getByTestId('cart_header')).toHaveCount(0);
  await expect(row(page, 'Black beans').getByRole('checkbox')).not.toBeChecked();

  await row(page, 'Black beans').getByRole('checkbox').click();
  await expect(page.getByTestId('button_clear_checked')).toHaveText('Clear cart');
  await page.getByTestId('button_clear_checked').click();
  await expect(row(page, 'Black beans')).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByTestId('cart_header')).toContainText('In cart (1)');

  // A staple below Keep at least lands on the default list once (the restock engine, DESIGN 9.6).
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedPantry(hid, uid, [{ id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' }]);
  // Seeded as another phone would write it, so this page's engine waits 5 s before adding it (DESIGN 9.6).
  await expect(row(page, 'Olive oil')).toContainText('Added automatically', { timeout: 15_000 });
  // Edit says why (after v1, UX-08).
  await row(page, 'Olive oil').getByRole('button', { name: 'Options for Olive oil' }).click();
  await page.getByRole('menuitem', { name: 'Edit' }).click();
  await expect(page.getByTestId('grocery_auto_reason')).toHaveText(
    'Added automatically: you have 0 and keep at least 1.',
  );
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('sheet_edit_grocery_item')).toHaveCount(0);
  // Stock coming back shows one line, "Back in stock · Remove", instead of two badges.
  await seedPantry(hid, uid, [{ id: 'oil', name: 'Olive oil', quantity: 2, min: 1, location: 'pantry' }]);
  await expect(row(page, 'Olive oil')).toContainText('Back in stock');
  await expect(row(page, 'Olive oil')).not.toContainText('Added automatically');
  await row(page, 'Olive oil')
    .getByRole('button', { name: "Remove Olive oil from the list, it's back in stock" })
    .click();
  await expect(row(page, 'Olive oil')).toHaveCount(0);

  // Another phone on the account adds an item: it shows here with a short highlight.
  const other = await (await newPhone(browser)).newPage();
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
