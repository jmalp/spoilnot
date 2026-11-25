// How a job signs in to Firebase (docs/RUNBOOK.md, "How the jobs get their keys"; DESIGN 12.2 control 8).
//
// - Workload identity (no key): GCP_WORKLOAD_IDENTITY_PROVIDER and GCP_SERVICE_ACCOUNT are set (repository
//   variables) and the workflow has `id-token: write`. GitHub's OIDC token for this run is exchanged at Google's
//   Security Token Service, through the pool's provider, for a short-lived token of the job's own service account.
//   This writes the same `external_account` credential file that google-github-actions/auth writes, so no outside
//   action is needed, and signs in with application default credentials.
// - A key: FIREBASE_SERVICE_ACCOUNT_JSON (a repository secret), used only when workload identity isn't set up.
//
// EXPECTED_PROJECT, when set, must match the service account's or key's project, so a credential stored for the wrong
// project fails loudly.
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { applicationDefault, cert, initializeApp, type App, type ServiceAccount } from 'firebase-admin/app';
import { JobError } from './errors.ts';

export type SignIn = 'workload identity' | 'key';

export interface Credentials {
  app: App;
  projectId: string;
  /** Which way the job signed in: printed by the entry points, so a dry run shows it. */
  signIn: SignIn;
  /** The service account's name, the part of its e-mail address before the @ (such as `export-job`), or '?'. */
  account: string;
}

/** How the entry points say they signed in: no e-mail address, key or project number, so it can go in public logs. */
export function signedIn(credentials: Credentials): string {
  return `Signed in with ${credentials.signIn} as ${credentials.account}.`;
}

const accountName = (email: string | undefined) => /^([a-z][a-z0-9-]{4,28}[a-z0-9])@/.exec(email ?? '')?.[1] ?? '?';

/** Google's endpoints for the token exchange; tests point them at a local server. */
export interface Endpoints {
  sts: string;
  iamCredentials: string;
}

export const GOOGLE: Endpoints = {
  sts: 'https://sts.googleapis.com/v1/token',
  iamCredentials: 'https://iamcredentials.googleapis.com/v1',
};

const PROVIDER = /^projects\/\d+\/locations\/global\/workloadIdentityPools\/[a-z0-9-]{4,32}\/providers\/[a-z0-9-]{4,32}$/;
const SERVICE_ACCOUNT = /^[a-z][a-z0-9-]{4,28}[a-z0-9]@([a-z][a-z0-9-]{4,28}[a-z0-9])\.iam\.gserviceaccount\.com$/;

/**
 * The `external_account` credential configuration for GitHub's OIDC token (Google's "Configure Workload Identity
 * Federation with deployment pipelines"). The token is fetched from GitHub's endpoint when needed, with the run's
 * request token, so a long run gets a fresh one.
 */
export function workloadIdentityConfig(
  provider: string,
  serviceAccount: string,
  idTokenRequestUrl: string,
  idTokenRequestToken: string,
  endpoints: Endpoints = GOOGLE,
) {
  const audience = `https://iam.googleapis.com/${provider}`;
  return {
    type: 'external_account',
    audience: `//iam.googleapis.com/${provider}`,
    subject_token_type: 'urn:ietf:params:oauth:token-type:jwt',
    token_url: endpoints.sts,
    service_account_impersonation_url:
      `${endpoints.iamCredentials}/projects/-/serviceAccounts/${serviceAccount}:generateAccessToken`,
    credential_source: {
      url: `${idTokenRequestUrl}${idTokenRequestUrl.includes('?') ? '&' : '?'}audience=${encodeURIComponent(audience)}`,
      headers: { Authorization: `Bearer ${idTokenRequestToken}` },
      format: { type: 'json', subject_token_field_name: 'value' },
    },
  };
}

function checkProject(projectId: string | undefined, what: string, env: NodeJS.ProcessEnv): void {
  const expected = env.EXPECTED_PROJECT;
  if (expected && projectId !== expected) {
    throw new JobError('wrong-project', `This ${what} is for project ${projectId ?? '?'}, but this run is for ${expected}.`);
  }
}

function fromWorkloadIdentity(name: string, env: NodeJS.ProcessEnv, endpoints: Endpoints): Credentials {
  const provider = env.GCP_WORKLOAD_IDENTITY_PROVIDER ?? '';
  const serviceAccount = env.GCP_SERVICE_ACCOUNT ?? '';
  if (!PROVIDER.test(provider)) {
    throw new JobError('bad-workload-identity', 'GCP_WORKLOAD_IDENTITY_PROVIDER is not projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/<provider>');
  }
  const projectId = SERVICE_ACCOUNT.exec(serviceAccount)?.[1];
  if (!projectId) {
    throw new JobError('bad-workload-identity', 'GCP_SERVICE_ACCOUNT is not a service account e-mail address');
  }
  checkProject(projectId, 'service account', env);
  const url = env.ACTIONS_ID_TOKEN_REQUEST_URL;
  const token = env.ACTIONS_ID_TOKEN_REQUEST_TOKEN;
  if (!url || !token) {
    throw new JobError('no-id-token', 'GitHub gave this step no OIDC token: the workflow needs `permissions: id-token: write`');
  }
  // The file holds the run's token request token (valid while the job runs), so only this user may read it, and it
  // is removed when the job ends. The runner's temp folder is emptied after every job anyway.
  const dir = mkdtempSync(join(env.RUNNER_TEMP || tmpdir(), 'spoilnot-wif-'));
  process.on('exit', () => rmSync(dir, { recursive: true, force: true }));
  const file = join(dir, 'credentials.json');
  writeFileSync(file, JSON.stringify(workloadIdentityConfig(provider, serviceAccount, url, token, endpoints)), { mode: 0o600 });
  // Application default credentials read both. With no project set, the auth library would ask Cloud Resource
  // Manager for it, which the job's service account may not do.
  process.env.GOOGLE_APPLICATION_CREDENTIALS = file;
  process.env.GOOGLE_CLOUD_PROJECT = projectId;
  return { app: initializeApp({ credential: applicationDefault(), projectId }, name), projectId, signIn: 'workload identity', account: accountName(serviceAccount) };
}

function fromKey(name: string, raw: string, env: NodeJS.ProcessEnv): Credentials {
  let account: ServiceAccount & { project_id?: string; client_email?: string };
  try {
    account = JSON.parse(raw);
  } catch {
    // JSON.parse's message quotes the text around the mistake, here a piece of the key.
    throw new JobError('key-not-json', 'FIREBASE_SERVICE_ACCOUNT_JSON is not a JSON key file');
  }
  checkProject(account.project_id, 'key', env);
  return { app: initializeApp({ credential: cert(account) }, name), projectId: account.project_id ?? '?', signIn: 'key',
    account: accountName(account.client_email) };
}

/**
 * Workload identity when both of its variables are set, else the key, else null (the job then has nothing to do).
 */
export function fromEnv(name = 'spoilnot', env: NodeJS.ProcessEnv = process.env, endpoints: Endpoints = GOOGLE): Credentials | null {
  if (env.GCP_WORKLOAD_IDENTITY_PROVIDER && env.GCP_SERVICE_ACCOUNT) return fromWorkloadIdentity(name, env, endpoints);
  const raw = env.FIREBASE_SERVICE_ACCOUNT_JSON;
  return raw ? fromKey(name, raw, env) : null;
}

/** What the entry points print when there is no credential. */
export const NO_CREDENTIAL = 'No credential is set (workload identity variables or FIREBASE_SERVICE_ACCOUNT_JSON)';
