// The built app under the hosting headers (DESIGN 18.5, 18.6): nothing breaks the Content Security Policy, the
// service worker installs, and the app opens and shows the household offline.
import { expect, test } from '@playwright/test';
import { createHousehold, openApp, signUp } from '../e2e/support';

test.setTimeout(90_000);

test('the build runs under the CSP, installs its service worker and opens offline', async ({ page, context }) => {
  // Every blocked request or inline code would be reported here.
  await page.addInitScript(() => {
    const violations: string[] = [];
    document.addEventListener('securitypolicyviolation', (e) =>
      violations.push(`${e.violatedDirective} ${e.blockedURI}`),
    );
    Object.assign(window, { cspViolations: violations });
  });
  const violations = () => page.evaluate(() => (window as unknown as { cspViolations: string[] }).cspViolations);

  const response = await page.request.get('/pantry');
  expect(response.headers()['content-security-policy']).toContain("script-src 'self'");
  expect(response.headers()['x-content-type-options']).toBe('nosniff');

  await openApp(page);
  await signUp(page, 'Sam');
  await createHousehold(page, `Hosting ${Date.now()}`);
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await page.getByTestId('button_add_first_item').click();
  await page.getByTestId('sheet_add_item').getByLabel('Name').fill('Milk');
  await page.getByTestId('button_save_item').click();
  await expect(page.getByTestId('pantry_list')).toContainText('Milk');
  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await page.getByTestId('row_about').click();
  await expect(page.getByTestId('licenses')).toContainText('react');

  const worker = await page.evaluate(async () => (await navigator.serviceWorker.ready).active?.scriptURL ?? '');
  expect(worker).toMatch(/\/sw\.js$/);
  expect(await violations()).toEqual([]);

  // Offline: the service worker serves the app (it opens on the tab in the address) and Firestore's copy has the
  // pantry (DESIGN 9.5).
  await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
  await context.setOffline(true);
  await page.reload();
  await expect(page.getByTestId('pantry_list')).toContainText('Milk', { timeout: 15_000 });
  await context.setOffline(false);
  expect(await violations()).toEqual([]);
});
