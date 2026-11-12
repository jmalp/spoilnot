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
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
  arrayUnion,
  deleteField,
  increment,
  query,
  where,
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

  it('a join request may carry the masked email of the sign-in that sends it, and no other', async () => {
    const as2 = (uid, email) => env.authenticatedContext(uid, email === undefined ? {} : { email }).firestore();
    const ref = (db) => doc(db, `households/${HID}/joinRequests/carol`);
    const withEmail = (emailMasked) => ({ ...joinRequest(), emailMasked });
    await assertFails(setDoc(ref(as2('carol', 'carol.lee@example.com')), withEmail('da***@example.com')));
    await assertFails(setDoc(ref(as2('carol', 'carol.lee@example.com')), withEmail('carol.lee@example.com')));
    await assertFails(setDoc(ref(as2('carol', 'carol.lee@example.com')), withEmail('ca***@other.com')));
    await assertFails(setDoc(ref(as2('carol', 'carol.lee@example.com')), withEmail(42)));
    // No address in the sign-in: no email on the request.
    await assertFails(setDoc(ref(as2('carol')), withEmail('ca***@example.com')));
    await assertSucceeds(setDoc(ref(as2('carol', 'carol.lee@example.com')), withEmail('ca***@example.com')));
    // Short names keep one character.
    const db = as2('dave', 'dj@example.org');
    await assertSucceeds(setDoc(doc(db, `households/${HID}/joinRequests/dave`), {
      ...joinRequest(CODE, 'Dave'), emailMasked: 'd***@example.org',
    }));
  });

  it('a join request without the masked email is still accepted, as older builds send it', async () => {
    const db = env.authenticatedContext('carol', { email: 'carol.lee@example.com' }).firestore();
    await assertSucceeds(setDoc(doc(db, `households/${HID}/joinRequests/carol`), joinRequest()));
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
    // Members delete a batch by marking it (DESIGN 9.8); deleting for real is the owner's, with the household.
    await assertFails(deleteDoc(doc(db, `households/${HID}/stock/s1`)));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/stock/s1`), {
      deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: 'bob',
    }, { merge: true }));
  });

  it('cannot write an invalid quantity, date format or location, or stamp someone else', async () => {
    const db = as('bob');
    const ref = doc(db, `households/${HID}/stock/s1`);
    await assertFails(setDoc(ref, stock('bob', { quantity: -2e9 })));
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

  it('a device is an Android phone or the web app, with a token', async () => {
    const db = as('alice');
    const device = (fields) => setDoc(doc(db, 'users/alice/devices/d2'), fields);
    await assertSucceeds(device({ token: 'w'.repeat(160), platform: 'web', appVersion: 'web abc1234' }));
    await assertSucceeds(device({ token: 'a'.repeat(160), platform: 'android' }));
    await assertFails(device({ token: 'i'.repeat(160), platform: 'ios' }));
    await assertFails(device({ token: 'short', platform: 'web' }));
    await assertFails(setDoc(doc(as('bob'), 'users/alice/devices/d3'), { token: 'w'.repeat(160), platform: 'web' }));
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

describe('the reminder job\'s state', () => {
  it('jobs/state (the daily prune date, DESIGN 10.1) is neither readable nor writable by clients', async () => {
    await assertFails(getDoc(doc(as('alice'), 'jobs/state')));
    await assertFails(setDoc(doc(as('alice'), 'jobs/state'), { lastPruneDate: '2099-01-01' }));
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

  it('favorite is a stamped update; members delete with a tombstone, only the owner outright', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), `households/${HID}/recipes/r1`), recipe('alice')));
    const ref = doc(as('bob'), `households/${HID}/recipes/r1`);
    await assertSucceeds(updateDoc(ref, { favorite: true, updatedAt: serverTimestamp(), updatedBy: 'bob' }));
    await assertFails(updateDoc(ref, { favorite: false }));
    await assertFails(deleteDoc(ref));
    await assertSucceeds(setDoc(ref, { deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: 'bob' }, { merge: true }));
    await assertSucceeds(deleteDoc(doc(as('alice'), `households/${HID}/recipes/r1`)));
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
    b.set(doc(db, `households/${HID}/stock/s1`), {
      deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: 'bob',
    }, { merge: true });
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
    await assertSucceeds(setDoc(doc(as('bob'), path()), counted('bob', { used: 1 })));
    await assertFails(deleteDoc(doc(as('bob'), path())));
    await assertSucceeds(deleteDoc(doc(as('alice'), path())));
  });

  it('outsiders cannot read or write counts', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), path()), counted('bob', { used: 1 })));
    await assertFails(getDoc(doc(as('carol'), path())));
    await assertFails(getDocs(collection(as('erin'), `households/${HID}/stats`)));
    await assertFails(setDoc(doc(as('carol'), path()), counted('carol', { used: increment(1) }), { merge: true }));
    await assertFails(getDoc(doc(anon(), path())));
  });
});

describe('tombstones and change queries', () => {
  const tombstone = (uid) => ({ deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: uid });
  const seed = async () => env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `households/${HID}/stock/s1`), stock('alice'));
    await setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), groceryItem('alice'));
    await setDoc(doc(db, `households/${HID}/recipes/r1`), recipe('alice'));
  });

  it('members delete a batch, item or recipe by marking it, and restore it by writing it again', async () => {
    await seed();
    const db = as('bob');
    for (const path of ['stock/s1', 'groceryLists/g1/items/i1', 'recipes/r1']) {
      await assertSucceeds(setDoc(doc(db, `households/${HID}/${path}`), tombstone('bob'), { merge: true }));
    }
    await assertSucceeds(setDoc(doc(db, `households/${HID}/stock/s1`), stock('bob')));
    const restored = await getDoc(doc(db, `households/${HID}/stock/s1`));
    if (restored.data().deletedAt !== undefined) throw new Error('restore keeps deletedAt');
  });

  it('deletedAt must be the write time, and a later merge keeps it as it was', async () => {
    await seed();
    const ref = doc(as('bob'), `households/${HID}/stock/s1`);
    await assertFails(setDoc(ref, { ...tombstone('bob'), deletedAt: new Date('2026-01-01') }, { merge: true }));
    await assertFails(setDoc(ref, { ...tombstone('bob'), deletedAt: 'yesterday' }, { merge: true }));
    await assertSucceeds(setDoc(ref, tombstone('bob'), { merge: true }));
    await assertSucceeds(setDoc(ref, { quantity: 3, updatedAt: serverTimestamp(), updatedBy: 'bob' }, { merge: true }));
  });

  it('a low-stock item comes back from a tombstone by removing deletedAt', async () => {
    await seed();
    const ref = doc(as('bob'), `households/${HID}/groceryLists/g1/items/i1`);
    await assertSucceeds(setDoc(ref, tombstone('bob'), { merge: true }));
    await assertSucceeds(setDoc(ref, {
      name: 'Eggs', quantity: 12, deletedAt: deleteField(), updatedAt: serverTimestamp(), updatedBy: 'bob',
    }, { merge: true }));
  });

  it('members may ask for changes since a time; outsiders may not', async () => {
    await seed();
    const since = new Date('2026-01-01T00:00:00Z');
    await assertSucceeds(getDocs(query(collection(as('bob'), `households/${HID}/stock`), where('updatedAt', '>', since))));
    await assertFails(getDocs(query(collection(as('carol'), `households/${HID}/stock`), where('updatedAt', '>', since))));
    await assertFails(setDoc(doc(as('carol'), `households/${HID}/stock/s1`), tombstone('carol'), { merge: true }));
  });
});

describe('recipe names taught to products', () => {
  it('members add a name to a product, up to 30, and items may say which name they came from', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, `households/${HID}/products/p1`), product('bob', { aliases: ['scallion'] })));
    await assertSucceeds(updateDoc(doc(db, `households/${HID}/products/p1`), {
      aliases: arrayUnion('green onion'), updatedAt: serverTimestamp(), updatedBy: 'bob',
    }));
    const many = Array.from({ length: 31 }, (_, i) => `name ${i}`);
    await assertFails(setDoc(doc(db, `households/${HID}/products/p2`), product('bob', { aliases: many })));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p3`), product('bob', { aliases: 'scallion' })));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), groceryItem('bob', { aliasOf: 'scallion' })));
    await assertFails(setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i2`), groceryItem('bob', { aliasOf: '' })));
    await assertFails(setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i3`), groceryItem('bob', { aliasOf: 'x'.repeat(81) })));
  });
});

describe('opened batches', () => {
  it('a batch may record when it was opened, as a date; a product how long it keeps once opened', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, `households/${HID}/stock/s1`), stock('bob', { openedOn: '2026-10-19', expiryDate: '2026-10-24' })));
    await assertSucceeds(updateDoc(doc(db, `households/${HID}/stock/s1`), {
      openedOn: null, expiryDate: '2026-10-30', updatedAt: serverTimestamp(), updatedBy: 'bob',
    }));
    await assertFails(setDoc(doc(db, `households/${HID}/stock/s2`), stock('bob', { openedOn: 'today' })));
    await assertFails(setDoc(doc(db, `households/${HID}/stock/s3`), stock('bob', { openedOn: 20261019 })));
    await assertSucceeds(setDoc(doc(db, `households/${HID}/products/p1`), product('bob', { openedShelfLifeDays: 5 })));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p2`), product('bob', { openedShelfLifeDays: 0 })));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p3`), product('bob', { openedShelfLifeDays: 2.5 })));
    await assertFails(setDoc(doc(db, `households/${HID}/products/p4`), product('bob', { openedShelfLifeDays: 400 })));
  });
});

describe('store order', () => {
  it('members save a list\'s aisle order, as a short list, and nothing else changes with it', async () => {
    const ref = doc(as('bob'), `households/${HID}/groceryLists/g1`);
    await assertSucceeds(updateDoc(ref, { aisleOrder: ['produce', 'dairy', 'bakery'], updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { aisleOrder: 'produce', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { aisleOrder: Array.from({ length: 31 }, (_, i) => `a${i}`), updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(ref, { aisleOrder: ['dairy'], isDefault: false, updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as('carol'), `households/${HID}/groceryLists/g1`), { aisleOrder: ['dairy'] }));
  });
});

// Meal planner (after v1, DESIGN 5.9).
describe('meal plan', () => {
  const path = (id = 'm1') => `households/${HID}/meals/${id}`;
  const meal = (uid, extra = {}) => ({
    date: '2026-10-29', slot: 'dinner', position: 0, recipeId: 'r1', title: 'Lasagna', servings: 4,
    cooks: ['alice', 'bob'], note: null, cookedAt: null, cookedBy: null,
    createdAt: serverTimestamp(), createdBy: uid, updatedAt: serverTimestamp(), updatedBy: uid, ...extra,
  });

  it('members plan, change and remove meals; a meal may have no recipe', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, path()), meal('bob')));
    await assertSucceeds(setDoc(doc(db, path('m2')), meal('bob', { recipeId: null, title: 'Leftovers', servings: null, cooks: [] })));
    await assertSucceeds(updateDoc(doc(db, path()), {
      servings: 6, cooks: ['alice'], note: 'Double batch', updatedAt: serverTimestamp(), updatedBy: 'bob',
    }));
    await assertSucceeds(setDoc(doc(as('alice'), path()), { deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: 'alice' }, { merge: true }));
    await assertSucceeds(getDocs(collection(db, `households/${HID}/meals`)));
    // A meal goes by tombstone; only the owner (deleting the household) removes it for real.
    await assertFails(deleteDoc(doc(db, path('m2'))));
    await assertSucceeds(deleteDoc(doc(as('alice'), path('m2'))));
  });

  it('dates, slots, titles, servings, cooks and notes are checked', async () => {
    const db = as('bob');
    for (const bad of [
      { date: '29/10/2026' }, { date: 20261029 }, { slot: 'brunch' }, { position: 'first' }, { title: '' },
      { title: 'x'.repeat(121) }, { servings: 0 }, { servings: 101 }, { servings: 2.5 }, { cooks: 'alice' },
      { cooks: Array.from({ length: 21 }, (_, i) => `u${i}`) }, { note: '' }, { note: 'x'.repeat(201) },
      { recipeId: '' },
    ]) {
      await assertFails(setDoc(doc(db, path()), meal('bob', bad)));
    }
    await assertFails(setDoc(doc(db, path()), meal('alice')));
    await assertFails(setDoc(doc(db, path()), { ...meal('bob'), updatedAt: new Date('2026-01-01') }));
  });

  it('cooked is set by the writer at server time, kept by later edits and cleared together', async () => {
    const cook = (uid) => ({ cookedAt: serverTimestamp(), cookedBy: uid, updatedAt: serverTimestamp(), updatedBy: uid });
    await assertSucceeds(setDoc(doc(as('bob'), path()), meal('bob')));
    await assertFails(updateDoc(doc(as('bob'), path()), { ...cook('bob'), cookedBy: 'alice' }));
    await assertFails(updateDoc(doc(as('bob'), path()), { ...cook('bob'), cookedAt: new Date('2026-10-29') }));
    await assertFails(updateDoc(doc(as('bob'), path()), { ...cook('bob'), cookedBy: null }));
    await assertSucceeds(updateDoc(doc(as('bob'), path()), cook('bob')));
    // Alice changes the cooked meal; Bob's mark stays as it was.
    await assertSucceeds(updateDoc(doc(as('alice'), path()), {
      servings: 2, updatedAt: serverTimestamp(), updatedBy: 'alice',
    }));
    await assertFails(updateDoc(doc(as('alice'), path()), {
      cookedBy: 'alice', updatedAt: serverTimestamp(), updatedBy: 'alice',
    }));
    await assertSucceeds(updateDoc(doc(as('alice'), path()), {
      cookedAt: null, cookedBy: null, updatedAt: serverTimestamp(), updatedBy: 'alice',
    }));
  });

  it('outsiders cannot read or plan', async () => {
    await assertSucceeds(setDoc(doc(as('bob'), path()), meal('bob')));
    await assertFails(getDoc(doc(as('carol'), path())));
    await assertFails(getDocs(collection(as('erin'), `households/${HID}/meals`)));
    await assertFails(setDoc(doc(as('carol'), path('m9')), meal('carol')));
    await assertFails(deleteDoc(doc(as('carol'), path())));
    await assertFails(getDoc(doc(anon(), path())));
  });
});

// Settings > Clear household data (owner, after v1, DESIGN 5.1): the app's batch, as the owner and as a member.
describe('clear household data', () => {
  const tombstone = (uid) => ({ deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: uid });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      const t = new Date('2026-10-01T00:00:00Z');
      await setDoc(doc(db, `households/${HID}/stock/s1`), stock('bob'));
      await setDoc(doc(db, `households/${HID}/recipes/r1`), recipe('bob'));
      await setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), groceryItem('bob'));
      await setDoc(doc(db, `households/${HID}/groceryLists/g2`), { name: 'Party', isDefault: false, createdAt: t, updatedAt: t });
      await setDoc(doc(db, `households/${HID}/groceryLists/g2/items/i2`), groceryItem('bob'));
      await setDoc(doc(db, `households/${HID}/activity/a1`), { type: 'stock_added', actorUid: 'bob', actorName: 'Bob', params: {}, createdAt: t });
      await setDoc(doc(db, `households/${HID}/stats/2026-10`), { used: 3, updatedAt: t, updatedBy: 'bob' });
      await setDoc(doc(db, `households/${HID}/meals/m1`), { date: '2026-10-29', slot: 'dinner', position: 0, recipeId: null, title: 'Tacos', servings: null, cooks: [], note: null, createdAt: t, createdBy: 'bob', updatedAt: t, updatedBy: 'bob' });
    });
  });

  function clearBatch(uid) {
    const db = as(uid);
    const h = `households/${HID}`;
    const batch = writeBatch(db);
    for (const path of ['stock/s1', 'recipes/r1', 'groceryLists/g1/items/i1', 'meals/m1']) {
      batch.set(doc(db, `${h}/${path}`), tombstone(uid), { merge: true });
    }
    batch.update(doc(db, `${h}/groceryLists/g1`), { name: 'Groceries', aisleOrder: [], updatedAt: serverTimestamp() });
    batch.delete(doc(db, `${h}/groceryLists/g2/items/i2`));
    batch.delete(doc(db, `${h}/groceryLists/g2`));
    batch.delete(doc(db, `${h}/activity/a1`));
    batch.delete(doc(db, `${h}/stats/2026-10`));
    batch.set(doc(db, `${h}/activity/reset`), {
      type: 'household_reset', actorUid: uid, actorName: 'Someone', params: {}, createdAt: serverTimestamp(),
    });
    return batch;
  }

  it('the owner clears everything but the household and its members in one batch', async () => {
    await assertSucceeds(clearBatch('alice').commit());
  });

  it('a member cannot (other people\'s activity and the counts are the owner\'s to delete)', async () => {
    await assertFails(clearBatch('bob').commit());
  });
});

// After v1 (2026-11-08): what each person may do on paths the matrix above did not cover, who wrote a document,
// deletes and counts, and the allowed fields and sizes (DESIGN 8.1, 8.5, 12.4).
describe('more of the matrix', () => {
  const t0 = new Date('2026-10-01T00:00:00Z');
  const h = (path) => `households/${HID}/${path}`;
  const meal = (uid, extra = {}) => ({
    date: '2026-11-01', slot: 'dinner', position: 0, recipeId: null, title: 'Tacos', servings: 4, cooks: [uid],
    note: null, cookedAt: null, cookedBy: null, createdAt: serverTimestamp(), createdBy: uid,
    updatedAt: serverTimestamp(), updatedBy: uid, ...extra,
  });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, 'config/app'), { minSupportedBuild: 1, message: '' });
      await setDoc(doc(db, 'users/erin'), profile('Erin', OTHER_HID));
      await setDoc(doc(db, h('groceryLists/g1/items/i1')), { ...groceryItem('alice'), createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('products/p1')), { ...product('alice'), createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('stock/s1')), { ...stock('alice'), createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('recipes/r1')), { ...recipe('alice'), createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('meals/m1')), { ...meal('alice'), createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('activity/a1')), {
        type: 'stock_added', actorUid: 'bob', actorName: 'Bob', params: { name: 'Milk' }, createdAt: t0,
      });
      await setDoc(doc(db, h('stats/2026-10')), { used: 3, expired: 1, updatedAt: t0, updatedBy: 'alice' });
      await setDoc(doc(db, h('joinRequests/carol')), {
        displayName: 'Carol', joinCode: CODE, status: 'pending', requestedAt: t0,
      });
    });
  });

  describe('paths and people', () => {
    it('config is listable when signed in, never updated or deleted by a client', async () => {
      await assertSucceeds(getDocs(collection(as('carol'), 'config')));
      await assertFails(getDocs(collection(anon(), 'config')));
      await assertFails(updateDoc(doc(as('alice'), 'config/app'), { minSupportedBuild: 0 }));
      await assertFails(deleteDoc(doc(as('alice'), 'config/app')));
    });

    it('nobody lists users; a member cannot change or delete another profile', async () => {
      await assertFails(getDocs(collection(as('alice'), 'users')));
      await assertFails(updateDoc(doc(as('bob'), 'users/alice'), { timezone: 'Pacific/Kiritimati', updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(doc(as('bob'), 'users/alice'), { notificationPrefs: { enabled: false }, updatedAt: serverTimestamp() }));
      await assertFails(deleteDoc(doc(as('bob'), 'users/alice')));
    });

    it('a device is read, re-pointed and deleted only by its owner', async () => {
      await assertSucceeds(getDoc(doc(as('alice'), 'users/alice/devices/d1')));
      await assertFails(getDoc(doc(as('bob'), 'users/alice/devices/d1')));
      await assertFails(setDoc(doc(as('bob'), 'users/alice/devices/d1'), { token: 'b'.repeat(160), platform: 'android' }));
      await assertFails(deleteDoc(doc(as('bob'), 'users/alice/devices/d1')));
    });

    it('nobody lists households; a member or another owner cannot delete one', async () => {
      await assertFails(getDocs(collection(as('alice'), 'households')));
      await assertFails(deleteDoc(doc(as('bob'), `households/${HID}`)));
      await assertFails(deleteDoc(doc(as('erin'), `households/${HID}`)));
    });

    it('members get one member document; outsiders cannot; nobody renames someone else', async () => {
      await assertSucceeds(getDoc(doc(as('bob'), h('members/alice'))));
      await assertFails(getDoc(doc(as('carol'), h('members/alice'))));
      await assertFails(updateDoc(doc(as('bob'), h('members/alice')), { displayName: 'Mallory' }));
      await assertFails(updateDoc(doc(as('alice'), h('members/bob')), { displayName: 'Mallory' }));
    });

    it('only the requester or the owner removes a join request', async () => {
      await assertFails(deleteDoc(doc(as('dave'), h('joinRequests/carol'))));
      await assertFails(deleteDoc(doc(as('bob'), h('joinRequests/carol'))));
      await assertSucceeds(deleteDoc(doc(as('alice'), h('joinRequests/carol'))));
    });

    it('products: members get; outsiders cannot get, update or delete; nobody stamps as someone else', async () => {
      await assertSucceeds(getDoc(doc(as('bob'), h('products/p1'))));
      await assertFails(getDoc(doc(as('carol'), h('products/p1'))));
      await assertFails(updateDoc(doc(as('carol'), h('products/p1')), { name: 'X', updatedAt: serverTimestamp(), updatedBy: 'carol' }));
      await assertFails(updateDoc(doc(as('bob'), h('products/p1')), { name: 'X', updatedAt: serverTimestamp(), updatedBy: 'alice' }));
      await assertFails(deleteDoc(doc(as('carol'), h('products/p1'))));
      await assertFails(deleteDoc(doc(as('erin'), h('products/p1'))));
    });

    it('stock, items, recipes, meals, lists and activity: members get; outsiders cannot get or delete', async () => {
      for (const path of ['stock/s1', 'groceryLists/g1/items/i1', 'recipes/r1', 'meals/m1', 'groceryLists/g1', 'activity/a1']) {
        await assertSucceeds(getDoc(doc(as('bob'), h(path))));
        await assertFails(getDoc(doc(as('carol'), h(path))));
        await assertFails(getDoc(doc(as('erin'), h(path))));
      }
      for (const path of ['stock/s1', 'groceryLists/g1/items/i1', 'recipes/r1', 'meals/m1', 'activity/a1', 'stats/2026-10']) {
        await assertFails(deleteDoc(doc(as('carol'), h(path))));
      }
    });

    it('members list the grocery lists; outsiders cannot list a list\'s items', async () => {
      await assertSucceeds(getDocs(collection(as('bob'), h('groceryLists'))));
      await assertFails(getDocs(collection(as('carol'), h('groceryLists/g1/items'))));
    });

    it('nobody queries a subcollection across households', async () => {
      for (const group of ['members', 'stock', 'joinRequests', 'devices', 'meals', 'activity']) {
        await assertFails(getDocs(collectionGroup(as('bob'), group)));
      }
      await assertFails(getDocs(query(collectionGroup(as('alice'), 'members'), where('role', '==', 'owner'))));
    });

    it('an activity event cannot be edited, not even by its author', async () => {
      await assertFails(updateDoc(doc(as('bob'), h('activity/a1')), { params: { name: 'Cake' } }));
      await assertFails(updateDoc(doc(as('alice'), h('activity/a1')), { params: { name: 'Cake' } }));
    });
  });

  describe('membership and ownership', () => {
    it('an outsider cannot make themselves a member or the owner in one batch', async () => {
      const db = as('carol');
      const b = writeBatch(db);
      b.update(doc(db, `households/${HID}`), { ownerUid: 'carol', updatedAt: serverTimestamp() });
      b.set(doc(db, h('members/carol')), { role: 'owner', displayName: 'Carol', joinedAt: serverTimestamp() });
      await assertFails(b.commit());
    });

    it('someone waiting to join cannot read the household or its members', async () => {
      await assertFails(getDoc(doc(as('carol'), `households/${HID}`)));
      await assertFails(getDocs(collection(as('carol'), h('members'))));
    });

    it('a member cannot take ownership in a batch, demote the owner, approve a join or change the code', async () => {
      const db = as('bob');
      const b = writeBatch(db);
      b.update(doc(db, `households/${HID}`), { ownerUid: 'bob', updatedAt: serverTimestamp() });
      b.update(doc(db, h('members/bob')), { role: 'owner' });
      await assertFails(b.commit());
      await assertFails(updateDoc(doc(db, h('members/alice')), { role: 'member' }));
      await assertFails(updateDoc(doc(db, `households/${HID}`), { inviteCode: 'BOBCD23456', updatedAt: serverTimestamp() }));
    });

    it('the owner cannot approve someone straight in as owner', async () => {
      const db = as('alice');
      const b = writeBatch(db);
      b.update(doc(db, h('joinRequests/carol')), { status: 'approved' });
      b.set(doc(db, h('members/carol')), { role: 'owner', displayName: 'Carol', joinedAt: serverTimestamp() });
      await assertFails(b.commit());
    });

    it('the owner of another household cannot approve into this one or join without a request', async () => {
      const db = as('erin');
      const b = writeBatch(db);
      b.update(doc(db, h('joinRequests/carol')), { status: 'approved' });
      b.set(doc(db, h('members/carol')), { role: 'member', displayName: 'Carol', joinedAt: serverTimestamp() });
      await assertFails(b.commit());
      await assertFails(setDoc(doc(db, h('members/erin')), { role: 'member', displayName: 'Erin', joinedAt: serverTimestamp() }));
    });

    it('a member cannot create a code for this household', async () => {
      await assertFails(setDoc(doc(as('bob'), 'inviteCodes/BOBCD23456'), {
        householdId: HID, householdName: 'x', active: true, createdBy: 'bob', createdAt: serverTimestamp(),
      }));
    });

    it('a removed member cannot write their membership back, log events or read requests and meals', async () => {
      await deleteDoc(doc(as('alice'), h('members/bob')));
      const db = as('bob');
      await assertFails(setDoc(doc(db, h('members/bob')), { role: 'member', displayName: 'Bob', joinedAt: serverTimestamp() }));
      await assertFails(setDoc(doc(db, h('activity/x')), { type: 'stock_added', actorUid: 'bob', actorName: 'Bob', params: {}, createdAt: serverTimestamp() }));
      await assertFails(getDocs(collection(db, h('joinRequests'))));
      await assertFails(getDocs(collection(db, h('meals'))));
    });

    it('an ownership transfer must demote the old owner in the same batch', async () => {
      const db = as('alice');
      const b = writeBatch(db);
      b.update(doc(db, `households/${HID}`), { ownerUid: 'bob', updatedAt: serverTimestamp() });
      b.update(doc(db, h('members/bob')), { role: 'owner' });
      await assertFails(b.commit());
      // So the old owner cannot then remove the new one: Alice is still the only owner.
      await assertFails(updateDoc(doc(db, h('members/alice')), { role: 'member' }));
      await assertFails(updateDoc(doc(db, h('members/bob')), { role: 'owner' }));
    });
  });

  describe('who wrote it', () => {
    it('updatedAt and deletedAt are the request time, updatedBy is the writer', async () => {
      const db = as('bob');
      await assertFails(setDoc(doc(db, h('products/p9')), { ...product('bob'), updatedAt: new Date('2030-01-01') }));
      await assertFails(setDoc(doc(db, h('groceryLists/g1/items/i9')), { ...groceryItem('bob'), updatedBy: 'alice' }));
      await assertFails(setDoc(doc(db, h('recipes/r9')), { ...recipe('bob'), deletedAt: new Date('2030-01-01') }));
      await assertFails(setDoc(doc(db, h('meals/m9')), { ...meal('bob'), deletedAt: new Date('2020-01-01') }));
    });

    it('createdBy is the writer on create and does not change after', async () => {
      const db = as('bob');
      await assertFails(setDoc(doc(db, h('stock/s9')), { ...stock('bob'), createdBy: 'alice' }));
      await assertFails(setDoc(doc(db, h('products/p9')), { ...product('bob'), createdBy: 'alice' }));
      await assertFails(setDoc(doc(db, h('recipes/r9')), { ...recipe('bob'), createdBy: 'alice' }));
      await assertFails(setDoc(doc(db, h('meals/m9')), meal('bob', { createdBy: 'alice' })));
      await assertFails(setDoc(doc(db, h('groceryLists/g1/items/i9')), { ...groceryItem('bob'), createdBy: 'alice' }));
      await assertSucceeds(setDoc(doc(db, h('stock/s9')), stock('bob')));
      // Alice's batch stays Alice's, whoever edits it.
      await assertFails(updateDoc(doc(db, h('stock/s1')), { createdBy: 'bob', updatedAt: serverTimestamp(), updatedBy: 'bob' }));
      await assertSucceeds(updateDoc(doc(db, h('stock/s1')), { quantity: 1, updatedAt: serverTimestamp(), updatedBy: 'bob' }));
    });

    it('Undo brings a deleted document back with its creator, or with the writer as creator', async () => {
      const db = as('bob');
      const tombstone = { deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: 'bob' };
      await assertSucceeds(setDoc(doc(db, h('stock/s1')), tombstone, { merge: true }));
      await assertSucceeds(setDoc(doc(db, h('stock/s1')), { ...stock('bob'), createdAt: t0 }));
      await assertSucceeds(setDoc(doc(db, h('recipes/r1')), tombstone, { merge: true }));
      await assertSucceeds(setDoc(doc(db, h('recipes/r1')), { ...recipe('bob'), createdAt: t0, createdBy: 'alice' }));
      // A live document keeps its creator.
      await assertFails(setDoc(doc(db, h('meals/m1')), { ...meal('bob'), createdAt: t0 }));
    });

    it('checkedBy is the writer or nobody; another member\'s check stays as it was', async () => {
      const ref = (uid) => doc(as(uid), h('groceryLists/g1/items/i1'));
      const check = (uid, by) => ({ checked: true, checkedBy: by, checkedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: uid });
      await assertFails(updateDoc(ref('bob'), check('bob', 'alice')));
      await assertSucceeds(updateDoc(ref('bob'), check('bob', 'bob')));
      await assertSucceeds(updateDoc(ref('alice'), { quantity: 6, updatedAt: serverTimestamp(), updatedBy: 'alice' }));
      await assertFails(updateDoc(ref('alice'), { checkedBy: 'carol', updatedAt: serverTimestamp(), updatedBy: 'alice' }));
      await assertSucceeds(updateDoc(ref('alice'), {
        checked: false, checkedBy: null, checkedAt: null, updatedAt: serverTimestamp(), updatedBy: 'alice',
      }));
      await assertFails(setDoc(doc(as('bob'), h('groceryLists/g1/items/i9')), groceryItem('bob', { checked: true, checkedBy: 'alice' })));
    });

    it('events have a known type, the writer as author and a name of 1 to 40 characters', async () => {
      const db = as('bob');
      const event = (extra) => ({ type: 'discarded', actorUid: 'bob', actorName: 'Bob', params: {}, createdAt: serverTimestamp(), ...extra });
      for (const type of ['stock_added', 'grocery_added', 'grocery_checked', 'recipe_saved', 'discarded', 'recipe_cooked']) {
        await assertSucceeds(setDoc(doc(db, h(`activity/${type}`)), event({ type })));
      }
      await assertFails(setDoc(doc(db, h('activity/x1')), event({ type: 'stock_stolen' })));
      await assertFails(setDoc(doc(db, h('activity/x2')), event({ actorName: '' })));
      await assertFails(setDoc(doc(db, h('activity/x3')), event({ actorName: 'B'.repeat(41) })));
      await assertFails(setDoc(doc(db, h('activity/x4')), event({ actorUid: 'alice' })));
      await assertFails(setDoc(doc(db, h('activity/x5')), event({ params: { name: 'Milk', note: 'x' } })));
      await assertFails(setDoc(doc(db, h('activity/x6')), event({ params: { name: 'x'.repeat(201) } })));
      await assertFails(setDoc(doc(db, h('activity/x7')), event({ params: { reason: 'boredom' } })));
      await assertSucceeds(setDoc(doc(db, h('activity/x8')), event({
        productId: 'p1', params: { name: 'Milk', quantity: 2, reason: 'spoiled' },
      })));
    });
  });

  describe('deletes and counts', () => {
    it('members delete products, batches, items, recipes and meals only with a tombstone', async () => {
      for (const path of ['products/p1', 'stock/s1', 'recipes/r1', 'meals/m1', 'groceryLists/g1/items/i1']) {
        await assertFails(deleteDoc(doc(as('bob'), h(path))));
        await assertSucceeds(setDoc(doc(as('bob'), h(path)), {
          deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: 'bob',
        }, { merge: true }));
      }
    });

    it('the owner deletes them outright (deleting the household)', async () => {
      for (const path of ['products/p1', 'stock/s1', 'recipes/r1', 'meals/m1', 'groceryLists/g1/items/i1']) {
        await assertSucceeds(deleteDoc(doc(as('alice'), h(path))));
      }
    });

    it('a member deletes a list with its items in one batch, the list first or last', async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        const db = ctx.firestore();
        await setDoc(doc(db, h('groceryLists/g2')), { name: 'Party', isDefault: false, createdAt: t0, updatedAt: t0 });
        for (const id of ['i1', 'i2', 'i3']) {
          await setDoc(doc(db, h(`groceryLists/g2/items/${id}`)), { ...groceryItem('alice'), createdAt: t0, updatedAt: t0 });
        }
      });
      const db = as('bob');
      await assertFails(deleteDoc(doc(db, h('groceryLists/g2/items/i1'))));
      // The app deletes the list first, so items in later batches (over 400 operations) still go.
      const first = writeBatch(db);
      first.delete(doc(db, h('groceryLists/g2')));
      first.delete(doc(db, h('groceryLists/g2/items/i1')));
      await assertSucceeds(first.commit());
      const later = writeBatch(db);
      later.delete(doc(db, h('groceryLists/g2/items/i2')));
      later.delete(doc(db, h('groceryLists/g2/items/i3')));
      await assertSucceeds(later.commit());
    });

    it('counts move by at most one per field per write', async () => {
      const ref = doc(as('bob'), h('stats/2026-10'));
      const counted = (fields) => ({ ...fields, updatedAt: serverTimestamp(), updatedBy: 'bob' });
      await assertFails(setDoc(ref, counted({ used: 0, expired: 100000 })));
      await assertFails(setDoc(ref, counted({ used: increment(2) }), { merge: true }));
      await assertFails(setDoc(ref, counted({ used: increment(-2) }), { merge: true }));
      await assertSucceeds(setDoc(ref, counted({ used: increment(1), expired: increment(-1) }), { merge: true }));
      await assertSucceeds(setDoc(ref, counted({ spoiled: increment(1), thrownByDay: { 3: increment(1) } }), { merge: true }));
      await assertFails(setDoc(ref, counted({ thrownByDay: { 3: increment(2) } }), { merge: true }));
      await assertFails(setDoc(ref, counted({ thrownByDay: { 4: increment(1), 5: increment(1) } }), { merge: true }));
      await assertSucceeds(setDoc(ref, counted({ spoiled: increment(-1), thrownByDay: { 3: increment(-1) } }), { merge: true }));
      // A new month starts from nothing.
      await assertFails(setDoc(doc(as('bob'), h('stats/2026-11')), counted({ used: 5 })));
      await assertSucceeds(setDoc(doc(as('bob'), h('stats/2026-11')), counted({ used: 1 })));
    });

    it('two counts of the same field in one batch count as one write', async () => {
      const db = as('bob');
      const b = writeBatch(db);
      const counted = { used: increment(1), updatedAt: serverTimestamp(), updatedBy: 'bob' };
      b.set(doc(db, h('stats/2026-10')), counted, { merge: true });
      b.set(doc(db, h('stats/2026-10')), counted, { merge: true });
      await assertFails(b.commit());
    });
  });

  describe('allowed fields and sizes', () => {
    const MB = 1_000_000;

    it('names up to 80 characters, notes up to 500', async () => {
      const db = as('bob');
      await assertSucceeds(setDoc(doc(db, h('stock/s8')), stock('bob', { productName: 'x'.repeat(80), note: 'n'.repeat(500) })));
      await assertFails(setDoc(doc(db, h('stock/s9')), stock('bob', { productName: 'x'.repeat(81) })));
      await assertFails(setDoc(doc(db, h('stock/s9')), stock('bob', { productName: 'x'.repeat(MB) })));
      await assertFails(setDoc(doc(db, h('stock/s9')), stock('bob', { note: 'n'.repeat(501) })));
      await assertFails(setDoc(doc(db, h('products/p9')), product('bob', { name: 'x'.repeat(81) })));
      await assertFails(setDoc(doc(db, h('groceryLists/g1/items/i9')), groceryItem('bob', { note: 'n'.repeat(501) })));
      await assertFails(setDoc(doc(db, h('groceryLists/g1/items/i9')), groceryItem('bob', { name: 'x'.repeat(81) })));
    });

    it('only the fields the apps write', async () => {
      const db = as('bob');
      const junk = 'j'.repeat(1000);
      await assertFails(setDoc(doc(db, h('products/big')), product('bob', { junk })));
      await assertFails(setDoc(doc(db, h('stock/big')), stock('bob', { junk })));
      await assertFails(setDoc(doc(db, h('groceryLists/g1/items/big')), groceryItem('bob', { junk })));
      await assertFails(setDoc(doc(db, h('recipes/big')), recipe('bob', { junk })));
      await assertFails(setDoc(doc(db, h('meals/big')), meal('bob', { junk })));
      await assertFails(setDoc(doc(db, h('groceryLists/big')), { name: 'Big', isDefault: false, junk }));
      await assertFails(setDoc(doc(db, h('activity/big')), {
        type: 'discarded', actorUid: 'bob', actorName: 'Bob', params: { junk }, createdAt: serverTimestamp(),
      }));
      await assertFails(setDoc(doc(db, h('activity/big2')), {
        type: 'discarded', actorUid: 'bob', actorName: 'Bob', params: {}, junk, createdAt: serverTimestamp(),
      }));
      await assertFails(setDoc(doc(as('alice'), 'users/alice/devices/d2'), { token: 'w'.repeat(160), platform: 'web', junk }));
      // A merge cannot add one either.
      await assertFails(setDoc(doc(db, h('products/p1')), { junk, updatedAt: serverTimestamp(), updatedBy: 'bob' }, { merge: true }));
    });

    it('lists of names are short: aliases, tags, cooks and aisle order', async () => {
      const db = as('bob');
      await assertSucceeds(setDoc(doc(db, h('products/p8')), product('bob', { aliases: ['scallion', 'green onion'] })));
      await assertFails(setDoc(doc(db, h('products/p9')), product('bob', { aliases: ['a'.repeat(MB / 2)] })));
      await assertFails(setDoc(doc(db, h('recipes/r9')), recipe('bob', { tags: ['t'.repeat(1000)] })));
      await assertFails(setDoc(doc(db, h('recipes/r9')), recipe('bob', { tags: Array.from({ length: 9 }, (_, i) => `t${i}`) })));
      await assertFails(setDoc(doc(db, h('meals/m9')), meal('bob', { cooks: ['c'.repeat(5000)] })));
      await assertFails(updateDoc(doc(db, h('groceryLists/g1')), { aisleOrder: ['d'.repeat(5000)], updatedAt: serverTimestamp() }));
    });

    it('recipe lists are lists, links are http or https, and quantities are finite', async () => {
      const db = as('bob');
      await assertFails(setDoc(doc(db, h('recipes/odd')), recipe('bob', { ingredients: 'not a list' })));
      await assertFails(setDoc(doc(db, h('recipes/odd')), recipe('bob', { sourceUrl: 'javascript:alert(1)' })));
      await assertFails(setDoc(doc(db, h('recipes/odd')), recipe('bob', { sourceUrl: 'tel:+15555550100' })));
      await assertFails(setDoc(doc(db, h('recipes/odd')), recipe('bob', { imageUrl: 'data:image/png;base64,AAAA' })));
      await assertFails(setDoc(doc(db, h('recipes/odd')), recipe('bob', { sourceUrl: `https://example.com/${'x'.repeat(2000)}` })));
      await assertFails(setDoc(doc(db, h('recipes/odd')), recipe('bob', { notes: 'n'.repeat(5001) })));
      await assertSucceeds(setDoc(doc(db, h('recipes/ok')), recipe('bob', {
        sourceUrl: 'https://example.com/pancakes', imageUrl: 'HTTPS://example.com/p.jpg', notes: 'n'.repeat(5000),
      })));
      await assertFails(setDoc(doc(db, h('stock/inf')), stock('bob', { quantity: Infinity })));
      await assertFails(setDoc(doc(db, h('stock/nan')), stock('bob', { quantity: NaN })));
      await assertFails(setDoc(doc(db, h('groceryLists/g1/items/inf')), groceryItem('bob', { quantity: Infinity })));
    });

    it('a new account\'s own household gets the same limits', async () => {
      const db = as('dave');
      const b = writeBatch(db);
      b.set(doc(db, 'households/hx'), { name: 'X', ownerUid: 'dave', inviteCode: 'DAVEX23456', schemaVersion: 1, createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
      b.set(doc(db, 'households/hx/members/dave'), { role: 'owner', displayName: 'Dave', joinedAt: serverTimestamp() });
      b.set(doc(db, 'inviteCodes/DAVEX23456'), { householdId: 'hx', householdName: 'X', active: true, createdBy: 'dave', createdAt: serverTimestamp() });
      b.update(doc(db, 'users/dave'), { householdId: 'hx', updatedAt: serverTimestamp() });
      await assertSucceeds(b.commit());
      await assertSucceeds(setDoc(doc(db, 'households/hx/products/p0'), product('dave')));
      await assertFails(setDoc(doc(db, 'households/hx/products/p1'), product('dave', { junk: 'j'.repeat(MB) })));
    });

    it('a profile keeps a short time zone and locale and the known notification settings', async () => {
      const ref = doc(as('carol'), 'users/carol');
      await assertSucceeds(updateDoc(ref, { timezone: 'America/Argentina/ComodRivadavia', updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { timezone: 'z'.repeat(MB / 2), updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { locale: 'l'.repeat(21), updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { units: 'cubits', updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { notificationPrefs: { enabled: true, stages: 'abc' }, updatedAt: serverTimestamp() }));
      await assertFails(updateDoc(ref, { notificationPrefs: { enabled: true, extra: 1 }, updatedAt: serverTimestamp() }));
      await assertSucceeds(updateDoc(ref, {
        notificationPrefs: { enabled: true, digestMinutes: 1260, stages: [3, 1, 0], expiredMode: 'daily', cooking: false },
        updatedAt: serverTimestamp(),
      }));
    });
  });
});

// After v1 (2026-11-09, DESIGN 8.5): when two phones finish the same thing, the rules pick the winner and refuse the
// later phone's whole batch: Add to pantry on two phones, Cooked on two phones, an item added to a deleted list.
describe('edits and Undo keep what someone else changed', () => {
  const tombstone = (uid) => ({ deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: uid });
  const restore = (uid) => ({ deletedAt: deleteField(), updatedAt: serverTimestamp(), updatedBy: uid });
  const seed = async () => env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `households/${HID}/products/p1`), product('alice'));
    await setDoc(doc(db, `households/${HID}/stock/s1`), stock('alice', { quantity: 1 }));
    await setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), groceryItem('alice'));
    await setDoc(doc(db, `households/${HID}/recipes/r1`), recipe('alice'));
  });

  it('two phones\' last -1 may take a batch below 0', async () => {
    await seed();
    const minusOne = (uid) => updateDoc(doc(as(uid), `households/${HID}/stock/s1`), {
      quantity: increment(-1), updatedAt: serverTimestamp(), updatedBy: uid,
    });
    await assertSucceeds(minusOne('bob'));
    await assertSucceeds(minusOne('alice'));
  });

  it('an edit writes single fields over another member\'s change', async () => {
    await seed();
    const ref = doc(as('bob'), `households/${HID}/stock/s1`);
    await assertSucceeds(updateDoc(ref, { expiryDate: '2026-11-20', updatedAt: serverTimestamp(), updatedBy: 'bob' }));
    await assertSucceeds(updateDoc(ref, { quantity: increment(2), updatedAt: serverTimestamp(), updatedBy: 'bob' }));
    await assertSucceeds(setDoc(doc(as('bob'), `households/${HID}/products/p1`), {
      name: 'Oat milk', nameKey: 'oat milk', updatedAt: serverTimestamp(), updatedBy: 'bob',
    }, { merge: true }));
  });

  it('Undo removes the tombstone only, keeping the creator', async () => {
    await seed();
    for (const path of ['stock/s1', 'groceryLists/g1/items/i1', 'recipes/r1']) {
      const ref = doc(as('bob'), `households/${HID}/${path}`);
      await assertSucceeds(setDoc(ref, tombstone('bob'), { merge: true }));
      await assertSucceeds(setDoc(ref, restore('bob'), { merge: true }));
      const back = await getDoc(ref);
      if (back.data().deletedAt !== undefined) throw new Error(`${path} keeps deletedAt`);
      if (back.data().createdBy !== 'alice') throw new Error(`${path} changed its creator`);
    }
  });

  it('Undo of a -1 that used a batch up adds it back and removes a tombstone in one write', async () => {
    await seed();
    const ref = doc(as('bob'), `households/${HID}/stock/s1`);
    await assertSucceeds(updateDoc(ref, { quantity: increment(-1), updatedAt: serverTimestamp(), updatedBy: 'bob' }));
    await assertSucceeds(setDoc(doc(as('alice'), `households/${HID}/stock/s1`), tombstone('alice'), { merge: true }));
    await assertSucceeds(updateDoc(ref, {
      quantity: increment(1), deletedAt: deleteField(), updatedAt: serverTimestamp(), updatedBy: 'bob',
    }));
  });

  it('Undo cannot bring back a document that is gone for real', async () => {
    const ref = doc(as('bob'), `households/${HID}/stock/gone`);
    await assertFails(setDoc(ref, restore('bob'), { merge: true }));
  });
});

describe('one phone wins', () => {
  const t0 = new Date('2026-10-01T00:00:00Z');
  const h = (path) => `households/${HID}/${path}`;
  const tombstone = (uid) => ({ deletedAt: serverTimestamp(), updatedAt: serverTimestamp(), updatedBy: uid });
  const checked = (uid, name) => ({
    ...groceryItem(uid, { name, nameKey: name.toLowerCase(), checked: true, checkedBy: uid, checkedAt: t0 }),
    createdAt: t0, updatedAt: t0,
  });
  const meal = (uid, extra = {}) => ({
    date: '2026-11-09', slot: 'dinner', position: 0, recipeId: 'r1', title: 'Risotto', servings: 2, cooks: [],
    note: null, cookedAt: null, cookedBy: null, createdAt: t0, createdBy: uid, updatedAt: t0, updatedBy: uid, ...extra,
  });

  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, h('groceryLists/g1/items/milk')), checked('bob', 'Milk'));
      await setDoc(doc(db, h('groceryLists/g1/items/rice')), checked('bob', 'Rice'));
      await setDoc(doc(db, h('groceryLists/g2')), { name: 'Party', isDefault: false, createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('stock/rice1')), { ...stock('alice', { productName: 'Rice', quantity: 5 }), createdAt: t0, updatedAt: t0 });
      await setDoc(doc(db, h('meals/m1')), meal('alice'));
    });
  });

  // What Add to pantry writes (GroceryService.addToPantry): a product and a purchase per item, the item's tombstone.
  function addToPantry(uid, suffix) {
    const db = as(uid);
    const batch = writeBatch(db);
    for (const name of ['Milk', 'Rice']) {
      const key = name.toLowerCase();
      batch.set(doc(db, h(`products/${key}-${suffix}`)), product(uid, { name, nameKey: key }));
      batch.set(doc(db, h(`stock/${key}-${suffix}`)), stock(uid, { productId: `${key}-${suffix}`, productName: name }));
      batch.set(doc(db, h(`groceryLists/g1/items/${key}`)), tombstone(uid), { merge: true });
    }
    return batch.commit();
  }

  async function live(path) {
    let docs = [];
    await env.withSecurityRulesDisabled(async (ctx) => {
      docs = (await getDocs(collection(ctx.firestore(), h(path)))).docs.filter((d) => d.data().deletedAt === undefined);
    });
    return docs.map((d) => d.id).sort();
  }

  it('Add to pantry on a second phone fails whole: no second purchases, no second products', async () => {
    await assertSucceeds(addToPantry('alice', 'a'));
    await assertFails(addToPantry('bob', 'b'));
    if ((await live('products')).join() !== 'milk-a,rice-a') throw new Error(`products: ${await live('products')}`);
    if ((await live('stock')).join() !== 'milk-a,rice-a,rice1') throw new Error(`stock: ${await live('stock')}`);
  });

  it('it fails when only one of its items was already removed', async () => {
    await assertSucceeds(setDoc(doc(as('alice'), h('groceryLists/g1/items/rice')), tombstone('alice'), { merge: true }));
    await assertFails(addToPantry('bob', 'b'));
    if ((await live('stock')).join() !== 'rice1') throw new Error(`stock: ${await live('stock')}`);
  });

  it('an item is removed once; later merges keep the tombstone, Undo and restock bring it back', async () => {
    const ref = (uid) => doc(as(uid), h('groceryLists/g1/items/milk'));
    await assertSucceeds(setDoc(ref('alice'), tombstone('alice'), { merge: true }));
    // Clear checked or delete on the other phone, for an item already gone.
    await assertFails(setDoc(ref('bob'), tombstone('bob'), { merge: true }));
    await assertFails(setDoc(ref('alice'), tombstone('alice'), { merge: true }));
    // A late check-off on a stale copy merges into the tombstone and keeps it.
    await assertSucceeds(updateDoc(ref('bob'), {
      checked: false, checkedBy: null, checkedAt: null, updatedAt: serverTimestamp(), updatedBy: 'bob',
    }));
    // Undo writes the item again without deletedAt; then it can be removed again.
    await assertSucceeds(setDoc(ref('alice'), {
      ...groceryItem('bob', { name: 'Milk', nameKey: 'milk' }), createdAt: t0, updatedBy: 'alice',
    }));
    await assertSucceeds(setDoc(ref('bob'), tombstone('bob'), { merge: true }));
    // The restock engine brings a low-stock item back by removing deletedAt.
    await assertSucceeds(setDoc(ref('alice'), {
      name: 'Milk', source: 'low_stock', deletedAt: deleteField(), updatedAt: serverTimestamp(), updatedBy: 'alice',
    }, { merge: true }));
  });

  it('Cooked on a second phone fails whole, its pantry take included; Undo and cooking again still work', async () => {
    const cooked = (uid, left) => {
      const db = as(uid);
      const batch = writeBatch(db);
      batch.update(doc(db, h('meals/m1')), {
        cookedAt: serverTimestamp(), cookedBy: uid, updatedAt: serverTimestamp(), updatedBy: uid,
      });
      batch.update(doc(db, h('stock/rice1')), { quantity: left, updatedAt: serverTimestamp(), updatedBy: uid });
      batch.set(doc(db, h(`activity/cooked-${uid}`)), {
        type: 'recipe_cooked', actorUid: uid, actorName: uid, recipeId: 'r1', params: { name: 'Risotto' },
        createdAt: serverTimestamp(),
      });
      return batch.commit();
    };
    await assertSucceeds(cooked('alice', 3));
    await assertFails(cooked('bob', 3));
    await assertFails(cooked('alice', 1));
    let rice;
    await env.withSecurityRulesDisabled(async (ctx) => {
      rice = (await getDoc(doc(ctx.firestore(), h('stock/rice1')))).data().quantity;
    });
    if (rice !== 3) throw new Error(`rice left ${rice}`);
    if ((await live('activity')).join() !== 'cooked-alice') throw new Error(`events: ${await live('activity')}`);
    // Undo of Cooked (or "Not cooked yet") clears it; then either phone may cook it.
    await assertSucceeds(updateDoc(doc(as('alice'), h('meals/m1')), {
      cookedAt: null, cookedBy: null, updatedAt: serverTimestamp(), updatedBy: 'alice',
    }));
    await assertSucceeds(updateDoc(doc(as('bob'), h('meals/m1')), {
      cookedAt: serverTimestamp(), cookedBy: 'bob', updatedAt: serverTimestamp(), updatedBy: 'bob',
    }));
  });

  it('items are created only under a list that exists, or is created in the same batch', async () => {
    const db = as('bob');
    await assertSucceeds(setDoc(doc(db, h('groceryLists/g2/items/chips')), groceryItem('bob', { name: 'Chips' })));
    await assertFails(setDoc(doc(db, h('groceryLists/gone/items/chips')), groceryItem('bob', { name: 'Chips' })));
    // Undo of a delete puts an item back with a full write: refused once its list is gone.
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), h('groceryLists/g2/items/chips')));
      await deleteDoc(doc(ctx.firestore(), h('groceryLists/g2')));
    });
    await assertFails(setDoc(doc(db, h('groceryLists/g2/items/chips')), groceryItem('bob', { name: 'Chips' })));
    // A tombstone or low-stock merge cannot make an item there either.
    await assertFails(setDoc(doc(db, h('groceryLists/g2/items/milk')), {
      name: 'Milk', source: 'low_stock', updatedAt: serverTimestamp(), updatedBy: 'bob',
    }, { merge: true }));
    const batch = writeBatch(db);
    batch.set(doc(db, h('groceryLists/g3')), {
      name: 'BBQ', isDefault: false, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    batch.set(doc(db, h('groceryLists/g3/items/buns')), groceryItem('bob', { name: 'Buns' }));
    await assertSucceeds(batch.commit());
  });

  it('a list deleted while another phone adds to it: the delete wins and the add is refused', async () => {
    const alice = as('alice');
    const del = writeBatch(alice);
    del.delete(doc(alice, h('groceryLists/g2')));
    await assertSucceeds(del.commit());
    await assertFails(setDoc(doc(as('bob'), h('groceryLists/g2/items/chips')), groceryItem('bob', { name: 'Chips' })));
    if ((await live('groceryLists/g2/items')).length !== 0) throw new Error('an item was left under a deleted list');
  });
});

// After v1 (2026-11-11, DESIGN 13.4): the restore sets each document as exported with updatedAt at the restore's
// time and updatedBy 'restore' (grocery lists: updatedAt only). Members must still be able to change it after.
describe('restored documents', () => {
  const restored = (data, list = false) => {
    const { createdAt: _c, updatedAt: _u, updatedBy: _b, ...fields } = data;
    const t = new Date('2026-10-01T00:00:00Z');
    return list ? { ...fields, createdAt: t, updatedAt: new Date() } : { ...fields, createdAt: t, updatedAt: new Date(), updatedBy: 'restore' };
  };
  const meal = restored({
    date: '2026-10-29', slot: 'dinner', position: 0, recipeId: 'r1', title: 'Lasagna', servings: 4,
    cooks: ['alice'], note: null, cookedAt: new Date('2026-10-29T19:00:00Z'), cookedBy: 'alice', createdBy: 'alice',
  });
  const seed = () => env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `households/${HID}/products/p1`), restored(product('alice')));
    await setDoc(doc(db, `households/${HID}/stock/s1`), restored(stock('alice', { note: '2026-12-24T18:00:00Z' })));
    await setDoc(doc(db, `households/${HID}/groceryLists/g1`), restored({ name: 'Groceries', isDefault: true }, true));
    await setDoc(doc(db, `households/${HID}/groceryLists/g1/items/i1`), restored(groceryItem('alice', {
      checked: true, checkedBy: 'alice', checkedAt: new Date('2026-10-20T10:00:00Z'),
    })));
    await setDoc(doc(db, `households/${HID}/recipes/r1`), restored(recipe('alice')));
    await setDoc(doc(db, `households/${HID}/meals/m1`), meal);
    await setDoc(doc(db, `households/${HID}/stats/2026-10`), { used: 3, updatedAt: new Date(), updatedBy: 'restore' });
  });
  const by = (uid, fields) => ({ ...fields, updatedAt: serverTimestamp(), updatedBy: uid });

  it('a member edits, counts and removes what a restore wrote', async () => {
    await seed();
    const db = as('bob');
    const h = (p) => doc(db, `households/${HID}/${p}`);
    await assertSucceeds(updateDoc(h('products/p1'), by('bob', { name: 'Oat milk', nameKey: 'oat milk' })));
    await assertSucceeds(updateDoc(h('stock/s1'), by('bob', { quantity: increment(-1) })));
    await assertSucceeds(updateDoc(h('stock/s1'), by('bob', { note: 'Top shelf' })));
    await assertSucceeds(updateDoc(h('groceryLists/g1'), { name: 'Weekly', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(h('groceryLists/g1/items/i1'), by('bob', { checked: false, checkedBy: null, checkedAt: null })));
    await assertSucceeds(updateDoc(h('recipes/r1'), by('bob', { favorite: true })));
    await assertSucceeds(updateDoc(h('meals/m1'), by('bob', { note: 'Extra cheese' })));
    await assertSucceeds(setDoc(h('stats/2026-10'), by('bob', { used: increment(1) }), { merge: true }));
    for (const p of ['products/p1', 'stock/s1', 'groceryLists/g1/items/i1', 'recipes/r1', 'meals/m1']) {
      await assertSucceeds(setDoc(h(p), by('bob', { deletedAt: serverTimestamp() }), { merge: true }));
    }
  });

  it('only the restore may write updatedBy restore', async () => {
    await seed();
    await assertFails(updateDoc(doc(as('bob'), `households/${HID}/products/p1`), {
      name: 'Oat milk', updatedAt: serverTimestamp(), updatedBy: 'restore',
    }));
  });
});
