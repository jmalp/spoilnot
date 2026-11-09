// The free daily quota (DESIGN 6.7, 7.5; core/error/QuotaSignal in the app, after v1 WEB-09): raised when Firestore
// answers "resource-exhausted". The quota resets at midnight Pacific time, so the banner holds until that day ends.
import { BehaviorSubject, concat, defer, distinctUntilChanged, of, switchMap, timer, type Observable } from 'rxjs';
import { systemClock, type Clock, type LocalDate } from './time';

export const QUOTA_ZONE = 'America/Los_Angeles';
/** How often the banner checks the clock while it shows; a timer doesn't count the time a phone sleeps. */
const RECHECK_MS = 60_000;
const DAY_MS = 86_400_000;

const pacific = new Intl.DateTimeFormat('en-CA', {
  timeZone: QUOTA_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

/** The date and time of day in Los Angeles at [at]. */
function pacificParts(at: number): { day: LocalDate; msOfDay: number } {
  const parts = Object.fromEntries(pacific.formatToParts(at).map((p) => [p.type, p.value]));
  const msOfDay = ((Number(parts.hour) * 60 + Number(parts.minute)) * 60 + Number(parts.second)) * 1000 + (at % 1000);
  return { day: `${parts.year}-${parts.month}-${parts.day}`, msOfDay };
}

/** The quota day (Pacific) at [at]. */
export const quotaDay = (at: number): LocalDate => pacificParts(at).day;

export class QuotaSignal {
  private readonly exhaustedOn = new BehaviorSubject<LocalDate | null>(null);

  constructor(private readonly clock: Clock = systemClock) {}

  /** The quota day (Pacific) on which the limit was reached, or null. */
  get exhaustedDay(): LocalDate | null {
    return this.exhaustedOn.value;
  }

  report(): void {
    this.exhaustedOn.next(quotaDay(this.clock.now()));
  }

  isExhausted(): boolean {
    return this.exhaustedOn.value === quotaDay(this.clock.now());
  }

  /**
   * Time left until midnight Pacific. Exact except on the two daylight saving days, where it can be an hour off;
   * [banner$] checks the day again every minute, so the banner still ends at midnight.
   */
  untilResetMs(): number {
    return DAY_MS - pacificParts(this.clock.now()).msOfDay;
  }

  /** Whether the quota banner shows: from a report until midnight Pacific, when it turns itself off. */
  readonly banner$: Observable<boolean> = this.exhaustedOn.pipe(
    switchMap(() => this.check()),
    distinctUntilChanged(),
  );

  /** Tests only: forget the report. */
  reset(): void {
    this.exhaustedOn.next(null);
  }

  private check(): Observable<boolean> {
    if (!this.isExhausted()) return of(false);
    return concat(
      of(true),
      timer(Math.max(1, Math.min(this.untilResetMs(), RECHECK_MS))).pipe(switchMap(() => defer(() => this.check()))),
    );
  }
}

/** The page's one signal, raised by src/data when Firestore answers "resource-exhausted" (QuotaSignal object). */
export const quotaSignal = new QuotaSignal();
