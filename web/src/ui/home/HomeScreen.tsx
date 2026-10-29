import { useMemo, type ReactNode } from 'react';
import { catchError, of, startWith } from 'rxjs';
import { useNow, useObservable, useToday } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import { HomeDashboard, type ActivityEvent, type HomeSummary } from '../../domain/home/models';
import { MonthTotals, type InsightsSummary } from '../../domain/insights/insights';
import type { PantryFilter } from '../../domain/pantry/items';
import type { PantryData } from '../../domain/pantry/models';
import type { PantryRow } from '../../domain/pantry/rows';
import { yearMonthOf, type LocalDate } from '../../domain/time';
import { Button, Card, Icon, type IconName } from '../kit';
import { insights$, MonthRing } from '../insights/InsightsScreen';
import { moodText } from '../insights/labels';
import { Badge, StatusChip } from '../pantry/PantryRow';
import { OfflineBanner, TabScaffold } from '../Scaffold';
import { activityText, relativeTime } from './labels';
import './home.css';

/** DESIGN 5.6: the last 10 events. */
const RECENT = 10;
/** How many rows a card lists before "See all". */
const CARD_ROWS = 4;

/** Where the Home cards lead. */
export type HomeNavigation = {
  openSettings: () => void;
  openPantry: (filter: PantryFilter) => void;
  openInsights: () => void;
  reviewJoinRequests: () => void;
};

/**
 * The Home tab (DESIGN 5.6): what needs attention, low stock, this month's kitchen (5.7) and recent activity. Today's
 * meals, Use it up and the grocery list join with their tabs (W3 to W5).
 */
export function HomeScreen({ navigation, joinRequests }: { navigation: HomeNavigation; joinRequests: number }) {
  const me = useHouseholdSession();
  const { pantry, activity, stats, clock } = useServices();
  const today = useToday(clock);
  const hid = me.householdId;
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(hid), [pantry, hid]),
    null,
  );
  const recent = useObservable<ActivityEvent[]>(
    useMemo(
      () =>
        activity.observeRecent(hid, RECENT).pipe(
          catchError(() => of([])),
          startWith([]),
        ),
      [activity, hid],
    ),
    [],
  );
  // Only the current month: one document per open (DESIGN 9.7).
  const insights = useObservable<InsightsSummary | null>(
    useMemo(() => insights$(stats, hid, of(today), yearMonthOf), [stats, hid, today]),
    null,
  );
  const summary = data ? HomeDashboard.summary(data, recent, today) : null;
  return (
    <TabScaffold
      title={str('tab_home')}
      onOpenSettings={navigation.openSettings}
      joinRequests={joinRequests}
      testId="screen_home"
    >
      {data?.fromCache && <OfflineBanner />}
      <div className="sn-home">
        {joinRequests > 0 && <JoinRequestsCard count={joinRequests} onReview={navigation.reviewJoinRequests} />}
        {summary === null ? (
          Array.from({ length: 4 }, (_, i) => <SkeletonCard key={i} />)
        ) : (
          <HomeCards summary={summary} today={today} myUid={me.user.uid} navigation={navigation} insights={insights} />
        )}
      </div>
    </TabScaffold>
  );
}

function HomeCards({
  summary,
  today,
  myUid,
  navigation,
  insights,
}: {
  summary: HomeSummary;
  today: LocalDate;
  myUid: string;
  navigation: HomeNavigation;
  insights: InsightsSummary | null;
}) {
  const { clock } = useServices();
  const now = useNow(clock);
  // Expired only: the Expiring soon filter would hide them.
  const attentionFilter: PantryFilter = summary.attention.length === summary.expiredCount ? 'expired' : 'expiringSoon';
  return (
    <>
      <HomeCard
        title={str('home_attention')}
        icon="round/warning_amber"
        testId="card_home_attention"
        tour="tour_home_cards"
        action={
          summary.attention.length > 0
            ? [
                plural('home_see_all', summary.attention.length, summary.attention.length),
                () => navigation.openPantry(attentionFilter),
              ]
            : undefined
        }
        calm={summary.attention.length === 0 ? str('home_attention_none') : undefined}
      >
        {summary.attention.slice(0, CARD_ROWS).map((row) => (
          <AttentionRow key={row.productId} row={row} today={today} />
        ))}
      </HomeCard>
      <HomeCard
        title={str('home_low_stock')}
        icon="round/inventory_2"
        testId="card_home_low_stock"
        action={
          summary.lowStock.length > 0
            ? [
                plural('home_see_all', summary.lowStock.length, summary.lowStock.length),
                () => navigation.openPantry('lowStock'),
              ]
            : undefined
        }
        calm={summary.lowStock.length === 0 ? str('home_low_stock_none') : undefined}
      >
        {summary.lowStock.slice(0, CARD_ROWS).map((row) => (
          <div key={row.productId} className="sn-home-row">
            <span className="t-body-large sn-home-row__name">{row.name}</span>
            <Badge text={str(row.isOut ? 'badge_out' : 'badge_low')} />
          </div>
        ))}
      </HomeCard>
      {insights && <InsightsCard insights={insights} onOpen={navigation.openInsights} />}
      <HomeCard
        title={str('home_recent_activity')}
        icon="round/history"
        testId="card_home_activity"
        calm={summary.recent.length === 0 ? str('home_activity_none') : undefined}
      >
        {summary.recent.map((event) => (
          <div key={event.id} className="sn-activity">
            <span className="t-body-large">{activityText(event, myUid)}</span>
            {event.createdAt !== null && (
              <span className="t-body-medium c-on-surface-variant">{relativeTime(event.createdAt, now)}</span>
            )}
          </div>
        ))}
      </HomeCard>
    </>
  );
}

/** This month's kitchen (DESIGN 5.7): a small ring, the counts and a friendly line. */
function InsightsCard({ insights, onOpen }: { insights: InsightsSummary; onOpen: () => void }) {
  const month = insights.current;
  return (
    <HomeCard
      title={str('insights_card_title')}
      icon="round/donut_large"
      testId="card_home_insights"
      action={[str('insights_see'), onOpen]}
    >
      <div className="sn-home-insights">
        <MonthRing stats={month} small />
        <div className="sn-home-insights__text">
          {/* The ring shows the percentage; the text gives the counts behind it. */}
          {MonthTotals.total(month) > 0 && (
            <span className="t-title-medium">{str('insights_counts', month.used, MonthTotals.thrown(month))}</span>
          )}
          <span className="t-body-medium">{moodText(insights.mood)}</span>
        </div>
      </div>
    </HomeCard>
  );
}

/**
 * An outlined card with an icon, a title and an optional button. With nothing to show ([calm]) it is one compact row
 * with a check instead (after v1). Solid buttons: tonal ones on tonal cards were hard to see in light mode.
 */
function HomeCard({
  title,
  icon,
  testId,
  tour,
  action,
  calm,
  children,
}: {
  title: string;
  icon: IconName;
  testId: string;
  tour?: string;
  action?: [string, () => void];
  calm?: string;
  children?: ReactNode;
}) {
  if (calm !== undefined) {
    return (
      <Card data-testid={testId} data-tour={tour}>
        <div className="sn-home-calm">
          <span className="c-primary">
            <Icon name="round/check_circle" />
          </span>
          <div className="sn-home-calm__text">
            <h2 className="t-title-small">{title}</h2>
            <span className="t-body-medium c-on-surface-variant">{calm}</span>
          </div>
        </div>
      </Card>
    );
  }
  return (
    <Card data-testid={testId} data-tour={tour}>
      <div className="sn-home-card">
        <div className="sn-home-card__header">
          <span className="c-primary">
            <Icon name={icon} />
          </span>
          <h2 className="t-title-medium">{title}</h2>
        </div>
        {children}
        {action && (
          <Button large className="sn-home-card__action" onClick={action[1]}>
            {action[0]}
          </Button>
        )}
      </div>
    </Card>
  );
}

function AttentionRow({ row, today }: { row: PantryRow; today: LocalDate }) {
  return (
    <div className="sn-home-row">
      <span className="t-body-large sn-home-row__name">{row.name}</span>
      {row.soonest && <StatusChip expiry={row.soonest.expiryDate} dateType={row.soonest.dateType} today={today} />}
    </div>
  );
}

/** Shown to the owner while someone is waiting for approval (DESIGN 5.6). */
function JoinRequestsCard({ count, onReview }: { count: number; onReview: () => void }) {
  return (
    <Card variant="filled" className="sn-home-join" data-testid="card_home_join_requests">
      <div className="sn-home-join__row">
        <Icon name="round/group_add" />
        <span className="t-title-medium sn-home-join__text">{plural('home_join_requests', count, count)}</span>
        <Button large onClick={onReview}>
          {str('action_review')}
        </Button>
      </div>
    </Card>
  );
}

/** Shown on the first load only (DESIGN 6.7); still when reduce motion is on. */
function SkeletonCard() {
  return (
    <Card data-testid="home_skeleton">
      <div className="sn-skeleton" aria-hidden>
        <span style={{ width: '40%', height: 20 }} />
        <span style={{ width: '90%', height: 16 }} />
        <span style={{ width: '70%', height: 16 }} />
      </div>
    </Card>
  );
}
