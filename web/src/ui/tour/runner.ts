import type { AppPreferences } from '../../core/preferences';
import { Tour, type TourEvent, type TourStepId } from '../../domain/tour/tour';

/**
 * The app tour (DESIGN 5.8), as TourViewModel in the app: starts once per phone in the household app, and again
 * when Settings asks for it (tourDone back to false). Finishing or skipping it is remembered. A store for
 * useSyncExternalStore: [get] is the step on screen, or null when no tour is running.
 */
export class TourRunner {
  private step: TourStepId | null = null;
  private readonly listeners = new Set<() => void>();

  constructor(private readonly preferences: AppPreferences) {}

  /** Follows the preferences until the returned function is called. */
  start(): () => void {
    const check = () => {
      if (!this.preferences.get().tourDone && this.step === null) this.set(Tour.first);
    };
    check();
    const stop = this.preferences.subscribe(check);
    return () => {
      stop();
    };
  }

  readonly get = (): TourStepId | null => this.step;

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  readonly on = (event: TourEvent): void => {
    if (this.step === null) return;
    const next = Tour.next(this.step, event);
    this.set(next);
    if (next === null) this.preferences.set({ tourDone: true });
  };

  private set(step: TourStepId | null) {
    if (step === this.step) return;
    this.step = step;
    this.listeners.forEach((listener) => listener());
  }
}
