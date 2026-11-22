// What the service worker leaves out of its precache (DESIGN 18.5, after v1 review EFF-06): the licenses and the font
// subsets for other scripts are not fetched at install, and the licenses read once still show offline.
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, openApp, signUp } from '../e2e/support';

test.setTimeout(90_000);

/** Every URL in the service worker's caches, by cache name. */
function cachedUrls(page: Page): Promise<Record<string, string[]>> {
  return page.evaluate(async () => {
    const result: Record<string, string[]> = {};
    for (const name of await caches.keys()) {
      result[name] = (await (await caches.open(name)).keys()).map((request) => new URL(request.url).pathname);
    }
    return result;
  });
}

async function openAbout(page: Page) {
  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await page.getByTestId('row_about').click();
  await expect(page.getByTestId('licenses')).toContainText('react');
}

test('the licenses and other scripts’ fonts are cached when used, not at install', async ({ page, context }) => {
  await openApp(page);
  await signUp(page, 'Sam');
  await createHousehold(page, `Runtime cache ${Date.now()}`);
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  // Installed: the app's code, styles and Latin fonts, nothing else.
  const installed = Object.values(await cachedUrls(page)).flat();
  expect(installed.some((path) => /\/assets\/firebase-.*\.js$/.test(path))).toBe(true);
  expect(installed.some((path) => /manrope-latin-wght/.test(path))).toBe(true);
  expect(installed.filter((path) => /licenses\.json|cyrillic|greek|vietnamese|hebrew/.test(path))).toEqual([]);

  // Opened again under the service worker, as from the Home Screen, About reads the licenses through it.
  await page.reload();
  await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null)).toBe(true);
  await openAbout(page);
  await expect.poll(async () => (await cachedUrls(page))['spoilnot-licenses']).toEqual(['/licenses.json']);

  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible({ timeout: 15_000 });
  await openAbout(page);
  await context.setOffline(false);
});
