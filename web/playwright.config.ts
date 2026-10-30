// Browser tests (DESIGN 18.7): WebKit with an iPhone profile, against the dev server and the Firebase emulators
// (./scripts/emulators.sh). Screenshots live next to the specs and are compared with the Android ones by eye.
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: 'tests/e2e',
  snapshotPathTemplate: '{testDir}/__screenshots__/{testFilePath}/{arg}{ext}',
  fullyParallel: false,
  // One emulator serves every browser: with more at once it confirms a new household's writes more slowly than the
  // app's listeners retry right after creating one (retryAfterOwnWrites), and screens wait seconds for data.
  workers: 2,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.01 } },
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure' },
  projects: [{ name: 'iphone', use: { ...devices['iPhone 15'], browserName: 'webkit' } }],
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
});
