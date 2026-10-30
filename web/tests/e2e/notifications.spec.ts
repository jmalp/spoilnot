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

/** The Notification and Push APIs of a Home Screen web app; the answer stays across reloads. */
async function installedIphone(page: Page) {
  await page.addInitScript(() => {
    const shown: string[] = [];
    function Notification(title: string) {
      shown.push(title);
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
    Object.assign(window, { Notification, PushManager: function PushManager() {}, testNotifications: shown });
  });
}

/** Adds [name] from the Pantry tab with a date a week away. */
async function addDated(page: Page, name: string) {
  await page.getByTestId('fab_add_item').click();
  const sheet = page.getByTestId('sheet_add_item');
  await sheet.getByLabel('Name').fill(name);
  await sheet.getByTestId(`date_chip_${inDays(7)}`).click();
  await sheet.getByTestId('button_save_item').click();
  await expect(status(page, `${name} added`)).toBeVisible();
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

  await openSettings(page);
  const row = page.getByTestId('row_notifications');
  await expect(row).toContainText('Daily, shortly after 9:00 AM');
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
  await expect(row).toContainText('Daily, shortly after 7:00 PM');

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

test('installed on the Home Screen: the third dated item explains reminders once, Turn on allows them, and the test notification shows', async ({
  page,
}) => {
  await installedIphone(page);
  await openApp(page);
  const { hid, uid } = await newHousehold(page);
  await seedPantry(hid, uid, [
    { id: 'milk', name: 'Milk', expiry: inDays(2) },
    { id: 'rice', name: 'Rice', expiry: null, location: 'pantry' },
  ]);
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_list')).toContainText('Rice', { timeout: 15_000 });

  // Two dated items: nothing yet. The third: the explanation, and Turn on asks the system.
  await addDated(page, 'Cheese');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await addDated(page, 'Yogurt');
  const explainer = page.getByRole('dialog');
  await expect(explainer).toContainText('Get a heads-up before food expires');
  await explainer.getByTestId('explainer_turn_on').click();
  await expect(explainer).toHaveCount(0);
  await addDated(page, 'Butter');
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // Allowed: no banner in Settings, and the test notification shows.
  await openSettings(page);
  await page.getByTestId('row_notifications').click();
  await expect(page.getByTestId('switch_notifications')).toBeVisible();
  await expect(page.getByTestId('notifications_banner')).toHaveCount(0);
  await page.getByTestId('button_test_notification').click();
  await expect(status(page, 'Test notification sent')).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { testNotifications: string[] }).testNotifications)).toEqual([
    'Test notification',
  ]);
});

test('not allowed yet: Settings offers Turn on; a tapped digest opens Pantry on Expiring soon', async ({ page }) => {
  await installedIphone(page);
  await openApp(page);
  const { hid, uid } = await newHousehold(page);
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
  await expect(page.getByTestId('pantry_filter_expiringSoon')).toHaveAttribute('aria-pressed', 'true');
  expect(new URL(page.url()).search).toBe('');
});
