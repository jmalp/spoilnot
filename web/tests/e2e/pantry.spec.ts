// The Pantry tab on an iPhone (DESIGN 5.2, 18.8 W2a), against the emulators: the list, filters and search, swipe and
// menu actions with Undo, Mark opened, and a second phone seeing the changes (changes-only listeners).
import { expect, test, type Page } from '@playwright/test';
import {
  closeOtherPhones,
  createHousehold,
  expectFromServer,
  householdId,
  inDays,
  highlightedRows,
  newPhone,
  openApp,
  PASSWORD,
  recordHighlights,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.afterEach(closeOtherPhones);

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
  return { email, hid };
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
  // A row says its status in words before its place, and reads the full wording as one sentence (DESIGN 19.6).
  const milk = page.getByTestId('pantry_item_milk');
  await expect(milk.locator('.sn-pantry-row__second')).toHaveText('Tomorrow · Fridge');
  await expect(milk.getByRole('button', { name: 'Milk, 1 piece, Expires tomorrow, Fridge' })).toBeVisible();

  // The default view in two groups (DESIGN 19.12); the line above the title counts every row (19.7).
  await expect(page.getByTestId('screen_eyebrow')).toHaveText('5 items');
  const groups = page.getByTestId('pantry_list');
  await expect(groups.getByRole('heading')).toHaveText(['Use first', 'Everything else']);
  await expect(groups.getByRole('region', { name: 'Use first' }).locator('[data-testid^="pantry_item_"]')).toHaveText([
    /Soup/,
    /Milk/,
  ]);

  // Needs attention holds the expired too, as Home and the digest count them (after v1, R8); a filter shows the list
  // without the two groups.
  await page.getByTestId('pantry_filter_needsAttention').click();
  await expect(rows(page)).toHaveText([/Soup/, /Milk/]);
  await expect(groups.getByRole('heading')).toHaveCount(0);
  await page.getByTestId('pantry_filter_needsAttention').click(); // the chosen filter again: everything
  await expect(rows(page)).toHaveCount(5);

  await page.getByRole('searchbox').fill('YOG');
  await expect(rows(page)).toHaveText([/Yogurt/]);
  await page.getByRole('button', { name: 'Cancel' }).click();

  await page.getByRole('button', { name: 'Sort and group' }).click();
  await page.getByRole('menuitem', { name: 'Location' }).click();
  const list = page.getByTestId('pantry_list');
  await expect(list.getByRole('heading')).toHaveText(['Fridge', 'Pantry']);
});

test("a search, filter or sort changes the rows at once and highlights none; another phone's change still is", async ({
  page,
}) => {
  const { email, hid } = await pantryWithItems(page);
  const uid = await uidOf(email);
  await recordHighlights(page);
  const animations = () =>
    page.getByTestId('pantry_list').evaluate((list) => list.getAnimations({ subtree: true }).length);

  // Rows a wider search, filter or sort brings back were there all along: none is highlighted, and none fades or slides
  // (after v1, 2026-11-17, DESIGN 6.4; the web followed the rows shown and highlighted them as another phone's).
  await page.getByRole('searchbox').fill('YOG');
  await expect(rows(page)).toHaveText([/Yogurt/]);
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(rows(page)).toHaveCount(5);
  expect(await animations()).toBe(0);
  await page.getByTestId('pantry_filter_needsAttention').click();
  await expect(rows(page)).toHaveCount(2);
  await page.getByTestId('pantry_filter_needsAttention').click();
  await expect(rows(page)).toHaveCount(5);
  await page.getByRole('button', { name: 'Sort and group' }).click();
  await page.getByRole('menuitem', { name: 'Name' }).click();
  await expect(rows(page).first()).toContainText('Milk');
  expect(await animations()).toBe(0);
  expect(await highlightedRows(page)).toEqual([]);

  // Another member's new purchase is highlighted, with the search empty and while it shows the row.
  await seedPantry(hid, uid, [{ id: 'butter', name: 'Butter', expiry: inDays(20) }]);
  await expect(page.getByTestId('pantry_item_butter')).toHaveAttribute('data-remote', 'true');
  await page.getByRole('searchbox').fill('rice');
  await expect(rows(page)).toHaveText([/Rice/]);
  await seedPantry(hid, uid, [
    { id: 'rice', name: 'Rice', quantity: 400, unit: 'g', location: 'pantry', expiry: inDays(200) },
  ]);
  await expect(page.getByTestId('pantry_item_rice')).toHaveAttribute('data-remote', 'true');
});

test('swipe to use up and Undo; throw away with a reason; mark some opened; another phone follows', async ({
  page,
  browser,
}) => {
  // Two phones and a long flow: the same room as the plan and recipes flows (under a full run's load the first phone's
  // part alone took most of 60 s).
  test.setTimeout(90_000);
  const { email } = await pantryWithItems(page);

  // Swipe right: used up, with Undo. The swipe is drawn at the row's measured place, so first wait for the server's
  // answer: one more than 2 s late brings the Offline banner above the list (DESIGN 9.5), and its going moves the rows.
  const milk = page.getByTestId('pantry_item_milk');
  await expectFromServer(page);
  const box = (await milk.boundingBox())!;
  // From a finger's width in: a swipe from the screen's left edge is Safari's Back (after v1, WEB-15).
  await page.mouse.move(box.x + 40, box.y + box.height / 2);
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
  const other = await (await newPhone(browser)).newPage();
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
