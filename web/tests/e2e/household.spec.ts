// Accounts and households on two iPhones (DESIGN 5.1, 18.8 W1), against the emulators, like HouseholdFlowTest.
import { expect, test, type Page } from '@playwright/test';
import {
  closeOtherPhones,
  createHousehold,
  deleteDocument,
  householdId,
  newPhone,
  openApp,
  PASSWORD,
  readFields,
  signUp,
  uidOf,
  uniqueEmail,
} from './support';

test.afterEach(closeOtherPhones);

async function openHouseholdSettings(page: Page) {
  await page.getByRole('button', { name: /Open settings/ }).click();
  await page.getByTestId('row_household').click();
  await expect(page.getByTestId('screen_household')).toBeVisible();
}

test('a second phone joins with the code after the owner approves; removing them returns them to the gate', async ({
  browser,
}) => {
  const alice = await (await newPhone(browser)).newPage();
  await openApp(alice);
  await signUp(alice, 'Alice');
  await createHousehold(alice, 'Test home');
  await openHouseholdSettings(alice);
  const code = (await alice.getByTestId('invite_code').textContent())!.trim();
  expect(code).toMatch(/^[A-Z2-9]{5}-[A-Z2-9]{5}$/);

  const bob = await (await newPhone(browser)).newPage();
  await openApp(bob);
  await signUp(bob, 'Bob');
  // Typed in lowercase without the hyphen: the field formats it.
  await bob.getByLabel('Invite code').fill(code.replace('-', '').toLowerCase());
  await expect(bob.getByLabel('Invite code')).toHaveValue(code);
  await bob.getByRole('button', { name: 'Continue' }).click();
  await expect(bob.getByRole('dialog')).toContainText('Test home');
  await bob.getByTestId('send_request').click();
  await expect(bob.getByTestId('card_join_status')).toBeVisible();
  // The owner gets no notification, and the waiting screen says so (after v1).
  await expect(bob.getByTestId('card_join_status')).toContainText("The owner doesn't get a notification");

  // The owner sees the request (and a badge on the avatar), with who is asking and when, and approves it.
  const requests = alice.getByTestId('card_join_requests');
  await expect(requests).toContainText('Bob');
  await expect(requests).toContainText('bo***@example.com');
  await expect(requests).toContainText('Sent: Just now');
  await expect(requests).toContainText('Only approve people you invited.');
  await requests.getByRole('button', { name: 'Approve' }).click();
  await expect(bob.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
  await expect(alice.getByTestId('card_members')).toContainText('Bob');

  // Removing Bob sends him back to the gate.
  await alice.getByRole('button', { name: 'Options for Bob' }).click();
  await alice.getByRole('menuitem', { name: 'Remove from household' }).click();
  await alice.getByTestId('confirm').click();
  await expect(bob.getByRole('heading', { name: 'Welcome, Bob' })).toBeVisible();
  await expect(alice.getByTestId('card_members')).not.toContainText('Bob');
});

test('an approved request deleted before the joiner opens the app still lets them in (after v1)', async ({
  browser,
}) => {
  const alice = await (await newPhone(browser)).newPage();
  await openApp(alice);
  await signUp(alice, 'Alice');
  const name = `Pruned home ${Date.now()}`;
  await createHousehold(alice, name);
  await openHouseholdSettings(alice);
  const code = (await alice.getByTestId('invite_code').textContent())!.trim();

  const carolContext = await newPhone(browser);
  const carol = await carolContext.newPage();
  await openApp(carol);
  const email = await signUp(carol, 'Carol');
  await carol.getByLabel('Invite code').fill(code);
  await carol.getByRole('button', { name: 'Continue' }).click();
  await carol.getByTestId('send_request').click();
  await expect(carol.getByTestId('card_join_status')).toBeVisible();
  // The waiting card shows as soon as the request is queued; close the app only once it has reached the server
  // (the owner sees it), or a busy emulator leaves it queued until the app opens again.
  const approve = alice.getByTestId('card_join_requests').getByRole('button', { name: 'Approve' });
  await expect(approve).toBeVisible({ timeout: 15_000 });
  await carol.close();

  // Approved while Carol's app is closed; then the job prunes the request, as it did before it kept approved ones.
  await approve.click();
  await expect(alice.getByTestId('card_members')).toContainText('Carol');
  // Alice's screen shows Carol from its own cache at once; the job prunes only what the server has, so wait for the
  // membership to be there before the request goes.
  const hid = await householdId(name);
  const carolUid = await uidOf(email);
  await expect.poll(() => readFields(`households/${hid}/members/${carolUid}`), { timeout: 15_000 }).not.toBeNull();
  await deleteDocument(`households/${hid}/joinRequests/${carolUid}`);

  const again = await carolContext.newPage();
  await again.goto('/');
  await expect(again.getByRole('heading', { name: 'Home', exact: true })).toBeVisible({ timeout: 20_000 });
  await expect(again.getByText('Request expired')).toHaveCount(0);
});

test('sign out, a wrong password, sign in again, and the reset link message', async ({ page }) => {
  await openApp(page);
  const email = uniqueEmail('Sam');
  await signUp(page, 'Sam', email);
  await createHousehold(page, 'Sam home');
  // The sync log names the household's collections (DESIGN 9.8); sign-out clears it with the rest.
  const syncKeys = () =>
    page.evaluate(() => Object.keys(localStorage).filter((key) => key.startsWith('spoilnot.sync.')).length);
  await expect.poll(syncKeys).toBeGreaterThan(0);
  await page.getByRole('button', { name: /Open settings/ }).click();
  await expect(page.getByTestId('screen_settings')).toContainText(email);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByTestId('screen_welcome')).toBeVisible();
  expect(await syncKeys()).toBe(0);

  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('wrong-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();

  // Signed in again after a reload: the sign-in is kept on the phone.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Home', exact: true })).toBeVisible();
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
  const page = await (await newPhone(browser)).newPage();
  await openApp(page);
  await signUp(page, 'Owner');
  await createHousehold(page, 'Owner home');
  await page.getByRole('button', { name: /Open settings/ }).click();
  await page.getByRole('button', { name: 'Delete account' }).click();
  await page.getByRole('dialog').getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('dialog').getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByRole('dialog')).toContainText('Make someone else the owner');
});

test('someone without a household deletes their account from the gate', async ({ page }) => {
  await openApp(page);
  const email = uniqueEmail('Gale');
  await signUp(page, 'Gale', email);
  await page.getByRole('button', { name: 'More options' }).click();
  await page.getByRole('menuitem', { name: 'Delete account' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('Password', { exact: true }).fill('wrong-password');
  await dialog.getByRole('button', { name: 'Delete' }).click();
  await expect(dialog).toContainText('Email or password is incorrect.');
  await dialog.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await dialog.getByRole('button', { name: 'Delete' }).click();
  await expect(page.getByTestId('screen_welcome')).toBeVisible();

  // The sign-in is gone.
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();
});
