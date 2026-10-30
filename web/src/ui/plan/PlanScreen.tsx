import { useEffect, useMemo, useRef, useState } from 'react';
import { catchError, filter, firstValueFrom, map, of, switchMap, take, timeout } from 'rxjs';
import { messageId } from '../../core/errors';
import { useObservable, useToday } from '../../core/hooks';
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
import { addDays, sortedWith, type LocalDate } from '../../domain/time';
import type { Undo } from '../../domain/write';
import { Button, Card, Dialog, IconButton, Menu, MenuItem, Progress, useSnackbars } from '../kit';
import { CookedSheet } from '../recipes/RecipeSheets';
import { OfflineBanner, TabScaffold, useOnInnerScroll } from '../Scaffold';
import { CookChip, CookedLabel, cookNames, dayTitle, phoneFirstDay, shortDate, slotLabel } from './components';
import { AddMealSheet, MealSheet, WeekGrocerySheet } from './PlanSheets';
import '../recipes/recipes.css';
import './plan.css';

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
  const { mealPlan, mealPlanService, households, recipes, writer, clock } = useServices();
  const snackbars = useSnackbars();
  const today = useToday(clock);
  const hid = me.householdId;
  const actor = useMemo(() => ({ uid: me.user.uid, name: me.displayName }), [me]);
  const firstDay = useMemo(() => phoneFirstDay(), []);
  const thisWeek = MealWeek.start(today, firstDay);
  const start = addDays(thisWeek, weekOffset * 7);

  const plan = useObservable<MealPlanData | null>(
    useMemo(() => mealPlan.observe(hid, start, MealWeek.end(start)), [mealPlan, hid, start]),
    null,
  );
  const members = useObservable<Member[]>(
    useMemo(
      () =>
        households.observeMembers(hid).pipe(
          map((all) => sortedWith(all, (a, b) => naturalOrder(a.displayName, b.displayName))),
          catchError(() => of([])),
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

  const [adding, setAdding] = useState<{ date: LocalDate; slot: MealSlot } | null>(null);
  const [openMealId, setOpenMealId] = useState<string | null>(null);
  const [cookingId, setCookingId] = useState<string | null>(null);
  const [shopping, setShopping] = useState(false);
  const [confirmClear, setConfirmClear] = useState(false);
  const findMeal = (id: string | null) => (id === null ? null : (weekMeals.find((m) => m.id === id) ?? null));
  const openMeal = findMeal(openMealId);
  const recipeOf = (meal: PlannedMeal) => recipeList.find((r) => r.id === meal.recipeId) ?? null;

  useEffect(() => {
    const subscription = writer.errors$.subscribe((e) => snackbars.show(str(messageId(e))));
    return () => subscription.unsubscribe();
  }, [writer, snackbars]);

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
      else snackbars.showUndo(str('plan_copied', copied[0]), () => void mealPlanService.undo(hid, actor, copied[1]));
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
          <PlanMenu canClear={weekMeals.length > 0} onCopy={copyLastWeek} onClear={() => setConfirmClear(true)} />
        </>
      }
    >
      {plan?.fromCache && <OfflineBanner />}
      <WeekHeader
        start={start}
        isThisWeek={weekOffset === 0}
        onPrevious={() => onWeekOffset(weekOffset - 1)}
        onNext={() => onWeekOffset(weekOffset + 1)}
        onThisWeek={() => onWeekOffset(0)}
      />
      <hr className="sn-plan-divider" />
      {plan === null ? (
        <div className="sn-centered">
          <Progress />
        </div>
      ) : (
        <PlanDaysList
          days={days}
          members={members}
          myUid={me.user.uid}
          recipeIds={new Set(recipeList.map((r) => r.id))}
          onAdd={(date, slot) => setAdding({ date, slot })}
          onOpen={(meal) => setOpenMealId(meal.id)}
        />
      )}
      {confirmClear && (
        <Dialog
          title={str('plan_clear_title')}
          onDismiss={() => setConfirmClear(false)}
          actions={
            <>
              <Button variant="text" onClick={() => setConfirmClear(false)}>
                {str('action_cancel')}
              </Button>
              <Button
                variant="text"
                data-testid="plan_clear_confirm"
                onClick={() => {
                  setConfirmClear(false);
                  withUndo(() => mealPlanService.clear(hid, actor, weekMeals), str('plan_cleared'));
                }}
              >
                {str('plan_clear_confirm')}
              </Button>
            </>
          }
        >
          <p className="t-body-medium c-on-surface-variant">
            {plural(
              'plan_clear_text',
              weekMeals.length,
              weekMeals.length,
              str('plan_week_range', shortDate(start), shortDate(MealWeek.end(start))),
            )}
          </p>
        </Dialog>
      )}
      {adding && (
        <AddMealSheetFor
          date={adding.date}
          slot={adding.slot}
          recipes={recipeList}
          members={members}
          myUid={me.user.uid}
          onDismiss={() => setAdding(null)}
          onAddRecipe={(recipe, cooks) => {
            setAdding(null);
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
            setAdding(null);
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
      {openMeal && (
        <MealSheet
          key={openMeal.id}
          meal={openMeal}
          members={members}
          myUid={me.user.uid}
          recipeExists={recipeOf(openMeal) !== null}
          weekDays={MealWeek.days(start)}
          onDismiss={() => setOpenMealId(null)}
          onSave={(servings, cooks, note) => {
            setOpenMealId(null);
            act(() => mealPlanService.update(hid, actor, openMeal, servings, cooks, note));
          }}
          onCook={() => {
            setOpenMealId(null);
            cook(openMeal);
          }}
          onNotCooked={() => {
            setOpenMealId(null);
            act(() => mealPlanService.notCooked(hid, actor, openMeal));
          }}
          onMove={(date, slot) => {
            setOpenMealId(null);
            withUndo(
              () => mealPlanService.move(hid, actor, openMeal, date, slot, slotMeals(date, slot)),
              str('plan_moved', openMeal.title),
            );
          }}
          onOpenRecipe={() => {
            setOpenMealId(null);
            if (openMeal.recipeId) onOpenRecipe(openMeal.recipeId);
          }}
          onRemove={() => {
            setOpenMealId(null);
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

function WeekHeader({
  start,
  isThisWeek,
  onPrevious,
  onNext,
  onThisWeek,
}: {
  start: LocalDate;
  isThisWeek: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onThisWeek: () => void;
}) {
  return (
    <>
      <div className="sn-week-header">
        <IconButton
          icon="round/keyboard_arrow_left"
          label={str('plan_previous_week')}
          data-testid="plan_previous_week"
          onClick={onPrevious}
        />
        <h2 className="t-title-medium sn-week-header__range" data-testid="plan_week_range">
          {str('plan_week_range', shortDate(start), shortDate(MealWeek.end(start)))}
        </h2>
        <IconButton
          icon="round/keyboard_arrow_right"
          label={str('plan_next_week')}
          data-testid="plan_next_week"
          onClick={onNext}
        />
      </div>
      {!isThisWeek && (
        <Button variant="text" large className="sn-week-this" data-testid="plan_this_week" onClick={onThisWeek}>
          {str('plan_this_week')}
        </Button>
      )}
    </>
  );
}

function PlanDaysList({
  days,
  members,
  myUid,
  recipeIds,
  onAdd,
  onOpen,
}: {
  days: PlanDay[];
  members: Member[];
  myUid: string;
  recipeIds: Set<string>;
  onAdd: (date: LocalDate, slot: MealSlot) => void;
  onOpen: (meal: PlannedMeal) => void;
}) {
  const onScroll = useOnInnerScroll();
  const list = useRef<HTMLDivElement>(null);
  const firstDate = days[0]?.date;
  // This week opens on today, not on the first day.
  useEffect(() => {
    const node = list.current;
    if (!node) return;
    const today = node.querySelector<HTMLElement>('[data-today]');
    node.scrollTop = today ? today.offsetTop - node.offsetTop - 16 : 0;
  }, [firstDate]);
  return (
    <div className="sn-plan-days" data-testid="plan_days" ref={list} onScroll={onScroll}>
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
  return (
    <Card
      className={`sn-day-card ${day.isToday ? 'sn-day-card--today' : ''} ${day.isPast ? 'sn-day-card--past' : ''}`}
      data-testid={`plan_day_${day.date}`}
      data-today={day.isToday || undefined}
    >
      <div className="sn-day-card__header">
        <h2 className="t-title-medium">{title}</h2>
        {day.isToday && <span className="sn-today-badge t-label-medium">{str('plan_today')}</span>}
      </div>
      {MEAL_SLOTS.map((slot) => (
        <div key={slot}>
          <div className="sn-slot-row">
            <span className="t-label-large c-on-surface-variant">{slotLabel(slot)}</span>
            <IconButton
              icon="round/add"
              label={str('plan_add_to', slotLabel(slot), title)}
              data-testid={`plan_add_${day.date}_${slot}`}
              onClick={() => onAdd(slot)}
            />
          </div>
          {day.slots[slot].map((meal) => (
            <MealRow
              key={meal.id}
              meal={meal}
              names={cookNames(meal.cooks, members, myUid)}
              deleted={meal.recipeId !== null && !recipeIds.has(meal.recipeId)}
              onOpen={() => onOpen(meal)}
            />
          ))}
        </div>
      ))}
    </Card>
  );
}

function MealRow({
  meal,
  names,
  deleted,
  onOpen,
}: {
  meal: PlannedMeal;
  names: string[];
  deleted: boolean;
  onOpen: () => void;
}) {
  const details = [
    deleted ? str('plan_recipe_deleted') : null,
    meal.servings !== null ? plural('plan_servings', meal.servings, meal.servings) : null,
  ].filter((d): d is string => d !== null);
  return (
    <button
      type="button"
      className="sn-meal-row m3-state"
      aria-description={str('plan_open_meal')}
      data-testid={`plan_meal_${meal.id}`}
      onClick={onOpen}
    >
      <span className="t-body-large sn-meal-row__title">{meal.title}</span>
      {(details.length > 0 || names.length > 0 || meal.cookedAt !== null) && (
        <span className="sn-meal-row__details">
          {details.length > 0 && <span className="t-body-small c-on-surface-variant">{details.join(' · ')}</span>}
          {names.map((name) => (
            <CookChip key={name} name={name} />
          ))}
          {meal.cookedAt !== null && <CookedLabel />}
        </span>
      )}
    </button>
  );
}

/** The add sheet with the pantry for Use it up; shown once it has loaded, so it opens at its full height. */
function AddMealSheetFor(props: Omit<Parameters<typeof AddMealSheet>[0], 'useItUp'>) {
  const me = useHouseholdSession();
  const { pantry, clock } = useServices();
  const today = useToday(clock);
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(me.householdId).pipe(catchError(() => of(NO_PANTRY))), [pantry, me.householdId]),
    null,
  );
  if (data === null) return null;
  return <AddMealSheet {...props} useItUp={UseItUp.suggestions(props.recipes, data, today)} />;
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
    useMemo(() => pantry.observe(me.householdId).pipe(catchError(() => of(NO_PANTRY))), [pantry, me.householdId]),
    null,
  );
  if (data === null) return null;
  return (
    <CookedSheet
      lines={Cooking.lines(recipe, data, MealPlanGrocery.factor(meal, recipe))}
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
    useMemo(() => grocery.observeLists(hid).pipe(catchError(() => of([]))), [grocery, hid]),
    null,
  );
  const list = lists?.find((l) => l.id === chosenList) ?? lists?.find((l) => l.isDefault) ?? lists?.[0] ?? null;
  const listId = list?.id ?? null;
  const items = useObservable<GroceryItem[] | null>(
    useMemo(
      () =>
        of(listId).pipe(
          switchMap((id) => (id === null ? of([]) : grocery.observeItems(hid, id))),
          catchError(() => of([])),
        ),
      [grocery, hid, listId],
    ),
    null,
  );
  const data = useObservable<PantryData | null>(
    useMemo(() => pantry.observe(hid).pipe(catchError(() => of(NO_PANTRY))), [pantry, hid]),
    null,
  );
  // Shown once loaded: a sheet that grows after it opens keeps its first resting place and hides its button.
  if (lists === null || items === null || data === null) return null;
  const sections = MealPlanGrocery.sections(MealPlanGrocery.toShop(days, recipes), data, items);
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
