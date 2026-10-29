// Home and Insights on an iPhone (DESIGN 5.6, 5.7, 18.8 W2b), against the emulators.
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  seedPantry,
  seedStats,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

const rows = (page: Page) => page.getByTestId('pantry_list').locator('[data-testid^="pantry_item_"]');
const goHome = (page: Page) => page.getByRole('navigation').getByRole('button', { name: 'Home' }).click();

/** This month and the one before, as 'YYYY-MM' in the browser's calendar. */
function months(): [string, string] {
  const now = new Date();
  const last = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const ym = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  return [ym(now), ym(last)];
}

test('the cards lead to the filtered Pantry, Insights shows each month, and Recent activity follows', async ({
  page,
}) => {
  const email = uniqueEmail('Sam');
  const household = `Home ${Date.now()}`;
  await openApp(page);
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);

  // A new household: the calm one-line cards.
  await expect(page.getByTestId('card_home_attention')).toContainText('Nothing expires in the next 3 days.');
  await expect(page.getByTestId('card_home_low_stock')).toContainText('Your staples are stocked.');
  await expect(page.getByTestId('card_home_activity')).toContainText('Changes by anyone in the household');

  const hid = await householdId(household);
  const uid = await uidOf(email);
  const [thisMonth, lastMonth] = months();
  await seedPantry(hid, uid, [
    { id: 'yogurt', name: 'Yogurt', expiry: inDays(-2) },
    { id: 'milk', name: 'Milk', expiry: inDays(1) },
    { id: 'rice', name: 'Rice', expiry: inDays(200), location: 'pantry' },
    { id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' },
    { id: 'eggs', name: 'Eggs', quantity: 2, min: 6 },
  ]);
  await seedStats(hid, uid, thisMonth, { used: 3, thrown: { expired: 1 }, byDay: { 1: 1 } });
  await seedStats(hid, uid, lastMonth, { used: 12, thrown: { expired: 4, spoiled: 1 }, byDay: { 3: 5 } });

  // Needs attention: soonest first; one is expired and one is not, so See all opens Expiring soon.
  const attention = page.getByTestId('card_home_attention');
  await expect(attention.locator('.sn-home-row')).toHaveText([/Yogurt.*Expired/, /Milk.*Tomorrow/]);
  await attention.getByRole('button', { name: 'See all 2 in Pantry' }).click();
  await expect(page.getByTestId('pantry_filter_expiringSoon')).toHaveAttribute('aria-pressed', 'true');
  await expect(rows(page)).toHaveText([/Milk/]);

  // Low stock: out of stock first.
  await goHome(page);
  const low = page.getByTestId('card_home_low_stock');
  await expect(low.locator('.sn-home-row')).toHaveText([/Olive oil.*Out/, /Eggs.*Low/]);
  await low.getByRole('button', { name: 'See all 2 in Pantry' }).click();
  await expect(page.getByTestId('pantry_filter_lowStock')).toHaveAttribute('aria-pressed', 'true');
  await expect(rows(page)).toHaveCount(2);

  // This month's kitchen, then Insights with the month details.
  await goHome(page);
  const card = page.getByTestId('card_home_insights');
  await expect(card).toContainText('3 used · 1 thrown away');
  await expect(card.getByRole('img')).toHaveAccessibleName('This month: 3 used, 1 thrown away, 75% used.');
  await card.getByRole('button', { name: 'See insights' }).click();
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

  // Throwing something away shows in Recent activity and in this month's counts.
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await page.getByTestId('pantry_filter_lowStock').click(); // back to everything
  await page.getByTestId('menu_yogurt').click();
  await page.getByRole('menuitem', { name: 'Throw away' }).click();
  await page.getByRole('dialog').getByText('Expired').click();
  await goHome(page);
  await expect(page.getByTestId('card_home_activity')).toContainText('You threw away Yogurt (expired)');
  await expect(page.getByTestId('card_home_activity')).toContainText('Just now');
  await expect(card).toContainText('3 used · 2 thrown away');
});
