/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The Firebase web config as JSON, set by scripts/deploy-web.sh; absent in development (emulators). */
  readonly VITE_FIREBASE_CONFIG?: string;
  /** The public VAPID key of the project's Web Push certificate (DESIGN 18.4); without it there is no web push. */
  readonly VITE_FIREBASE_VAPID_KEY?: string;
  /** Overrides the emulators' host (default: the page's host). */
  readonly VITE_EMULATOR_HOST?: string;
}

/** The commit the app was built from, or "dev" (vite.config.ts). */
declare const __APP_BUILD__: string;
