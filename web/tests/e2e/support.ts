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

const FIRESTORE = 'http://127.0.0.1:8080/v1/projects/demo-spoilnot/databases/(default)/documents';
/** The emulator's admin access, which bypasses the rules (test data only). */
const ADMIN = { Authorization: 'Bearer owner', 'Content-Type': 'application/json' };

type Value = string | number | boolean | null | Date | string[] | { [key: string]: Value };
const firestoreFields = (fields: Record<string, Value>) =>
  Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, firestoreValue(v)]));
const firestoreValue = (v: Value): object =>
  v === null
    ? { nullValue: null }
    : v instanceof Date
      ? { timestampValue: v.toISOString() }
      : Array.isArray(v)
        ? { arrayValue: { values: v.map((x) => ({ stringValue: x })) } }
        : typeof v === 'object'
          ? { mapValue: { fields: firestoreFields(v) } }
          : typeof v === 'boolean'
            ? { booleanValue: v }
            : typeof v === 'number'
              ? Number.isInteger(v)
                ? { integerValue: String(v) }
                : { doubleValue: v }
              : { stringValue: v };

async function put(path: string, fields: Record<string, Value>) {
  const body = { fields: firestoreFields(fields) };
  const response = await fetch(`${FIRESTORE}/${path}`, { method: 'PATCH', headers: ADMIN, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`seed ${path}: ${response.status} ${await response.text()}`);
}

/**
 * The id of the household named [name] (test names are unique per run). The app shows Home from its own copy
 * before the server has the household, so this waits a little for it.
 */
export async function householdId(name: string): Promise<string> {
  for (let attempt = 0; attempt < 20; attempt++) {
    const id = await findHousehold(name);
    if (id) return id;
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`No household ${name}`);
}

async function findHousehold(name: string): Promise<string | null> {
  const response = await fetch(`${FIRESTORE}:runQuery`, {
    method: 'POST',
    headers: ADMIN,
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: 'households' }],
        where: { fieldFilter: { field: { fieldPath: 'name' }, op: 'EQUAL', value: { stringValue: name } } },
      },
    }),
  });
  const rows = (await response.json()) as { document?: { name: string } }[];
  const doc = rows.find((r) => r.document)?.document;
  return doc ? doc.name.split('/').pop()! : null;
}

/** A date [days] from today in the browser's calendar. */
export function inDays(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export type SeedItem = {
  id: string;
  name: string;
  quantity?: number;
  unit?: string;
  expiry?: string | null;
  location?: string;
  category?: string;
  min?: number | null;
};

/** Writes products and purchases as the app would, for a household's pantry. */
export async function seedPantry(hid: string, uid: string, items: SeedItem[]) {
  const now = new Date();
  const stamp = { createdAt: now, createdBy: uid, updatedAt: now, updatedBy: uid };
  for (const item of items) {
    const productId = `p-${item.id}`;
    await put(`households/${hid}/products/${productId}`, {
      name: item.name,
      nameKey: item.name.toLowerCase(),
      category: item.category ?? 'dairy',
      defaultLocation: item.location ?? 'fridge',
      unit: item.unit ?? 'pcs',
      minQuantity: item.min ?? null,
      autoAddToGrocery: item.min != null,
      buyQuantity: null,
      restockArmed: true,
      typicalShelfLifeDays: null,
      recentShelfLifeDays: [],
      lastAddedQuantity: null,
      openedShelfLifeDays: null,
      aliases: [],
      ...stamp,
    });
    if ((item.quantity ?? 1) > 0) {
      await put(`households/${hid}/stock/${item.id}`, {
        productId,
        productName: item.name,
        unit: item.unit ?? 'pcs',
        category: item.category ?? 'dairy',
        quantity: item.quantity ?? 1,
        expiryDate: item.expiry === undefined ? inDays(5) : item.expiry,
        dateType: 'use_by',
        location: item.location ?? 'fridge',
        note: null,
        openedOn: null,
        ...stamp,
      });
    }
  }
}

/** A month's Insights counts ('YYYY-MM'), as the app's increments leave them. */
export async function seedStats(
  hid: string,
  uid: string,
  month: string,
  {
    used = 0,
    thrown = {},
    byDay = {},
  }: { used?: number; thrown?: Record<string, number>; byDay?: Record<number, number> },
) {
  await put(`households/${hid}/stats/${month}`, {
    used,
    ...thrown,
    thrownByDay: Object.fromEntries(Object.entries(byDay).map(([day, n]) => [day, n])),
    updatedAt: new Date(),
    updatedBy: uid,
  });
}

/** The id of a household's default grocery list. */
export async function defaultListId(hid: string): Promise<string> {
  const response = await fetch(`${FIRESTORE}/households/${hid}:runQuery`, {
    method: 'POST',
    headers: ADMIN,
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: 'groceryLists' }],
        where: { fieldFilter: { field: { fieldPath: 'isDefault' }, op: 'EQUAL', value: { booleanValue: true } } },
      },
    }),
  });
  const rows = (await response.json()) as { document?: { name: string } }[];
  const doc = rows.find((r) => r.document)?.document;
  if (!doc) throw new Error(`No default list in ${hid}`);
  return doc.name.split('/').pop()!;
}

export type SeedGroceryItem = {
  id: string;
  name: string;
  quantity?: number | null;
  category?: string | null;
  source?: string;
  productId?: string | null;
  checked?: boolean;
  createdBy?: string;
  note?: string | null;
};

/**
 * Writes grocery items as the app would, added an hour ago. updatedAt is now, like a server timestamp: the
 * changes-only listeners read what changed since their last full read (DESIGN 9.8).
 */
export async function seedGroceries(hid: string, uid: string, listId: string, items: SeedGroceryItem[]) {
  const now = new Date();
  const anHourAgo = new Date(now.getTime() - 60 * 60 * 1000);
  for (const item of items) {
    await put(`households/${hid}/groceryLists/${listId}/items/${item.id}`, {
      name: item.name,
      nameKey: item.name.toLowerCase(),
      quantity: item.quantity ?? null,
      unit: null,
      note: item.note ?? null,
      category: item.category ?? null,
      checked: item.checked ?? false,
      checkedBy: item.checked ? uid : null,
      checkedAt: null,
      source: item.source ?? 'manual',
      productId: item.productId ?? null,
      recipeId: null,
      aliasOf: null,
      createdAt: anHourAgo,
      createdBy: item.createdBy ?? uid,
      updatedAt: now,
      updatedBy: uid,
    });
  }
}

/** A member who joined some time ago, for names such as "added by Jordan". */
export async function seedMember(hid: string, uid: string, displayName: string) {
  await put(`households/${hid}/members/${uid}`, { role: 'member', displayName, joinedAt: new Date() });
}

/** One line of Recent activity, written now. */
export async function seedActivity(
  hid: string,
  uid: string,
  actorName: string,
  { type, name }: { type: string; name: string },
) {
  await put(`households/${hid}/activity/a-${Date.now()}`, {
    type,
    actorUid: uid,
    actorName,
    params: { name },
    createdAt: new Date(),
  });
}

/** An account's id, from the Auth emulator (accounts:lookup with admin access). */
export async function uidOf(email: string): Promise<string> {
  const response = await fetch(
    'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/demo-spoilnot/accounts:lookup',
    { method: 'POST', headers: ADMIN, body: JSON.stringify({ email: [email] }) },
  );
  const json = (await response.json()) as { users?: { localId: string }[] };
  const uid = json.users?.[0]?.localId;
  if (!uid) throw new Error(`No account ${email}`);
  return uid;
}
