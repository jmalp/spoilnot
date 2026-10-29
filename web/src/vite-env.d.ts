/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** The Firebase web config as JSON, set by scripts/deploy-web.sh; absent in development (emulators). */
  readonly VITE_FIREBASE_CONFIG?: string;
  /** Overrides the emulators' host (default: the page's host). */
  readonly VITE_EMULATOR_HOST?: string;
}
