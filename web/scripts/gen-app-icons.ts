// Renders the PNG app icons iOS and the web manifest need (DESIGN 18.5) from public/icons/*.svg, which
// scripts/gen-icons.ts writes from the Android launcher icon. Run by hand after the launcher icon changes:
// `node scripts/gen-app-icons.ts` (needs `npx playwright install webkit`). The PNGs are committed.
import { readFileSync } from 'node:fs';
import { webkit } from '@playwright/test';

const ICONS = new URL('../public/icons/', import.meta.url);
const OUTPUTS: [source: string, out: string, size: number][] = [
  ['icon.svg', 'apple-touch-icon.png', 180],
  ['icon.svg', 'icon-192.png', 192],
  ['icon.svg', 'icon-512.png', 512],
  ['maskable.svg', 'maskable-512.png', 512],
];

const browser = await webkit.launch();
for (const [source, out, size] of OUTPUTS) {
  const page = await browser.newPage({ viewport: { width: size, height: size } });
  const svg = readFileSync(new URL(source, ICONS), 'utf8').replace('<svg ', `<svg width="${size}" height="${size}" `);
  await page.setContent(`<style>html,body{margin:0}</style>${svg}`);
  await page.screenshot({ path: new URL(out, ICONS).pathname, omitBackground: false });
  console.log(`Wrote public/icons/${out}`);
}
await browser.close();
