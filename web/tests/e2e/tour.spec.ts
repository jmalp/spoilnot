// The app tour on an iPhone (DESIGN 5.8, 18.8 W6a), as TourUiTest in the app: it follows real taps on the tabs, is
// remembered once skipped and starts again from Settings.
import { expect, test } from '@playwright/test';
import { createHousehold, openApp, signUp } from './support';

test.setTimeout(60_000);

test('the tour follows the tabs, skip is remembered, and Settings starts it again', async ({ page }) => {
  await openApp(page, { tour: true });
  await signUp(page, 'Sam');
  await createHousehold(page, `Tour ${Date.now()}`);
  const card = page.getByTestId('coach_mark_card');
  const next = page.getByTestId('coach_mark_next');
  await expect(card).toContainText('Welcome to Spoil Not');
  await next.click(); // Start the tour
  await expect(card).toContainText('1 of 10');
  await expect(card).toContainText('Home starts with what needs you');
  // Taps outside the highlighted card do nothing.
  await page.getByRole('navigation').getByRole('button', { name: 'Groceries' }).click({ force: true });
  await expect(page.getByTestId('screen_home')).toBeVisible();
  await next.click();
  await expect(card).toContainText('Tap Pantry to see everything you have.');

  // A real tap through the hole opens the tab, and the tour moves on with it.
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await expect(page.getByTestId('screen_pantry')).toBeVisible();
  await expect(card).toContainText('3 of 10');

  // Tapping Add item opens the sheet and moves on; the sheet covers the tour until it closes.
  await page.getByTestId('button_add_first_item').click();
  await expect(page.getByTestId('sheet_add_item')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(card).toContainText('4 of 10');

  // Next on "Tap Plan" and "Tap Recipes" switches the tab for the user; the web app's recipes step shows the box.
  await next.click();
  await expect(card).toContainText('Tap Plan to plan the week');
  await next.click();
  await expect(page.getByTestId('screen_plan')).toBeVisible();
  await expect(card).toContainText('Tap Recipes.');
  await next.click();
  await expect(page.getByTestId('screen_recipes')).toBeVisible();
  await expect(card).toContainText('Recipes your household saves in the Android app');

  await page.getByTestId('coach_mark_skip').click();
  await expect(page.getByTestId('coach_mark_overlay')).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId('screen_recipes').or(page.getByTestId('screen_home'))).toBeVisible();
  await expect(page.getByTestId('coach_mark_overlay')).toHaveCount(0);

  // Settings > Take the app tour: the welcome card over Settings, then Home.
  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await page.getByTestId('row_take_tour').click();
  await expect(card).toContainText('Welcome to Spoil Not');
  await next.click();
  await expect(page.getByTestId('screen_home')).toBeVisible();
  await expect(card).toContainText('1 of 10');
  // To the end: Done finishes it.
  for (let i = 0; i < 9; i++) await next.click();
  await expect(card).toContainText('Invite your household from here');
  await expect(page.getByTestId('coach_mark_skip')).toHaveCount(0);
  await next.click();
  await expect(page.getByTestId('coach_mark_overlay')).toHaveCount(0);
});
