// Helpers for the browser tests: accounts on the Auth emulator and households made through the app, like
// EmulatorSupport and TestPhone in the Android tests. Needs ./scripts/emulators.sh.
import { expect, type Browser, type Page } from '@playwright/test';

export const PASSWORD = 'password1';

/** A fresh address per test, so runs never collide in the emulator. */
export function uniqueEmail(name: string): string {
  return `${name.toLowerCase()}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

export async function openApp(page: Page, { dark = false, textScale = 1 } = {}) {
  await page.emulateMedia({ colorScheme: dark ? 'dark' : 'light' });
  await page.addInitScript((scale) => localStorage.setItem('spoilnot.textScale', String(scale)), textScale);
  await page.goto('/');
}

/** Creates an account through Welcome > Create account and waits for the household gate. */
export async function signUp(page: Page, name: string, email = uniqueEmail(name)): Promise<string> {
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByLabel('Your name').fill(name);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: `Welcome, ${name}` })).toBeVisible();
  return email;
}

export async function createHousehold(page: Page, name: string) {
  await page.getByLabel('Household name').fill(name);
  await page.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
}

/** A new phone (browser context) signed up with its own household, on Home. */
export async function memberWithHousehold(browser: Browser, name: string, options = {}): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await openApp(page, options);
  await signUp(page, name);
  await createHousehold(page, `${name}'s home`);
  await page.evaluate(() => document.fonts.ready);
  return page;
}
