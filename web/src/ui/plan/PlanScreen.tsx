import { useEffect, useMemo, useRef, useState, type ReactNode, type RefObject } from 'react';
import { filter, firstValueFrom, map, of, switchMap, take, timeout } from 'rxjs';
import { useSessionDraft } from '../../core/drafts';
import { messageId } from '../../core/errors';
import { useLoadRetry, useObservable, useToday } from '../../core/hooks';
import { retryLoading } from '../../domain/load';
import { plural, str } from '../../core/i18n';
import { useHouseholdSession, useServices } from '../../core/services';
import type { GroceryItem, GroceryList } from '../../domain/grocery/models';
import type { Member } from '../../domain/household';
import type { PantryData } from '../../domain/pantry/models';
import { naturalOrder } from '../../domain/pantry/rules';
import { MEAL_SLOTS, type MealPlanData, type MealSlot, type PlannedMeal } from '../../domain/plan/models';
import { MealPlanGrocery } from '../../domain/plan/service';
import { MealWeek, PlanDays, type PlanDay } from '../../domain/plan/week';
import { Cooking, UseItUp } from '../../domain/recipes/cooking';
import type { Recipe } from '../../domain/recipes/models';
import { addDays, daysBetween, sortedWith, type LocalDate } from '../../domain/time';
import type { Undo } from '../../domain/write';
import {
  Button,
  HighlightCard,
  IconButton,
  LabelChip,
  ListGroup,
  Menu,
  MenuItem,
  PlainCard,
  Progress,
  useSnackbars,
} from '../kit';
import { CookedSheet } from '../recipes/RecipeSheets';
import { LoadError, OfflineBanner, TabScaffold } from '../Scaffold';
import { CookedLabel, cookNames, dayTitle, shortDate, shortWeekday, slotLabel, useFirstDay } from './components';
import { AddMealSheet, MealSheet, WeekGrocerySheet } from './PlanSheets';
import { PlanDrafts, type PlanSheetDraft } from './planDrafts';
import '../recipes/recipes.css';
import './plan.css';
import { recipeWords } from '../recipes/words';

/** How long Copy last week waits for the server before using the phone's copy. */
const COPY_WAIT_MS = 5_000;
const NO_PANTRY: PantryData = { products: [], batches: [], fromCache: false };

/**
 * The Plan tab (DESIGN 5.9): one week of meals, who cooks them, and adding, changing and removing meals; the week's
 * ingredients to a grocery list, Cooked, and Use it up in the picker. The pantry and grocery lists are listened to
 * only while a sheet that needs them is open.
 */
export function PlanScreen({
  onOpenSettings,
  joinRequests,
  weekOffset,
  onWeekOffset,
  onOpenRecipe,
}: {
  onOpenSettings: () => void;
  joinRequests: number;
  /** Weeks from this one (0 = this week), kept by the shell as the app's view model keeps it. */
  weekOffset: number;
  onWeekOffset: (offset: number) => void;
  onOpenRecipe: (id: string) => void;
}) {
  const me = useHouseholdSession();
  const { mealPlan, mealPlanService, households, recipes, clock } = useServices();
  const snackbars = useSnackbars();
  const today = useToday(clock);
  const hid = me.householdId;
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);
  const firstDay = useFirstDay();
  const thisWeek = MealWeek.start(today, firstDay);
  const start = addDays(thisWeek, weekOffset * 7);

  // "Couldn't load. Retry" (DESIGN 6.7): the week listens again instead of spinning (after v1, WEB-10).
  const { load, failed: loadFailed } = useLoadRetry();
  // Tagged with its week: the last week's data stays on screen until the new week's first answer.
  const plan = useObservable<(MealPlanData & { start: LocalDate }) | null>(
    useMemo(
      () =>
        mealPlan.observe(hid, start, MealWeek.end(start)).pipe(
          map((data) => ({ ...data, start })),
          retryLoading(load),
        ),
      [mealPlan, hid, start, load],
    ),
    null,
  );
  const members = useObservable<Member[]>(
    useMemo(
      () =>
        households.observeMembers(hid).pipe(
          map((all) => sortedWith(all, (a, b) => naturalOrder(a.displayName, b.displayName))),
          retryLoading(undefined, [] as Member[]),
        ),
      [households, hid],
    ),
    [],
  );
  const box = useObservable(recipes.box$, null);
  const recipeList = useMemo(
    () => sortedWith(box?.recipes ?? [], (a, b) => naturalOrder(a.content.title, b.content.title)),
    [box],
  );
  const days = plan ? MealWeek.build(start, plan.meals, today) : [];
  const canShop = MealPlanGrocery.toShop(days, recipeList).length > 0;
  const weekMeals = days.flatMap(PlanDays.meals);

  // Add meal or a meal's sheet, with what was picked in it, kept through a reload (after v1, REL-09).
  const [sheet, setSheet] = useSessionDraft<PlanSheetDraft | null>(`plan.${hid}`, PlanDrafts.parse, null);
  const adding = sheet?.kind === 'add' ? sheet : null;
  const openMealId = sheet?.kind === 'meal' ? sheet.mealId : null;
  const closeSheet = () => setSheet(null);
  const editSheet = <T extends PlanSheetDraft>(change: Partial<T>) =>
    setSheet((d) => (d === null ? d : ({ ...d, ...change } as PlanSheetDraft)));
  // A sheet restored after a reload opens on its own week, which the shell does not keep.
  const restoring = useRef(sheet !== null);
  useEffect(() => {
    if (!restoring.current) return;
    restoring.current = false;
    if (sheet === null) return;
    const week = MealWeek.start(sheet.date, firstDay);
    if (week !== start) onWeekOffset(daysBetween(thisWeek, week) / 7);
  }, [sheet, firstDay, start, thisWeek, onWeekOffset]);
  const [cookingId, setCookingId] = useState<string | null>(null);
  const daysList = useRef<HTMLDivElement>(null);
  const [shopping, setShopping] = useState(false);
  const findMeal = (id: string | null) => (id === null ? null : (weekMeals.find((m) => m.id === id) ?? null));
  const openMeal = findMeal(openMealId);
  const recipeOf = (meal: PlannedMeal) => recipeList.find((r) => r.id === meal.recipeId) ?? null;

  const failed = (e: unknown) => snackbars.show(str(messageId(e)));
  const act = (work: () => Promise<unknown>) => void work().catch(failed);
  const withUndo = (work: () => Promise<Undo | null>, message: string) =>
    void work().then((undo) => {
      if (undo) snackbars.showUndo(message, () => void mealPlanService.undo(hid, actor, undo));
    }, failed);
  const slotMeals = (date: LocalDate, slot: MealSlot) => days.find((d) => d.date === date)?.slots[slot] ?? [];

  const copyLastWeek = () => {
    if (!plan) return;
    // Last week from the server when it answers within a few seconds, otherwise from the phone's copy.
    const from = addDays(start, -7);
    const week = mealPlan.observe(hid, from, MealWeek.end(from));
    act(async () => {
      const previous = await firstValueFrom(
        week.pipe(
          filter((d) => !d.fromCache),
          timeout({ first: COPY_WAIT_MS, with: () => week.pipe(take(1)) }),
        ),
      );
      const copied = await mealPlanService.copyWeek(hid, actor, previous.meals, from, start, weekMeals);
      if (!copied) snackbars.show(str(previous.meals.length === 0 ? 'plan_copy_none' : 'plan_copy_already'));
      else
        snackbars.showUndo(
          plural('plan_copied_meals', copied[0], copied[0]),
          () => void mealPlanService.undo(hid, actor, copied[1]),
        );
    });
  };

  const cook = (meal: PlannedMeal) => {
    // A recipe meal opens the sheet of what it used; a meal without a saved recipe is marked cooked at once.
    if (recipeOf(meal)) setCookingId(meal.id);
    else
      withUndo(
        () => mealPlanService.cooked(hid, actor, meal, null, new Map(), NO_PANTRY),
        str('plan_cooked_done', meal.title),
      );
  };

  return (
    <TabScaffold
      title={str('tab_plan')}
      // "This week" while this week shows (DESIGN 19.7).
      eyebrow={weekOffset === 0 ? str('plan_eyebrow_this_week') : undefined}
      onOpenSettings={onOpenSettings}
      joinRequests={joinRequests}
      innerScroll
      testId="screen_plan"
      actions={
        <>
          {canShop && (
            <IconButton
              icon="round/add_shopping_cart"
              label={str('plan_shop')}
              data-testid="plan_shop"
              onClick={() => setShopping(true)}
            />
          )}
          <PlanMenu
            canClear={weekMeals.length > 0}
            onCopy={copyLastWeek}
            // Undo, no dialog (after v1, R13 and DESIGN 6.1).
            onClear={() => withUndo(() => mealPlanService.clear(hid, actor, weekMeals), str('plan_cleared'))}
          />
        </>
      }
    >
      {loadFailed ? <LoadError onRetry={() => load.retry()} /> : <OfflineBanner offline={!!plan?.fromCache} />}
      {/* The week bar and the row of days stay above the days while they scroll (DESIGN 19.12). */}
      <div className="sn-plan-controls">
        <WeekSwitcher
          start={start}
          onPrevious={() => onWeekOffset(weekOffset - 1)}
          onNext={() => onWeekOffset(weekOffset + 1)}
        />
        {weekOffset !== 0 && (
          <Button
            variant="text"
            large
            className="sn-week-this t-button-medium"
            data-testid="plan_this_week"
            onClick={() => onWeekOffset(0)}
          >
            {str('plan_this_week')}
          </Button>
        )}
        <DayRow dates={MealWeek.days(start)} today={today} onPick={(date) => showDay(daysList.current, date)} />
      </div>
      {plan === null ? (
        <div className="sn-centered">{!loadFailed && <Progress />}</div>
      ) : (
        <PlanDaysList
          days={days}
          members={members}
          myUid={me.user.uid}
          recipeIds={new Set(recipeList.map((r) => r.id))}
          onAdd={(date, slot) => setSheet(PlanDrafts.addMeal(date, slot))}
          onOpen={(meal) => setSheet(PlanDrafts.meal(meal))}
          loaded={plan.start === start}
          onCopyLastWeek={copyLastWeek}
          listRef={daysList}
        />
      )}
      {adding && (
        <AddMealSheetFor
          planned={MealWeek.plannedFrom(weekMeals, today)}
          draft={adding}
          onDraft={editSheet}
          recipes={recipeList}
          members={members}
          myUid={me.user.uid}
          onDismiss={closeSheet}
          onAddRecipe={(recipe, cooks) => {
            closeSheet();
            act(() =>
              mealPlanService.addRecipe(
                hid,
                actor,
                adding.date,
                adding.slot,
                recipe,
                cooks,
                slotMeals(adding.date, adding.slot),
              ),
            );
          }}
          onAddOther={(title, cooks) => {
            closeSheet();
            act(() =>
              mealPlanService.addOther(
                hid,
                actor,
                adding.date,
                adding.slot,
                title,
                cooks,
                slotMeals(adding.date, adding.slot),
              ),
            );
          }}
        />
      )}
      {openMeal && sheet?.kind === 'meal' && (
        <MealSheet
          key={openMeal.id}
          meal={openMeal}
          draft={sheet}
          onDraft={editSheet}
          members={members}
          myUid={me.user.uid}
          recipeExists={recipeOf(openMeal) !== null}
          recipeServings={recipeOf(openMeal)?.content.servings ?? null}
          weekDays={MealWeek.days(start)}
          onDismiss={closeSheet}
          onSave={(servings, cooks, note) => {
            closeSheet();
            act(() => mealPlanService.update(hid, actor, openMeal, servings, cooks, note));
          }}
          onCook={() => {
            closeSheet();
            cook(openMeal);
          }}
          onNotCooked={() => {
            closeSheet();
            act(() => mealPlanService.notCooked(hid, actor, openMeal));
          }}
          onMove={(date, slot) => {
            closeSheet();
            withUndo(
              () => mealPlanService.move(hid, actor, openMeal, date, slot, slotMeals(date, slot)),
              str('plan_moved', openMeal.title),
            );
          }}
          onOpenRecipe={() => {
            closeSheet();
            if (openMeal.recipeId) onOpenRecipe(openMeal.recipeId);
          }}
          onRemove={() => {
            closeSheet();
            withUndo(() => mealPlanService.remove(hid, actor, openMeal), str('plan_removed', openMeal.title));
          }}
        />
      )}
      {cookingId && findMeal(cookingId) && recipeOf(findMeal(cookingId)!) && (
        <CookedSheetFor
          meal={findMeal(cookingId)!}
          recipe={recipeOf(findMeal(cookingId)!)!}
          onDismiss={() => setCookingId(null)}
          onDone={(meal, recipe, amounts, pantry) => {
            setCookingId(null);
            withUndo(
              () => mealPlanService.cooked(hid, actor, meal, recipe, amounts, pantry),
              str('plan_cooked_done', meal.title),
            );
          }}
        />
      )}
      {shopping && (
        <WeekGroceryFor
          days={days}
          recipes={recipeList}
          onDismiss={() => setShopping(false)}
          onAdded={(listName) => {
            setShopping(false);
            snackbars.show(str('recipe_added_to_list', listName));
          }}
        />
      )}
    </TabScaffold>
  );
}

function PlanMenu({ canClear, onCopy, onClear }: { canClear: boolean; onCopy: () => void; onClear: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <span className="sn-anchor">
      <IconButton
        icon="round/more_vert"
        label={str('plan_more_actions')}
        data-testid="plan_menu"
        onClick={() => setOpen(true)}
      />
      <Menu open={open} onDismiss={() => setOpen(false)}>
        <MenuItem
          label={str('plan_copy_last_week')}
          testId="plan_copy_last_week"
          onClick={() => {
            setOpen(false);
            onCopy();
          }}
        />
        <MenuItem
          label={str('plan_clear_week')}
          testId="plan_clear_week"
          disabled={!canClear}
          onClick={() => {
            setOpen(false);
            onClear();
          }}
        />
      </Menu>
    </span>
  );
}

/** "Oct 26 – Nov 1" between Previous and Next week, on a round bar of the row color (DESIGN 19.12). */
function WeekSwitcher({ start, onPrevious, onNext }: { start: LocalDate; onPrevious: () => void; onNext: () => void }) {
  return (
    <div className="sn-week-bar">
      <IconButton
        icon="round/keyboard_arrow_left"
        label={str('plan_previous_week')}
        data-testid="plan_previous_week"
        onClick={onPrevious}
      />
      <h2 className="t-section-title sn-week-bar__range" data-testid="plan_week_range">
        {str('plan_week_range', shortDate(start), shortDate(MealWeek.end(start)))}
      </h2>
      <IconButton
        icon="round/keyboard_arrow_right"
        label={str('plan_next_week')}
        data-testid="plan_next_week"
        onClick={onNext}
      />
    </div>
  );
}

/**
 * The week's days as buttons (DESIGN 19.12; the row of days in the app), in the week's order: the weekday's short
 * name over the day of the month, today's filled. Each is named in full for VoiceOver ("Saturday, Nov 14, today")
 * and shows that day. From 1.5× text the row scrolls sideways instead of shrinking (plan.css).
 */
function DayRow({ dates, today, onPick }: { dates: LocalDate[]; today: LocalDate; onPick: (date: LocalDate) => void }) {
  return (
    <div className="sn-day-row" data-testid="plan_day_row">
      {dates.map((date) => {
        const isToday = date === today;
        const title = dayTitle(date);
        return (
          <button
            key={date}
            type="button"
            className={`sn-day-button m3-state ${isToday ? 'sn-day-button--today' : ''}`}
            aria-label={isToday ? str('plan_day_today', title) : title}
            aria-description={str('plan_show_day')}
            data-testid={`plan_day_button_${date}`}
            onClick={() => onPick(date)}
          >
            <span className="t-chip-label sn-day-button__weekday" aria-hidden>
              {shortWeekday(date)}
            </span>
            <span className="t-section-title sn-day-button__number" aria-hidden>
              {Number(date.slice(8, 10))}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function PlanDaysList({
  days,
  members,
  myUid,
  recipeIds,
  onAdd,
  onOpen,
  loaded,
  onCopyLastWeek,
  listRef,
}: {
  days: PlanDay[];
  members: Member[];
  myUid: string;
  recipeIds: Set<string>;
  onAdd: (date: LocalDate, slot: MealSlot) => void;
  onOpen: (meal: PlannedMeal) => void;
  /** The meals are the shown week's, not the last week's while the new one loads. */
  loaded: boolean;
  onCopyLastWeek: () => void;
  listRef: RefObject<HTMLDivElement | null>;
}) {
  const firstDate = days[0]?.date;
  // An empty week says how to fill it, with Copy last week out of the ⋮ menu too (after v1, UX-15).
  const empty = loaded && days.every(PlanDays.isEmpty);
  // This week opens on today, not on the first day; an empty one at its sentence.
  useEffect(() => {
    const node = listRef.current;
    if (!node || !loaded) return;
    const today = empty ? null : node.querySelector<HTMLElement>('[data-today]');
    node.scrollTop = today ? dayTop(node, today) : 0;
    // Once per week, when its meals arrive: the first meal added to an empty week must not move the list.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [firstDate, loaded]);
  return (
    <div className="sn-plan-days" data-testid="plan_days" ref={listRef}>
      {empty && <EmptyWeek onCopyLastWeek={onCopyLastWeek} />}
      {days.map((day) => (
        <DayCard
          key={day.date}
          day={day}
          members={members}
          myUid={myUid}
          recipeIds={recipeIds}
          onAdd={(slot) => onAdd(day.date, slot)}
          onOpen={onOpen}
        />
      ))}
    </div>
  );
}

/** Where [day] starts in [list]'s content, less the list's 12 px of padding, so it shows at the top. */
const dayTop = (list: HTMLElement, day: HTMLElement) => day.offsetTop - list.offsetTop - 12;

/** Scrolls [list] to the card of [date]: a jump when the phone reduces motion. The last days go as far as it goes. */
function showDay(list: HTMLElement | null, date: LocalDate) {
  const day = list?.querySelector<HTMLElement>(`[data-day="${date}"]`);
  if (!list || !day) return;
  const reduce = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  list.scrollTo({ top: dayTop(list, day), behavior: reduce ? 'auto' : 'smooth' });
}

/** "Nothing planned this week…" with Copy last week (after v1, UX-15), on a plain card (DESIGN 19.8). */
function EmptyWeek({ onCopyLastWeek }: { onCopyLastWeek: () => void }) {
  return (
    <PlainCard className="sn-plan-empty" data-testid="plan_week_empty">
      <p className="t-body-large">{str('plan_week_empty')}</p>
      <Button variant="tonal" large className="t-button-medium" data-testid="plan_empty_copy" onClick={onCopyLastWeek}>
        {str('plan_copy_last_week')}
      </Button>
    </PlainCard>
  );
}

/**
 * One day (DESIGN 5.9, 19.8): today is the highlight card with a Today badge, never color alone; any other day is a
 * heading over a group of rows, a past day's heading in onSurfaceVariant. Each slot is a row with + to add to it, or
 * its meals, the first of them carrying the +.
 */
function DayCard({
  day,
  members,
  myUid,
  recipeIds,
  onAdd,
  onOpen,
}: {
  day: PlanDay;
  members: Member[];
  myUid: string;
  recipeIds: Set<string>;
  onAdd: (slot: MealSlot) => void;
  onOpen: (meal: PlannedMeal) => void;
}) {
  const title = dayTitle(day.date);
  const rows = (
    <ListGroup nested={day.isToday}>
      {MEAL_SLOTS.flatMap((slot) => {
        const add = (
          <IconButton
            icon="round/add"
            label={str('plan_add_to', slotLabel(slot), title)}
            data-testid={`plan_add_${day.date}_${slot}`}
            onClick={() => onAdd(slot)}
          />
        );
        const meals = day.slots[slot];
        if (meals.length === 0) {
          return [
            <div key={slot} className="sn-segment sn-plan-slot">
              <span className="t-label-large sn-plan-slot__name">{slotLabel(slot)}</span>
              {add}
            </div>,
          ];
        }
        return meals.map((meal, i) => (
          <MealRow
            key={meal.id}
            meal={meal}
            names={cookNames(meal.cooks, members, myUid)}
            deleted={meal.recipeId !== null && !recipeIds.has(meal.recipeId)}
            add={i === 0 ? add : null}
            onOpen={() => onOpen(meal)}
          />
        ));
      })}
    </ListGroup>
  );
  if (day.isToday) {
    return (
      <HighlightCard className="sn-plan-today" data-testid={`plan_day_${day.date}`} data-day={day.date} data-today>
        <div className="sn-plan-today__header">
          <h2 className="t-card-title">{title}</h2>
          <span className="sn-plan-today__badge t-chip-label">{str('plan_today')}</span>
        </div>
        {rows}
      </HighlightCard>
    );
  }
  return (
    <section className="sn-plan-day" data-testid={`plan_day_${day.date}`} data-day={day.date}>
      <h2 className={`t-section-title sn-plan-day__title ${day.isPast ? 'c-on-surface-variant' : ''}`}>{title}</h2>
      {rows}
    </section>
  );
}

/**
 * A planned meal (DESIGN 5.9, 19.5): its slot over its title, then the servings, who cooks and Cooked. The row is one
 * button read as one sentence ("Dinner: Tacos, 4 servings, cooking: Sam, Cooked"); [add], the slot's +, is its own.
 */
function MealRow({
  meal,
  names,
  deleted,
  add,
  onOpen,
}: {
  meal: PlannedMeal;
  names: string[];
  deleted: boolean;
  add: ReactNode;
  onOpen: () => void;
}) {
  const details = [
    deleted ? str('plan_recipe_deleted') : null,
    meal.servings !== null ? plural('plan_servings', meal.servings, meal.servings) : null,
  ].filter((d): d is string => d !== null);
  const cooked = meal.cookedAt !== null;
  const sentence = [
    str('home_today_meal_description', slotLabel(meal.slot), meal.title),
    ...details,
    names.length > 0 ? str('home_today_cooking', names.join(', ')) : null,
    cooked ? str('plan_cooked') : null,
  ]
    .filter((p): p is string => p !== null)
    .join(', ');
  return (
    <div className="sn-segment sn-plan-meal">
      <button
        type="button"
        className="sn-plan-meal__main m3-state"
        aria-label={sentence}
        aria-description={str('plan_open_meal')}
        data-testid={`plan_meal_${meal.id}`}
        onClick={onOpen}
      >
        <span className="t-row-supporting c-on-surface-variant" aria-hidden>
          {slotLabel(meal.slot)}
        </span>
        <span className="t-row-title sn-plan-meal__title" aria-hidden>
          {meal.title}
        </span>
        {(details.length > 0 || names.length > 0 || cooked) && (
          <span className="sn-plan-meal__details" aria-hidden>
            {details.length > 0 && <span className="t-row-supporting c-on-surface-variant">{details.join(' · ')}</span>}
            {names.map((name) => (
              <LabelChip key={name} text={name} icon="round/person" kind="person" />
            ))}
            {cooked && <CookedLabel />}
          </span>
        )}
      </button>
      {add}
    </div>
  );
}

/**
 * The add sheet with the pantry for Use it up, leaving out the recipes the shown week already plans from today on
 * ([planned], UX-05); shown once the pantry has loaded, so it opens at its full height.
 */
function AddMealSheetFor({
  planned,
  ...props
}: Omit<Parameters<typeof AddMealSheet>[0], 'useItUp'> & { planned: ReadonlySet<string> }) {
  const me = useHouseholdSession();
  const { pantry, clock } = useServices();
  const today = useToday(clock);
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(me.householdId).pipe(retryLoading(undefined, NO_PANTRY)), [pantry, me.householdId]),
    null,
  );
  if (data === null) return null;
  return <AddMealSheet {...props} useItUp={UseItUp.suggestions(props.recipes, data, today, planned)} />;
}

/** The Cooked sheet for a recipe meal: the pantry products it uses, at the meal's servings. */
function CookedSheetFor({
  meal,
  recipe,
  onDismiss,
  onDone,
}: {
  meal: PlannedMeal;
  recipe: Recipe;
  onDismiss: () => void;
  onDone: (meal: PlannedMeal, recipe: Recipe, amounts: Map<string, number>, pantry: PantryData) => void;
}) {
  const me = useHouseholdSession();
  const { pantry } = useServices();
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(me.householdId).pipe(retryLoading(undefined, NO_PANTRY)), [pantry, me.householdId]),
    null,
  );
  if (data === null) return null;
  return (
    <CookedSheet
      lines={Cooking.lines(recipe, data, recipeWords(), MealPlanGrocery.factor(meal, recipe))}
      markCooked
      onDismiss={onDismiss}
      onUse={(amounts) => onDone(meal, recipe, amounts, data)}
    />
  );
}

/** The week's ingredients with the lists, items and pantry it reads while open; shown once loaded. */
function WeekGroceryFor({
  days,
  recipes,
  onDismiss,
  onAdded,
}: {
  days: PlanDay[];
  recipes: Recipe[];
  onDismiss: () => void;
  onAdded: (listName: string) => void;
}) {
  const me = useHouseholdSession();
  const { grocery, pantry, recipeService } = useServices();
  const snackbars = useSnackbars();
  const hid = me.householdId;
  const [chosenList, setChosenList] = useState<string | null>(null);
  const lists = useObservable<GroceryList[] | null>(
    useMemo(() => grocery.observeLists(hid).pipe(retryLoading<GroceryList[]>(undefined, [])), [grocery, hid]),
    null,
  );
  const list = lists?.find((l) => l.id === chosenList) ?? lists?.find((l) => l.isDefault) ?? lists?.[0] ?? null;
  const listId = list?.id ?? null;
  const items = useObservable<GroceryItem[] | null>(
    useMemo(
      () =>
        of(listId).pipe(
          switchMap((id) => (id === null ? of([]) : grocery.observeItems(hid, id))),
          retryLoading<GroceryItem[]>(undefined, []),
        ),
      [grocery, hid, listId],
    ),
    null,
  );
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(hid).pipe(retryLoading(undefined, NO_PANTRY)), [pantry, hid]),
    null,
  );
  // Shown once loaded: a sheet that grows after it opens keeps its first resting place and hides its button.
  if (lists === null || items === null || data === null) return null;
  const sections = MealPlanGrocery.sections(MealPlanGrocery.toShop(days, recipes), data, items, recipeWords());
  return (
    <WeekGrocerySheet
      lists={lists}
      listId={listId}
      sections={sections}
      products={data.products}
      onChooseList={setChosenList}
      onDismiss={onDismiss}
      onAdd={(chosen) => {
        if (!list) return;
        recipeService
          .addItems(
            hid,
            { uid: me.user.uid, name: me.displayName },
            list.id,
            chosen,
            data,
            list.name,
            'meal_plan',
            str('plan_shop_from'),
          )
          .then(
            (count) => (count > 0 ? onAdded(list.name) : onDismiss()),
            (e: unknown) => snackbars.show(str(messageId(e))),
          );
      }}
    />
  );
}
