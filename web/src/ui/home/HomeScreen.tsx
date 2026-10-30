import { useMemo, type ReactNode } from 'react';
import { catchError, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { useNow, useObservable, useToday } from '../../core/hooks';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import type { GroceryItem } from '../../domain/grocery/models';
import { HomeDashboard, type ActivityEvent, type HomeSummary } from '../../domain/home/models';
import { MonthTotals, type InsightsSummary } from '../../domain/insights/insights';
import type { PantryFilter } from '../../domain/pantry/items';
import type { PantryData } from '../../domain/pantry/models';
import type { PantryRow } from '../../domain/pantry/rows';
import type { Member } from '../../domain/household';
import type { PlannedMeal } from '../../domain/plan/models';
import { MealWeek, PlanDays } from '../../domain/plan/week';
import { UseItUp, type UseItUpSuggestion } from '../../domain/recipes/cooking';
import { CookChip, CookedLabel, cookNames, phoneFirstDay, slotLabel } from '../plan/components';
import { yearMonthOf, type LocalDate } from '../../domain/time';
import { Button, Card, Icon, type IconName } from '../kit';
import { insights$, MonthRing } from '../insights/InsightsScreen';
import { moodText } from '../insights/labels';
import { chipText } from '../pantry/labels';
import { Badge, StatusChip } from '../pantry/PantryRow';
import { InstallCard } from '../install/InstallHelp';
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
  openGroceries: () => void;
  openRecipe: (id: string) => void;
  openPlan: () => void;
  reviewJoinRequests: () => void;
};

/**
 * The Home tab (DESIGN 5.6): what needs attention, today's meals (5.9), Use it up, low stock, the grocery list, this
 * month's kitchen (5.7) and recent activity.
 */
export function HomeScreen({ navigation, joinRequests }: { navigation: HomeNavigation; joinRequests: number }) {
  const me = useHouseholdSession();
  const { pantry, grocery, activity, stats, recipes, mealPlan, households, clock } = useServices();
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
  // The default list's items, as the Groceries tab reads them (DESIGN 9.1).
  const groceryItems = useObservable<GroceryItem[]>(
    useMemo(
      () =>
        grocery.observeLists(hid).pipe(
          map((lists) => lists.find((l) => l.isDefault)?.id ?? null),
          distinctUntilChanged(),
          switchMap((id) => (id === null ? of([]) : grocery.observeItems(hid, id))),
          catchError(() => of([])),
          startWith([]),
        ),
      [grocery, hid],
    ),
    [],
  );
  // Only the current month: one document per open (DESIGN 9.7).
  const insights = useObservable<InsightsSummary | null>(
    useMemo(() => insights$(stats, hid, of(today), yearMonthOf), [stats, hid, today]),
    null,
  );
  const box = useObservable(recipes.box$, null);
  // The same week window as the Plan tab, so both share one full read (DESIGN 9.8).
  const weekStart = MealWeek.start(today, phoneFirstDay());
  const todayMeals = useObservable<PlannedMeal[]>(
    useMemo(
      () =>
        mealPlan.observe(hid, weekStart, MealWeek.end(weekStart)).pipe(
          map((plan) => PlanDays.meals(MealWeek.build(weekStart, plan.meals, today).find((d) => d.isToday)!)),
          catchError(() => of([])),
        ),
      [mealPlan, hid, weekStart, today],
    ),
    [],
  );
  const members = useObservable<Member[]>(
    useMemo(() => households.observeMembers(hid).pipe(catchError(() => of([]))), [households, hid]),
    [],
  );
  const summary = data ? HomeDashboard.summary(data, groceryItems, recent, today) : null;
  // Saved recipes that use food expiring soon (DESIGN 5.6).
  const useItUp = data && box ? UseItUp.suggestions(box.recipes, data, today) : [];
  return (
    <TabScaffold
      title={str('tab_home')}
      onOpenSettings={navigation.openSettings}
      joinRequests={joinRequests}
      testId="screen_home"
    >
      {data?.fromCache && <OfflineBanner />}
      <div className="sn-home">
        <InstallCard />
        {joinRequests > 0 && <JoinRequestsCard count={joinRequests} onReview={navigation.reviewJoinRequests} />}
        {summary === null ? (
          Array.from({ length: 4 }, (_, i) => <SkeletonCard key={i} />)
        ) : (
          <HomeCards
            summary={summary}
            today={today}
            myUid={me.user.uid}
            navigation={navigation}
            insights={insights}
            useItUp={useItUp}
            todayMeals={todayMeals}
            members={members}
          />
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
  useItUp,
  todayMeals,
  members,
}: {
  summary: HomeSummary;
  today: LocalDate;
  myUid: string;
  navigation: HomeNavigation;
  insights: InsightsSummary | null;
  useItUp: UseItUpSuggestion[];
  todayMeals: PlannedMeal[];
  members: Member[];
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
      {todayMeals.length > 0 && (
        <TodayCard meals={todayMeals} members={members} myUid={myUid} onOpen={navigation.openPlan} />
      )}
      {useItUp.length > 0 && <UseItUpCard suggestions={useItUp} today={today} onOpen={navigation.openRecipe} />}
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
      <HomeCard
        title={str('home_grocery_list')}
        icon="round/list_alt"
        testId="card_home_groceries"
        action={[str('home_open_list'), navigation.openGroceries]}
      >
        <span className="t-body-large">
          {summary.groceryCount === 0
            ? str('home_grocery_empty')
            : plural('home_grocery_count', summary.groceryCount, summary.groceryCount)}
        </span>
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

/** Today's planned meals and who cooks them (DESIGN 5.9). Shown only when something is planned. */
function TodayCard({
  meals,
  members,
  myUid,
  onOpen,
}: {
  meals: PlannedMeal[];
  members: Member[];
  myUid: string;
  onOpen: () => void;
}) {
  return (
    <HomeCard
      title={str('home_today')}
      icon="round/calendar_month"
      testId="card_home_today"
      action={[str('home_today_open'), onOpen]}
    >
      {meals.map((meal) => {
        const cooks = cookNames(meal.cooks, members, myUid);
        return (
          <div key={meal.id} className="sn-today-meal">
            <span className="t-label-medium c-on-surface-variant">{slotLabel(meal.slot)}</span>
            <span className="t-body-large sn-home-row__name">{meal.title}</span>
            {(cooks.length > 0 || meal.cookedAt !== null) && (
              <span className="sn-meal-row__details">
                {cooks.map((name) => (
                  <CookChip key={name} name={name} />
                ))}
                {meal.cookedAt !== null && <CookedLabel />}
              </span>
            )}
          </div>
        );
      })}
    </HomeCard>
  );
}

/**
 * "Use it up" (DESIGN 5.6, after v1): saved recipes that use food expiring soon, each with what it uses and when that
 * expires. Shown only when there is a match.
 */
function UseItUpCard({
  suggestions,
  today,
  onOpen,
}: {
  suggestions: UseItUpSuggestion[];
  today: LocalDate;
  onOpen: (id: string) => void;
}) {
  return (
    <HomeCard title={str('home_use_it_up')} icon="round/restaurant_menu" testId="card_home_use_it_up">
      {suggestions.map(({ recipe, uses }) => (
        <button
          key={recipe.id}
          type="button"
          className="sn-use-it-up m3-state"
          aria-description={str('home_use_it_up_open')}
          onClick={() => onOpen(recipe.id)}
        >
          <span className="t-body-large sn-home-row__name">{recipe.content.title}</span>
          <span className="t-body-medium c-on-surface-variant">
            {str(
              'home_use_it_up_uses',
              uses
                .map((u) => `${u.product.name} (${chipText(u.batch.expiryDate, u.batch.dateType, today)})`)
                .join(', '),
            )}
          </span>
        </button>
      ))}
    </HomeCard>
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
  action,
  calm,
  children,
}: {
  title: string;
  icon: IconName;
  testId: string;
  action?: [string, () => void];
  calm?: string;
  children?: ReactNode;
}) {
  if (calm !== undefined) {
    return (
      <Card data-testid={testId}>
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
    <Card data-testid={testId}>
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
