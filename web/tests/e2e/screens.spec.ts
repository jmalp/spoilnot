// Screenshots of every web screen so far (DESIGN 18.2, 18.7), in light and dark at 100% and 200% text, to compare
// with the Android app. Text that differs per run (emails, codes, dates) is masked.
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, openApp, signUp } from './support';

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

      await signUp(page, 'Sam');
      await shot(page, `gate_${suffix}`);

      await createHousehold(page, 'The Smiths');
      await shot(page, `home_${suffix}`);

      await page.getByRole('button', { name: /Open settings/ }).click();
      await expect(page.getByTestId('row_household')).toContainText('The Smiths');
      await page.getByTestId('account_email').evaluate((el) => (el.textContent = 'sam@example.com'));
      await shot(page, `settings_${suffix}`);

      await page.getByTestId('row_household').click();
      await expect(page.getByTestId('card_members')).toContainText('Sam (you)');
      await shot(page, `household_${suffix}`, ['invite_code', 'member_since']);
    });
  }
}
