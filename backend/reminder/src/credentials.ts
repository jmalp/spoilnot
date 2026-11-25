// The service account from FIREBASE_SERVICE_ACCOUNT_JSON (a GitHub Actions secret, docs/RUNBOOK.md). EXPECTED_PROJECT,
// when set, must match the key's project, so a key stored under the wrong secret fails loudly.
import { cert, initializeApp, type App, type ServiceAccount } from 'firebase-admin/app';
import { JobError } from './errors.ts';

export interface Credentials {
  app: App;
  projectId: string;
}

/** Null when no key is set (the job then has nothing to do). */
export function fromEnv(name = 'spoilnot'): Credentials | null {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;
  let account: ServiceAccount & { project_id?: string };
  try {
    account = JSON.parse(raw);
  } catch {
    // JSON.parse's message quotes the text around the mistake, here a piece of the key.
    throw new JobError('key-not-json', 'FIREBASE_SERVICE_ACCOUNT_JSON is not a JSON key file');
  }
  const expected = process.env.EXPECTED_PROJECT;
  if (expected && account.project_id !== expected) {
    throw new JobError('wrong-project', `This key is for project ${account.project_id ?? '?'}, but this run is for ${expected}.`);
  }
  return { app: initializeApp({ credential: cert(account) }, name), projectId: account.project_id ?? '?' };
}
