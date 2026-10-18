// Entry point for the GitHub Actions workflow (DESIGN 10.4). Needs FIREBASE_SERVICE_ACCOUNT_JSON; DRY_RUN=true
// logs what would be sent and changes nothing. EXPECTED_PROJECT, when set, must match the key's project, so a key
// stored under the wrong secret fails loudly instead of quietly serving the other project.
import { cert, initializeApp, type ServiceAccount } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { runJob, type Sender } from './job.ts';

const DEAD_TOKEN_CODES = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-argument']);

async function main(): Promise<void> {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    console.log('FIREBASE_SERVICE_ACCOUNT_JSON is not set; nothing to do.');
    return;
  }
  const account = JSON.parse(raw) as ServiceAccount & { project_id?: string };
  const expected = process.env.EXPECTED_PROJECT;
  if (expected && account.project_id !== expected) {
    throw new Error(`This key is for project ${account.project_id ?? '?'}, but this run is for ${expected}.`);
  }
  const app = initializeApp({ credential: cert(account) });
  const db = getFirestore(app);
  const messaging = getMessaging(app);
  const dryRun = process.env.DRY_RUN === 'true';

  const sender: Sender = {
    async sendEach(messages) {
      const response = await messaging.sendEach(messages);
      return response.responses.map((r) => ({
        success: r.success,
        deadToken: !r.success && DEAD_TOKEN_CODES.has(r.error?.code ?? ''),
      }));
    },
  };

  const summary = await runJob(db, sender, { now: new Date(), dryRun, log: (line) => console.log(line) });
  console.log(`${dryRun ? '[dry run] ' : ''}project ${account.project_id ?? '?'}: ${JSON.stringify(summary)}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
