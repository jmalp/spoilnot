// Restore from an export (docs/RUNBOOK.md).
//   node dist/restore-main.js <file.json> <householdId> [--apply]   restore one household (dry run without --apply)
//   node dist/restore-main.js --list <directory>                     list the export files: household IDs and counts
// See planRestore for what is and is not restored.
import { readdir, readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { getFirestore } from 'firebase-admin/firestore';
import { applyRestore, planRestore, type HouseholdExport } from './backup.ts';
import { fromEnv } from './credentials.ts';

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
    console.log(`  household ${id} (exported ${file.exportedAt}): ${summarize(planRestore(file, id))}`);
  }
  console.log('[list only] Nothing written. Run again with a household ID to restore one.');
}

async function main(): Promise<void> {
  const [file, householdId, flag] = process.argv.slice(2);
  if (file === '--list' && householdId) return list(householdId);
  if (!file || !householdId) throw new Error('Usage: restore-main.js <file.json> <householdId> [--apply] | --list <dir>');
  const plan = planRestore(JSON.parse(await readFile(file, 'utf8')) as HouseholdExport, householdId);
  console.log(`Household ${householdId}: ${summarize(plan)}`);
  if (flag !== '--apply') {
    console.log('[dry run] Nothing written. Run again with --apply to restore.');
    return;
  }
  const credentials = fromEnv('restore');
  if (!credentials) throw new Error('FIREBASE_SERVICE_ACCOUNT_JSON is not set');
  const written = await applyRestore(getFirestore(credentials.app), plan);
  console.log(`project ${credentials.projectId}: restored ${written} document(s)`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
