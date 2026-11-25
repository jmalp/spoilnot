// Listeners that let go (DESIGN 9.1, 9.6, 18.3; after v1, 2026-11-10, review 09 EFF-01 on the web), against the
// emulators. The restock engine reads the cache only, so it hears another phone's change only through a screen's
// listeners; a hidden page lets go of its screens' listeners and catches up when it comes back.
import { expect, test, type Page } from '@playwright/test';
import {
  createHousehold,
  defaultListId,
  householdId,
  openApp,
  patchFields,
  readFields,
  seedPantry,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.setTimeout(90_000);

const tab = (page: Page, name: string) => page.getByRole('navigation').getByRole('button', { name }).click();

/** Hides or shows the page as iOS does when the Home Screen app leaves or comes back to the screen. */
async function setVisibility(page: Page, state: 'hidden' | 'visible') {
  await page.evaluate((s) => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => s });
    document.dispatchEvent(new Event('visibilitychange'));
  }, state);
}

async function household(page: Page, name: string) {
  const email = uniqueEmail(name);
  const title = `Listeners ${name} ${Date.now()}`;
  await openApp(page);
  await signUp(page, name, email);
  await createHousehold(page, title);
  const hid = await householdId(title);
  return { hid, uid: await uidOf(email) };
}

test('the restock engine reads the cache only: another phone’s change reaches it through a screen', async ({
  page,
}) => {
  const { hid, uid } = await household(page, 'Sam');
  const items = `households/${hid}/groceryLists/${await defaultListId(hid)}/items`;
  await seedPantry(hid, uid, [{ id: 'cheese', name: 'Cheese' }]);
  // Pantry reads products and stock in full once, so the engine has a server read on record.
  await tab(page, 'Pantry');
  await expect(page.getByTestId('pantry_item_cheese')).toBeVisible();
  // Settings shows no pantry; its listeners stop 5 s after Pantry went away.
  await page.getByRole('button', { name: /Open settings/ }).click();
  await page.waitForTimeout(6_000);

  // Another phone runs out of olive oil, a staple. Nothing on this page listens to the stock now.
  await seedPantry(hid, uid, [{ id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' }]);
  await page.waitForTimeout(8_000);
  expect(await readFields(`${items}/low_p-oil`), 'no server listener of its own').toBeNull();

  // Back to a screen that shows the pantry: the change reaches the cache, and the engine adds it after its 5 s wait.
  await page.getByRole('button', { name: 'Back' }).click();
  await tab(page, 'Pantry');
  await expect
    .poll(async () => (await readFields(`${items}/low_p-oil`))?.source ?? null, { timeout: 15_000 })
    .toBe('low_stock');
});

test('a hidden page lets go of its listeners and catches up when it comes back', async ({ page }) => {
  const { hid, uid } = await household(page, 'Alex');
  await seedPantry(hid, uid, [{ id: 'cheese', name: 'Cheese', quantity: 1 }]);
  await tab(page, 'Pantry');
  const cheese = page.getByTestId('pantry_item_cheese');
  await expect(cheese).toBeVisible();
  await expect(cheese).not.toContainText('× 2');

  // Shown: another phone's +1 arrives.
  await patchFields(`households/${hid}/stock/cheese`, { quantity: 2, updatedAt: new Date(), updatedBy: 'u-other' });
  await expect(cheese).toContainText('× 2');

  // Hidden: the screen keeps what it showed, and hears nothing.
  await setVisibility(page, 'hidden');
  await page.waitForTimeout(1_000);
  await patchFields(`households/${hid}/stock/cheese`, { quantity: 3, updatedAt: new Date(), updatedBy: 'u-other' });
  await page.waitForTimeout(4_000);
  await expect(cheese).toContainText('× 2');

  // Back on screen: changes-only listeners from the cursor bring what changed meanwhile.
  await setVisibility(page, 'visible');
  await expect(cheese).toContainText('× 3');
});
