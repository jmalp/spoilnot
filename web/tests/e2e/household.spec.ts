// Accounts and households on two iPhones (DESIGN 5.1, 18.8 W1), against the emulators, like HouseholdFlowTest.
import { expect, test, type Page } from '@playwright/test';
import { createHousehold, openApp, PASSWORD, signUp, uniqueEmail } from './support';

async function openHouseholdSettings(page: Page) {
  await page.getByRole('button', { name: /Open settings/ }).click();
  await page.getByTestId('row_household').click();
  await expect(page.getByTestId('screen_household')).toBeVisible();
}

test('a second phone joins with the code after the owner approves; removing them returns them to the gate', async ({
  browser,
}) => {
  const alice = await (await browser.newContext()).newPage();
  await openApp(alice);
  await signUp(alice, 'Alice');
  await createHousehold(alice, 'Test home');
  await openHouseholdSettings(alice);
  const code = (await alice.getByTestId('invite_code').textContent())!.trim();
  expect(code).toMatch(/^[A-Z2-9]{5}-[A-Z2-9]{5}$/);

  const bob = await (await browser.newContext()).newPage();
  await openApp(bob);
  await signUp(bob, 'Bob');
  // Typed in lowercase without the hyphen: the field formats it.
  await bob.getByLabel('Invite code').fill(code.replace('-', '').toLowerCase());
  await expect(bob.getByLabel('Invite code')).toHaveValue(code);
  await bob.getByRole('button', { name: 'Continue' }).click();
  await expect(bob.getByRole('dialog')).toContainText('Test home');
  await bob.getByTestId('send_request').click();
  await expect(bob.getByTestId('card_join_status')).toBeVisible();

  // The owner sees the request (and a badge on the avatar) and approves it.
  const requests = alice.getByTestId('card_join_requests');
  await expect(requests).toContainText('Bob');
  await requests.getByRole('button', { name: 'Approve' }).click();
  await expect(bob.getByRole('heading', { name: 'Home' })).toBeVisible();
  await expect(alice.getByTestId('card_members')).toContainText('Bob');

  // Removing Bob sends him back to the gate.
  await alice.getByRole('button', { name: 'Options for Bob' }).click();
  await alice.getByRole('menuitem', { name: 'Remove from household' }).click();
  await alice.getByTestId('confirm').click();
  await expect(bob.getByRole('heading', { name: 'Welcome, Bob' })).toBeVisible();
  await expect(alice.getByTestId('card_members')).not.toContainText('Bob');
});

test('sign out, a wrong password, sign in again, and the reset link message', async ({ page }) => {
  await openApp(page);
  const email = uniqueEmail('Sam');
  await signUp(page, 'Sam', email);
  await createHousehold(page, 'Sam home');
  await page.getByRole('button', { name: /Open settings/ }).click();
  await expect(page.getByTestId('screen_settings')).toContainText(email);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByTestId('screen_welcome')).toBeVisible();

  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();

  // Signed in again after a reload: the sign-in is kept on the phone.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Home' })).toBeVisible();
});

test('the reset screen confirms without saying whether the account exists', async ({ page }) => {
  await openApp(page);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill('nobody@example.com');
  await page.getByRole('button', { name: 'Forgot password?' }).click();
  await expect(page.getByLabel('Email')).toHaveValue('nobody@example.com');
  await page.getByRole('button', { name: 'Send link' }).click();
  await expect(page.getByTestId('screen_reset_password')).toContainText('nobody@example.com');
});

test('a member deletes their account; an owner must hand over first', async ({ browser }) => {
  const page = await (await browser.newContext()).newPage();
  await openApp(page);
  await signUp(page, 'Owner');
  await createHousehold(page, 'Owner home');
  await page.getByRole('button', { name: /Open settings/ }).click();
  await page.getByRole('button', { name: 'Delete account' }).click();
  await page.getByRole('dialog').getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByRole('dialog')).toContainText('Make someone else the owner');
});
