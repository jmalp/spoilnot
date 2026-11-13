// Notifications on an iPhone (DESIGN 5.4, 10.2, 18.4, 18.8 W6b), against the emulators. Playwright's WebKit has
// neither the Notification nor the Push API, like Safari outside the Home Screen; an installed web app is played by
// giving the page both, with the system answering Allow. A real push is checked on an iPhone (docs/QA.md).
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  householdId,
  inDays,
  openApp,
  patchFields,
  readFields,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(60_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();
const openSettings = (page: Page) =>
  page
    .getByRole('button', { name: /Open settings/ })
    .first()
    .click();
const status = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });

/**
 * The Notification and Push APIs of a Home Screen web app; the answer stays across reloads, and `test.permission` in
 * localStorage plays one changed in the iPhone's Settings. Development builds have no service worker, so local
 * notifications are `new Notification(…)`: their titles and bodies are kept.
 */
async function installedIphone(page: Page) {
  await page.addInitScript(() => {
    const shown: string[] = [];
    const bodies: string[] = [];
    function Notification(title: string, options?: { body?: string }) {
      shown.push(title);
      bodies.push(options?.body ?? '');
    }
    Object.defineProperty(Notification, 'permission', {
      get: () => localStorage.getItem('test.permission') ?? 'default',
    });
    Object.assign(Notification, {
      requestPermission: async () => {
        localStorage.setItem('test.permission', 'granted');
        return 'granted';
      },
    });
    Object.assign(window, {
      Notification,
      PushManager: function PushManager() {},
      testNotifications: shown,
      testBodies: bodies,
    });
  });
}

/** Adds [name] from the Pantry tab with a date a week away. */
async function addDated(page: Page, name: string) {
  // An empty pantry has its own Add button instead of the floating one.
  await page
    .getByTestId('fab_add_item')
    .or(page.getByRole('button', { name: 'Add your first item' }))
    .click();
  const sheet = page.getByTestId('sheet_add_item');
  await sheet.getByLabel('Name').fill(name);
  await sheet.getByTestId(`date_chip_${inDays(7)}`).click();
  await sheet.getByTestId('button_save_item').click();
  await expect(status(page, `${name} added`)).toBeVisible();
}

const shownTitles = (page: Page) =>
  page.evaluate(() => (window as unknown as { testNotifications: string[] }).testNotifications);
const shownBodies = (page: Page) => page.evaluate(() => (window as unknown as { testBodies: string[] }).testBodies);

/**
 * Home has read the new household from the server, empty (no Offline banner once its card shows): food seeded after
 * this is food added while the app was open, not food it opened with (DESIGN 5.4).
 */
async function readEmpty(page: Page) {
  await expect(page.getByTestId('card_home_attention')).toContainText('Add food with its date');
  await expect(page.getByTestId('offline_banner')).toHaveCount(0);
}

async function newHousehold(page: Page) {
  const email = uniqueEmail('Sam');
  const household = `Reminders ${Date.now()}`;
  await signUp(page, 'Sam', email);
  await createHousehold(page, household);
  return { hid: await householdId(household), uid: await uidOf(email) };
}

test('reminder settings are saved on the profile and summed up in Settings; Safari asks for the Home Screen', async ({
  page,
}) => {
  await openApp(page);
  const { hid, uid } = await newHousehold(page);

  // The phone's time zone is written when the app starts and it changed (DESIGN 10.2).
  const zone = await page.evaluate(() => Intl.DateTimeFormat().resolvedOptions().timeZone);
  await patchFields(`users/${uid}`, { timezone: zone === 'Pacific/Auckland' ? 'Europe/Madrid' : 'Pacific/Auckland' });
  await page.reload();
  await expect.poll(async () => (await readFields(`users/${uid}`))?.timezone).toBe(zone);

  // Safari can't show reminders: the row says so instead of the time (after v1, UX-02).
  await openSettings(page);
  const row = page.getByTestId('row_notifications');
  await expect(row).toContainText('Off on this phone');
  await row.click();
  await expect(page.getByTestId('notifications_banner')).toContainText('add Spoil Not to your Home Screen');
  await expect(page.getByTestId('button_turn_on_notifications')).toHaveCount(0);

  await page.getByTestId('field_notification_time').click();
  await page.getByRole('menuitem', { name: '7:00 PM' }).click();
  await page.getByTestId('switch_cooking').click();
  await page.getByRole('checkbox', { name: 'Today' }).click();
  await page.getByRole('radio', { name: 'Never' }).click();
  await expect
    .poll(async () => (await readFields(`users/${uid}`))?.notificationPrefs)
    .toEqual({ enabled: true, digestMinutes: 19 * 60, stages: [3, 2, 1], expiredMode: 'never', cooking: false });
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(row).toContainText('Off on this phone');

  await row.click();
  await page.getByTestId('switch_notifications').click();
  await expect(page.getByTestId('field_notification_time')).toHaveCount(0);
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(row).toContainText('Off');

  // In Safari the third dated item asks nothing: the explanation waits for the installed app.
  await seedPantry(hid, uid, [
    { id: 'milk', name: 'Milk', expiry: inDays(2) },
    { id: 'cheese', name: 'Cheese', expiry: inDays(9) },
  ]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toContainText('Cheese', { timeout: 15_000 });
  await addDated(page, 'Yogurt');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('installed, a new household: the third dated item asks, whoever adds it; Turn on says "You\'re set"', async ({
  page,
}) => {
  await installedIphone(page);
  await openApp(page);
  const { hid, uid } = await newHousehold(page);
  // The household has been read from the server (no Offline banner once the pantry shows), so it opened empty.
  await tab(page, 'Pantry');
  await expect(page.getByRole('button', { name: 'Add your first item' })).toBeVisible();
  await expect(page.getByTestId('offline_banner')).toHaveCount(0);

  // Two dated items and an undated one: nothing yet.
  await addDated(page, 'Cheese');
  await addDated(page, 'Butter');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  // The third comes from another phone: it asks here all the same (after v1, UX-02), and names the time.
  await seedPantry(hid, uid, [
    { id: 'rice', name: 'Rice', expiry: null, location: 'pantry' },
    { id: 'yogurt', name: 'Yogurt', expiry: inDays(2) },
  ]);
  const explainer = page.getByRole('dialog');
  await expect(explainer).toContainText('Get a heads-up before food expires', { timeout: 15_000 });
  await expect(explainer).toContainText('around 9:00 AM');
  await explainer.getByTestId('explainer_turn_on').click();
  await expect(explainer).toHaveCount(0);

  // "You're set" right away, with the time and what tomorrow's reminder names (owner's decision 2, UX-03).
  await expect.poll(() => shownTitles(page)).toEqual(["You're set"]);
  expect(await shownBodies(page)).toEqual([
    "Spoil Not reminds you once a day, around 9:00 AM, when food is about to expire. Tomorrow's reminder: Yogurt.",
  ]);
  await addDated(page, 'Ham');
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Allowed: the time in Settings, no banner, and the test notification shows.
  await openSettings(page);
  await expect(page.getByTestId('row_notifications')).toContainText('Daily, shortly after 9:00 AM');
  await page.getByTestId('row_notifications').click();
  await expect(page.getByTestId('switch_notifications')).toBeVisible();
  await expect(page.getByTestId('notifications_banner')).toHaveCount(0);
  await page.getByTestId('button_test_notification').click();
  await expect(status(page, 'Test notification sent')).toBeVisible();
  expect(await shownTitles(page)).toEqual(["You're set", 'Test notification']);
});

test('a household that opens with dated food asks at once on Home; Not now leaves a line there and in Settings', async ({
  page,
}) => {
  await installedIphone(page);
  await openApp(page);
  const { hid, uid } = await newHousehold(page);
  const attention = page.getByTestId('card_home_attention');
  await readEmpty(page);
  await seedPantry(hid, uid, [{ id: 'milk', name: 'Milk', expiry: inDays(1) }]);
  await expect(attention).toContainText('Milk', { timeout: 15_000 });
  // Everything is already on the card: Open in Pantry, not "See 1 in Pantry" (after v1, UX-18).
  await expect(attention.getByRole('button', { name: 'Open in Pantry' })).toBeVisible();
  // While this run of the app saw the household empty, one dated item doesn't ask.
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // A new start (a new phone, or a member who joined) finds dated food: it asks at once, without opening Pantry.
  await page.reload();
  const explainer = page.getByRole('dialog');
  await expect(explainer).toContainText('around 9:00 AM', { timeout: 15_000 });
  await expect(page.getByTestId('screen_home')).toBeVisible();
  await explainer.getByRole('button', { name: 'Not now' }).click();
  await expect(explainer).toHaveCount(0);

  // Not now: Needs attention ends with the line, and Settings says Off on this phone.
  const line = attention.getByTestId('home_reminders_off');
  await expect(line).toContainText('Reminders are off on this phone');
  await openSettings(page);
  await expect(page.getByTestId('row_notifications')).toContainText('Off on this phone');
  await page.getByRole('button', { name: 'Back' }).click();
  await page.reload();
  await expect(line).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Turn on asks, and "You're set" names tomorrow's reminder; the line goes.
  await line.getByTestId('home_reminders_turn_on').click();
  await expect(line).toHaveCount(0);
  await expect.poll(() => shownTitles(page)).toEqual(["You're set"]);
  expect((await shownBodies(page))[0]).toContain("Tomorrow's reminder: Milk.");
  await openSettings(page);
  await expect(page.getByTestId('row_notifications')).toContainText('Daily, shortly after 9:00 AM');
  await page.getByRole('button', { name: 'Back' }).click();

  // Turned off in the iPhone's Settings: the line is back, and Turn on says where, since a web app can't ask again.
  await page.evaluate(() => localStorage.setItem('test.permission', 'denied'));
  await page.reload();
  await expect(line).toBeVisible({ timeout: 15_000 });
  await line.getByTestId('home_reminders_turn_on').click();
  await expect(page.getByTestId('notifications_banner')).toContainText("Turn them on in the iPhone's Settings");

  // The daily reminder off: no line, and Settings says Off.
  await page.getByTestId('switch_notifications').click();
  await page.getByRole('button', { name: 'Back' }).click();
  await expect(attention).toContainText('Milk');
  await expect(line).toHaveCount(0);
  await openSettings(page);
  await expect(page.getByTestId('row_notifications')).toContainText('Off');
  await expect(page.getByTestId('row_notifications')).not.toContainText('on this phone');
});

test('not allowed yet: Settings offers Turn on; a tapped digest opens Pantry on Needs attention', async ({ page }) => {
  await installedIphone(page);
  await openApp(page);
  const { hid, uid } = await newHousehold(page);
  await readEmpty(page);
  await seedPantry(hid, uid, [{ id: 'milk', name: 'Milk', expiry: inDays(1) }]);
  await openSettings(page);
  await page.getByTestId('row_notifications').click();
  const banner = page.getByTestId('notifications_banner');
  await expect(banner).toContainText('Notifications are turned off for Spoil Not');
  await banner.getByTestId('button_turn_on_notifications').click();
  await expect(banner).toHaveCount(0);

  // The digest's route, as the service worker opens it when the app was closed.
  await page.goto('/pantry?filter=expiring');
  await expect(page.getByTestId('pantry_list')).toContainText('Milk', { timeout: 15_000 });
  await expect(page.getByTestId('pantry_filter_needsAttention')).toHaveAttribute('aria-pressed', 'true');
  expect(new URL(page.url()).search).toBe('');
});
