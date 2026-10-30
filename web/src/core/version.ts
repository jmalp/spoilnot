/** The commit the web app was built from, or "dev" when served for development (vite.config.ts). */
export const APP_BUILD = __APP_BUILD__;

/** The app version written in exports, where the Android app writes "1.2.0 (12)". */
export const EXPORT_APP_VERSION = `web ${APP_BUILD}`;
