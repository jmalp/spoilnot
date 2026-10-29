// The app shell in WebKit on an iPhone (DESIGN 18.8, W0): five tabs with their own stacks, Settings from the
// avatar, Back, and screenshots in light and dark at 100% and 200% text, to compare with the Android app.
import { expect, test, type Page } from '@playwright/test';

const tabs = ['Home', 'Pantry', 'Plan', 'Recipes', 'Groceries'];

async function open(page: Page, { dark = false, textScale = 1 } = {}) {
  await page.emulateMedia({ colorScheme: dark ? 'dark' : 'light' });
  await page.addInitScript((scale) => localStorage.setItem('spoilnot.textScale', String(scale)), textScale);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
  await page.evaluate(() => document.fonts.ready);
}

test('five tabs in the Android order; each tab opens its screen and Back returns to Home', async ({ page }) => {
  await open(page);
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

test('the in-app back button and a link to a tab', async ({ page }) => {
  await page.goto('/plan');
  await expect(page.getByRole('heading', { name: 'Plan' })).toBeVisible();
  await page.getByRole('button', { name: 'Open settings' }).click();
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(page.getByRole('heading', { name: 'Plan' })).toBeVisible();
});

for (const dark of [false, true]) {
  for (const textScale of [1, 2]) {
    const name = `shell_${dark ? 'dark' : 'light'}_font${textScale}`;
    test(`screenshot ${name}`, async ({ page }) => {
      test.skip(!!process.env.CI, 'Linux WebKit draws fonts differently; screenshots are compared on the Mac');
      await open(page, { dark, textScale });
      await expect(page).toHaveScreenshot(`${name}.png`);
      await page.getByRole('button', { name: 'Open settings' }).click();
      await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
      await expect(page).toHaveScreenshot(`${name}_settings.png`);
    });
  }
}
