// The Pantry tab on an iPhone (DESIGN 5.2, 18.8 W2a), against the emulators: the list, filters and search, swipe and
// menu actions with Undo, Mark opened, and a second phone seeing the changes (changes-only listeners).
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  PASSWORD,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

// Long flows with two phones: room for slow machines.
test.setTimeout(60_000);

const rows = (page: Page) => page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]');

async function pantryWithItems(page: Page) {
  const email = uniqueEmail('Sam');
  const household = `Pantry ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  await seedPantry(hid, await uidOf(email), [
    { id: 'milk', name: 'Milk', expiry: inDays(1) },
    { id: 'yogurt', name: 'Yogurt', quantity: 6, expiry: inDays(14) },
    {
      id: 'rice',
      name: 'Rice',
      quantity: 500,
      unit: 'g',
      location: 'pantry',
      category: 'pantry_staples',
      expiry: inDays(200),
    },
    { id: 'soup', name: 'Soup', expiry: inDays(-1), category: 'cooked' },
    { id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' },
  ]);
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await expect(rows(page)).toHaveCount(5);
  return email;
}

test('the list, filters and search, as on Android', async ({ page }) => {
  await pantryWithItems(page);
  await expect(rows(page)).toHaveText([
    /Soup.*Expired/,
    /Milk.*Tomorrow/,
    /Yogurt.*× 6/,
    /Rice.*500 g/,
    /Olive oil.*Out/,
  ]);
  await expect(page.getByTestId('pantry_item_milk').getByTestId('status_chip')).toHaveAccessibleName(
    'Expires tomorrow',
  );

  await page.getByTestId('pantry_filter_expiringSoon').click();
  await expect(rows(page)).toHaveText([/Milk/]);
  await page.getByTestId('pantry_filter_expiringSoon').click(); // the chosen filter again: everything
  await expect(rows(page)).toHaveCount(5);

  await page.getByRole('searchbox').fill('YOG');
  await expect(rows(page)).toHaveText([/Yogurt/]);
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.getByRole('button', { name: 'Sort and group' }).click();
  await page.getByRole('menuitem', { name: 'Location' }).click();
  const list = page.getByTestId('pantry_list');
  await expect(list.getByRole('heading')).toHaveText(['Fridge', 'Pantry']);
});

test('swipe to use up and Undo; throw away with a reason; mark some opened; another phone follows', async ({
  page,
  browser,
}) => {
  const email = await pantryWithItems(page);

  // Swipe right: used up, with Undo.
  const milk = page.getByTestId('pantry_item_milk');
  const box = (await milk.boundingBox())!;
  await page.mouse.move(box.x + 20, box.y + box.height / 2);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.8, box.y + box.height / 2, { steps: 8 });
  await page.mouse.up();
  await expect(page.getByRole('status').filter({ hasText: 'Milk used up' })).toBeVisible();
  await expect(milk).toHaveCount(0);
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(milk).toBeVisible();

  // The menu: throw away asks why.
  await page.getByTestId('menu_soup').click();
  await page.getByRole('menuitem', { name: 'Throw away' }).click();
  await page.getByRole('dialog').getByText('Spoiled').click();
  await expect(page.getByTestId('pantry_item_soup')).toHaveCount(0);
  await expect(page.getByRole('status').filter({ hasText: 'Soup thrown away' })).toBeVisible();

  // Mark 1 of 6 yogurts opened: it moves to its own purchase.
  await page.getByTestId('menu_yogurt').click();
  await page.getByRole('menuitem', { name: 'Mark opened' }).click();
  await expect(page.getByTestId('opened_count')).toHaveText('1 of 6');
  await page.getByTestId('opened_days_3').click();
  await page.getByTestId('button_mark_opened').click();
  await expect(page.getByTestId('pantry_item_yogurt')).toContainText('× 5');
  await expect(rows(page).filter({ hasText: 'Opened' })).toHaveCount(1);

  // The same household on another phone sees all of it.
  const other = await (await browser.newContext()).newPage();
  await openApp(other);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await other.getByLabel('Email').fill(email);
  await other.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await other.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await expect(other.getByTestId('pantry_item_soup')).toHaveCount(0);
  await expect(other.getByTestId('pantry_item_yogurt')).toContainText('× 5');
  await expect(rows(other).filter({ hasText: 'Opened' })).toHaveCount(1);
  // And a change there reaches the first phone.
  await other.getByTestId('menu_milk').click();
  await other.getByRole('menuitem', { name: 'Used up' }).click();
  await expect(page.getByTestId('pantry_item_milk')).toHaveCount(0);
  // A row changed there is highlighted here for a moment (DESIGN 6.4).
  await other.getByTestId('menu_rice').click();
  await other.getByRole('menuitem', { name: 'Add 1' }).click();
  await expect(page.getByTestId('pantry_item_rice')).toHaveAttribute('data-remote', 'true');
  await expect(page.getByTestId('pantry_item_rice')).not.toHaveAttribute('data-remote', 'true', { timeout: 4000 });
});

test('three expired purchases fold into a header', async ({ page }) => {
  const email = uniqueEmail('Sam');
  const household = `Expired ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  await seedPantry(await householdId(household), await uidOf(email), [
    { id: 'a', name: 'Ham', expiry: inDays(-1) },
    { id: 'b', name: 'Cream', expiry: inDays(-2) },
    { id: 'c', name: 'Salsa', expiry: inDays(-3) },
    { id: 'd', name: 'Eggs', expiry: inDays(9) },
  ]);
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await expect(page.getByTestId('pantry_expired_header')).toContainText('3 expired items');
  await expect(rows(page)).toHaveText([/Eggs/]);
  await page.getByTestId('pantry_expired_toggle').click();
  await expect(rows(page)).toHaveCount(4);
});
