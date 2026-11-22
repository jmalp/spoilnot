// The web app's build (DESIGN 18). `npm run dev` serves it against the Firebase emulators; `npm run build` writes
// dist/ with a service worker that caches the app shell (18.5).
import { execSync } from 'node:child_process';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';
import firebaseJson from '../firebase/firebase.json' with { type: 'json' };
import { PRECACHE_IGNORES } from './src/core/runtimeCache.ts';

/**
 * The hosting headers of firebase/firebase.json for `vite preview`, so the built app is tested under the same
 * Content Security Policy (playwright.preview.config.ts). Only here it may reach the emulators over http.
 */
function previewHeaders(): Record<string, string> {
  const headers = Object.fromEntries(firebaseJson.hosting.headers[0]!.headers.map((h) => [h.key, h.value]));
  const emulators = ['localhost', '127.0.0.1'].flatMap((host) => [`http://${host}:9099`, `http://${host}:8080`]);
  headers['Content-Security-Policy'] = headers['Content-Security-Policy']!.replace(
    "connect-src 'self'",
    `connect-src 'self' ${emulators.join(' ')}`,
  ).replace('; upgrade-insecure-requests', '');
  return headers;
}

/** The build shown in Settings > About and written in exports: the commit, or "dev" when serving. */
function buildId(command: string): string {
  if (command !== 'build') return 'dev';
  try {
    return execSync('git rev-parse --short HEAD').toString().trim();
  } catch {
    return 'unknown';
  }
}

/**
 * A chunk group's test: modules of the packages whose path in node_modules `name` matches, except Firebase Messaging,
 * which data/notifications.ts loads only when someone turns reminders on, and styles (the fonts' @font-face rules),
 * which stay in the app's one stylesheet so the fonts are found as early as before.
 */
function thirdParty(name: RegExp): (id: string) => boolean {
  const onDemand = /node_modules[\\/](@firebase[\\/](messaging|installations)|firebase[\\/]messaging)[\\/]/;
  return (id) => {
    const match = /.*node_modules[\\/](.*)$/.exec(id); // the path inside the innermost node_modules
    return match !== null && name.test(match[1]!) && !onDemand.test(id) && !id.endsWith('.css');
  };
}

export default defineConfig(({ command }) => ({
  define: { __APP_BUILD__: JSON.stringify(buildId(command)) },
  plugins: [
    react(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      injectRegister: false,
      registerType: 'prompt',
      manifest: false, // public/manifest.webmanifest, linked from index.html
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,woff2,svg,png,webmanifest,json}'],
        // Cached when first used instead (src/core/runtimeCache.ts, after v1 review EFF-06).
        globIgnores: [...PRECACHE_IGNORES],
      },
    }),
  ],
  build: {
    target: 'safari16',
    sourcemap: true,
    // Third-party code in chunks of its own (after v1, review EFF-06, DECISIONS 2026-11-22): a release that changes
    // only the app's code leaves them cached, so a phone downloads the app chunk again, not Firebase and React.
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            { name: 'firebase', test: thirdParty(/^(@firebase|firebase|re2js|idb|tslib)[\\/]/), priority: 3 },
            { name: 'react', test: thirdParty(/^(react|react-dom|scheduler)[\\/]/), priority: 2 },
            { name: 'vendor', test: thirdParty(/./), priority: 1 },
          ],
        },
      },
    },
    // The largest chunk, Firebase (Auth, Firestore with offline persistence and its re2js), is about 660 kB; the
    // limit sits just above it, so a library update that grows it by a seventh warns in every build
    // (DECISIONS 2026-11-22).
    chunkSizeWarningLimit: 750,
    // Fonts stay files: the Content Security Policy allows fonts from the site only, not data: URLs.
    assetsInlineLimit: (file) => (file.endsWith('.woff2') ? false : undefined),
  },
  preview: { headers: previewHeaders() },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}', 'scripts/**/*.test.ts'],
    setupFiles: ['src/test-setup.ts'],
  },
}));
