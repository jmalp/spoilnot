// How the jobs sign in (credentials.ts, DESIGN 12.2 control 8): workload identity when its variables are set, else the
// key. The workload identity chain runs against a local server standing in for GitHub's OIDC endpoint, Google's
// Security Token Service and IAM Credentials, through the real Firebase Admin SDK and Firestore client, so nothing
// here talks to Google. The workflows' side (who gets the key, installs without the OIDC token) is checked below.
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { createServer, type IncomingMessage } from 'node:http';
import type { AddressInfo } from 'node:net';
import { after, before, describe, it } from 'node:test';
import { getFirestore } from 'firebase-admin/firestore';
import { fromEnv, signedIn, workloadIdentityConfig, type Endpoints } from '../src/credentials.ts';
import { errorCode } from '../src/errors.ts';

const PROVIDER = 'projects/123456789012/locations/global/workloadIdentityPools/github/providers/spoilnot';
const ACCOUNT = 'reminder-job@spoilnot-dev.iam.gserviceaccount.com';

let count = 0;
const name = () => `credentials-test-${++count}`;

/** Runs [body] with these variables set (and the others the code reads cleared), then puts process.env back. */
async function withEnv<T>(vars: Record<string, string>, body: () => Promise<T> | T): Promise<T> {
  const keys = ['GOOGLE_APPLICATION_CREDENTIALS', 'GOOGLE_CLOUD_PROJECT', 'FIRESTORE_EMULATOR_HOST', ...Object.keys(vars)];
  const saved = Object.fromEntries(keys.map((k) => [k, process.env[k]]));
  for (const k of keys) delete process.env[k];
  Object.assign(process.env, vars);
  try {
    return await body();
  } finally {
    for (const [k, v] of Object.entries(saved)) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  }
}

const throws = (fn: () => unknown, code: string) =>
  assert.throws(fn, (e: unknown) => errorCode(e) === code, `throws ${code}`);

describe('workload identity configuration', () => {
  it('is the external_account file Google documents for GitHub', () => {
    const config = workloadIdentityConfig(PROVIDER, ACCOUNT, 'https://gh.example/token?api-version=2.0', 'req');
    assert.deepEqual(config, {
      type: 'external_account',
      audience: `//iam.googleapis.com/${PROVIDER}`,
      subject_token_type: 'urn:ietf:params:oauth:token-type:jwt',
      token_url: 'https://sts.googleapis.com/v1/token',
      service_account_impersonation_url:
        `https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/${ACCOUNT}:generateAccessToken`,
      credential_source: {
        // The provider's default audience: its full name after https://iam.googleapis.com/.
        url: `https://gh.example/token?api-version=2.0&audience=${encodeURIComponent(`https://iam.googleapis.com/${PROVIDER}`)}`,
        headers: { Authorization: 'Bearer req' },
        format: { type: 'json', subject_token_field_name: 'value' },
      },
    });
  });

  it('refuses a malformed provider or account, another project, and a workflow without id-token: write', () => {
    const ok = {
      GCP_WORKLOAD_IDENTITY_PROVIDER: PROVIDER, GCP_SERVICE_ACCOUNT: ACCOUNT,
      ACTIONS_ID_TOKEN_REQUEST_URL: 'https://gh.example/token', ACTIONS_ID_TOKEN_REQUEST_TOKEN: 'req',
    };
    throws(() => fromEnv(name(), { ...ok, GCP_WORKLOAD_IDENTITY_PROVIDER: 'github/spoilnot' }), 'bad-workload-identity');
    throws(() => fromEnv(name(), { ...ok, GCP_WORKLOAD_IDENTITY_PROVIDER: `${PROVIDER}/x` }), 'bad-workload-identity');
    throws(() => fromEnv(name(), { ...ok, GCP_SERVICE_ACCOUNT: 'reminder-job@gmail.com' }), 'bad-workload-identity');
    throws(() => fromEnv(name(), { ...ok, EXPECTED_PROJECT: 'spoilnot-prod' }), 'wrong-project');
    throws(() => fromEnv(name(), { ...ok, ACTIONS_ID_TOKEN_REQUEST_TOKEN: '' }), 'no-id-token');
  });

  it('is used only when both variables are set, and wins over the key', async () => {
    assert.equal(fromEnv(name(), {}), null);
    assert.equal(fromEnv(name(), { GCP_WORKLOAD_IDENTITY_PROVIDER: PROVIDER }), null, 'half set: no credential');
    const key = keyJson('spoilnot-dev');
    assert.equal(fromEnv(name(), { GCP_WORKLOAD_IDENTITY_PROVIDER: PROVIDER, FIREBASE_SERVICE_ACCOUNT_JSON: key })?.signIn, 'key');
    await withEnv({}, () => {
      const both = fromEnv(name(), {
        GCP_WORKLOAD_IDENTITY_PROVIDER: PROVIDER, GCP_SERVICE_ACCOUNT: ACCOUNT, FIREBASE_SERVICE_ACCOUNT_JSON: key,
        ACTIONS_ID_TOKEN_REQUEST_URL: 'https://gh.example/token', ACTIONS_ID_TOKEN_REQUEST_TOKEN: 'req',
      });
      assert.equal(both?.signIn, 'workload identity');
      assert.equal(both?.projectId, 'spoilnot-dev');
      assert.equal(signedIn(both!), 'Signed in with workload identity as reminder-job.');
    });
  });
});

function keyJson(project: string): string {
  const { privateKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048, privateKeyEncoding: { type: 'pkcs8', format: 'pem' }, publicKeyEncoding: { type: 'spki', format: 'pem' },
  });
  return JSON.stringify({
    type: 'service_account', project_id: project, private_key: privateKey,
    client_email: `reminder-job@${project}.iam.gserviceaccount.com`,
  });
}

describe('the key', () => {
  it('still signs in, and refuses one for another project or one that is not JSON', () => {
    const credentials = fromEnv(name(), { FIREBASE_SERVICE_ACCOUNT_JSON: keyJson('spoilnot-prod'), EXPECTED_PROJECT: 'spoilnot-prod' });
    assert.equal(credentials?.signIn, 'key');
    assert.equal(credentials?.projectId, 'spoilnot-prod');
    assert.equal(signedIn(credentials!), 'Signed in with key as reminder-job.', 'no e-mail address, no project');
    throws(() => fromEnv(name(), { FIREBASE_SERVICE_ACCOUNT_JSON: keyJson('spoilnot-dev'), EXPECTED_PROJECT: 'spoilnot-prod' }), 'wrong-project');
    throws(() => fromEnv(name(), { FIREBASE_SERVICE_ACCOUNT_JSON: '{"private_key": "-----BEGIN' }), 'key-not-json');
  });
});

describe('the workload identity chain, against a stand-in for GitHub and Google', () => {
  interface Seen { method: string; url: string; auth?: string; body: string }
  const seen: Seen[] = [];
  const server = createServer((req: IncomingMessage, res) => {
    let body = '';
    req.on('data', (chunk) => (body += chunk));
    req.on('end', () => {
      const url = req.url ?? '';
      seen.push({ method: req.method ?? '', url, auth: req.headers.authorization, body });
      res.setHeader('content-type', 'application/json');
      if (url.startsWith('/github/token')) return res.end(JSON.stringify({ value: 'github-oidc-jwt' }));
      if (url === '/sts') {
        return res.end(JSON.stringify({
          access_token: 'federated-token', issued_token_type: 'urn:ietf:params:oauth:token-type:access_token',
          token_type: 'Bearer', expires_in: 3600,
        }));
      }
      if (url.endsWith(':generateAccessToken')) {
        return res.end(JSON.stringify({ accessToken: 'service-account-token', expireTime: new Date(Date.now() + 3_600_000).toISOString() }));
      }
      // Firestore's REST batchGet: the document doesn't exist.
      res.end(JSON.stringify([{ missing: 'projects/spoilnot-dev/databases/(default)/documents/households/h1', readTime: new Date().toISOString() }]));
    });
  });
  let base = '';
  before(async () => {
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  after(() => server.close());

  it('gives the Admin SDK and Firestore the service account token, and nothing more is asked of Google', async () => {
    const endpoints: Endpoints = { sts: `http://${base}/sts`, iamCredentials: `http://${base}/iam` };
    await withEnv({}, async () => {
      const credentials = fromEnv(name(), {
        GCP_WORKLOAD_IDENTITY_PROVIDER: PROVIDER, GCP_SERVICE_ACCOUNT: ACCOUNT, EXPECTED_PROJECT: 'spoilnot-dev',
        ACTIONS_ID_TOKEN_REQUEST_URL: `http://${base}/github/token?api-version=2.0`, ACTIONS_ID_TOKEN_REQUEST_TOKEN: 'request-token',
        RUNNER_TEMP: process.env.TMPDIR ?? '/tmp',
      }, endpoints);
      assert.ok(credentials);
      assert.equal(process.env.GOOGLE_CLOUD_PROJECT, 'spoilnot-dev', 'so the library never asks Cloud Resource Manager');

      const file = process.env.GOOGLE_APPLICATION_CREDENTIALS ?? '';
      assert.equal(statSync(file).mode & 0o777, 0o600, 'only this user may read the file');
      assert.equal(JSON.parse(readFileSync(file, 'utf8')).type, 'external_account');

      // The Admin SDK's own credential: what FCM sends with.
      const token = await credentials.app.options.credential!.getAccessToken();
      assert.equal(token.access_token, 'service-account-token');

      // Firestore signs in on its own, with application default credentials.
      const db = getFirestore(credentials.app);
      db.settings({ host: base, ssl: false, preferRest: true });
      assert.equal((await db.doc('households/h1').get()).exists, false);
      assert.ok(existsSync(file));
    });

    const oidc = seen.filter((s) => s.url.startsWith('/github/token'));
    assert.ok(oidc.length >= 1);
    for (const s of oidc) {
      assert.equal(s.auth, 'Bearer request-token');
      assert.equal(new URL(s.url, 'http://x').searchParams.get('audience'), `https://iam.googleapis.com/${PROVIDER}`);
    }
    const sts = seen.filter((s) => s.url === '/sts');
    assert.ok(sts.length >= 1);
    for (const s of sts) {
      const form = new URLSearchParams(s.body);
      assert.equal(form.get('grant_type'), 'urn:ietf:params:oauth:grant-type:token-exchange');
      assert.equal(form.get('audience'), `//iam.googleapis.com/${PROVIDER}`);
      assert.equal(form.get('subject_token'), 'github-oidc-jwt');
    }
    const impersonate = seen.filter((s) => s.url.endsWith(':generateAccessToken'));
    assert.ok(impersonate.length >= 1);
    for (const s of impersonate) {
      assert.equal(s.url, `/iam/projects/-/serviceAccounts/${ACCOUNT}:generateAccessToken`);
      assert.equal(s.auth, 'Bearer federated-token');
    }
    const reads = seen.filter((s) => s.url.includes('documents:batchGet'));
    assert.equal(reads.length, 1);
    assert.equal(reads[0].auth, 'Bearer service-account-token');
    assert.equal(seen.length, oidc.length + sts.length + impersonate.length + reads.length, 'no other request');
  });
});

describe('the workflows', () => {
  const read = (file: string) => readFileSync(new URL(`../../../.github/workflows/${file}`, import.meta.url), 'utf8');
  const jobs = { 'reminders.yml': 'GCP_SA_REMINDERS', 'nightly-export.yml': 'GCP_SA_EXPORT', 'restore.yml': 'GCP_SA_RESTORE' };

  for (const [file, variable] of Object.entries(jobs)) {
    it(`${file}: may ask for an OIDC token, installs without it, and gets a key only without workload identity`, () => {
      const yml = read(file);
      assert.match(yml, /^ {2}id-token: write/m);
      const installs = yml.split('\n').filter((l) => l.includes('npm ci'));
      assert.ok(installs.length >= 1);
      for (const line of installs) {
        assert.match(line, /unset ACTIONS_ID_TOKEN_REQUEST_URL ACTIONS_ID_TOKEN_REQUEST_TOKEN && npm ci --ignore-scripts/);
      }
      assert.ok(yml.includes(variable), `names ${variable}`);
      const account = String.raw`vars(?:\.GCP_SA_\w+|\[matrix\.service_account\])`;
      assert.match(yml, new RegExp(String.raw`GCP_SERVICE_ACCOUNT: \$\{\{ ${account} \}\}`));
      const keys = yml.split('\n').filter((l) => /^\s*FIREBASE_SERVICE_ACCOUNT_JSON:/.test(l));
      assert.equal(keys.length, 1);
      assert.match(keys[0], new RegExp(String.raw`\$\{\{ \(!vars\.GCP_WORKLOAD_IDENTITY_PROVIDER \|\| !${account}\) && `));
    });
  }
});
