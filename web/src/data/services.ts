import { AppPreferences } from '../core/preferences';
import type { Services } from '../core/services';
import { APP_VERSION } from '../core/version';
import { RestockRunner } from '../domain/grocery/restock';
import { GroceryService } from '../domain/grocery/service';
import { PantryService } from '../domain/pantry/service';
import { MealPlanService } from '../domain/plan/service';
import { RecipeService } from '../domain/recipes/service';
import { RecipeStore } from '../domain/recipes/store';
import { DeviceSync } from '../domain/notifications';
import { SessionManager } from '../domain/session';
import { systemClock } from '../domain/time';
import { FirestoreActivityRepository } from './activity';
import { FirebaseAuthRepository } from './auth';
import { FirestoreHouseholdExporter } from './export';
import { FcmDeviceRegistry, FirestoreNotificationPrefsRepository } from './notifications';
import { FirestoreGroceryRepository, groceryMapper } from './grocery';
import { firebase, type FirebaseSdk } from './firebase';
import { FirestoreHouseholdRepository, FirestoreLocalData } from './households';
import { FirestorePantryRepository, pantryMapper } from './pantry';
import { FirestoreMealPlanRepository, mealPlanMapper } from './plan';
import { FirestoreRecipeRepository, recipeMapper } from './recipes';
import { FirestoreStatsRepository, statsMapper } from './stats';
import { FirestoreUserRepository } from './users';
import { SyncLog } from './delta-sync';
import { FirestoreHouseholdWriter } from './writer';

/** The real services, on Firebase (the app's Hilt modules). */
export function createServices(sdk: FirebaseSdk = firebase()): Services {
  const auth = new FirebaseAuthRepository(sdk);
  const users = new FirestoreUserRepository(sdk);
  const households = new FirestoreHouseholdRepository(sdk);
  const devices = new FcmDeviceRegistry(sdk, APP_VERSION);
  const session = new SessionManager(auth, users, households, new FirestoreLocalData(sdk), devices);
  const clock = systemClock;
  const writer = new FirestoreHouseholdWriter(sdk, [
    pantryMapper,
    groceryMapper,
    recipeMapper,
    mealPlanMapper,
    statsMapper,
  ]);
  const log = new SyncLog(() => clock.now());
  const pantry = new FirestorePantryRepository(sdk, log);
  const pantryService = new PantryService(writer, clock);
  const grocery = new FirestoreGroceryRepository(sdk, log);
  const recipeService = new RecipeService(writer);
  return {
    session,
    auth,
    users,
    households,
    writer,
    pantry,
    pantryService,
    grocery,
    groceryService: new GroceryService(writer, grocery, pantryService),
    restock: new RestockRunner(session, pantry, grocery, writer, clock),
    recipes: new RecipeStore(session, new FirestoreRecipeRepository(sdk, log)),
    recipeService,
    mealPlan: new FirestoreMealPlanRepository(sdk, log),
    mealPlanService: new MealPlanService(writer, pantryService, recipeService),
    activity: new FirestoreActivityRepository(sdk, log),
    stats: new FirestoreStatsRepository(sdk),
    exporter: new FirestoreHouseholdExporter(sdk, clock, APP_VERSION),
    notificationPrefs: new FirestoreNotificationPrefsRepository(sdk),
    deviceSync: new DeviceSync(session.session$, devices),
    preferences: new AppPreferences(),
    clock,
  };
}
