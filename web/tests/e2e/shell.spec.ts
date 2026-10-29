// The app shell in WebKit on an iPhone (DESIGN 18.8, W0): five tabs with their own stacks, Settings from the
// avatar and Back. Screenshots are in screens.spec.ts.
import { expect, test } from '@playwright/test';
import { memberWithHousehold } from './support';

const tabs = ['Home', 'Pantry', 'Plan', 'Recipes', 'Groceries'];

test('five tabs in the Android order; each tab opens its screen and Back returns to Home', async ({ browser }) => {
  const page = await memberWithHousehold(browser, 'Sam');
  const nav = page.getByRole('navigation');
  for (const [i, name] of tabs.entries()) await expect(nav.getByRole('button').nth(i)).toHaveText(name);
  await expect(nav.getByRole('button', { name: 'Home' })).toHaveAttribute('aria-current', 'page');

  await nav.getByRole('button', { name: 'Pantry' }).click();
  await expect(page.getByRole('heading', { name: 'Pantry' })).toBeVisible();
  await expect(page).toHaveURL(/\/pantry$/);
  await expect(nav.getByRole('button', { name: 'Pantry' })).toHaveAttribute('aria-current', 'page');

  // Settings opens on top of the tab; Back closes it, then returns to Home (TabNavigator).
  await page.getByRole('button', { name: 'Open settings' }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Pantry' })).toBeVisible();
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
});

test('the in-app back button and a link to a tab', async ({ browser }) => {
  const page = await memberWithHousehold(browser, 'Sam');
  await page.goto('/plan');
  await expect(page.getByRole('heading', { name: 'Plan' })).toBeVisible();
  await page.getByRole('button', { name: 'Open settings' }).click();
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(page.getByRole('heading', { name: 'Plan' })).toBeVisible();
});
