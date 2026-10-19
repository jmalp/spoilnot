// Firestore rules test matrix (DESIGN 12.4). Runs against the emulator: `npm test` in firebase/.
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  increment,
  writeBatch,
} from 'firebase/firestore';

const HID = 'h1';
const CODE = 'ABCDE23456';
const PAUSED = 'PAUSED2345';
const OTHER_HID = 'h2';
const OTHER_CODE = 'OTHER23456';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-spoilnot',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

// Seeds: household h1 owned by alice, bob is a member, carol and dave are outsiders.
// h2 belongs to erin and has its own active code.
// A long-running emulator that other clients (the Android tests) just used can answer the first clear with an
// HTTP 500; the next attempt succeeds. CI always starts a fresh emulator.
async function clearFirestore() {
  for (let attempt = 1; ; attempt++) {
    try {
      return await env.clearFirestore();
    } catch (e) {
      if (attempt >= 3) throw e;
      await new Promise((resolve) => setTimeout(resolve, 500));
    }
  }
}

beforeEach(async () => {
  await clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const t = new Date('2026-10-01T00:00:00Z');
    await setDoc(doc(db, 'users/alice'), profile('Alice', HID));
    await setDoc(doc(db, 'users/bob'), profile('Bob', HID));
    await setDoc(doc(db, 'users/carol'), profile('Carol', null));
    await setDoc(doc(db, 'users/dave'), profile('Dave', null));
    await setDoc(doc(db, 'users/alice/devices/d1'), { token: 'x'.repeat(30), platform: 'android' });
    await setDoc(doc(db, `households/${HID}`), {
      name: 'The Smiths', ownerUid: 'alice', inviteCode: CODE, schemaVersion: 1, createdAt: t, updatedAt: t,
    });
    await setDoc(doc(db, `households/${HID}/members/alice`), { role: 'owner', displayName: 'Alice', joinedAt: t });
    await setDoc(doc(db, `households/${HID}/members/bob`), { role: 'member', displayName: 'Bob', joinedAt: t });
    await setDoc(doc(db, `inviteCodes/${CODE}`), {
      householdId: HID, householdName: 'The Smiths', active: true, createdBy: 'alice', createdAt: t,
    });
    await setDoc(doc(db, `inviteCodes/${PAUSED}`), {
      householdId: HID, householdName: 'The Smiths', active: false, createdBy: 'alice', createdAt: t,
    });
    await setDoc(doc(db, `households/${HID}/groceryLists/g1`), {
      name: 'Groceries', isDefault: true, createdAt: t, updatedAt: t,
    });
    await setDoc(doc(db, `households/${OTHER_HID}`), {
      name: 'Erin home', ownerUid: 'erin', inviteCode: OTHER_CODE, schemaVersion: 1, createdAt: t, updatedAt: t,
    });
    await setDoc(doc(db, `households/${OTHER_HID}/members/erin`), { role: 'owner', displayName: 'Erin', joinedAt: t });
    await setDoc(doc(db, `inviteCodes/${OTHER_CODE}`), {
      householdId: OTHER_HID, householdName: 'Erin home', active: true, createdBy: 'erin', createdAt: t,
    });
  });
});

function profile(displayName, householdId) {
  return {
    displayName,
    householdId,
    timezone: 'America/New_York',
    locale: 'en',
    units: 'imperial',
    notificationPrefs: { enabled: true, digestMinutes: 540, stages: [3, 2, 1, 0], expiredMode: 'once' },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

const as = (uid) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();

function joinRequest(code = CODE, name = 'Carol') {
  return { displayName: name, joinCode: code, status: 'pending', requestedAt: serverTimestamp() };
}

async function seedPendingRequest(uid = 'carol', name = 'Carol') {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `households/${HID}/joinRequests/${uid}`), {
      displayName: name, joinCode: CODE, status: 'pending', requestedAt: new Date(),
    });
  });
}

function approveBatch(db, uid = 'carol', name = 'Carol') {
  const b = writeBatch(db);
  b.update(doc(db, `households/${HID}/joinRequests/${uid}`), { status: 'approved' });
  b.set(doc(db, `households/${HID}/members/${uid}`), { role: 'member', displayName: name, joinedAt: serverTimestamp() });
  return b.commit();
}

function stamp(uid, extra) {
  return { ...extra, createdAt: serverTimestamp(), createdBy: uid, updatedAt: serverTimestamp(), updatedBy: uid };
}

const product = (uid, extra = {}) => stamp(uid, {
  name: 'Milk', nameKey: 'milk', category: 'dairy', defaultLocation: 'fridge', unit: 'pcs',
  minQuantity: null, autoAddToGrocery: false, buyQuantity: null, restockArmed: true, typicalShelfLifeDays: null,
  ...extra,
});
const stock = (uid, extra = {}) => stamp(uid, {
  productId: 'p1', productName: 'Milk', unit: 'pcs', category: 'dairy', quantity: 2,
  expiryDate: '2026-10-20', dateType: 'use_by', location: 'fridge', note: null, ...extra,
});
const groceryItem = (uid, extra = {}) => stamp(uid, {
  name: 'Eggs', nameKey: 'eggs', quantity: 12, unit: 'pcs', note: null, category: 'dairy',
  source: 'manual', productId: null, recipeId: null, ...extra,
});
const recipe = (uid, extra = {}) => stamp(uid, {
  title: 'Pancakes', sourceUrl: null, sourceName: null, imageUrl: null, description: null,
  servings: 4, servingsText: '4', prepMinutes: 5, cookMinutes: 10, totalMinutes: 15,
  ingredients: [{ raw: '2 eggs', qty: 2, unit: null, name: 'eggs', group: null }],
  steps: [{ text: 'Mix', group: null }], notes: null, tags: [], favorite: false, rating: null,
  importMethod: 'manual', ...extra,
});

describe('signed out', () => {
  it('cannot read or write anything', async () => {
    const db = anon();
    await assertFails(getDoc(doc(db, `households/${HID}`)));
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(getDoc(doc(db, `inviteCodes/${CODE}`)));
    await assertFails(getDoc(doc(db, 'config/app')));
    await assertFails(setDoc(doc(db, 'users/x'), profile('X', null)));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p1`), product('x')));
  });
});

describe('non-members', () => {
  it('cannot read any household document', async () => {
    const db = as('carol');
    await assertFails(getDoc(doc(db, `households/${HID}`)));
    await assertFails(getDocs(collection(db, `households/${HID}/members`)));
    await assertFails(getDocs(collection(db, `households/${HID}/products`)));
    await assertFails(getDocs(collection(db, `households/${HID}/groceryLists`)));
    await assertFails(getDocs(collection(db, `households/${HID}/joinRequests`)));
  });

  it('cannot write household data', async () => {
    const db = as('carol');
    await assertFails(setDoc(doc(db, `households/${HID}/products/p1`), product('carol')));
    await assertFails(setDoc(doc(db, `households/${HID}/groceryLists/g2`), { name: 'Costco', isDefault: false }));
  });

  it('can create their own join request with an active code', async () => {
    await assertSucceeds(setDoc(doc(as('carol'), `households/${HID}/joinRequests/carol`), joinRequest()));
  });

  it('cannot create a join request with a wrong code, a paused code or a code for another household', async () => {
    const db = as('carol');
    const ref = doc(db, `households/${HID}/joinRequests/carol`);
    await assertFails(setDoc(ref, joinRequest('ZZZZZ23456')));
    await assertFails(setDoc(ref, joinRequest(PAUSED)));
    await assertFails(setDoc(ref, joinRequest(OTHER_CODE)));
    await assertFails(setDoc(ref, joinRequest('bad')));
  });

  it("cannot create a join request for someone else's uid", async () => {
    await assertFails(setDoc(doc(as('carol'), `households/${HID}/joinRequests/dave`), joinRequest(CODE, 'Dave')));
  });

  it('cannot create a join request with a status other than pending', async () => {
    const ref = doc(as('carol'), `households/${HID}/joinRequests/carol`);
    await assertFails(setDoc(ref, { ...joinRequest(), status: 'approved' }));
  });

  it('cannot create a join request when already a member', async () => {
    await assertFails(setDoc(doc(as('bob'), `households/${HID}/joinRequests/bob`), joinRequest(CODE, 'Bob')));
  });

  it('cannot create their own member document directly, skipping approval', async () => {
    const db = as('carol');
    const ref = doc(db, `households/${HID}/members/carol`);
    await assertFails(setDoc(ref, { role: 'member', displayName: 'Carol', joinedAt: serverTimestamp() }));
    await assertFails(setDoc(ref, { role: 'owner', displayName: 'Carol', joinedAt: serverTimestamp() }));
    await seedPendingRequest();
    await assertFails(setDoc(ref, { role: 'member', displayName: 'Carol', joinedAt: serverTimestamp() }));
  });

  it('cannot point their profile at a household they are not in', async () => {
    await assertFails(updateDoc(doc(as('carol'), 'users/carol'), { householdId: HID, updatedAt: serverTimestamp() }));
  });
});

describe('join requests', () => {
  it('the requester can read and cancel their own request', async () => {
    await seedPendingRequest();
    const db = as('carol');
    await assertSucceeds(getDoc(doc(db, `households/${HID}/joinRequests/carol`)));
    await assertSucceeds(deleteDoc(doc(db, `households/${HID}/joinRequests/carol`)));
  });

  it("another non-member cannot read someone else's request", async () => {
    await seedPendingRequest();
    await assertFails(getDoc(doc(as('dave'), `households/${HID}/joinRequests/carol`)));
  });

  it('the requester cannot approve their own request', async () => {
    await seedPendingRequest();
    await assertFails(updateDoc(doc(as('carol'), `households/${HID}/joinRequests/carol`), { status: 'approved' }));
  });

  it('the owner can list requests and approve one in a batch', async () => {
    await seedPendingRequest();
    const db = as('alice');
    await assertSucceeds(getDocs(collection(db, `households/${HID}/joinRequests`)));
    await assertSucceeds(approveBatch(db));
  });

  it('after approval the new member can finish joining and log member_joined', async () => {
    await seedPendingRequest();
    await approveBatch(as('alice'));
    const db = as('carol');
    const b = writeBatch(db);
    b.update(doc(db, 'users/carol'), { householdId: HID, pendingJoin: null, updatedAt: serverTimestamp() });
    b.delete(doc(db, `households/${HID}/joinRequests/carol`));
    b.set(doc(db, `households/${HID}/activity/joined`), {
      type: 'member_joined', actorUid: 'carol', actorName: 'Carol', params: {}, createdAt: serverTimestamp(),
    });
    await assertSucceeds(b.commit());
    await assertSucceeds(getDoc(doc(db, `households/${HID}`)));
  });

  it('someone still waiting cannot log member_joined', async () => {
    await seedPendingRequest();
    const db = as('carol');
    await assertFails(setDoc(doc(db, `households/${HID}/activity/early`), {
      type: 'member_joined', actorUid: 'carol', actorName: 'Carol', params: {}, createdAt: serverTimestamp(),
    }));
  });

  it('the owner cannot approve with a different display name', async () => {
    await seedPendingRequest();
    await assertFails(approveBatch(as('alice'), 'carol', 'Mallory'));
  });

  it('the owner cannot create a member without an approved request', async () => {
    const db = as('alice');
    await assertFails(setDoc(doc(db, `households/${HID}/members/carol`), {
      role: 'member', displayName: 'Carol', joinedAt: serverTimestamp(),
    }));
    await seedPendingRequest();
    await assertFails(setDoc(doc(db, `households/${HID}/members/carol`), {
      role: 'member', displayName: 'Carol', joinedAt: serverTimestamp(),
    }));
  });

  it('the owner can decline a request, but not reopen it', async () => {
    await seedPendingRequest();
    const ref = doc(as('alice'), `households/${HID}/joinRequests/carol`);
    await assertSucceeds(updateDoc(ref, { status: 'declined' }));
    await assertFails(updateDoc(ref, { status: 'approved' }));
  });

  it('a member who is not the owner cannot approve, decline or read requests', async () => {
    await seedPendingRequest();
    const db = as('bob');
    await assertFails(getDocs(collection(db, `households/${HID}/joinRequests`)));
    await assertFails(updateDoc(doc(db, `households/${HID}/joinRequests/carol`), { status: 'declined' }));
    await assertFails(approveBatch(db));
  });

  it('a pending profile pointer can be set and cleared by the user', async () => {
    const ref = doc(as('carol'), 'users/carol');
    await assertSucceeds(updateDoc(ref, {
      pendingJoin: { householdId: HID, householdName: 'The Smiths', code: CODE }, updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(ref, { pendingJoin: null, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { pendingJoin: { householdId: HID, extra: 1 }, updatedAt: serverTimestamp() }));
  });
});

describe('members', () => {
  it('can read and write products, stock, lists and recipes', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, `households/${HID}/products/p1`), product('bob')));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/stock/s1`), stock('bob')));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/stock/s2`), stock('bob', { expiryDate: null })));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/groceryLists/g2`), {
      name: 'Costco', isDefault: false, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), groceryItem('bob')));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/recipes/r1`), recipe('bob')));
    await assertSucceeds(getDocs(collection(db, `households/${HID}/products`)));
    await assertSucceeds(getDocs(collection(db, `households/${HID}/stock`)));
    await assertSucceeds(getDocs(collection(db, `households/${HID}/groceryLists/g1/items`)));
    await assertSucceeds(getDocs(collection(db, `households/${HID}/recipes`)));
    await assertSucceeds(getDocs(collection(db, `households/${HID}/members`)));
    await assertSucceeds(deleteDoc(doc(db, `households/${HID}/stock/s1`)));
  });

  it('cannot write an invalid quantity, date format or location, or stamp someone else', async () => {
    const db = as('bob');
    const ref = doc(db, `households/${HID}/stock/s1`);
    await assertFails(setDoc(ref, stock('bob', { quantity: -1 })));
    await assertFails(setDoc(ref, stock('bob', { quantity: '2' })));
    await assertFails(setDoc(ref, stock('bob', { expiryDate: '20/10/2026' })));
    await assertFails(setDoc(ref, stock('bob', { location: 'garage' })));
    await assertFails(setDoc(ref, stock('bob', { dateType: 'sell_by' })));
    await assertFails(setDoc(ref, { ...stock('bob'), updatedBy: 'alice' }));
    await assertFails(setDoc(ref, { ...stock('bob'), updatedAt: new Date() }));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p1`), product('bob', { name: '' })));
    await assertFails(setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), groceryItem('bob', { name: '' })));
    await assertFails(setDoc(doc(db, `households/${HID}/recipes/r1`), recipe('bob', { title: '' })));
  });

  it('can leave: delete their own membership and clear their profile pointer', async () => {
    const db = as('bob');
    const b = writeBatch(db);
    b.delete(doc(db, `households/${HID}/members/bob`));
    b.update(doc(db, 'users/bob'), { householdId: null, updatedAt: serverTimestamp() });
    await assertSucceeds(b.commit());
  });

  it('cannot delete another member or promote themselves', async () => {
    const db = as('bob');
    await assertFails(deleteDoc(doc(db, `households/${HID}/members/alice`)));
    await assertFails(updateDoc(doc(db, `households/${HID}/members/bob`), { role: 'owner' }));
  });

  it('can change their own display name in the member list', async () => {
    await assertSucceeds(updateDoc(doc(as('bob'), `households/${HID}/members/bob`), { displayName: 'Robert' }));
  });

  it('cannot update the household or the invite code', async () => {
    const db = as('bob');
    await assertFails(updateDoc(doc(db, `households/${HID}`), { name: 'Bob house', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(db, `inviteCodes/${CODE}`), { active: false }));
    await assertFails(deleteDoc(doc(db, `inviteCodes/${CODE}`)));
  });

  it('can read the household and its invite code', async () => {
    const db = as('bob');
    await assertSucceeds(getDoc(doc(db, `households/${HID}`)));
    await assertSucceeds(getDoc(doc(db, `inviteCodes/${CODE}`)));
  });
});

describe('grocery lists', () => {
  const list = (extra = {}) => ({ name: 'Costco', isDefault: false, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra });

  it('members create, rename and delete extra lists', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, `households/${HID}/groceryLists/g2`), list()));
    await assertSucceeds(updateDoc(doc(db, `households/${HID}/groceryLists/g2`), { name: 'Market', updatedAt: serverTimestamp() }));
    await assertSucceeds(deleteDoc(doc(db, `households/${HID}/groceryLists/g2`)));
  });

  it('nobody creates a second default list or moves the default flag', async () => {
    const db = as('bob');
    await assertFails(setDoc(doc(db, `households/${HID}/groceryLists/g3`), list({ isDefault: true })));
    await assertFails(setDoc(doc(db, `households/${HID}/groceryLists/g3`), list({ name: '' })));
    await assertFails(updateDoc(doc(db, `households/${HID}/groceryLists/g1`), { isDefault: false }));
    await assertFails(updateDoc(doc(as('alice'), `households/${HID}/groceryLists/g1`), { isDefault: false }));
  });

  it('only the owner deletes the default list', async () => {
    await assertFails(deleteDoc(doc(as('bob'), `households/${HID}/groceryLists/g1`)));
    await assertSucceeds(deleteDoc(doc(as('alice'), `households/${HID}/groceryLists/g1`)));
  });

  it('a low-stock item can be merge-written by any member, also after it was checked', async () => {
    const ref = (db) => doc(db, `households/${HID}/groceryLists/g1/items/low_p1`);
    const fields = (uid) => ({
      name: 'Milk', nameKey: 'milk', unit: 'pcs', category: 'dairy', source: 'low_stock', productId: 'p1',
      quantity: 2, updatedAt: serverTimestamp(), updatedBy: uid,
    });
    await assertSucceeds(setDoc(ref(as('bob')), { ...fields('bob'), createdAt: serverTimestamp(), createdBy: 'bob' }, { merge: true }));
    await assertSucceeds(updateDoc(ref(as('alice')), { checked: true, updatedAt: serverTimestamp(), updatedBy: 'alice' }));
    await assertSucceeds(setDoc(ref(as('alice')), fields('alice'), { merge: true }));
    await assertFails(setDoc(ref(as('carol')), fields('carol'), { merge: true }));
  });
});

describe('activity', () => {
  const event = (uid) => ({ type: 'discarded', actorUid: uid, actorName: 'x', productId: 'p1', params: {}, createdAt: serverTimestamp() });

  it('members log their own events, read all, and delete only their own', async () => {
    const bobDb = as('bob');
    await assertSucceeds(setDoc(doc(bobDb, `households/${HID}/activity/a1`), event('bob')));
    await assertFails(setDoc(doc(bobDb, `households/${HID}/activity/a2`), event('alice')));
    await assertSucceeds(setDoc(doc(as('alice'), `households/${HID}/activity/a3`), event('alice')));
    await assertSucceeds(getDocs(collection(bobDb, `households/${HID}/activity`)));
    await assertFails(deleteDoc(doc(bobDb, `households/${HID}/activity/a3`)));
    await assertSucceeds(deleteDoc(doc(bobDb, `households/${HID}/activity/a1`)));
  });

  it('outsiders cannot read or write activity', async () => {
    await assertFails(getDocs(collection(as('carol'), `households/${HID}/activity`)));
    await assertFails(setDoc(doc(as('carol'), `households/${HID}/activity/a4`), event('carol')));
  });
});

describe('owner', () => {
  it('can remove a member', async () => {
    await assertSucceeds(deleteDoc(doc(as('alice'), `households/${HID}/members/bob`)));
  });

  it('cannot leave without transferring ownership', async () => {
    await assertFails(deleteDoc(doc(as('alice'), `households/${HID}/members/alice`)));
  });

  it('can rename the household together with its invite code', async () => {
    const db = as('alice');
    const b = writeBatch(db);
    b.update(doc(db, `households/${HID}`), { name: 'Smith family', updatedAt: serverTimestamp() });
    b.update(doc(db, `inviteCodes/${CODE}`), { householdName: 'Smith family' });
    await assertSucceeds(b.commit());
  });

  it('can pause and resume the code', async () => {
    const ref = doc(as('alice'), `inviteCodes/${CODE}`);
    await assertSucceeds(updateDoc(ref, { active: false }));
    await assertSucceeds(updateDoc(ref, { active: true }));
  });

  it('can rotate the code', async () => {
    const db = as('alice');
    const next = 'NEWCD23456';
    const b = writeBatch(db);
    b.set(doc(db, `inviteCodes/${next}`), {
      householdId: HID, householdName: 'The Smiths', active: true, createdBy: 'alice', createdAt: serverTimestamp(),
    });
    b.update(doc(db, `households/${HID}`), { inviteCode: next, updatedAt: serverTimestamp() });
    b.delete(doc(db, `inviteCodes/${CODE}`));
    await assertSucceeds(b.commit());
  });

  it('cannot create a code that is not the household code, or a code for another household', async () => {
    const db = as('alice');
    await assertFails(setDoc(doc(db, 'inviteCodes/EXTRA23456'), {
      householdId: HID, householdName: 'The Smiths', active: true, createdBy: 'alice', createdAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(db, 'inviteCodes/STEAL23456'), {
      householdId: OTHER_HID, householdName: 'x', active: true, createdBy: 'alice', createdAt: serverTimestamp(),
    }));
  });

  it('cannot change ownerUid in a plain update', async () => {
    await assertFails(updateDoc(doc(as('alice'), `households/${HID}`), { ownerUid: 'bob', updatedAt: serverTimestamp() }));
  });

  it('can transfer ownership to a member in one batch', async () => {
    const db = as('alice');
    const b = writeBatch(db);
    b.update(doc(db, `households/${HID}`), { ownerUid: 'bob', updatedAt: serverTimestamp() });
    b.update(doc(db, `households/${HID}/members/bob`), { role: 'owner' });
    b.update(doc(db, `households/${HID}/members/alice`), { role: 'member' });
    await assertSucceeds(b.commit());
    // The former owner is now a member and can leave; the new owner manages the code.
    await assertSucceeds(updateDoc(doc(as('bob'), `inviteCodes/${CODE}`), { active: false }));
    await assertFails(updateDoc(doc(as('alice'), `inviteCodes/${CODE}`), { active: true }));
  });

  it('cannot transfer ownership to a non-member', async () => {
    const db = as('alice');
    const b = writeBatch(db);
    b.update(doc(db, `households/${HID}`), { ownerUid: 'carol', updatedAt: serverTimestamp() });
    b.update(doc(db, `households/${HID}/members/alice`), { role: 'member' });
    await assertFails(b.commit());
  });

  it('can delete the household and all its subcollections', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      for (let i = 0; i < 30; i++) await setDoc(doc(db, `households/${HID}/products/p${i}`), { name: `P${i}` });
      await setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), { name: 'Eggs' });
      await setDoc(doc(db, `households/${HID}/activity/a1`), { type: 'stock_added' });
    });
    await seedPendingRequest();
    const db = as('alice');

    // Subcollections first, in batches (Firestore does not cascade deletes).
    const first = writeBatch(db);
    for (let i = 0; i < 30; i++) first.delete(doc(db, `households/${HID}/products/p${i}`));
    first.delete(doc(db, `households/${HID}/groceryLists/g1/items/i1`));
    first.delete(doc(db, `households/${HID}/groceryLists/g1`));
    first.delete(doc(db, `households/${HID}/activity/a1`));
    first.delete(doc(db, `households/${HID}/joinRequests/carol`));
    first.delete(doc(db, `households/${HID}/members/bob`));
    await assertSucceeds(first.commit());

    // Then the code, the owner's membership, the household and the profile pointer together.
    const last = writeBatch(db);
    last.delete(doc(db, `inviteCodes/${CODE}`));
    last.delete(doc(db, `households/${HID}/members/alice`));
    last.delete(doc(db, `households/${HID}`));
    last.update(doc(db, 'users/alice'), { householdId: null, updatedAt: serverTimestamp() });
    await assertSucceeds(last.commit());
  });
});

describe('creating a household', () => {
  function createBatch(db, uid, hid, code, overrides = {}) {
    const b = writeBatch(db);
    b.set(doc(db, `households/${hid}`), {
      name: 'Carol home', ownerUid: uid, inviteCode: code, schemaVersion: 1,
      createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides.household,
    });
    b.set(doc(db, `households/${hid}/members/${uid}`), {
      role: 'owner', displayName: 'Carol', joinedAt: serverTimestamp(),
    });
    b.set(doc(db, `inviteCodes/${code}`), {
      householdId: hid, householdName: 'Carol home', active: true, createdBy: uid, createdAt: serverTimestamp(),
    });
    b.set(doc(db, `households/${hid}/groceryLists/default`), {
      name: 'Groceries', isDefault: true, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    b.update(doc(db, `users/${uid}`), { householdId: hid, updatedAt: serverTimestamp() });
    return b.commit();
  }

  it('a new user can create the household batch', async () => {
    await assertSucceeds(createBatch(as('carol'), 'carol', 'h3', 'CARYL23456'));
  });

  it('cannot create a household owned by someone else or with a bad name', async () => {
    await assertFails(createBatch(as('carol'), 'carol', 'h3', 'CARYL23456', { household: { ownerUid: 'dave' } }));
    await assertFails(createBatch(as('carol'), 'carol', 'h3', 'CARYL23456', { household: { name: '' } }));
  });

  it('cannot take over an existing household id', async () => {
    await assertFails(createBatch(as('carol'), 'carol', HID, 'CARYL23456'));
  });

  it('cannot reuse an invite code that already exists', async () => {
    await assertFails(createBatch(as('carol'), 'carol', 'h3', OTHER_CODE));
  });
});

describe('invite codes', () => {
  it('nobody can list codes', async () => {
    await assertFails(getDocs(collection(as('alice'), 'inviteCodes')));
    await assertFails(getDocs(collection(as('carol'), 'inviteCodes')));
  });

  it('a signed-in user can get a well-formed code but not a malformed one', async () => {
    const db = as('carol');
    await assertSucceeds(getDoc(doc(db, `inviteCodes/${CODE}`)));
    await assertSucceeds(getDoc(doc(db, 'inviteCodes/ZZZZZ23456')));
    await assertFails(getDoc(doc(db, 'inviteCodes/abcde23456')));
    await assertFails(getDoc(doc(db, 'inviteCodes/ABCDE2345O')));
    await assertFails(getDoc(doc(db, 'inviteCodes/SHORT')));
  });
});

describe('profiles and devices', () => {
  it("user A cannot read user B's profile or devices", async () => {
    const db = as('bob');
    await assertFails(getDoc(doc(db, 'users/alice')));
    await assertFails(getDocs(collection(db, 'users/alice/devices')));
    await assertSucceeds(getDoc(doc(as('alice'), 'users/alice')));
    await assertSucceeds(getDocs(collection(as('alice'), 'users/alice/devices')));
  });

  it('a user can create their own profile with no household', async () => {
    const db = as('frank');
    await assertSucceeds(setDoc(doc(db, 'users/frank'), {
      ...profile('Frank', null), createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(setDoc(doc(as('gina'), 'users/gina'), profile('Gina', HID)));
    await assertFails(setDoc(doc(as('gina'), 'users/hank'), profile('Hank', null)));
  });

  it('the client cannot write lastDigestDate or lastExpiredThrough', async () => {
    const ref = doc(as('alice'), 'users/alice');
    await assertFails(updateDoc(ref, { lastDigestDate: '2026-10-15' }));
    await assertFails(updateDoc(ref, { lastExpiredThrough: '2026-10-14' }));
    await assertFails(setDoc(doc(as('frank'), 'users/frank'), { ...profile('Frank', null), lastDigestDate: '2026-10-15' }));
  });

  it('a user can delete their own profile and devices', async () => {
    const db = as('alice');
    await assertSucceeds(deleteDoc(doc(db, 'users/alice/devices/d1')));
    await assertSucceeds(deleteDoc(doc(db, 'users/alice')));
  });
});

describe('removed members', () => {
  it('lose all household read access immediately', async () => {
    await deleteDoc(doc(as('alice'), `households/${HID}/members/bob`));
    const db = as('bob');
    await assertFails(getDoc(doc(db, `households/${HID}`)));
    await assertFails(getDoc(doc(db, `households/${HID}/members/bob`)));
    await assertFails(getDocs(collection(db, `households/${HID}/products`)));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p1`), product('bob')));
  });

  it('can still clear their own profile pointer', async () => {
    await deleteDoc(doc(as('alice'), `households/${HID}/members/bob`));
    await assertSucceeds(updateDoc(doc(as('bob'), 'users/bob'), { householdId: null, updatedAt: serverTimestamp() }));
  });
});

describe('config', () => {
  it('is readable when signed in and never writable by clients', async () => {
    await assertSucceeds(getDoc(doc(as('alice'), 'config/app')));
    await assertFails(setDoc(doc(as('alice'), 'config/app'), { minSupportedBuild: 0 }));
  });
});

describe('recipes', () => {
  it('enforce the import limits: title up to 200, at most 100 ingredients and 100 steps', async () => {
    const ref = doc(as('bob'), `households/${HID}/recipes/r1`);
    const many = (n, make) => Array.from({ length: n }, (_, i) => make(i));
    await assertSucceeds(setDoc(ref, recipe('bob', { title: 'x'.repeat(200) })));
    await assertFails(setDoc(ref, recipe('bob', { title: 'x'.repeat(201) })));
    await assertSucceeds(setDoc(ref, recipe('bob', {
      ingredients: many(100, (i) => ({ raw: `${i} g flour`, qty: i, unit: 'g', name: 'flour', group: null })),
      steps: many(100, (i) => ({ text: `Step ${i}`, group: null })),
    })));
    await assertFails(setDoc(ref, recipe('bob', {
      ingredients: many(101, (i) => ({ raw: `${i}`, qty: null, unit: null, name: null, group: null })),
    })));
    await assertFails(setDoc(ref, recipe('bob', { steps: many(101, (i) => ({ text: `${i}`, group: null })) })));
  });

  it('favorite is a stamped update, and any member may delete', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), `households/${HID}/recipes/r1`), recipe('alice')));
    const ref = doc(as('bob'), `households/${HID}/recipes/r1`);
    await assertSucceeds(updateDoc(ref, { favorite: true, updatedAt: serverTimestamp(), updatedBy: 'bob' }));
    await assertFails(updateDoc(ref, { favorite: false }));
    await assertSucceeds(deleteDoc(ref));
  });

  it('a save writes the recipe and its recipe_saved event in one batch', async () => {
    const db = as('bob');
    const b = writeBatch(db);
    const imported = recipe('bob', { importMethod: 'web', sourceUrl: 'https://example.com/r' });
    b.set(doc(db, `households/${HID}/recipes/r2`), imported);
    b.set(doc(db, `households/${HID}/activity/e1`), {
      type: 'recipe_saved', actorUid: 'bob', actorName: 'Bob', recipeId: 'r2',
      params: { name: 'Pancakes' }, createdAt: serverTimestamp(),
    });
    await assertSucceeds(b.commit());
  });

  it('outsiders cannot read or write recipes', async () => {
    await assertFails(getDocs(collection(as('carol'), `households/${HID}/recipes`)));
    await assertFails(setDoc(doc(as('carol'), `households/${HID}/recipes/r3`), recipe('carol')));
  });
});

describe('insights stats', () => {
  const path = (month = '2026-10') => `households/${HID}/stats/${month}`;
  const counted = (uid, fields) => ({ ...fields, updatedAt: serverTimestamp(), updatedBy: uid });

  it('members count used and thrown away batches with increments, and read them', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, path()), counted('bob', { used: increment(1) }), { merge: true }));
    await assertSucceeds(setDoc(doc(as('alice'), path()), counted('alice', {
      spoiled: increment(1), thrownByDay: { 18: increment(1) },
    }), { merge: true }));
    const snap = await assertSucceeds(getDoc(doc(db, path())));
    if (snap.data().used !== 1 || snap.data().thrownByDay['18'] !== 1) throw new Error('counts not applied');
    await assertSucceeds(getDocs(collection(db, `households/${HID}/stats`)));
  });

  it('a throw away writes the batch delete, the event and the count in one batch, and Undo takes it back', async () => {
    const db = as('bob');
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `households/${HID}/stock/s1`), stock('bob'));
    });
    const b = writeBatch(db);
    b.delete(doc(db, `households/${HID}/stock/s1`));
    b.set(doc(db, `households/${HID}/activity/e1`), {
      type: 'discarded', actorUid: 'bob', actorName: 'Bob', productId: 'p1',
      params: { name: 'Milk', quantity: 2, reason: 'expired' }, createdAt: serverTimestamp(),
    });
    b.set(doc(db, path()), counted('bob', { expired: increment(1), thrownByDay: { 3: increment(1) } }), { merge: true });
    await assertSucceeds(b.commit());
    await assertSucceeds(setDoc(doc(db, path()), counted('bob', {
      expired: increment(-1), thrownByDay: { 3: increment(-1) },
    }), { merge: true }));
  });

  it('counts are whole numbers from 0, with known fields, a real month and a stamp', async () => {
    const db = as('bob');
    await assertFails(setDoc(doc(db, path()), counted('bob', { used: -1 })));
    await assertFails(setDoc(doc(db, path()), counted('bob', { used: 1.5 })));
    await assertFails(setDoc(doc(db, path()), counted('bob', { used: 'many' })));
    await assertFails(setDoc(doc(db, path()), counted('bob', { used: 1, notes: 'x' })));
    await assertFails(setDoc(doc(db, path()), counted('bob', { thrownByDay: { 32: 1 } })));
    await assertFails(setDoc(doc(db, path()), counted('bob', { thrownByDay: 3 })));
    await assertFails(setDoc(doc(db, path('2026-13')), counted('bob', { used: 1 })));
    await assertFails(setDoc(doc(db, path('october')), counted('bob', { used: 1 })));
    await assertFails(setDoc(doc(db, path()), { used: 1 }));
    await assertFails(setDoc(doc(db, path()), counted('alice', { used: 1 })));
    await assertSucceeds(setDoc(doc(db, path()), counted('bob', { used: 0 })));
  });

  it('only the owner deletes counts (with the household)', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), path()), counted('bob', { used: 2 })));
    await assertFails(deleteDoc(doc(as('bob'), path())));
    await assertSucceeds(deleteDoc(doc(as('alice'), path())));
  });

  it('outsiders cannot read or write counts', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), path()), counted('bob', { used: 2 })));
    await assertFails(getDoc(doc(as('carol'), path())));
    await assertFails(getDocs(collection(as('erin'), `households/${HID}/stats`)));
    await assertFails(setDoc(doc(as('carol'), path()), counted('carol', { used: increment(1) }), { merge: true }));
    await assertFails(getDoc(doc(anon(), path())));
  });
});
