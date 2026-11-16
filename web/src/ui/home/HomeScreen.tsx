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
import { Expiry } from '../../domain/pantry/rules';
import type { Member } from '../../domain/household';
import type { PlannedMeal } from '../../domain/plan/models';
import { MealWeek, PlanDays } from '../../domain/plan/week';
import { UseItUp, type UseItUpSuggestion } from '../../domain/recipes/cooking';
import { CookedLabel, cookNames, slotLabel, useFirstDay } from '../plan/components';
import { yearMonthOf, type LocalDate } from '../../domain/time';
import {
  AccentCard,
  Button,
  HighlightCard,
  Icon,
  LabelChip,
  LeafBadge,
  ListGroup,
  PlainCard,
  StatusTile,
  type IconName,
} from '../kit';
import { UsageRing } from '../insights/Charts';
import { insights$ } from '../insights/InsightsScreen';
import { moodText, percent } from '../insights/labels';
import { chipText, statusIcon, statusText, tone } from '../pantry/labels';
import { Badge } from '../pantry/PantryRow';
import { InstallCard } from '../install/InstallHelp';
import { LoadError, OfflineBanner, TabScaffold } from '../Scaffold';
import { activityText, homeDate, relativeTime } from './labels';
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
 * The Home tab (DESIGN 5.6, 19.12): what needs attention, today's meals (5.9), this month's kitchen (5.7), Use it up,
 * the grocery list with what is running low, and recent activity.
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
      eyebrow={homeDate(today)}
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
  return (
    <>
      <NeedsAttentionCard summary={summary} today={today} navigation={navigation} remindersOff={remindersOff} />
      {todayMeals.length > 0 && (
        <TodaySection meals={todayMeals} members={members} myUid={myUid} onOpen={navigation.openPlan} />
      )}
      {insights && <KitchenCard insights={insights} onOpen={navigation.openInsights} />}
      {useItUp.length > 0 && <UseItUpCard suggestions={useItUp} today={today} onOpen={navigation.openRecipe} />}
      <GroceryCard summary={summary} onOpen={navigation.openGroceries} />
      <HomePlainCard
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
      </HomePlainCard>
    </>
  );
}

/**
 * Needs attention (DESIGN 5.6, 19.8): the highlight card, its count in a badge, what can still be used first and the
 * expired folded into one row, as in Pantry (after v1, UX-01). With nothing to show it is a compact plain card that
 * says so, or for a new household how to start (UX-07).
 */
function NeedsAttentionCard({
  summary,
  today,
  navigation,
  remindersOff,
}: {
  summary: HomeSummary;
  today: LocalDate;
  navigation: HomeNavigation;
  remindersOff: boolean;
}) {
  const attention = summary.attention;
  const size = NeedsAttention.size(attention);
  // What reminders would tell, and the phone that blocks them, belong together: a line, not another banner.
  const reminders = remindersOff ? <RemindersOffLine onTurnOn={navigation.turnOnReminders} /> : undefined;
  if (size === 0) {
    return (
      <CalmCard
        title={str('home_attention')}
        message={summary.pantryEmpty ? str('home_attention_start') : str('home_attention_none')}
        // A new household has nothing done yet: no check mark (after v1, UX-07).
        icon={summary.pantryEmpty ? 'round/kitchen' : 'round/check_circle'}
        testId="card_home_attention"
        footer={reminders}
      />
    );
  }
  // "See 1 in Pantry" next to the one item already shown led to the same thing (after v1, UX-18). The same set and
  // count as Pantry's Needs attention filter (after v1, R8).
  const allShown = attention.expired.length === 0 && attention.usable.length <= CARD_ROWS;
  return (
    <HighlightCard className="sn-attention" data-testid="card_home_attention">
      <div className="sn-attention__header">
        <Icon name="round/warning_amber" />
        <h2 className="t-card-title">{str('home_attention')}</h2>
        {/* The rows and See all N say the count; a bare number after the heading would not. */}
        <span className="sn-attention__count t-section-title" aria-hidden>
          {size}
        </span>
      </div>
      <ListGroup nested>
        {/* What can still be used comes first; the expired are one row, as in Pantry (after v1, UX-01). */}
        {attention.usable.slice(0, CARD_ROWS).map((item) => (
          <AttentionRow key={item.key} item={item} today={today} />
        ))}
        {attention.expired.length > 0 && <ExpiredRow count={attention.expired.length} />}
      </ListGroup>
      {reminders}
      <Button large className="sn-card-button" onClick={() => navigation.openPantry('needsAttention')}>
        {allShown ? str('home_open_pantry') : plural('home_see_all', size, size)}
      </Button>
    </HighlightCard>
  );
}

/**
 * One purchase that can still be used (DESIGN 19.6): its status tile, the name, and the status in words in its color,
 * under the name from 1.5x text (after v1, review A11Y-04). VoiceOver reads one sentence, "Milk, Expires tomorrow"
 * (A11Y-07).
 */
function AttentionRow({ item, today }: { item: PurchaseItem; today: LocalDate }) {
  const { expiryDate, dateType } = item.batch;
  const status = Expiry.of(expiryDate, today);
  return (
    <div className="sn-segment sn-home-row sn-home-row--nested" data-testid="home_attention_row">
      <span className="visually-hidden">{`${item.name}, ${statusText(expiryDate, dateType, today)}`}</span>
      <StatusTile tone={tone(status)} icon={statusIcon(status)} nested />
      <span className="sn-home-row__text sn-home-row--trailing" aria-hidden>
        <span className="t-row-title sn-home-row__name">{item.name}</span>
        <span
          className={`t-row-status sn-home-status sn-home-status--${tone(status)}`}
          data-testid="home_attention_status"
        >
          {chipText(expiryDate, dateType, today)}
        </span>
      </span>
    </div>
  );
}

/** "3 expired items to throw away or check": the expired folded into the last row (after v1, UX-01). */
function ExpiredRow({ count }: { count: number }) {
  return (
    <div className="sn-segment sn-home-row sn-home-row--nested" data-testid="home_attention_expired">
      <StatusTile tone="expired" icon="round/delete_outline" nested />
      {/* A sentence, not a name: shown whole at any text size, as in the app. */}
      <span className="t-row-title sn-home-row__sentence">{plural('home_attention_expired', count, count)}</span>
    </div>
  );
}

/**
 * Today's planned meals and who cooks them (DESIGN 5.9, 19.12): a section on the page with Open plan beside its
 * heading, one tile per meal, two side by side and one per line from 1.5x text. Shown only when something is planned.
 */
function TodaySection({
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
    <section className="sn-today" data-testid="card_home_today">
      <div className="sn-today__header">
        <h2 className="t-section-title">{str('home_today')}</h2>
        <Button variant="text" large className="t-button-medium" onClick={onOpen}>
          {str('home_today_open')}
        </Button>
      </div>
      <div className="sn-today__tiles">
        {meals.map((meal) => {
          const cooks = cookNames(meal.cooks, members, myUid);
          // One stop per meal for VoiceOver: "Breakfast: Porridge, cooking: You, Cooked" (after v1, review A11Y-07).
          const description = [
            str('home_today_meal_description', slotLabel(meal.slot), meal.title),
            cooks.length > 0 ? str('home_today_cooking', cooks.join(', ')) : null,
            meal.cookedAt !== null ? str('plan_cooked') : null,
          ]
            .filter((part) => part !== null)
            .join(', ');
          return (
            <div key={meal.id} className="sn-today-meal" data-testid="home_today_meal">
              <span className="visually-hidden">{description}</span>
              <span className="t-row-supporting c-on-surface-variant" aria-hidden>
                {slotLabel(meal.slot)}
              </span>
              <span className="t-row-title sn-home-row__name" aria-hidden>
                {meal.title}
              </span>
              {(cooks.length > 0 || meal.cookedAt !== null) && (
                <span className="sn-today-meal__details" aria-hidden>
                  {cooks.map((name) => (
                    <LabelChip key={name} text={name} icon="round/person" kind="person" />
                  ))}
                  {meal.cookedAt !== null && <CookedLabel />}
                </span>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}

/**
 * Your kitchen this month (DESIGN 5.7, 19.8): the percentage used, large, with the counts and the ring on a primary
 * card. The whole card opens Your kitchen; VoiceOver reads the title and the ring's sentence once.
 */
function KitchenCard({ insights, onOpen }: { insights: InsightsSummary; onOpen: () => void }) {
  const month = insights.current;
  const thrown = MonthTotals.thrown(month);
  const share = MonthTotals.usedShare(month);
  const description =
    share === null ? str('insights_ring_empty') : str('insights_ring_description', month.used, thrown, percent(share));
  return (
    <button
      type="button"
      className="sn-kitchen-card m3-state"
      data-testid="card_home_insights"
      aria-label={`${str('insights_card_title')}, ${description}`}
      aria-description={str('insights_see')}
      onClick={onOpen}
    >
      <span className="sn-kitchen-card__text" aria-hidden>
        <span className="t-row-supporting">{str('insights_card_title')}</span>
        {share === null ? (
          <span className="t-body-medium">{moodText(insights.mood)}</span>
        ) : (
          <>
            <span className="t-big-number">{str('insights_percent', percent(share))}</span>
            <span className="sn-kitchen-card__legend t-row-supporting">
              <span className="sn-kitchen-card__entry">
                <span className="sn-kitchen-card__swatch" />
                {`${str('insights_legend_used')} ${month.used}`}
              </span>
              <span className="sn-kitchen-card__entry">
                <span className="sn-kitchen-card__swatch sn-kitchen-card__swatch--outlined" />
                {`${str('insights_legend_thrown')} ${thrown}`}
              </span>
            </span>
          </>
        )}
      </span>
      <UsageRing
        used={month.used}
        thrown={thrown}
        centerText=""
        description={description}
        size={84}
        small
        colors={{
          used: 'var(--md-sys-color-primary-container)',
          thrown: 'var(--md-sys-color-on-primary)',
          track: 'color-mix(in srgb, var(--md-sys-color-on-primary) 24%, var(--md-sys-color-primary))',
        }}
      />
    </button>
  );
}

/**
 * "Use it up" (DESIGN 5.6, 19.8): an accent card with saved recipes that use food expiring soon, each with what it
 * uses and when that expires, each opening its recipe. Shown only when there is a match.
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
    <AccentCard data-testid="card_home_use_it_up">
      <LeafBadge icon="round/restaurant_menu" />
      <div className="sn-use-it-up__list">
        <h2 className="t-row-supporting">{str('home_use_it_up')}</h2>
        {suggestions.map(({ recipe, uses }) => (
          <button
            key={recipe.id}
            type="button"
            className="sn-use-it-up m3-state"
            aria-description={str('home_use_it_up_open')}
            onClick={() => onOpen(recipe.id)}
          >
            <span className="sn-use-it-up__text">
              <span className="t-row-title sn-home-row__name">{recipe.content.title}</span>
              <span className="t-row-supporting">
                {str(
                  'home_use_it_up_uses',
                  uses
                    .map((u) => `${u.product.name} (${chipText(u.batch.expiryDate, u.batch.dateType, today)})`)
                    .join(', '),
                )}
              </span>
            </span>
            <Icon name="round/chevron_right" />
          </button>
        ))}
      </div>
    </AccentCard>
  );
}

/**
 * The grocery list: what is left to buy, and what is running low (after v1, R1 and R2: Low stock was its own card,
 * though every low product is already on the list). A household with no Keep some in stock yet is told how to start.
 */
function GroceryCard({ summary, onOpen }: { summary: HomeSummary; onOpen: () => void }) {
  const more = summary.lowStock.length - CARD_ROWS;
  return (
    <HomePlainCard
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
          <h3 className="t-row-supporting c-on-surface-variant">{str('home_running_low')}</h3>
          {summary.lowStock.slice(0, CARD_ROWS).map((row) => {
            const badge = str(row.isOut ? 'badge_out' : 'badge_low');
            // One sentence for VoiceOver, "Olive oil, Low", the badge under the name from 1.5x text (A11Y-04, A11Y-07).
            return (
              <div key={row.productId} className="sn-home-row sn-home-row--trailing" data-testid="home_low_row">
                <span className="visually-hidden">{`${row.name}, ${badge}`}</span>
                <span className="t-body-large sn-home-row__name" aria-hidden>
                  {row.name}
                </span>
                <Badge text={badge} hidden />
              </div>
            );
          })}
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
    </HomePlainCard>
  );
}

/**
 * A plain card (DESIGN 19.8): the row surface with 24 px corners, an icon and a title, its content, and a filled
 * button at the end. With nothing to show ([calm]) it is one compact row instead (after v1).
 */
function HomePlainCard({
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
  if (calm !== undefined) return <CalmCard title={title} message={calm} icon={icon} testId={testId} />;
  return (
    <PlainCard data-testid={testId}>
      <div className="sn-home-card">
        <div className="sn-home-card__header">
          <span className="c-primary">
            <Icon name={icon} />
          </span>
          <h2 className="t-section-title">{title}</h2>
        </div>
        {children}
        {action && (
          <Button large className="sn-card-button" onClick={action[1]}>
            {action[0]}
          </Button>
        )}
      </div>
    </PlainCard>
  );
}

/** Nothing to show: a compact plain card with an icon, the title and one line (after v1). */
function CalmCard({
  title,
  message,
  icon,
  testId,
  footer,
}: {
  title: string;
  message: string;
  icon: IconName;
  testId: string;
  /** A last line under the card's row. */
  footer?: ReactNode;
}) {
  return (
    <PlainCard data-testid={testId}>
      <div className="sn-home-calm">
        <span className="c-primary">
          <Icon name={icon} />
        </span>
        <div className="sn-home-calm__text">
          <h2 className="t-row-title">{title}</h2>
          <span className="t-body-medium c-on-surface-variant">{message}</span>
        </div>
      </div>
      {footer && <div className="sn-home-calm__footer">{footer}</div>}
    </PlainCard>
  );
}

/**
 * "Reminders are off on this phone · Turn on" (after v1, review UX-02): the member wants reminders and this phone
 * blocks them. Said in words with an icon, not by color. Turn on takes the card's text color on the highlight card.
 */
function RemindersOffLine({ onTurnOn }: { onTurnOn: () => void }) {
  return (
    <div className="sn-home-reminders" data-testid="home_reminders_off">
      <Icon name="round/notifications_off" />
      <span className="t-body-medium sn-home-reminders__text">{str('home_reminders_off')}</span>
      <Button
        variant="text"
        large
        className="sn-home-reminders__turn-on t-button-medium"
        onClick={onTurnOn}
        data-testid="home_reminders_turn_on"
      >
        {str('home_reminders_turn_on')}
      </Button>
    </div>
  );
}

/** Shown to the owner while someone is waiting for approval (DESIGN 5.6). */
function JoinRequestsCard({ count, onReview }: { count: number; onReview: () => void }) {
  return (
    <section className="sn-home-join" data-testid="card_home_join_requests">
      {/* From 1.5x text the button goes under the sentence; beside it, the sentence broke one syllable per line and
          Review ran out of its button (after v1, review A11Y-04). */}
      <div className="sn-home-join__row">
        <span className="sn-home-join__text">
          <Icon name="round/group_add" />
          <span className="t-row-title">{plural('home_join_requests', count, count)}</span>
        </span>
        <Button large className="sn-card-button" onClick={onReview}>
          {str('action_review')}
        </Button>
      </div>
    </section>
  );
}

/** Shown on the first load only (DESIGN 6.7); still when reduce motion is on. */
function SkeletonCard() {
  return (
    <PlainCard data-testid="home_skeleton">
      <div className="sn-skeleton" aria-hidden>
        <span style={{ width: '40%', height: 20 }} />
        <span style={{ width: '90%', height: 16 }} />
        <span style={{ width: '70%', height: 16 }} />
      </div>
    </PlainCard>
  );
}
