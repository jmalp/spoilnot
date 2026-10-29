// Accounts and profiles (DESIGN 5.1, 8.2), ported from feature/auth/domain.

/** The signed-in account, without Firebase types. */
export type AuthUser = { uid: string; email: string | null; displayName: string | null; emailVerified: boolean };

export type Units = 'metric' | 'imperial';

/** Where the user's one waiting join request was sent. */
export type PendingJoin = { householdId: string; householdName: string; code: string };

/** users/{uid}: the private profile. */
export type UserProfile = {
  uid: string;
  displayName: string;
  householdId: string | null;
  pendingJoin: PendingJoin | null;
  units: Units;
};

/** Defaults for a new profile. The owner chose imperial (docs/DECISIONS.md). */
export const ProfileDefaults = {
  UNITS: 'imperial' as Units,
  LOCALE: 'en',
  DIGEST_MINUTES: 9 * 60,
  STAGES: [3, 2, 1, 0],
  EXPIRED_MODE: 'once',
} as const;

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Form checks for sign-up and sign-in. */
export const AccountValidation = {
  MIN_PASSWORD_LENGTH: 8,
  MAX_DISPLAY_NAME_LENGTH: 40,

  displayNameProblem(name: string): 'blank' | 'tooLong' | null {
    if (name.trim() === '') return 'blank';
    if (name.trim().length > this.MAX_DISPLAY_NAME_LENGTH) return 'tooLong';
    return null;
  },

  isEmailValid: (email: string) => EMAIL.test(email.trim()),

  isPasswordLongEnough(password: string): boolean {
    return password.length >= this.MIN_PASSWORD_LENGTH;
  },

  /** A display name for a profile that has none, such as "sam" for sam@example.com. */
  fallbackDisplayName(email: string | null): string {
    const local = email?.split('@')[0]?.slice(0, this.MAX_DISPLAY_NAME_LENGTH).trim();
    return local ? local : 'Me';
  },
};

/** Email and password accounts. Implementations throw AppError. */
export interface AuthRepository {
  /** The signed-in user, or null; emits on sign-in and sign-out. */
  readonly authUser$: import('rxjs').Observable<AuthUser | null>;
  currentUser(): AuthUser | null;
  /** Creates the account, sets its display name and sends a verification email; leaves the user signed in. */
  signUp(displayName: string, email: string, password: string): Promise<void>;
  signIn(email: string, password: string): Promise<void>;
  /** Succeeds whether or not the address has an account (no email enumeration). */
  sendPasswordReset(email: string): Promise<void>;
  sendEmailVerification(): Promise<void>;
  /** Refreshes the current user, for example to pick up a verified email. */
  reload(): Promise<AuthUser | null>;
  /** Checks the password without touching the current sign-in; throws invalidCredentials when it does not match. */
  verifyPassword(password: string): Promise<void>;
  reauthenticate(password: string): Promise<void>;
  signOut(): Promise<void>;
  /** Deletes the sign-in; the profile must be deleted first, while the user can still write it. */
  deleteAccount(): Promise<void>;
}

/** users/{uid}. Implementations throw AppError. */
export interface UserRepository {
  /** The profile, or null once the server confirms it does not exist. */
  observeProfile(uid: string): import('rxjs').Observable<UserProfile | null>;
  createProfileIfMissing(uid: string, displayName: string): Promise<void>;
  clearHousehold(uid: string): Promise<void>;
  /** Deletes the profile and its device tokens. */
  deleteProfile(uid: string): Promise<void>;
  setUnits(uid: string, units: Units): Promise<void>;
}
