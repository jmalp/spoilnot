// Updates of the web app (DESIGN 18.5): when to look for a new version, and when the household data needs one
// (UpdateGate in the app).
import type { Observable } from 'rxjs';
import type { Clock } from './time';

/** config/app (DESIGN 8.2): read-only for clients, set by the owner in the console. */
export type AppConfig = {
  /** The oldest web build (WEB_BUILD) the household data still supports; 0 when not set. */
  minWebBuild: number;
};

export interface AppConfigRepository {
  /** The document, or null when it is missing or can't be read: neither ever blocks the app. */
  observe(): Observable<AppConfig | null>;
}

/** True when this build is older than the oldest one the household data still supports. */
export function updateRequired(config: AppConfig | null, build: number): boolean {
  return config !== null && build < config.minWebBuild;
}

/** How often the app asks the browser for a new version while it stays open (a Home Screen app can for days). */
export const UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000;

/**
 * When to ask the browser for a new service worker. It looks by itself only when the app is loaded, so the app also
 * asks when it becomes visible, at most once an hour; while an update is required and none is waiting, every time.
 */
export class UpdateChecks {
  private last: number | null = null;

  constructor(
    private readonly clock: Clock,
    private readonly intervalMs = UPDATE_CHECK_INTERVAL_MS,
  ) {}

  /** The browser has just looked (the service worker was registered, or the app asked). */
  checked(): void {
    this.last = this.clock.now();
  }

  /** Whether to look now; a yes counts as a check. */
  shouldCheck({ required, waiting }: { required: boolean; waiting: boolean }): boolean {
    if (waiting) return false;
    const now = this.clock.now();
    // A clock set back counts as an hour gone by.
    const due = this.last === null || now - this.last >= this.intervalMs || now < this.last;
    if (!required && !due) return false;
    this.last = now;
    return true;
  }
}

/** The Update snackbar: 'offer' for 10 seconds, 'require' until Update is tapped. */
export type UpdatePrompt = 'offer' | 'require';

/**
 * What to show for the version that is waiting, given what was already shown for it: a new version is offered once,
 * and required once more when config/app asks for it after the offer.
 */
export function nextPrompt(waiting: boolean, required: boolean, shown: UpdatePrompt | null): UpdatePrompt | null {
  if (!waiting) return null;
  if (required) return shown === 'require' ? null : 'require';
  return shown === null ? 'offer' : null;
}
