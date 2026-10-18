// Restore from an export (docs/RUNBOOK.md). Usage: node dist/restore-main.js <file.json> <householdId> [--apply]
// Without --apply it only lists what it would write. See planRestore for what is and is not restored.
import { readFile } from 'node:fs/promises';
import { getFirestore } from 'firebase-admin/firestore';
import { applyRestore, planRestore, type HouseholdExport } from './backup.ts';
import { fromEnv } from './credentials.ts';

async function main(): Promise<void> {
  const [file, householdId, flag] = process.argv.slice(2);
  if (!file || !householdId) throw new Error('Usage: restore-main.js <file.json> <householdId> [--apply]');
  const plan = planRestore(JSON.parse(await readFile(file, 'utf8')) as HouseholdExport, householdId);
  const counts = new Map<string, number>();
  for (const w of plan.writes) {
    const kind = w.path.split('/').at(-2) ?? '?';
    counts.set(kind, (counts.get(kind) ?? 0) + 1);
  }
  console.log(`${plan.writes.length} document(s) for household ${householdId}: ${JSON.stringify(Object.fromEntries(counts))}`);
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
