// The built app as Firebase Hosting serves it (DESIGN 18.6): `vite preview` with the hosting headers of
// firebase/firebase.json, the service worker registered, against the emulators. Run after `npm run build`.
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: 'tests/preview',
  // Its own folder, so it never clears the browser tests' failures.
  outputDir: 'test-results-preview',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never', outputFolder: 'playwright-report-preview' }]]
    : 'list',
  use: { baseURL: 'http://localhost:4173', trace: 'retain-on-failure' },
  projects: [{ name: 'iphone', use: { ...devices['iPhone 15'], browserName: 'webkit' } }],
  webServer: {
    command: 'npx vite preview --port 4173 --strictPort',
    url: 'http://localhost:4173',
    reuseExistingServer: !process.env.CI,
  },
});
