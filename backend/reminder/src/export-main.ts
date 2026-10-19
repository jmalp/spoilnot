// Nightly export (DESIGN 13.4, M7): every household's data to EXPORT_DIR/<householdId>.json, which the workflow
// keeps as a private Actions artifact for 14 days. Reads only; changes nothing.
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { getFirestore } from 'firebase-admin/firestore';
import { exportHousehold, householdIds } from './backup.ts';
import { fromEnv } from './credentials.ts';

async function main(): Promise<void> {
  const credentials = fromEnv('export');
  if (!credentials) {
    console.log('FIREBASE_SERVICE_ACCOUNT_JSON is not set; nothing to do.');
    return;
  }
  const db = getFirestore(credentials.app);
  const dir = process.env.EXPORT_DIR ?? 'exports';
  await mkdir(dir, { recursive: true });
  const now = new Date();
  const ids = await householdIds(db);
  let documents = 0;
  for (const id of ids) {
    const data = await exportHousehold(db, id, now, 'nightly-export');
    documents += data.products.length + data.stock.length + data.recipes.length + (data.stats?.length ?? 0) +
      data.groceryLists.reduce((n, l) => n + 1 + l.items.length, 0);
    await writeFile(join(dir, `${id}.json`), JSON.stringify(data, null, 2) + '\n');
  }
  // Counts only: the log must not contain household data.
  console.log(`project ${credentials.projectId}: exported ${ids.length} household(s), ${documents} document(s)`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
