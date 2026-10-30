// Installing from Safari on an iPhone (DESIGN 18.1, 18.8 W7): the welcome screen says how, Home's card does until
// Not now, and the app opened from the Home Screen shows neither.
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, openApp, signUp } from './support';

test.setTimeout(60_000);

/** Opened from the Home Screen: the display mode is standalone (Playwright cannot emulate it). */
async function fromHomeScreen(page: Page) {
  await page.addInitScript(() => {
    const original = window.matchMedia.bind(window);
    window.matchMedia = (query: string) =>
      query === '(display-mode: standalone)'
        ? ({ ...original(query), matches: true } as MediaQueryList)
        : original(query);
  });
}

test('Safari shows how to add the app to the Home Screen until Not now', async ({ page }) => {
  await openApp(page, { installHelp: true });
  await expect(page.getByTestId('install_help')).toContainText('tap Share, then Add to Home Screen');
  await signUp(page, 'Sam');
  await createHousehold(page, `Install ${Date.now()}`);
  const card = page.getByTestId('card_install');
  await expect(card).toContainText('Add Spoil Not to your Home Screen');
  await card.getByRole('button', { name: 'Not now' }).click();
  await expect(card).toHaveCount(0);
  await page.reload();
  await expect(page.getByTestId('card_home_attention')).toBeVisible();
  await expect(card).toHaveCount(0);
});

test('the app opened from the Home Screen does not ask to be installed', async ({ page }) => {
  await fromHomeScreen(page);
  await openApp(page, { installHelp: true });
  await expect(page.getByTestId('screen_welcome')).toBeVisible();
  await expect(page.getByTestId('install_help')).toHaveCount(0);
  await signUp(page, 'Sam');
  await createHousehold(page, `Installed ${Date.now()}`);
  await expect(page.getByTestId('card_home_attention')).toBeVisible();
  await expect(page.getByTestId('card_install')).toHaveCount(0);
});
