// The free daily quota running out (DESIGN 6.7, 7.5), against the emulators. The emulator has no quota, so the app's
// development hook refuses every server listener with 'resource-exhausted', as a project out of quota answers; writes
// still pass. Before, the session dropped to the loading screen and nothing said why (WEB-04, WEB-09).
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, householdId, openApp, seedPantry, signUp, uidOf, uniqueEmail } from './support';

test.setTimeout(90_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();

/** Refuses every server listener with [code] until called with null (development builds only). */
const refuseListens = (page: Page, code: string | null) =>
  page.evaluate((c) => {
    (
      window as unknown as { spoilnotTest: { refuseListens: (code: string | null) => void } }
    ).spoilnotTest.refuseListens(c);
  }, code);

for (const dark of [false, true]) {
  const suffix = dark ? 'dark' : 'light';
  test(`quota reached: the app stays on screen with the banner (${suffix})`, async ({ page }) => {
    const email = uniqueEmail('Sam');
    const household = `Quota ${suffix} ${Date.now()}`;
    await openApp(page, { dark });
    await signUp(page, 'Sam', email);
    await createHousehold(page, household);
    await seedPantry(await householdId(household), await uidOf(email), [{ id: 'cheese', name: 'Cheese' }]);
    await tab(page, 'Pantry');
    await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();
    await expect(page.getByTestId('offline_banner')).toHaveCount(0);
    await expect(page.getByTestId('quota_banner')).toHaveCount(0);

    await refuseListens(page, 'resource-exhausted');

    // The banner above the app, the cached pantry still there, and "Offline" while the server can't be read.
    await expect(page.getByTestId('quota_banner')).toBeVisible();
    await expect(page.getByTestId('quota_banner')).toContainText(
      'Spoil Not has reached its free daily limit and will work again tomorrow.',
    );
    await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();
    await expect(page.getByTestId('offline_banner')).toBeVisible();
    // The session retries after 1, 2 and 4 s without the loading screen.
    await page.waitForTimeout(8_000);
    await expect(page.getByTestId('screen_loading')).toHaveCount(0);
    await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();

    // Groceries works from the phone too, and says it is offline.
    await tab(page, 'Groceries');
    await expect(page.getByText('Your list is empty.', { exact: false })).toBeVisible();
    await expect(page.getByTestId('offline_banner')).toBeVisible();
    await expect(page.getByTestId('screen_loading')).toHaveCount(0);
    if (!process.env.CI) {
      // Linux WebKit draws fonts differently; screenshots are compared on the Mac (as screens.spec).
      await page.evaluate(() => document.fonts.ready);
      await expect(page).toHaveScreenshot(`quota_groceries_${suffix}.png`);
    }

    // The quota is back: the listeners recover within their backoff (32 s at most). The banner holds until
    // midnight Pacific, when the quota resets.
    await refuseListens(page, null);
    await expect(page.getByTestId('offline_banner')).toHaveCount(0, { timeout: 40_000 });
    await expect(page.getByTestId('quota_banner')).toBeVisible();
    await tab(page, 'Pantry');
    await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();
  });
}
