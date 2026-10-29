// The web app's build (DESIGN 18). `npm run dev` serves it against the Firebase emulators; `npm run build` writes
// dist/ with a service worker that caches the app shell (18.5).
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      injectRegister: false,
      registerType: 'prompt',
      manifest: false, // public/manifest.webmanifest, linked from index.html
      injectManifest: { globPatterns: ['**/*.{js,css,html,woff2,svg,png,webmanifest}'] },
    }),
  ],
  // Firebase Auth and Firestore with offline persistence are most of the ~900 kB; the service worker caches it.
  build: { target: 'safari16', sourcemap: true, chunkSizeWarningLimit: 1200 },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}', 'scripts/**/*.test.ts'],
    setupFiles: ['src/test-setup.ts'],
  },
});
