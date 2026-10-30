/** The commit the web app was built from, or "dev" when served for development (vite.config.ts). */
export const APP_BUILD = __APP_BUILD__;

/** The app version in exports and on this phone's device document, where the Android app writes "1.2.0 (12)". */
export const APP_VERSION = `web ${APP_BUILD}`;
