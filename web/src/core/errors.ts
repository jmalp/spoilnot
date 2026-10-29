// Typed errors (DESIGN 7.5; AppError and ErrorMessages in the app): repositories throw these, screens show the
// message for each, and nothing outside src/data sees Firebase error types.
import type { StringId } from './i18n';

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
  | 'unknown';

export class AppError extends Error {
  constructor(
    readonly kind: AppErrorKind,
    options?: { cause?: unknown },
  ) {
    super(kind, options);
    this.name = 'AppError';
  }
}

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
  unknown: 'error_unknown',
};

/** What to tell the user about a failure: what happened and what to do (DESIGN 6.9). */
export function messageId(error: unknown): StringId {
  return error instanceof AppError ? MESSAGES[error.kind] : 'error_unknown';
}
