// The web app's build (DESIGN 18). `npm run dev` serves it against the Firebase emulators; `npm run build` writes
// dist/ with a service worker that caches the app shell (18.5).
import { execSync } from 'node:child_process';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';
import firebaseJson from '../firebase/firebase.json' with { type: 'json' };

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
      injectManifest: { globPatterns: ['**/*.{js,css,html,woff2,svg,png,webmanifest,json}'] },
    }),
  ],
  // Firebase Auth and Firestore with offline persistence are most of the ~900 kB; the service worker caches it.
  build: {
    target: 'safari16',
    sourcemap: true,
    chunkSizeWarningLimit: 1200,
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
