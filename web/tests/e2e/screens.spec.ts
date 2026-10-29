// Screenshots of every web screen so far (DESIGN 18.2, 18.7), in light and dark at 100% and 200% text, to compare
// with the Android app. Text that differs per run (emails, codes, dates) is masked.
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, householdId, inDays, openApp, seedPantry, signUp, uidOf, uniqueEmail } from './support';

test.skip(!!process.env.CI, 'Linux WebKit draws fonts differently; screenshots are compared on the Mac');

async function shot(page: Page, name: string, mask: string[] = []) {
  await page.evaluate(() => document.fonts.ready);
  await expect(page).toHaveScreenshot(`${name}.png`, { mask: mask.map((id) => page.getByTestId(id)) });
}

for (const dark of [false, true]) {
  for (const textScale of [1, 2]) {
    const suffix = `${dark ? 'dark' : 'light'}_font${textScale}`;
    test(`screens ${suffix}`, async ({ page }) => {
      await openApp(page, { dark, textScale });
      await expect(page.getByTestId('screen_welcome')).toBeVisible();
      await shot(page, `welcome_${suffix}`);

      await page.getByRole('button', { name: 'Create account' }).click();
      await page.getByRole('button', { name: 'Create account' }).click(); // empty: shows the field errors
      await shot(page, `sign_up_errors_${suffix}`);
      await page.getByRole('button', { name: 'Back' }).click();

      const email = uniqueEmail('Sam');
      await signUp(page, 'Sam', email);
      await shot(page, `gate_${suffix}`);

      const household = `The Smiths ${suffix} ${Date.now()}`;
      await createHousehold(page, household);
      await shot(page, `home_${suffix}`);

      // Dates relative to today, so the chips read the same on every run.
      await seedPantry(await householdId(household), await uidOf(email), [
        { id: 'cheese', name: 'Cheese', expiry: inDays(-3) },
        { id: 'milk', name: 'Milk', expiry: inDays(1) },
        { id: 'yogurt', name: 'Greek yogurt', quantity: 6, expiry: inDays(2) },
        { id: 'eggs', name: 'Eggs', quantity: 12, expiry: inDays(6), location: 'fridge' },
        { id: 'rice', name: 'Rice', quantity: 500, unit: 'g', expiry: null, location: 'pantry', min: 200 },
        { id: 'oil', name: 'Olive oil', quantity: 0, min: 1, location: 'pantry' },
      ]);
      await page.getByRole('navigation').getByRole('button', { name: 'Pantry' }).click();
      await expect(page.getByTestId('pantry_item_rice')).toBeVisible();
      await shot(page, `pantry_${suffix}`);
      await page.getByRole('navigation').getByRole('button', { name: 'Home' }).click();

      await page.getByRole('button', { name: /Open settings/ }).click();
      await expect(page.getByTestId('row_household')).toContainText('The Smiths');
      await page
        .getByTestId('row_household')
        .locator('.m3-list-item__supporting')
        .evaluate((el) => (el.textContent = 'The Smiths · 1 member'));
      await page.getByTestId('account_email').evaluate((el) => (el.textContent = 'sam@example.com'));
      await shot(page, `settings_${suffix}`);

      await page.getByTestId('row_household').click();
      await expect(page.getByTestId('card_members')).toContainText('Sam (you)');
      await shot(page, `household_${suffix}`, ['invite_code', 'member_since']);
    });
  }
}
