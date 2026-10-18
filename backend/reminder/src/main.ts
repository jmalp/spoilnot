// Entry point for the GitHub Actions workflow (DESIGN 10.4). Needs FIREBASE_SERVICE_ACCOUNT_JSON; DRY_RUN=true
// logs what would be sent and changes nothing. See credentials.ts for EXPECTED_PROJECT.
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { fromEnv } from './credentials.ts';
import { runJob, type Sender } from './job.ts';

const PRUNE_HOUR_UTC = 7;
const DEAD_TOKEN_CODES = new Set(['messaging/registration-token-not-registered', 'messaging/invalid-argument']);

async function main(): Promise<void> {
  const credentials = fromEnv();
  if (!credentials) {
    console.log('FIREBASE_SERVICE_ACCOUNT_JSON is not set; nothing to do.');
    return;
  }
  const { app, projectId } = credentials;
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

  const now = new Date();
  // Housekeeping costs reads for every household, so it runs once a day, not hourly (M7 quota simulation).
  const prune = now.getUTCHours() === PRUNE_HOUR_UTC;
  const summary = await runJob(db, sender, { now, dryRun, prune, log: (line) => console.log(line) });
  console.log(`${dryRun ? '[dry run] ' : ''}project ${projectId}: ${JSON.stringify(summary)}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
