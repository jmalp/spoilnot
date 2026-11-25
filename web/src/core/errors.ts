// Typed errors (DESIGN 7.5; AppError and ErrorMessages in the app): repositories throw these, screens show the
// message for each, and nothing outside src/data sees Firebase error types.
import { str, type StringId } from './i18n';

export type AppErrorKind =
  | 'permissionDenied'
  | 'quotaExceeded'
  | 'offline'
  | 'notFound'
  | 'conflict'
  | 'nameTaken'
  | 'invalidCredentials'
  | 'emailInUse'
  | 'invalidEmail'
  | 'weakPassword'
  | 'tooManyAttempts'
  | 'recentLoginRequired'
  | 'inviteCodeInactive'
  | 'ownerMustTransferFirst'
  | 'lostRace'
  | 'unknown';

/**
 * What another phone finished first when the rules refused this phone's whole write (DESIGN 8.5; AppError.LostRace
 * in the app): Add to pantry or a removal of items already taken off the list, Cooked on a meal already cooked, or an
 * item added to a deleted list.
 */
export type Race = 'addedToPantry' | 'removed' | 'cooked' | 'listDeleted';

export class AppError extends Error {
  /** For lostRace: what was lost, and the member whose change won when it is known. */
  readonly race: Race | null;
  readonly byUid: string | null;

  constructor(
    readonly kind: AppErrorKind,
    options?: { cause?: unknown; race?: Race; byUid?: string | null },
  ) {
    super(kind, options);
    this.name = 'AppError';
    this.race = options?.race ?? null;
    this.byUid = options?.byUid ?? null;
  }
}

/** Another phone finished the same thing first (DESIGN 8.5). */
export const lostRace = (race: Race, byUid: string | null = null) => new AppError('lostRace', { race, byUid });

export const isAppError = (error: unknown, kind?: AppErrorKind): error is AppError =>
  error instanceof AppError && (kind === undefined || error.kind === kind);

const MESSAGES: Record<AppErrorKind, StringId> = {
  invalidCredentials: 'error_invalid_credentials',
  emailInUse: 'error_email_in_use',
  invalidEmail: 'error_email_invalid',
  weakPassword: 'error_weak_password',
  tooManyAttempts: 'error_too_many_attempts',
  recentLoginRequired: 'error_recent_login',
  offline: 'error_offline',
  permissionDenied: 'error_permission',
  quotaExceeded: 'error_quota',
  notFound: 'error_not_found',
  conflict: 'error_conflict',
  nameTaken: 'error_name_taken',
  inviteCodeInactive: 'error_code_inactive',
  ownerMustTransferFirst: 'error_owner_must_transfer',
  lostRace: 'race_removed',
  unknown: 'error_unknown',
};

const RACES: Record<Race, { named: StringId | null; unnamed: StringId }> = {
  addedToPantry: { named: 'race_pantry_by', unnamed: 'race_pantry' },
  removed: { named: 'race_removed_by', unnamed: 'race_removed' },
  cooked: { named: 'race_cooked_by', unnamed: 'race_cooked' },
  listDeleted: { named: null, unnamed: 'race_list_deleted' },
};

/** What to tell the user about a failure: what happened and what to do (DESIGN 6.9). */
export function messageId(error: unknown): StringId {
  if (!(error instanceof AppError)) return 'error_unknown';
  return error.race !== null ? RACES[error.race].unnamed : MESSAGES[error.kind];
}

/**
 * [messageId] as text. A lost race names the member whose change won (DESIGN 8.5), from [nameOf]; it says it without
 * a name when that is unknown or [myUid] (the same account on another phone).
 */
export function errorText(
  error: unknown,
  nameOf: (uid: string) => string | undefined = () => undefined,
  myUid?: string,
): string {
  if (error instanceof AppError && error.race !== null) {
    const { named, unnamed } = RACES[error.race];
    const name = error.byUid !== null && error.byUid !== myUid ? nameOf(error.byUid) : undefined;
    return named !== null && name !== undefined ? str(named, name) : str(unnamed);
  }
  return str(messageId(error));
}
