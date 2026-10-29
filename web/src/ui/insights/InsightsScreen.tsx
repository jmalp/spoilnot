import { useMemo, useState, type ReactNode } from 'react';
import { catchError, combineLatest, distinctUntilChanged, map, of, switchMap, type Observable } from 'rxjs';
import { useObservable, useToday } from '../../core/hooks';
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
import { Card, Icon, Progress } from '../kit';
import { reasonLabel } from '../pantry/labels';
import { DetailScaffold } from '../Scaffold';
import { LegendItem, MonthBars, ReasonBars, UsageRing } from './Charts';
import { monthName, moodText, percent, streakText } from './labels';
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
  return combineLatest([first$.pipe(switchMap((first) => stats.observeMonths(householdId, first))), today$]).pipe(
    map(([months, day]) => Insights.summary(months, day)),
    catchError(() => today$.pipe(map((day) => Insights.summary([], day)))),
  );
}

/** The Insights screen (DESIGN 5.7). */
export function InsightsScreen({ onBack }: { onBack: () => void }) {
  const me = useHouseholdSession();
  const { stats, clock } = useServices();
  const today = useToday(clock);
  const summary = useObservable(
    useMemo(() => insights$(stats, me.householdId, of(today), Insights.firstMonth), [stats, me.householdId, today]),
    null,
  );
  return (
    <DetailScaffold title={str('insights_title')} onBack={onBack} testId="screen_insights">
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
  const current = summary.current;
  const thisMonth = current.month;
  // The latest month is selected first; tapping another shows its details (after v1).
  const [selected, setSelected] = useState(summary.months.length - 1);
  const best = summary.bestMonth;
  return (
    <div className="sn-insights">
      <Section title={str('insights_card_title')} testId="card_insights_month">
        <div className="sn-insights__ring-row">
          <MonthRing stats={current} />
          <div className="sn-insights__legend-column">
            <LegendItem text={`${str('insights_legend_used')} ${current.used}`} thrown={false} />
            <LegendItem text={`${str('insights_legend_thrown')} ${MonthTotals.thrown(current)}`} thrown />
          </div>
        </div>
        <p className="t-body-large">{moodText(summary.mood)}</p>
      </Section>
      <Section title={str('insights_section_months')} testId="card_insights_months">
        {summary.months.every((m) => MonthTotals.total(m) === 0) ? (
          <p className="t-body-large">{str('insights_months_none')}</p>
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
            <div className="sn-insights__legend-row">
              <LegendItem text={str('insights_legend_used')} thrown={false} />
              <LegendItem text={str('insights_legend_thrown')} thrown />
            </div>
            {summary.months[selected] && <MonthDetails stats={summary.months[selected]} thisMonth={thisMonth} />}
          </>
        )}
        {best && (
          <div className="sn-icon-line">
            <span className="c-primary">
              <Icon name="round/emoji_events" />
            </span>
            <span className="t-body-large">
              {str(
                'insights_best_month',
                monthName(best.month, 'long', thisMonth),
                percent(MonthTotals.usedShare(best)),
              )}
            </span>
          </div>
        )}
      </Section>
      <Section title={str('insights_section_streak')} testId="card_insights_streak">
        <div className="sn-icon-line">
          <span className="c-primary">
            <Icon name="round/local_fire_department" />
          </span>
          <span className="t-body-large">{streakText(summary)}</span>
        </div>
      </Section>
      <Section title={str('insights_section_reasons')} testId="card_insights_reasons">
        {summary.reasons.length === 0 ? (
          <p className="t-body-large">{str('insights_reasons_none')}</p>
        ) : (
          <ReasonBars reasons={summary.reasons.map(([reason, n]) => [reasonLabel(reason), n])} />
        )}
      </Section>
      <p className="t-body-small c-on-surface-variant">{str('insights_counting_note')}</p>
    </div>
  );
}

/** The tapped month: its counts, the share used and why things were thrown away (after v1). */
function MonthDetails({ stats, thisMonth }: { stats: MonthStats; thisMonth: YearMonth }) {
  const total = MonthTotals.total(stats);
  const reasons = DISCARD_REASONS.map((r) => [r, stats.thrown[r] ?? 0] as const)
    .filter(([, n]) => n > 0)
    .sort((a, b) => b[1] - a[1]);
  return (
    <div className="sn-insights__month-details" aria-live="polite" data-testid="insights_month_details">
      <span className="t-title-small">{monthName(stats.month, 'long', thisMonth)}</span>
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

/** This month's ring with its percentage, shared by Home and Insights. */
export function MonthRing({ stats, small = false }: { stats: MonthStats; small?: boolean }) {
  const share = MonthTotals.usedShare(stats);
  const thrown = MonthTotals.thrown(stats);
  return (
    <UsageRing
      used={stats.used}
      thrown={thrown}
      centerText={share === null ? '–' : str('insights_percent', percent(share))}
      description={
        share === null
          ? str('insights_ring_empty')
          : str('insights_ring_description', stats.used, thrown, percent(share))
      }
      size={small ? 72 : 140}
      small={small}
    />
  );
}

function Section({ title, testId, children }: { title: string; testId: string; children: ReactNode }) {
  return (
    <Card data-testid={testId}>
      <div className="sn-insights-section">
        <h2 className="t-title-medium">{title}</h2>
        {children}
      </div>
    </Card>
  );
}
