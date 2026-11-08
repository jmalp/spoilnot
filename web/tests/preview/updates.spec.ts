// Updates of the built app (DESIGN 18.5): when config/app.minWebBuild is newer than this build, the app looks for a
// new version at once and its Update prompt stays past the usual 10 seconds, until Update reloads into it.
import { expect, test } from '@playwright/test';
import { createHousehold, openApp, patchFields, removeFields, signUp } from '../e2e/support';

test.setTimeout(90_000);

test('a build older than minWebBuild keeps the Update prompt until Update', async ({ page, context }) => {
  // The next deploy: from now on the server sends a changed service worker.
  let deployed = false;
  await context.route('**/sw.js', async (route) => {
    const response = await route.fetch();
    const body = await response.text();
    await route.fulfill({ response, body: deployed ? `${body}\n// the next version\n` : body });
  });

  await openApp(page);
  await signUp(page, 'Sam');
  await createHousehold(page, `Updates ${Date.now()}`);
  // Opened again, the app runs under its service worker, as from the Home Screen.
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null)).toBe(true);

  deployed = true;
  // Far above any real build, so this test never depends on WEB_BUILD.
  await patchFields('config/app', { minWebBuild: 1_000_000 });
  try {
    const prompt = page.getByRole('status').filter({ hasText: 'A new version of Spoil Not is ready' });
    await expect(prompt).toBeVisible({ timeout: 20_000 });
    // Past the 10 seconds an offer stays.
    await page.waitForTimeout(12_000);
    await expect(prompt).toBeVisible();

    const reloaded = page.waitForEvent('load');
    await prompt.getByRole('button', { name: 'Update' }).click();
    await reloaded;
    await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
    const workers = await page.evaluate(async () => {
      const registration = await navigator.serviceWorker.getRegistration();
      return { waiting: Boolean(registration?.waiting), controlled: navigator.serviceWorker.controller !== null };
    });
    expect(workers).toEqual({ waiting: false, controlled: true });
  } finally {
    await removeFields('config/app', ['minWebBuild']);
  }
});
