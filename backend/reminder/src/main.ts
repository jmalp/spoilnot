// Entry point for the GitHub Actions workflow (DESIGN 10.4). Signs in with workload identity or a key (credentials.ts);
// DRY_RUN=true logs what would be sent and changes nothing.
import { getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { fromEnv, NO_CREDENTIAL, signedIn } from './credentials.ts';
import { runMain } from './errors.ts';
import { isDeadToken, runJob, type Sender } from './job.ts';

async function main(): Promise<void> {
  const credentials = fromEnv();
  if (!credentials) {
    console.log(`${NO_CREDENTIAL}; nothing to do.`);
    return;
  }
  const { app, projectId } = credentials;
  console.log(signedIn(credentials));
  const db = getFirestore(app);
  const messaging = getMessaging(app);
  const dryRun = process.env.DRY_RUN === 'true';

  const sender: Sender = {
    async sendEach(messages) {
      const response = await messaging.sendEach(messages);
      return response.responses.map((r) => ({
        success: r.success,
        deadToken: !r.success && isDeadToken(r.error?.code),
      }));
    },
  };

  // The daily prune runs in the first run of each UTC day (jobs/state.lastPruneDate, DESIGN 10.1).
  const summary = await runJob(db, sender, { now: new Date(), dryRun, log: (line) => console.log(line) });
  console.log(`${dryRun ? '[dry run] ' : ''}project ${projectId}: ${JSON.stringify(summary)}`);
  // A failed run makes GitHub send its failure email; everything else in the run still went ahead.
  if (summary.failures > 0) {
    console.error(`${summary.failures} household(s) or user(s) failed; the next run tries them again.`);
    process.exitCode = 1;
  }
  if (summary.pruneFailures > 0) {
    console.error(`${summary.pruneFailures} deletion(s) failed; the next run prunes again.`);
    process.exitCode = 1;
  }
}

runMain('expiry-reminders', main);
