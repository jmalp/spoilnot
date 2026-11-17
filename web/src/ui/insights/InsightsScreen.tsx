import { useMemo, useState } from 'react';
import { combineLatest, distinctUntilChanged, map, of, switchMap, type Observable } from 'rxjs';
import { useObservable, useToday } from '../../core/hooks';
import { retryLoading } from '../../domain/load';
import { str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import {
  Insights,
  MonthTotals,
  type InsightsSummary,
  type MonthStats,
  type StatsRepository,
} from '../../domain/insights/insights';
import { DISCARD_REASONS } from '../../domain/pantry/models';
import type { LocalDate, YearMonth } from '../../domain/time';
import { AccentCard, Icon, LeafBadge, PlainCard, Progress } from '../kit';
import { reasonLabel } from '../pantry/labels';
import { DetailScaffold } from '../Scaffold';
import { LegendItem, MonthBars, ON_PRIMARY_RING, OnPrimaryLegend, ReasonBars, UsageRing } from './Charts';
import { monthName, moodText, percent, streakParts } from './labels';
import './insights.css';

/**
 * The household's summary from [from] on, recomputed when the day changes (insightsFlow). Home asks for the current
 * month only (one document per open); the Insights screen for the last six months.
 */
export function insights$(
  stats: StatsRepository,
  householdId: string,
  today$: Observable<LocalDate>,
  from: (today: LocalDate) => YearMonth,
): Observable<InsightsSummary> {
  const first$ = today$.pipe(map(from), distinctUntilChanged());
  return combineLatest([
    first$.pipe(
      switchMap((first) => stats.observeMonths(householdId, first).pipe(retryLoading(undefined, [] as MonthStats[]))),
    ),
    today$,
  ]).pipe(map(([months, day]) => Insights.summary(months, day)));
}

/**
 * Your kitchen (DESIGN 5.7, 19.8; InsightsPage in the app): a pushed screen with this month's name above its title,
 * then this month on the primary card, the last six months, the streak and why things were thrown away.
 */
export function InsightsScreen({ onBack }: { onBack: () => void }) {
  const me = useHouseholdSession();
  const { stats, clock } = useServices();
  const today = useToday(clock);
  const summary = useObservable(
    useMemo(() => insights$(stats, me.householdId, of(today), Insights.firstMonth), [stats, me.householdId, today]),
    null,
  );
  return (
    <DetailScaffold
      title={str('insights_title')}
      eyebrow={summary ? monthName(summary.current.month, 'long', summary.current.month) : undefined}
      onBack={onBack}
      header="pushed"
      testId="screen_insights"
    >
      {summary === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : (
        <InsightsContent summary={summary} />
      )}
    </DetailScaffold>
  );
}

export function InsightsContent({ summary }: { summary: InsightsSummary }) {
  const thisMonth = summary.current.month;
  // The latest month is selected first; tapping another shows its details (after v1).
  const [selected, setSelected] = useState(summary.months.length - 1);
  const best = summary.bestMonth;
  return (
    <div className="sn-insights">
      <MonthCard summary={summary} />
      <PlainCard className="sn-insights-card sn-insights-card--months" data-testid="card_insights_months">
        <h2 className="t-section-title sn-insights-card__inset">{str('insights_section_months')}</h2>
        {summary.months.every((m) => MonthTotals.total(m) === 0) ? (
          <p className="t-body-large sn-insights-card__inset">{str('insights_months_none')}</p>
        ) : (
          <>
            <MonthBars
              bars={summary.months.map((m) => ({
                label: monthName(m.month, 'short', thisMonth),
                used: m.used,
                thrown: MonthTotals.thrown(m),
                description: str(
                  'insights_month_entry',
                  monthName(m.month, 'long', thisMonth),
                  m.used,
                  MonthTotals.thrown(m),
                ),
              }))}
              selected={selected}
              onSelect={setSelected}
            />
            <div className="sn-insights__legend-row sn-insights-card__inset">
              <LegendItem text={str('insights_legend_used')} thrown={false} />
              <LegendItem text={str('insights_legend_thrown')} thrown />
            </div>
            {summary.months[selected] && <MonthDetails stats={summary.months[selected]} thisMonth={thisMonth} />}
          </>
        )}
        {best && (
          <div className="sn-icon-line sn-insights-card__inset">
            <span className="c-primary">
              <Icon name="round/emoji_events" />
            </span>
            <span className="t-row-title">
              {str(
                'insights_best_month',
                monthName(best.month, 'long', thisMonth),
                percent(MonthTotals.usedShare(best)),
              )}
            </span>
          </div>
        )}
      </PlainCard>
      <StreakCard summary={summary} />
      <PlainCard className="sn-insights-card" data-testid="card_insights_reasons">
        <h2 className="t-section-title">{str('insights_section_reasons')}</h2>
        {summary.reasons.length === 0 ? (
          <p className="t-body-large">{str('insights_reasons_none')}</p>
        ) : (
          <ReasonBars reasons={summary.reasons.map(([reason, n]) => [reasonLabel(reason), n])} />
        )}
      </PlainCard>
      <p className="t-body-small c-on-surface-variant sn-insights__note">{str('insights_counting_note')}</p>
    </div>
  );
}

/**
 * This month on primary (DESIGN 19.8), like Home's card: the percentage used large, the counts as a legend, the ring
 * at the end and the friendly sentence under them. VoiceOver reads the title, the ring's sentence and the friendly
 * one; the numbers written out aren't read again (review A11Y-07). From 1.5× text the ring goes under the numbers.
 */
function MonthCard({ summary }: { summary: InsightsSummary }) {
  const month = summary.current;
  const thrown = MonthTotals.thrown(month);
  const share = MonthTotals.usedShare(month);
  const description =
    share === null ? str('insights_ring_empty') : str('insights_ring_description', month.used, thrown, percent(share));
  return (
    <section className="sn-month-card" data-testid="card_insights_month">
      <div className={`sn-month-card__row ${share === null ? 'sn-month-card__row--empty' : ''}`}>
        <div className="sn-month-card__numbers">
          {/* onPrimary, not primaryContainer: that is under 4.5:1 on four light themes' primary (19.8). */}
          <h2 className="t-row-supporting">{str('insights_card_title')}</h2>
          {share !== null && (
            <div aria-hidden>
              <p className="t-hero-number">{str('insights_percent', percent(share))}</p>
              <OnPrimaryLegend used={month.used} thrown={thrown} />
            </div>
          )}
        </div>
        <UsageRing
          used={month.used}
          thrown={thrown}
          centerText=""
          description={description}
          size={112}
          colors={ON_PRIMARY_RING}
        />
      </div>
      <p className="t-body-large sn-month-card__mood">{moodText(summary.mood)}</p>
    </section>
  );
}

/**
 * The tapped month in a box of the page's color: its counts, the share used and why things were thrown away (after
 * v1). It is a polite live region, so VoiceOver reads the new month when another is tapped.
 */
function MonthDetails({ stats, thisMonth }: { stats: MonthStats; thisMonth: YearMonth }) {
  const total = MonthTotals.total(stats);
  const reasons = DISCARD_REASONS.map((r) => [r, stats.thrown[r] ?? 0] as const)
    .filter(([, n]) => n > 0)
    .sort((a, b) => b[1] - a[1]);
  return (
    <div className="sn-insights__month-details" aria-live="polite" data-testid="insights_month_details">
      <span className="t-row-title sn-insights__month-name">{monthName(stats.month, 'long', thisMonth)}</span>
      <span className="t-body-large">
        {total === 0
          ? str('insights_month_nothing')
          : str('insights_month_details', stats.used, MonthTotals.thrown(stats), percent(MonthTotals.usedShare(stats)))}
      </span>
      {reasons.length > 0 && (
        <span className="t-body-medium c-on-surface-variant">
          {str('insights_month_reasons', reasons.map(([r, n]) => `${reasonLabel(r)} ${n}`).join(', '))}
        </span>
      )}
    </div>
  );
}

/**
 * The streak as an accent card (DESIGN 19.8): the fire in a leaf, "Streak", then the days large over what they mean,
 * read together. Before a streak can be counted, or on a day something was thrown away, a sentence says so instead.
 */
function StreakCard({ summary }: { summary: InsightsSummary }) {
  const streak = streakParts(summary);
  return (
    <AccentCard className="sn-streak" data-testid="card_insights_streak">
      <LeafBadge icon="round/local_fire_department" size={56} iconSize={28} />
      <div className="sn-streak__text">
        <h2 className="t-row-supporting sn-streak__title">{str('insights_section_streak')}</h2>
        {streak.days ? (
          <p>
            <span className="t-accent-number">{streak.days}</span>
            <br />
            <span className="t-body-medium sn-streak__sentence">{streak.sentence}</span>
          </p>
        ) : (
          <p className="t-body-medium sn-streak__sentence">{streak.sentence}</p>
        )}
      </div>
    </AccentCard>
  );
}
