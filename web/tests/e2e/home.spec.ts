// Home and Insights on an iPhone (DESIGN 5.6, 5.7, 18.8 W2b; after v1 review B15), against the emulators.
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  seedMeal,
  seedPantry,
  seedRecipe,
  seedStats,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

const rows = (page: Page) => page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]');
const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const goHome = (page: Page) => tab(page, 'Home');

/** This month and the one before, as 'YYYY-MM' in the browser's calendar. */
function months(): [string, string] {
  const now = new Date();
  const last = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const ym = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  return [ym(now), ym(last)];
}

test('Needs attention, the Grocery list card, Insights and Recent activity, from a new household on', async ({
  page,
}) => {
  const email = uniqueEmail('Sam');
  const household = `Home ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);

  // A new household is told how to start, not that all is done (after v1, UX-07).
  await expect(page.getByTestId('card_home_attention')).toContainText(
    'Add food with its date, and what to use first shows here.',
  );
  await expect(page.getByTestId('home_grocery_start')).toHaveText(
    'Turn on Keep some in stock for food you always want at home, and it goes on the list when it runs low.',
  );
  await expect(page.getByTestId('card_home_groceries')).toContainText('Nothing to buy right now.');
  await expect(page.getByTestId('card_home_low_stock')).toHaveCount(0);
  await expect(page.getByTestId('card_home_activity')).toContainText('Changes by anyone in the household');

  // Add item's switch says what it does while off.
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await page.getByRole('button', { name: 'Add your first item' }).click();
  const keep = page.getByRole('switch', { name: 'Keep some in stock' });
  await expect(keep).toHaveAccessibleDescription('Puts it on the grocery list when it runs low');
  await keep.click();
  await expect(page.getByTestId('switch_auto_add')).not.toContainText('Puts it on the grocery list');
  await expect(page.getByTestId('field_keep_at_least')).toBeVisible();
  await page.keyboard.press('Escape');
  await goHome(page);

  const hid = await householdId(household);
  const uid = await uidOf(email);
  const [thisMonth, lastMonth] = months();
  await seedPantry(hid, uid, [
    { id: 'yogurt', name: 'Yogurt', expiry: inDays(-2) },
    { id: 'cheese', name: 'Cheese', expiry: inDays(-5) },
    { id: 'milk', name: 'Milk', expiry: inDays(1) },
    { id: 'bread', name: 'Bread', expiry: inDays(0), category: 'bakery' },
    { id: 'rice', name: 'Rice', expiry: inDays(200), location: 'pantry' },
    // Running low: three out, two low.
    { id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' },
    { id: 'flour', name: 'Flour', quantity: 0, min: 1, location: 'pantry' },
    { id: 'sugar', name: 'Sugar', quantity: 0, min: 1, location: 'pantry' },
    { id: 'eggs', name: 'Eggs', quantity: 2, min: 6 },
    { id: 'butter', name: 'Butter', quantity: 1, min: 2 },
  ]);
  await seedStats(hid, uid, thisMonth, { used: 3, thrown: { expired: 1 }, byDay: { 1: 1 } });
  await seedStats(hid, uid, lastMonth, { used: 12, thrown: { expired: 4, spoiled: 1 }, byDay: { 3: 5 } });

  // Needs attention: what can still be used first, soonest first; the expired are one line (UX-01). See all counts
  // the whole set and opens Pantry's Needs attention filter, which lists the same purchases (R8).
  const attention = page.getByTestId('card_home_attention');
  await expect(attention.locator('.sn-home-row')).toHaveText([
    /Bread.*Today/,
    /Milk.*Tomorrow/,
    '2 expired items to throw away or check',
  ]);
  await expect(page.getByTestId('home_attention_expired')).toBeVisible();
  await attention.getByRole('button', { name: 'See all 4 in Pantry' }).click();
  await expect(page.getByTestId('pantry_filter_needsAttention')).toHaveAttribute('aria-pressed', 'true');
  await expect(rows(page)).toHaveText([/Cheese/, /Yogurt/, /Bread/, /Milk/]);

  // One Grocery list card with what is running low, out of stock first (R1, R2); no Low stock card.
  await goHome(page);
  const groceries = page.getByTestId('card_home_groceries');
  await expect(page.getByTestId('card_home_low_stock')).toHaveCount(0);
  await expect(page.getByTestId('home_grocery_start')).toHaveCount(0);
  const low = page.getByTestId('home_running_low');
  await expect(low.getByRole('heading', { name: 'Running low' })).toBeVisible();
  await expect(low.locator('.sn-home-row')).toHaveText([/Flour.*Out/, /Olive oil.*Out/, /Sugar.*Out/, /Butter.*Low/]);
  await expect(low).toContainText('and 1 more');
  await groceries.getByRole('button', { name: 'Open list' }).click();
  await expect(page.getByTestId('screen_groceries')).toBeVisible();

  // This month's kitchen, then Insights with the month details.
  await goHome(page);
  const card = page.getByTestId('card_home_insights');
  await expect(card).toContainText('3 used · 1 thrown away');
  await expect(card.getByRole('img')).toHaveAccessibleName('This month: 3 used, 1 thrown away, 75% used.');
  await card.getByRole('button', { name: 'See your kitchen' }).click();
  await expect(page.getByTestId('screen_insights')).toBeVisible();
  const details = page.getByTestId('insights_month_details');
  await expect(details).toContainText('3 used · 1 thrown away · 75% used');
  await expect(page.getByTestId('month_bar_5')).toHaveAttribute('aria-selected', 'true');
  await page.getByTestId('month_bar_4').click();
  await expect(page.getByTestId('month_bar_4')).toHaveAttribute('aria-selected', 'true');
  await expect(details).toContainText('12 used · 5 thrown away · 71% used');
  await expect(details).toContainText('Thrown away: Expired 4, Spoiled 1');
  await expect(page.getByTestId('card_insights_reasons')).toContainText('Expired');
  await page.getByRole('button', { name: 'Back' }).click();

  // Throwing something away shows in Recent activity, in this month's counts and in Needs attention.
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await page.getByTestId('menu_yogurt').click();
  await page.getByRole('menuitem', { name: 'Throw away' }).click();
  await page.getByRole('dialog').getByText('Expired').click();
  await goHome(page);
  await expect(page.getByTestId('card_home_activity')).toContainText('You threw away Yogurt (expired)');
  await expect(page.getByTestId('card_home_activity')).toContainText('Just now');
  await expect(card).toContainText('3 used · 2 thrown away');
  await expect(page.getByTestId('home_attention_expired')).toHaveText('1 expired item to throw away or check');
  await expect(attention.getByRole('button', { name: 'See all 3 in Pantry' })).toBeVisible();
});

test("Use it up leaves out recipes planned this week, on Home and in Plan's picker, with Week starts on", async ({
  page,
}) => {
  const email = uniqueEmail('Sam');
  const household = `Use it up ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  const hid = await householdId(household);
  const uid = await uidOf(email);

  // Monday weeks on an en-US iPhone: Home listens to Plan's week, not the region's (after v1, WEB-08).
  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await page.getByTestId('week_start').click();
  await page.getByRole('menuitem', { name: 'Monday' }).click();
  await expect(page.getByTestId('week_start')).toHaveText('Monday');
  await goHome(page);

  await seedPantry(hid, uid, [{ id: 'milk', name: 'Milk', expiry: inDays(1) }]);
  await seedRecipe(hid, uid, { id: 'pancakes', title: 'Pancakes', ingredients: ['2 cups milk', '1 egg'] });
  await seedRecipe(hid, uid, { id: 'shake', title: 'Milkshake', ingredients: ['1 cup milk', '2 scoops ice cream'] });
  const useItUp = page.getByTestId('card_home_use_it_up');
  await expect(useItUp.locator('.sn-use-it-up')).toHaveText([/Milkshake/, /Pancakes/]);

  // Pancakes on the last day of Plan's week: on a Monday-first week, a Sunday (or today, on a Sunday).
  await tab(page, 'Plan');
  const days = page.getByTestId('plan_days').locator('[data-testid^="plan_day_"]');
  await expect(days).toHaveCount(7);
  const first = (await days.first().getAttribute('data-testid'))!.slice(9);
  const last = (await days.last().getAttribute('data-testid'))!.slice(9);
  expect(new Date(`${first}T12:00:00Z`).getUTCDay()).toBe(1);
  await seedMeal(hid, uid, { id: 'm1', date: last, slot: 'dinner', title: 'Pancakes', recipeId: 'pancakes' });
  await expect(page.getByTestId('plan_days')).toContainText('Pancakes');

  // The picker leaves it out for this week…
  const today = inDays(0);
  await page.getByTestId(`plan_add_${today}_lunch`).click();
  const picker = page.getByTestId('plan_use_it_up');
  await expect(picker.getByRole('button')).toHaveText([/Milkshake/]);
  await page.keyboard.press('Escape');
  await expect(page.getByTestId('sheet_add_meal')).toHaveCount(0);

  // …and suggests it for next week, which doesn't plan it.
  await page.getByTestId('plan_next_week').click();
  await expect(page.getByTestId('plan_week_empty')).toBeVisible();
  const nextFirst = (await days.first().getAttribute('data-testid'))!.slice(9);
  await page.getByTestId(`plan_add_${nextFirst}_lunch`).click();
  await expect(picker.getByRole('button')).toHaveText([/Milkshake/, /Pancakes/]);
  await page.keyboard.press('Escape');

  // Home's card leaves it out too.
  await goHome(page);
  await expect(useItUp.locator('.sn-use-it-up')).toHaveText([/Milkshake/]);
});
