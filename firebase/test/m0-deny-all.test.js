// M0 smoke test: proves the rules load into the emulator and deny by default (DESIGN 12.2, control 1).
// M1 replaces this with the full matrix from DESIGN 12.4.
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc } from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-spoilnot',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
});

after(async () => {
  await env?.cleanup();
});

describe('M0 deny-all rules', () => {
  it('denies a signed-out read', async () => {
    const db = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, 'config/app')));
  });

  it('denies a signed-in write', async () => {
    const db = env.authenticatedContext('alice').firestore();
    await assertFails(setDoc(doc(db, 'users/alice'), { displayName: 'Alice' }));
  });
});
