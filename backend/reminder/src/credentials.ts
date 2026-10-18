// The service account from FIREBASE_SERVICE_ACCOUNT_JSON (a GitHub Actions secret, docs/RUNBOOK.md). EXPECTED_PROJECT,
// when set, must match the key's project, so a key stored under the wrong secret fails loudly.
import { cert, initializeApp, type App, type ServiceAccount } from 'firebase-admin/app';

export interface Credentials {
  app: App;
  projectId: string;
}

/** Null when no key is set (the job then has nothing to do). */
export function fromEnv(name = 'spoilnot'): Credentials | null {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;
  const account = JSON.parse(raw) as ServiceAccount & { project_id?: string };
  const expected = process.env.EXPECTED_PROJECT;
  if (expected && account.project_id !== expected) {
    throw new Error(`This key is for project ${account.project_id ?? '?'}, but this run is for ${expected}.`);
  }
  return { app: initializeApp({ credential: cert(account) }, name), projectId: account.project_id ?? '?' };
}
