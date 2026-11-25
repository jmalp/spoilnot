// The narrower connect-src (after v1, S5; DESIGN 18.6): the built app may reach only the Firebase hosts it uses, and
// every flow that talks to Firebase still works under it. The emulators stand in for Firebase here, so the hosts
// themselves are checked two ways: the build names no Google API host outside the list, and the browser lets the
// listed hosts through and blocks the rest (answered by the test, so nothing leaves the machine). A real iPhone
// checks sign-in, sync and push against Firebase (docs/QA.md).
import { readdirSync, readFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, openApp, PASSWORD, signUp, uniqueEmail } from '../e2e/support';

test.setTimeout(90_000);

/** The hosts the web app's Firebase SDKs call: Firestore, Auth (two), Installations and FCM registrations. */
const FIREBASE_HOSTS = [
  'https://firestore.googleapis.com',
  'https://identitytoolkit.googleapis.com',
  'https://securetoken.googleapis.com',
  'https://firebaseinstallations.googleapis.com',
  'https://fcmregistrations.googleapis.com',
];

/**
 * Google API hosts in the build that the app never calls: Auth's internal test environments, chosen only by a config
 * this app doesn't set.
 */
const UNUSED_IN_BUILD = [
  'https://staging-identitytoolkit.sandbox.googleapis.com',
  'https://test-identitytoolkit.sandbox.googleapis.com',
];

function hostingCsp(): string {
  const json = readFileSync(new URL('../../../firebase/firebase.json', import.meta.url), 'utf8');
  const firebase = JSON.parse(json) as { hosting: { headers: { headers: { key: string; value: string }[] }[] } };
  return firebase.hosting.headers[0]!.headers.find((h) => h.key === 'Content-Security-Policy')!.value;
}

function connectSrc(csp: string): string[] {
  const directive = csp
    .split(';')
    .map((d) => d.trim())
    .find((d) => d.startsWith('connect-src '))!;
  return directive.split(/\s+/).slice(1);
}

/** Records every Content Security Policy violation in the page. */
async function recordViolations(page: Page): Promise<() => Promise<string[]>> {
  await page.addInitScript(() => {
    const violations: string[] = [];
    document.addEventListener('securitypolicyviolation', (e) =>
      violations.push(`${e.violatedDirective} ${e.blockedURI}`),
    );
    Object.assign(window, { cspViolations: violations });
  });
  return () => page.evaluate(() => (window as unknown as { cspViolations: string[] }).cspViolations);
}

test('connect-src lists the five Firebase hosts the app uses, not all of googleapis.com', () => {
  const csp = hostingCsp();
  expect(connectSrc(csp)).toEqual(["'self'", ...FIREBASE_HOSTS]);
  expect(csp).not.toContain('*.googleapis.com');
  // Recipe photos come from their own sites.
  expect(csp).toContain("img-src 'self' data: blob: https:;");
});

test('the build calls no Google API host outside connect-src', () => {
  const assets = new URL('../../dist/assets/', import.meta.url);
  const hosts = new Set<string>();
  for (const file of readdirSync(assets).filter((f) => f.endsWith('.js'))) {
    const code = readFileSync(new URL(file, assets), 'utf8');
    for (const [host] of code.matchAll(/https:\/\/[a-z0-9.-]+\.googleapis\.com/g)) hosts.add(host);
  }
  const sw = readFileSync(new URL('../../dist/sw.js', import.meta.url), 'utf8');
  for (const [host] of sw.matchAll(/https:\/\/[a-z0-9.-]+\.googleapis\.com/g)) hosts.add(host);
  // The SDKs are in the build: a test that found nothing would prove nothing.
  expect([...hosts]).toEqual(expect.arrayContaining(FIREBASE_HOSTS.slice(0, 3)));
  const allowed = new Set([...FIREBASE_HOSTS, ...UNUSED_IN_BUILD]);
  expect([...hosts].filter((h) => !allowed.has(h))).toEqual([]);
});

test('the browser lets the Firebase hosts through and blocks other Google hosts', async ({ page }) => {
  const violations = await recordViolations(page);
  // Answered here: a request the policy allows reaches this handler; a blocked one never leaves the page.
  const reached: string[] = [];
  await page.route(/^https:\/\/[a-z0-9.-]+\.googleapis\.com\//, (route) => {
    reached.push(new URL(route.request().url()).origin);
    return route.fulfill({ status: 204, headers: { 'Access-Control-Allow-Origin': '*' } });
  });
  await openApp(page);
  const others = ['https://storage.googleapis.com', 'https://www.googleapis.com', 'https://example.com'];
  const results = await page.evaluate(
    async (urls) =>
      Promise.all(
        urls.map((url) =>
          fetch(`${url}/s5-probe`).then(
            () => 'reached',
            () => 'blocked',
          ),
        ),
      ),
    [...FIREBASE_HOSTS, ...others],
  );
  expect(results).toEqual([...FIREBASE_HOSTS.map(() => 'reached'), ...others.map(() => 'blocked')]);
  expect(reached.sort()).toEqual([...FIREBASE_HOSTS].sort());
  expect((await violations()).map((v) => v.split(' ')[0])).toEqual(others.map(() => 'connect-src'));
});

test('sign-up, household, sync, sign-out, sign-in, a wrong password and the reset link all run under the CSP', async ({
  page,
}) => {
  const violations = await recordViolations(page);
  await openApp(page);
  const email = uniqueEmail('Csp');
  await signUp(page, 'Csp', email);
  await createHousehold(page, `CSP home ${Date.now()}`);
  // A write and the listener that shows it.
  await page.getByRole('navigation').getByRole('button', { name: 'Groceries' }).click();
  await page.getByTestId('grocery_quick_add').fill('Eggs');
  await page.getByTestId('grocery_quick_add').press('Enter');
  await expect(page.getByText('Eggs', { exact: true })).toBeVisible();

  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByTestId('screen_welcome')).toBeVisible();
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
  // The household's data comes back from the server after the sign-out cleared this browser's copy.
  await page.getByRole('navigation').getByRole('button', { name: 'Groceries' }).click();
  await expect(page.getByText('Eggs', { exact: true })).toBeVisible({ timeout: 15_000 });

  await page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByRole('button', { name: 'Forgot password?' }).click();
  await page.getByRole('button', { name: 'Send link' }).click();
  await expect(page.getByTestId('screen_reset_password')).toContainText(email);
  expect(await violations()).toEqual([]);
});
