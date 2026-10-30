// Generates public/licenses.json for Settings > About (DESIGN 5.6, 18.2): every package the web app depends on at
// run time, from package-lock.json, with its license and the license text it ships, like the AboutLibraries plugin
// does for the Android app's runtime dependencies. Type packages (@types/*) are left out: nothing of them ships.
// Run with `npm run gen`; CI checks the output is current.
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const ROOT = new URL('../', import.meta.url);
const OUT = new URL('../public/licenses.json', import.meta.url);

type LockPackage = { version?: string; license?: string; dev?: boolean; devOptional?: boolean };
type Lock = { packages: Record<string, LockPackage> };
type PackageJson = { homepage?: string; repository?: string | { url?: string } };

export type Library = { name: string; version: string; license: string; url: string | null; text: number | null };
export type Licenses = { texts: string[]; libraries: Library[] };

/** Run-time packages in the lock file: not dev-only and not type packages, by name. */
export function productionPackages(lock: Lock): { path: string; name: string; version: string; license: string }[] {
  const seen = new Set<string>();
  return Object.entries(lock.packages)
    .filter(([path, p]) => path.startsWith('node_modules/') && !p.dev && !p.devOptional)
    .map(([path, p]) => ({
      path,
      name: path.slice(path.lastIndexOf('node_modules/') + 'node_modules/'.length),
      version: p.version ?? '',
      license: p.license ?? 'Unknown',
    }))
    .filter((p) => !p.name.startsWith('@types/'))
    .filter((p) => {
      const key = `${p.name}@${p.version}`;
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    })
    .sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
}

/** The package's web page: its homepage, or its repository as an https link. */
export function projectUrl(pkg: PackageJson): string | null {
  const repository = typeof pkg.repository === 'string' ? pkg.repository : pkg.repository?.url;
  const url =
    pkg.homepage ??
    repository
      ?.replace(/^git\+/, '')
      .replace(/^git:\/\//, 'https://')
      .replace(/^ssh:\/\/git@/, 'https://')
      .replace(/^github:/, 'https://github.com/')
      .replace(/\.git$/, '');
  if (url && /^https?:\/\//.test(url)) return url;
  // "owner/repo" means GitHub.
  return url && /^[\w.-]+\/[\w.-]+$/.test(url) ? `https://github.com/${url}` : null;
}

function licenseText(dir: URL): string | null {
  const file = readdirSync(dir).find((f) => /^(licen[cs]e|copying)(\.(md|txt))?$/i.test(f));
  return file ? readFileSync(new URL(file, dir), 'utf8').trim() : null;
}

export function render(): string {
  const lock = JSON.parse(readFileSync(new URL('package-lock.json', ROOT), 'utf8')) as Lock;
  const texts: string[] = [];
  const libraries = productionPackages(lock).map((p): Library => {
    const dir = new URL(`${p.path}/`, ROOT);
    if (!existsSync(dir)) throw new Error(`${p.name} is not installed: run npm ci`);
    const pkg = JSON.parse(readFileSync(new URL('package.json', dir), 'utf8')) as PackageJson;
    const text = licenseText(dir);
    let index: number | null = null;
    if (text !== null) {
      index = texts.indexOf(text);
      if (index < 0) index = texts.push(text) - 1;
    }
    return { name: p.name, version: p.version, license: p.license, url: projectUrl(pkg), text: index };
  });
  // The Apache license names no one in its text, so packages without the file (Firebase's) show the same text.
  const apache = libraries.find((l) => l.license === 'Apache-2.0' && l.text !== null)?.text ?? null;
  for (const library of libraries) if (library.license === 'Apache-2.0' && library.text === null) library.text = apache;
  const licenses: Licenses = { texts, libraries };
  return `${JSON.stringify(licenses, null, 2)}\n`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  writeFileSync(OUT, render());
  console.log(`Wrote ${fileURLToPath(OUT)}`);
}
