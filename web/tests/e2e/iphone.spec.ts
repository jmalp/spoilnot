// iPhone polish (after v1, B14's web half and B20; DESIGN 5.9, 6.1, 18.1, 18.2): sheets keep what was picked through
// another phone's check-off and a reload (iOS reloads pages it discarded in the background), Settings' Week starts
// on, Safari's edge swipe, and sheets above the on-screen keyboard as far as WebKit can play one.
import { expect, test, type Page } from '@playwright/test';
import {
  closeOtherPhones,
  createHousehold,
  defaultListId,
  householdId,
  newPhone,
  openApp,
  PASSWORD,
  seedGroceries,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.afterEach(closeOtherPhones);
// Two phones and reloads: room for slow machines.
test.setTimeout(90_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const row = (page: Page, name: string) =>
  page
    .getByTestId('grocery_list')
    .locator('[data-testid^="grocery_item_"]')
    .filter({ has: page.getByText(name, { exact: true }) });
const entry = (page: Page, name: string) =>
  page.getByTestId('sheet_add_to_pantry').locator('[data-testid^="entry_"]').filter({ hasText: name });

/** A new household with [items] on its list, on Groceries once they show. */
async function groceries(page: Page, items: { id: string; name: string; checked?: boolean }[]) {
  const email = uniqueEmail('Sam');
  const household = `iPhone ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);
  await seedGroceries(hid, uid, await defaultListId(hid), items);
  await tab(page, 'Groceries');
  await expect(row(page, items[0]!.name)).toBeVisible();
  return { email, hid, uid };
}

test("Add to pantry keeps typed amounts through another phone's check-off and a reload", async ({ page, browser }) => {
  const { email } = await groceries(page, [
    { id: 'butter', name: 'Butter', checked: true },
    { id: 'milk', name: 'Milk', checked: true },
    { id: 'sugar', name: 'Sugar' },
  ]);
  await expect(page.getByTestId('cart_header')).toContainText('In cart (2)');

  // Butter: 3 in the freezer.
  await page.getByTestId('fab_add_to_pantry').click();
  await entry(page, 'Butter').getByRole('button').first().click();
  await entry(page, 'Butter').getByLabel('Quantity').fill('3');
  await entry(page, 'Butter').getByTestId('location_freezer').click();
  await expect(entry(page, 'Butter')).toContainText('3 · Freezer');

  // Another phone checks off Sugar: a line for it appears and Butter keeps its edits (REL-08).
  const other = await (await newPhone(browser)).newPage();
  await openApp(other);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await other.getByLabel('Email').fill(email);
  await other.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await other.getByRole('button', { name: 'Sign in' }).click();
  await tab(other, 'Groceries');
  await row(other, 'Sugar').getByRole('checkbox').click();
  await expect(entry(page, 'Sugar')).toContainText('1 · Fridge');
  await expect(entry(page, 'Butter')).toContainText('3 · Freezer');
  await expect(entry(page, 'Butter').getByLabel('Quantity')).toHaveValue('3');

  // iOS reloads a page it discarded in the background: the sheet comes back as it was.
  await page.reload();
  await expect(entry(page, 'Butter')).toContainText('3 · Freezer');
  await expect(entry(page, 'Butter').getByLabel('Quantity')).toHaveValue('3');
  await expect(entry(page, 'Sugar')).toBeVisible();
  await expect(page.getByTestId('button_confirm_add_to_pantry')).toHaveText('Add all 3 to pantry');
  await page.getByTestId('button_confirm_add_to_pantry').click();
  await expect(page.getByRole('status').filter({ hasText: 'Added to the pantry' })).toBeVisible();

  // Closed sheets leave nothing behind: a reload shows the list without it.
  await page.reload();
  await expect(page.getByTestId('screen_groceries')).toBeVisible();
  await expect(page.getByTestId('sheet_add_to_pantry')).toHaveCount(0);
  expect(await page.evaluate(() => Object.keys(sessionStorage).filter((k) => k.startsWith('spoilnot.draft.')))).toEqual(
    [],
  );
});

test("Plan's sheets keep their picks through a reload, on the week they were opened in", async ({ page }) => {
  const email = uniqueEmail('Sam');
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, `Plan drafts ${Date.now()}`);
  const uid = await uidOf(email);
  await tab(page, 'Plan');
  await page.getByTestId('plan_next_week').click();
  const nextWeek = await page.getByTestId('plan_week_range').textContent();
  const day = (await page
    .getByTestId('plan_days')
    .locator('[data-testid^="plan_day_"]')
    .first()
    .getAttribute('data-testid'))!.replace('plan_day_', '');

  // Add meal: a cook picked and a meal typed, then the page reloads.
  await page.getByTestId(`plan_add_${day}_dinner`).click();
  const add = page.getByTestId('sheet_add_meal');
  await add.getByTestId(`plan_cook_${uid}`).click();
  await add.getByTestId('plan_other_field').fill('Pizza night');
  await page.reload();
  await expect(add.getByTestId('plan_other_field')).toHaveValue('Pizza night');
  await expect(add.getByTestId(`plan_cook_${uid}`)).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByTestId('plan_week_range')).toHaveText(nextWeek!);
  await add.getByTestId('plan_add_other').click();
  const pizza = page.getByTestId(`plan_day_${day}`).getByRole('button', { name: /Pizza night/ });
  await expect(pizza).toContainText('You');

  // A meal's sheet: servings and a note, then the page reloads.
  await pizza.click();
  const sheet = page.getByTestId('sheet_meal');
  await sheet.getByRole('button', { name: 'More servings' }).click();
  await sheet.getByRole('button', { name: 'More servings' }).click();
  await sheet.getByLabel('Note').fill('Two large ones');
  await page.reload();
  await expect(sheet.getByTestId('plan_servings')).toHaveText('2');
  await expect(sheet.getByLabel('Note')).toHaveValue('Two large ones');
  await sheet.getByTestId('plan_save_meal').click();
  await expect(sheet).toHaveCount(0);
  await expect(pizza).toContainText('2 servings');
});

test('Week starts on: Plan follows the choice in Settings, kept on the phone', async ({ page }) => {
  await openApp(page);
  await signUp(page, 'Sam');
  await createHousehold(page, `Weeks ${Date.now()}`);
  const firstDay = async () => {
    const id = await page
      .getByTestId('plan_days')
      .locator('[data-testid^="plan_day_"]')
      .first()
      .getAttribute('data-testid');
    return new Date(`${id!.replace('plan_day_', '')}T12:00:00Z`).getUTCDay();
  };

  // The iPhone 15 profile is en-US: Sunday weeks.
  await tab(page, 'Plan');
  await expect(page.getByTestId('plan_days')).toBeVisible();
  expect(await firstDay()).toBe(0);

  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  const weekStart = page.getByTestId('week_start');
  await expect(weekStart).toHaveText('Automatic (Sunday)');
  await weekStart.click();
  await page.getByRole('menuitem', { name: 'Monday' }).click();
  await expect(weekStart).toHaveText('Monday');

  await tab(page, 'Plan');
  await expect(page.getByTestId('plan_days')).toBeVisible();
  expect(await firstDay()).toBe(1);
  await page.reload();
  await expect(page.getByTestId('plan_days')).toBeVisible();
  expect(await firstDay()).toBe(1);

  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await weekStart.click();
  await page.getByRole('menuitem', { name: 'Saturday' }).click();
  await tab(page, 'Plan');
  await expect(page.getByTestId('plan_days')).toBeVisible();
  expect(await firstDay()).toBe(6);
});

test("in a Safari tab, a swipe from the screen's left edge is left to Safari", async ({ page }) => {
  await groceries(page, [
    { id: 'bread', name: 'Bread' },
    { id: 'eggs', name: 'Eggs' },
  ]);
  const swipe = async (name: string, fromX: number, toX: number) => {
    const box = (await row(page, name).boundingBox())!;
    await page.mouse.move(fromX, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(toX, box.y + box.height / 2, { steps: 8 });
    await page.mouse.up();
  };
  const width = page.viewportSize()!.width;

  // From 4 px, all the way across: Safari's Back, not the row's check-off (WEB-15; right checks off since UX-10).
  await swipe('Bread', 4, width - 10);
  await expect(page.getByTestId('screen_groceries')).toBeVisible();
  await expect(row(page, 'Bread')).toBeVisible();
  await expect(page.getByTestId('cart_header')).toHaveCount(0);

  // From inside the row, the same swipe checks it off.
  await swipe('Bread', 40, width - 10);
  await expect(page.getByTestId('cart_header')).toContainText('In cart (1)');
  await expect(row(page, 'Bread').getByRole('checkbox')).toBeChecked();
});

test('a sheet stays above the keyboard and scrolls the focused field into view', async ({ page }) => {
  await groceries(
    page,
    ['Apples', 'Bread', 'Cheese', 'Dates', 'Eggs'].map((name) => ({ id: name.toLowerCase(), name, checked: true })),
  );
  await expect(page.getByTestId('cart_header')).toContainText('In cart (5)');
  await page.getByTestId('fab_add_to_pantry').click();
  const sheet = page.getByTestId('sheet_add_to_pantry');
  for (const name of ['Apples', 'Bread', 'Cheese', 'Dates', 'Eggs'])
    await entry(page, name).getByRole('button').first().click();
  const height = await page.evaluate(() => window.innerHeight);
  const keyboard = 336;

  // Playwright's WebKit shows no keyboard: play one by shrinking the visual viewport, as iOS does.
  await page.evaluate((visible) => {
    const viewport = window.visualViewport!;
    Object.defineProperty(viewport, 'height', { configurable: true, get: () => visible });
    viewport.dispatchEvent(new Event('resize'));
  }, height - keyboard);
  const save = page.getByTestId('button_confirm_add_to_pantry');
  await expect
    .poll(async () => (await sheet.boundingBox())!.y + (await sheet.boundingBox())!.height)
    .toBeCloseTo(height - keyboard, 0);
  const saveBox = (await save.boundingBox())!;
  expect(saveBox.y + saveBox.height).toBeLessThanOrEqual(height - keyboard);
  expect((await sheet.boundingBox())!.y).toBeGreaterThanOrEqual(0);

  // The last line's quantity, below what fits, scrolls into view above Save when focused.
  const field = entry(page, 'Eggs').getByLabel('Quantity');
  await field.focus();
  await expect
    .poll(async () => {
      const box = (await field.boundingBox())!;
      return box.y >= (await sheet.boundingBox())!.y && box.y + box.height <= (await save.boundingBox())!.y;
    })
    .toBe(true);

  // The keyboard closes: the sheet goes back to the bottom.
  await page.evaluate(() => {
    const viewport = window.visualViewport!;
    delete (viewport as unknown as Record<string, unknown>).height;
    viewport.dispatchEvent(new Event('resize'));
  });
  await expect
    .poll(async () => (await sheet.boundingBox())!.y + (await sheet.boundingBox())!.height)
    .toBeCloseTo(height, 0);
});
