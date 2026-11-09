/** The commit the web app was built from, or "dev" when served for development (vite.config.ts). */
export const APP_BUILD = __APP_BUILD__;

/**
 * The web app's build number, compared with `config/app.minWebBuild` (DESIGN 18.5), as the Android app's
 * `versionCode` is with `minSupportedBuild`. Raise it by one in a change that older web builds must not keep running
 * (one that changes what the app writes or what stored data means); after that build is deployed the owner sets
 * `minWebBuild` to it (RUNBOOK).
 */
export const WEB_BUILD = 2;

/** The app version in exports and on this phone's device document, where the Android app writes "1.2.0 (12)". */
export const APP_VERSION = `web ${APP_BUILD}`;
