// Restore from an export (docs/RUNBOOK.md).
//   node dist/restore-main.js <file.json> <householdId> [--apply] [--different-owner]
//       restore one household (a dry run without --apply). With a key it first checks the target exists and has the
//       export's owner; --different-owner allows another owner.
//   node dist/restore-main.js --list <directory>     list the export files: short IDs and counts
// Workflow logs are public with the repository, so household IDs are only ever printed shortened (shortId).
// See planRestore for what is and is not restored.
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { getFirestore } from 'firebase-admin/firestore';
import { applyRestore, checkTarget, planRestore, shortId, type HouseholdExport } from './backup.ts';
import { fromEnv } from './credentials.ts';
import { JobError, runMain } from './errors.ts';

/** Documents a restore would write, by collection: counts only, never contents. */
function summarize(plan: ReturnType<typeof planRestore>): string {
  const counts = new Map<string, number>();
  for (const w of plan.writes) {
    const kind = w.path.split('/').at(-2) ?? '?';
    counts.set(kind, (counts.get(kind) ?? 0) + 1);
  }
  return `${plan.writes.length} document(s) ${JSON.stringify(Object.fromEntries(counts))}`;
}

async function list(dir: string): Promise<void> {
  const files = (await readdir(dir)).filter((f) => f.endsWith('.json')).sort();
  console.log(`${files.length} export file(s) in this run:`);
  for (const f of files) {
    const id = f.slice(0, -'.json'.length);
    const file = JSON.parse(await readFile(join(dir, f), 'utf8')) as HouseholdExport;
    console.log(`  household ${shortId(id)} (exported ${file.exportedAt}): ${summarize(planRestore(file, id))}`);
  }
  console.log('[list only] Nothing written. Run again with a household ID to restore one.');
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const flags = new Set(args.filter((a) => a.startsWith('--') && a !== '--list'));
  const [file, householdId] = args.filter((a) => !flags.has(a));
  const unknown = [...flags].filter((f) => f !== '--apply' && f !== '--different-owner');
  if (file === '--list' && householdId) return list(householdId);
  if (!file || !householdId || unknown.length > 0) {
    console.log('Usage: restore-main.js <file.json> <householdId> [--apply] [--different-owner] | --list <dir>');
    throw new JobError('usage', 'Missing or unknown arguments');
  }
  const apply = flags.has('--apply');
  const options = { differentOwner: flags.has('--different-owner') };
  const plan = planRestore(JSON.parse(await readFile(file, 'utf8')) as HouseholdExport, householdId);
  console.log(`Household ${shortId(householdId)}: ${summarize(plan)}`);
  const credentials = fromEnv('restore');
  if (!credentials) {
    if (!apply) {
      console.log('[dry run] Nothing written; the target was not checked (no key). Run again with --apply to restore.');
      return;
    }
    throw new JobError('no-key', 'FIREBASE_SERVICE_ACCOUNT_JSON is not set');
  }
  const db = getFirestore(credentials.app);
  // Refuses a missing household, or one with another owner unless --different-owner (DESIGN 13.4).
  const sameOwner = await checkTarget(db, plan, options);
  console.log(sameOwner ? 'Target: same owner as the export.' : 'Target: a different owner, as asked (--different-owner).');
  if (!apply) {
    console.log('[dry run] Nothing written. Run again with --apply to restore.');
    return;
  }
  const { written, failed } = await applyRestore(db, plan, options);
  console.log(`project ${credentials.projectId}: restored ${written} document(s), ${failed} failed`);
  if (failed > 0) {
    console.error(`${failed} document(s) were not written. Running the restore again writes the same documents.`);
    process.exitCode = 1;
  }
}

runMain('restore', main);
