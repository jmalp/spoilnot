import { useMemo, type ReactNode } from 'react';
import { distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { useLoadRetry, useNow, useObservable, useToday } from '../../core/hooks';
import { retryLoading } from '../../domain/load';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import type { GroceryItem } from '../../domain/grocery/models';
import { ActivityFeed, HomeDashboard, type ActivityEvent, type HomeSummary } from '../../domain/home/models';
import { MonthTotals, type InsightsSummary } from '../../domain/insights/insights';
import { NeedsAttention, type PantryFilter, type PurchaseItem } from '../../domain/pantry/items';
import type { PantryData } from '../../domain/pantry/models';
import type { Member } from '../../domain/household';
import type { PlannedMeal } from '../../domain/plan/models';
import { MealWeek, PlanDays } from '../../domain/plan/week';
import { UseItUp, type UseItUpSuggestion } from '../../domain/recipes/cooking';
import { CookChip, CookedLabel, cookNames, slotLabel, useFirstDay } from '../plan/components';
import { yearMonthOf, type LocalDate } from '../../domain/time';
import { Button, Card, Icon, type IconName } from '../kit';
import { insights$, MonthRing } from '../insights/InsightsScreen';
import { moodText } from '../insights/labels';
import { chipText } from '../pantry/labels';
import { Badge, StatusChip } from '../pantry/PantryRow';
import { InstallCard } from '../install/InstallHelp';
import { LoadError, OfflineBanner, TabScaffold } from '../Scaffold';
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
  /** Asks for notifications, or opens Settings > Notifications when the system no longer asks (after v1, UX-02). */
  turnOnReminders: () => void;
};

/** Today's meals, breakfast first, and the recipes planned from today on (Use it up leaves them out, UX-05). */
type PlanToday = { meals: PlannedMeal[]; planned: Set<string> };
const NO_PLAN: PlanToday = { meals: [], planned: new Set() };

/**
 * The Home tab (DESIGN 5.6): what needs attention, today's meals (5.9), Use it up, the grocery list with what is
 * running low, this month's kitchen (5.7) and recent activity.
 */
export function HomeScreen({
  navigation,
  joinRequests,
  remindersOff = false,
}: {
  navigation: HomeNavigation;
  joinRequests: number;
  /** The member wants reminders and this phone blocks them (DESIGN 5.4, 5.6). */
  remindersOff?: boolean;
}) {
  const me = useHouseholdSession();
  const { pantry, grocery, activity, stats, recipes, mealPlan, households, clock } = useServices();
  const today = useToday(clock);
  const hid = me.householdId;
  // "Couldn't load. Retry" (DESIGN 6.7): Home's listeners listen again instead of spinning (after v1, WEB-10).
  const { load, failed: loadFailed } = useLoadRetry();
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(hid).pipe(retryLoading(load)), [pantry, hid, load]),
    null,
  );
  const recent = useObservable<ActivityEvent[]>(
    useMemo(() => activity.observeRecent(hid, RECENT).pipe(retryLoading(load), startWith([])), [activity, hid, load]),
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
          retryLoading(load),
          startWith([]),
        ),
      [grocery, hid, load],
    ),
    [],
  );
  // Only the current month: one document per open (DESIGN 9.7).
  const insights = useObservable<InsightsSummary | null>(
    useMemo(() => insights$(stats, hid, of(today), yearMonthOf), [stats, hid, today]),
    null,
  );
  const box = useObservable(recipes.box$, null);
  // The same week window as the Plan tab, Week starts on included, so both share one full read (DESIGN 9.8).
  const weekStart = MealWeek.start(today, useFirstDay());
  const week = useObservable<PlanToday>(
    useMemo(
      () =>
        mealPlan.observe(hid, weekStart, MealWeek.end(weekStart)).pipe(
          map((plan) => ({
            meals: PlanDays.meals(MealWeek.build(weekStart, plan.meals, today).find((d) => d.isToday)!),
            planned: MealWeek.plannedFrom(plan.meals, today),
          })),
          retryLoading(load),
        ),
      [mealPlan, hid, weekStart, today, load],
    ),
    NO_PLAN,
  );
  const members = useObservable<Member[]>(
    useMemo(() => households.observeMembers(hid).pipe(retryLoading<Member[]>(undefined, [])), [households, hid]),
    [],
  );
  // Authors are named from the member list (by actorUid), not by the name stored with the event.
  const named = useMemo(
    () => ActivityFeed.named(recent, new Map(members.map((m) => [m.uid, m.displayName]))),
    [recent, members],
  );
  const summary = data ? HomeDashboard.summary(data, groceryItems, named, today) : null;
  // Saved recipes that use food expiring soon, but not those already planned this week (DESIGN 5.6).
  const useItUp = data && box ? UseItUp.suggestions(box.recipes, data, today, week.planned) : [];
  return (
    <TabScaffold
      title={str('tab_home')}
      onOpenSettings={navigation.openSettings}
      joinRequests={joinRequests}
      testId="screen_home"
    >
      {loadFailed ? <LoadError onRetry={() => load.retry()} /> : data?.fromCache && <OfflineBanner />}
      <div className="sn-home">
        <InstallCard />
        {joinRequests > 0 && <JoinRequestsCard count={joinRequests} onReview={navigation.reviewJoinRequests} />}
        {summary === null ? (
          !loadFailed && Array.from({ length: 4 }, (_, i) => <SkeletonCard key={i} />)
        ) : (
          <HomeCards
            summary={summary}
            today={today}
            myUid={me.user.uid}
            navigation={navigation}
            insights={insights}
            useItUp={useItUp}
            todayMeals={week.meals}
            members={members}
            remindersOff={remindersOff}
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
  remindersOff,
}: {
  summary: HomeSummary;
  today: LocalDate;
  myUid: string;
  navigation: HomeNavigation;
  insights: InsightsSummary | null;
  useItUp: UseItUpSuggestion[];
  todayMeals: PlannedMeal[];
  members: Member[];
  remindersOff: boolean;
}) {
  const { clock } = useServices();
  const now = useNow(clock);
  const attention = summary.attention;
  const attentionSize = NeedsAttention.size(attention);
  return (
    <>
      <HomeCard
        title={str('home_attention')}
        icon="round/warning_amber"
        testId="card_home_attention"
        // The same set and count as Pantry's Needs attention filter (after v1, R8).
        // "See 1 in Pantry" next to the one item already shown led to the same thing (after v1, UX-18).
        action={
          attentionSize > 0
            ? [
                attention.expired.length === 0 && attention.usable.length <= CARD_ROWS
                  ? str('home_open_pantry')
                  : plural('home_see_all', attentionSize, attentionSize),
                () => navigation.openPantry('needsAttention'),
              ]
            : undefined
        }
        calm={
          attentionSize > 0 ? undefined : summary.pantryEmpty ? str('home_attention_start') : str('home_attention_none')
        }
        // A new household has nothing done yet: no check mark (after v1, UX-07).
        calmIcon={summary.pantryEmpty ? 'round/kitchen' : 'round/check_circle'}
        // What reminders would tell, and the phone that blocks them, belong together: a line, not another banner.
        footer={remindersOff ? <RemindersOffLine onTurnOn={navigation.turnOnReminders} /> : undefined}
      >
        {/* What can still be used comes first; the expired are one line, as in Pantry (after v1, UX-01). */}
        {attention.usable.slice(0, CARD_ROWS).map((item) => (
          <AttentionRow key={item.key} item={item} today={today} />
        ))}
        {attention.expired.length > 0 && <ExpiredLine count={attention.expired.length} />}
      </HomeCard>
      {todayMeals.length > 0 && (
        <TodayCard meals={todayMeals} members={members} myUid={myUid} onOpen={navigation.openPlan} />
      )}
      {useItUp.length > 0 && <UseItUpCard suggestions={useItUp} today={today} onOpen={navigation.openRecipe} />}
      <GroceryCard summary={summary} onOpen={navigation.openGroceries} />
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

/**
 * The grocery list: what is left to buy, and what is running low (after v1, R1 and R2: Low stock was its own card,
 * though every low product is already on the list). A household with no Keep some in stock yet is told how to start.
 */
function GroceryCard({ summary, onOpen }: { summary: HomeSummary; onOpen: () => void }) {
  const more = summary.lowStock.length - CARD_ROWS;
  return (
    <HomeCard
      title={str('home_grocery_list')}
      icon="round/list_alt"
      testId="card_home_groceries"
      action={[str('home_open_list'), onOpen]}
    >
      <span className="t-body-large">
        {summary.groceryCount === 0
          ? str('home_grocery_empty')
          : plural('home_grocery_count', summary.groceryCount, summary.groceryCount)}
      </span>
      {summary.lowStock.length > 0 ? (
        <div className="sn-home-low" data-testid="home_running_low">
          <h3 className="t-label-large c-on-surface-variant">{str('home_running_low')}</h3>
          {summary.lowStock.slice(0, CARD_ROWS).map((row) => (
            <div key={row.productId} className="sn-home-row">
              <span className="t-body-large sn-home-row__name">{row.name}</span>
              <Badge text={str(row.isOut ? 'badge_out' : 'badge_low')} />
            </div>
          ))}
          {more > 0 && (
            <span className="t-body-medium c-on-surface-variant">{plural('home_running_low_more', more, more)}</span>
          )}
        </div>
      ) : (
        summary.noStaples && (
          <span className="t-body-medium c-on-surface-variant" data-testid="home_grocery_start">
            {str('home_grocery_start')}
          </span>
        )
      )}
    </HomeCard>
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
 * with a check instead (after v1), or [calmIcon] where nothing is done yet. Solid buttons: tonal ones on tonal cards
 * were hard to see in light mode.
 */
function HomeCard({
  title,
  icon,
  testId,
  action,
  calm,
  calmIcon = 'round/check_circle',
  footer,
  children,
}: {
  title: string;
  icon: IconName;
  testId: string;
  action?: [string, () => void];
  calm?: string;
  calmIcon?: IconName;
  /** A last line under the card's content or its calm row. */
  footer?: ReactNode;
  children?: ReactNode;
}) {
  if (calm !== undefined) {
    return (
      <Card data-testid={testId}>
        <div className="sn-home-calm">
          <span className="c-primary">
            <Icon name={calmIcon} />
          </span>
          <div className="sn-home-calm__text">
            <h2 className="t-title-small">{title}</h2>
            <span className="t-body-medium c-on-surface-variant">{calm}</span>
          </div>
        </div>
        {footer && <div className="sn-home-calm__footer">{footer}</div>}
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
        {footer}
        {action && (
          <Button large className="sn-home-card__action" onClick={action[1]}>
            {action[0]}
          </Button>
        )}
      </div>
    </Card>
  );
}

/**
 * "Reminders are off on this phone · Turn on" (after v1, review UX-02): the member wants reminders and this phone
 * blocks them. Said in words with an icon, not by color.
 */
function RemindersOffLine({ onTurnOn }: { onTurnOn: () => void }) {
  return (
    <div className="sn-home-reminders" data-testid="home_reminders_off">
      <span className="c-on-surface-variant">
        <Icon name="round/notifications_off" />
      </span>
      <span className="t-body-medium sn-home-reminders__text">{str('home_reminders_off')}</span>
      <Button variant="text" large onClick={onTurnOn} data-testid="home_reminders_turn_on">
        {str('home_reminders_turn_on')}
      </Button>
    </div>
  );
}

/** One purchase that can still be used, as Pantry shows it: name and status chip. */
function AttentionRow({ item, today }: { item: PurchaseItem; today: LocalDate }) {
  return (
    <div className="sn-home-row">
      <span className="t-body-large sn-home-row__name">{item.name}</span>
      <StatusChip expiry={item.batch.expiryDate} dateType={item.batch.dateType} today={today} />
    </div>
  );
}

/** "3 expired items to throw away or check": the expired folded into one line (after v1, UX-01). */
function ExpiredLine({ count }: { count: number }) {
  return (
    <div className="sn-home-row" data-testid="home_attention_expired">
      <span className="sn-home-expired__icon">
        <Icon name="round/delete_outline" />
      </span>
      <span className="t-body-large sn-home-row__name">{plural('home_attention_expired', count, count)}</span>
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
