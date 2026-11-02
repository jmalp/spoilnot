import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { errorCode, failureLine, JobError } from '../src/errors.ts';

// Shaped like a failed Firestore write: a gRPC code, a message naming the document, and the client's settings.
const KEY = '-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----\n';
const HOUSEHOLD = 'Hh7Q2xLmR9tVb4KcW1zN';
const leaky = () => Object.assign(new Error(`No document to update: households/${HOUSEHOLD}/products/p1`), {
  code: 3,
  documentRef: { path: `households/${HOUSEHOLD}/products/p1`, firestore: { _settings: { credentials: { private_key: KEY } } } },
});

describe('error codes', () => {
  it('names gRPC codes and keeps Firebase, Node and job codes', () => {
    assert.equal(errorCode({ code: 7 }), 'PERMISSION_DENIED');
    assert.equal(errorCode({ code: 99 }), '99');
    assert.equal(errorCode({ code: 'messaging/invalid-argument' }), 'messaging/invalid-argument');
    assert.equal(errorCode({ code: 'ENOENT' }), 'ENOENT');
    assert.equal(errorCode({ code: 'ERR_OSSL_EVP_BAD_DECRYPT' }), 'ERR_OSSL_EVP_BAD_DECRYPT');
    assert.equal(errorCode({ code: '403' }), '403');
    assert.equal(errorCode(new JobError('wrong-project', 'This key is for project a, but this run is for b.')), 'wrong-project');
  });

  it('a missing or odd code is "no code", never the value', () => {
    assert.equal(errorCode(new SyntaxError('Unexpected token in "{"private_key": "-----BEGIN"')), 'no code');
    assert.equal(errorCode({ code: `households/${HOUSEHOLD}` }), 'no code');
    assert.equal(errorCode({ code: HOUSEHOLD }), 'no code');
    assert.equal(errorCode({ code: KEY }), 'no code');
    assert.equal(errorCode({ code: 'x'.repeat(65) }), 'no code');
    assert.equal(errorCode(null), 'no code');
    assert.equal(errorCode(undefined), 'no code');
    assert.equal(errorCode('a string'), 'no code');
  });

  it('the failure line holds the job and the code only', () => {
    const line = failureLine('restore', leaky());
    assert.equal(line, 'restore failed: INVALID_ARGUMENT. Details are not printed in public logs; see docs/RUNBOOK.md.');
  });
});

describe('entry points', () => {
  const errors = fileURLToPath(new URL('../src/errors.ts', import.meta.url));
  const leakySource = `Object.assign(new Error('households/${HOUSEHOLD}'), { code: 9, settings: { private_key: ${JSON.stringify(KEY)} } })`;
  const run = (body: string) => spawnSync(process.execPath, [
    '--input-type=module', '-e', `import { runMain } from ${JSON.stringify(errors)};\n${body}`,
  ], { encoding: 'utf8' });

  for (const [name, body] of [
    ['a failed main', `runMain('expiry-reminders', async () => { throw ${leakySource}; });`],
    ['a rejection nobody handled', `runMain('expiry-reminders', async () => { void Promise.reject(${leakySource}); await new Promise((r) => setTimeout(r, 50)); });`],
    ['an exception thrown later', `runMain('expiry-reminders', async () => { setTimeout(() => { throw ${leakySource}; }, 0); });`],
  ] as const) {
    it(`${name} prints one fixed line and exits 1`, () => {
      const result = run(body);
      assert.equal(result.status, 1);
      assert.equal(result.stdout, '');
      assert.equal(result.stderr, 'expiry-reminders failed: FAILED_PRECONDITION. Details are not printed in public logs; see docs/RUNBOOK.md.\n');
    });
  }

  it('a successful main prints nothing of its own and exits 0', () => {
    const result = run(`runMain('nightly-export', async () => { console.log('exported 2 household(s)'); });`);
    assert.equal(result.status, 0);
    assert.equal(result.stdout, 'exported 2 household(s)\n');
    assert.equal(result.stderr, '');
  });
});
