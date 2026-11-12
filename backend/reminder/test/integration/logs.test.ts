// Failed writes in the prune and the restore against the Firestore emulator (DESIGN 13.4). Workflow logs are public:
// the jobs count failures, and nothing they print holds the service account key or a full household ID. The app
// here signs in with a service account key, as the workflows do (credentials.ts): a throwaway one made for each run.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { generateKeyPairSync } from 'node:crypto';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, beforeEach, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { inspect } from 'node:util';
import { cert, deleteApp, initializeApp, type App } from 'firebase-admin/app';
import { getFirestore, Timestamp, type DocumentReference, type Firestore } from 'firebase-admin/firestore';
import { FORMAT, FORMAT_VERSION, shortId, type HouseholdExport } from '../../src/backup.ts';
import { runJob, STATE_DOC, type Sender } from '../../src/job.ts';

const PROJECT = 'demo-logs-test';
const host = process.env.FIRESTORE_EMULATOR_HOST;
// Shaped like a Firestore auto-ID; logs may show its first four characters only.
const HOUSEHOLD = 'Hh7Q2xLmR9tVb4KcW1zN';
// Over Firestore's 1 MiB document limit: refused with INVALID_ARGUMENT, which is not retried.
const TOO_BIG = 'x'.repeat(1_100_000);
const NOW = new Date('2026-10-12T07:07:00Z');

const { privateKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' },
});
const clientEmail = `throwaway@${PROJECT}.iam.gserviceaccount.com`;
const keyFile = JSON.stringify({ type: 'service_account', project_id: PROJECT, private_key: privateKey, client_email: clientEmail });

/** Fails if the text holds any part of the key or a full household ID (the messages never quote them). */
function assertClean(text: string, household = HOUSEHOLD) {
  const keyLines = privateKey.split('\n').filter((l) => l.length > 0 && !l.startsWith('-----'));
  assert.equal(text.includes('PRIVATE KEY') || text.includes('private_key'), false, 'the key\'s markers');
  assert.equal(keyLines.some((l) => text.includes(l)), false, 'a line of the key');
  assert.equal(text.includes(clientEmail), false, 'the key\'s account');
  assert.equal(text.includes(household), false, 'the full household ID');
}

const sender: Sender = { sendEach: async (messages) => messages.map(() => ({ success: true, deadToken: false })) };

let app: App;
let db: Firestore;

before(() => {
  assert.ok(host, 'Run inside the Firestore emulator (FIRESTORE_EMULATOR_HOST)');
  app = initializeApp({ credential: cert({ projectId: PROJECT, clientEmail, privateKey }) }, 'logs-test');
  db = getFirestore(app);
});

after(async () => {
  await deleteApp(app);
});

beforeEach(async () => {
  await fetch(`http://${host}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`, { method: 'DELETE' });
  await db.doc(`households/${HOUSEHOLD}`).set({ name: 'Home', ownerUid: 'u1' });
});

it('control: the error of a refused bulk write holds the key and the household', async () => {
  const writer = db.bulkWriter();
  const write = writer.set(db.doc(`households/${HOUSEHOLD}/products/p1`), { name: TOO_BIG }).catch((e: unknown) => e);
  await writer.close();
  // Depth 5, as Node prints an uncaught error.
  const dump = inspect(await write, { depth: 5 });
  assert.equal(dump.includes('BEGIN PRIVATE KEY') && dump.includes(HOUSEHOLD), true, 'what Node prints for an unhandled one');
});

/** The same database, except that the bulk writer sends a deletion in HOUSEHOLD as a write the server refuses. */
function refusingDeletes(target: Firestore): Firestore {
  const bind = (object: object, prop: string | symbol) => {
    const value = Reflect.get(object, prop, object);
    return typeof value === 'function' ? value.bind(object) : value;
  };
  return new Proxy(target, {
    get(t, prop) {
      if (prop !== 'bulkWriter') return bind(t, prop);
      return () => new Proxy(t.bulkWriter(), {
        get(writer, p) {
          if (p !== 'delete') return bind(writer, p);
          return (ref: DocumentReference) =>
            ref.path.startsWith(`households/${HOUSEHOLD}/`) ? writer.set(ref, { junk: TOO_BIG }) : writer.delete(ref);
        },
      });
    },
  });
}

it('a prune whose deletions fail counts them, goes on, and prints nothing of the key or the household', async () => {
  const old = Timestamp.fromDate(new Date('2026-09-01T00:00:00Z'));
  await db.doc(`households/${HOUSEHOLD}/activity/a1`).set({ type: 'stock_added', createdAt: old });
  await db.doc(`households/${HOUSEHOLD}/joinRequests/u2`).set({ status: 'pending', requestedAt: old });
  // Pruned after HOUSEHOLD ('H' sorts before 'h').
  await db.doc('households/h2').set({ name: 'Other', ownerUid: 'u3' });
  await db.doc('households/h2/activity/a1').set({ type: 'stock_added', createdAt: old });

  const printed: string[] = [];
  const unhandled: unknown[] = [];
  const onUnhandled = (reason: unknown) => unhandled.push(reason);
  const methods = ['log', 'info', 'warn', 'error', 'debug'] as const;
  const originals = methods.map((m) => console[m]);
  methods.forEach((m) => { console[m] = (...args: unknown[]) => { printed.push(args.map((a) => inspect(a)).join(' ')); }; });
  process.on('unhandledRejection', onUnhandled);
  try {
    const summary = await runJob(refusingDeletes(db), sender, { now: NOW, log: (line) => console.log(line) });
    console.log(JSON.stringify(summary));
    // Let anything left unhandled surface.
    await new Promise((resolve) => setTimeout(resolve, 100));

    assert.equal(summary.pruneFailures, 2);
    assert.equal(summary.prunedActivity, 2);
    assert.equal(summary.prunedJoinRequests, 1);
  } finally {
    process.off('unhandledRejection', onUnhandled);
    methods.forEach((m, i) => { console[m] = originals[i]!; });
  }
  assert.equal(unhandled.length, 0, 'no failed write was left unhandled');
  assertClean(printed.join('\n'));
  assert.equal((await db.doc(`households/${HOUSEHOLD}/activity/a1`).get()).exists, true, 'failed, so still there');
  assert.equal((await db.doc('households/h2/activity/a1').get()).exists, false, 'the next household was pruned');

  // A prune with failures doesn't count: the date stays unset, so the next run, the same day, prunes again.
  assert.equal((await db.doc(STATE_DOC).get()).exists, false);
  const next = await runJob(db, sender, { now: new Date(NOW.getTime() + 20 * 60_000) });
  assert.equal(next.pruned, true);
  assert.equal(next.pruneFailures, 0);
  assert.equal(next.lastPruneDate, '2026-10-12');
  assert.equal((await db.doc(`households/${HOUSEHOLD}/activity/a1`).get()).exists, false, 'pruned at the next run');
});

const restoreMain = fileURLToPath(new URL('../../src/restore-main.ts', import.meta.url));

/** Runs the restore entry point as the workflow does, with the throwaway key, and returns everything it printed. */
async function restore(file: HouseholdExport, target: string, ...flags: string[]) {
  const dir = await mkdtemp(join(tmpdir(), 'spoilnot-restore-'));
  try {
    const path = join(dir, `${HOUSEHOLD}.json`);
    await writeFile(path, JSON.stringify(file));
    return spawnSync(process.execPath, [restoreMain, path, target, '--apply', ...flags], {
      encoding: 'utf8',
      env: { PATH: process.env.PATH, FIRESTORE_EMULATOR_HOST: host, FIREBASE_SERVICE_ACCOUNT_JSON: keyFile },
    });
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

const exported = (products: HouseholdExport['products']): HouseholdExport => ({
  format: FORMAT, formatVersion: FORMAT_VERSION, appVersion: 'test', exportedAt: '2026-10-12T07:17:00.000Z',
  household: { id: HOUSEHOLD, name: 'Home', ownerUid: 'u1' }, members: [], products, stock: [], groceryLists: [], recipes: [],
});

it('a restore whose writes fail counts them, exits 1 and prints only counts', async () => {
  const result = await restore(exported([{ id: 'p1', name: 'Milk' }, { id: 'p2', name: TOO_BIG }]), HOUSEHOLD);
  assert.equal(result.status, 1);
  assert.match(result.stdout, new RegExp(`^Household ${shortId(HOUSEHOLD)}: 2 document\\(s\\)`));
  // The emulator refuses the whole batch the large document is in; Firestore may refuse that document only.
  const [, written, failed] = /restored (\d) document\(s\), (\d) failed/.exec(result.stdout) ?? [];
  assert.ok(Number(failed) >= 1 && Number(written) + Number(failed) === 2, 'counted');
  assert.match(result.stderr, new RegExp(`^${failed} document\\(s\\) were not written\\.`));
  assertClean(result.stdout + result.stderr);
});

it('a restore that stops prints the code only', async () => {
  const missing = HOUSEHOLD.replace('Hh7Q', 'Zz9X');
  const result = await restore(exported([{ id: 'p1', name: 'Milk' }]), missing);
  assert.equal(result.status, 1);
  assert.equal(result.stderr, 'restore failed: no-such-household. Details are not printed in public logs; see docs/RUNBOOK.md.\n');
  assertClean(result.stdout + result.stderr, missing);
});

it('a restore into a household with a different owner stops before writing, unless asked', async () => {
  const other = { ...exported([{ id: 'p1', name: 'Milk' }]), household: { id: HOUSEHOLD, name: 'Home', ownerUid: 'u9' } };
  const refused = await restore(other, HOUSEHOLD);
  assert.equal(refused.status, 1);
  assert.equal(refused.stderr, 'restore failed: different-owner. Details are not printed in public logs; see docs/RUNBOOK.md.\n');
  assert.equal((await db.doc(`households/${HOUSEHOLD}/products/p1`).get()).exists, false, 'nothing written');
  assertClean(refused.stdout + refused.stderr);

  const asked = await restore(other, HOUSEHOLD, '--different-owner');
  assert.equal(asked.status, 0, asked.stderr);
  assert.match(asked.stdout, /Target: a different owner, as asked/);
  assert.match(asked.stdout, /restored 1 document\(s\), 0 failed/);
  assertClean(asked.stdout + asked.stderr);
});
