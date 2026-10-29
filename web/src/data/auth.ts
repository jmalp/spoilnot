// Email and password accounts (feature/auth/data in the app).
import { deleteApp, FirebaseError, initializeApp } from 'firebase/app';
import {
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  deleteUser,
  EmailAuthProvider,
  inMemoryPersistence,
  initializeAuth,
  onAuthStateChanged,
  reauthenticateWithCredential,
  reload,
  sendEmailVerification,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signOut,
  updateProfile,
  type User,
} from 'firebase/auth';
import { Observable } from 'rxjs';
import { AppError } from '../core/errors';
import type { AuthRepository, AuthUser } from '../domain/account';
import type { FirebaseSdk } from './firebase';

const toAuthUser = (user: User): AuthUser => ({
  uid: user.uid,
  email: user.email,
  displayName: user.displayName,
  emailVerified: user.emailVerified,
});

function toAuthError(error: unknown): AppError {
  if (error instanceof AppError) return error;
  if (!(error instanceof FirebaseError)) return new AppError('unknown', { cause: error });
  switch (error.code) {
    case 'auth/weak-password':
      return new AppError('weakPassword', { cause: error });
    case 'auth/email-already-in-use':
      return new AppError('emailInUse', { cause: error });
    case 'auth/requires-recent-login':
      return new AppError('recentLoginRequired', { cause: error });
    case 'auth/invalid-email':
      return new AppError('invalidEmail', { cause: error });
    case 'auth/invalid-credential':
    case 'auth/invalid-login-credentials':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
    case 'auth/user-disabled':
      return new AppError('invalidCredentials', { cause: error });
    case 'auth/too-many-requests':
      return new AppError('tooManyAttempts', { cause: error });
    case 'auth/network-request-failed':
      return new AppError('offline', { cause: error });
    default:
      return new AppError('unknown', { cause: error });
  }
}

async function authMapped<T>(work: () => Promise<T>): Promise<T> {
  try {
    return await work();
  } catch (error) {
    throw toAuthError(error);
  }
}

export class FirebaseAuthRepository implements AuthRepository {
  readonly authUser$: Observable<AuthUser | null>;

  constructor(private readonly sdk: FirebaseSdk) {
    this.authUser$ = new Observable((subscriber) =>
      onAuthStateChanged(sdk.auth, (user) => subscriber.next(user ? toAuthUser(user) : null)),
    );
  }

  currentUser(): AuthUser | null {
    const user = this.sdk.auth.currentUser;
    return user ? toAuthUser(user) : null;
  }

  signUp(displayName: string, email: string, password: string): Promise<void> {
    return authMapped(async () => {
      const { user } = await createUserWithEmailAndPassword(this.sdk.auth, email, password);
      await updateProfile(user, { displayName });
      // Verification is encouraged, not required (DESIGN 5.1); a failed send must not fail sign-up.
      await sendEmailVerification(user).catch(() => undefined);
    });
  }

  signIn(email: string, password: string): Promise<void> {
    return authMapped(async () => {
      await signInWithEmailAndPassword(this.sdk.auth, email, password);
    });
  }

  sendPasswordReset(email: string): Promise<void> {
    return authMapped(async () => {
      try {
        await sendPasswordResetEmail(this.sdk.auth, email);
      } catch (error) {
        // Same outcome as success, so the screen never reveals whether an account exists.
        if (!(error instanceof FirebaseError && error.code === 'auth/user-not-found')) throw error;
      }
    });
  }

  sendEmailVerification(): Promise<void> {
    return authMapped(async () => {
      const user = this.sdk.auth.currentUser;
      if (user) await sendEmailVerification(user);
    });
  }

  reload(): Promise<AuthUser | null> {
    return authMapped(async () => {
      const user = this.sdk.auth.currentUser;
      if (user) await reload(user);
      return this.currentUser();
    });
  }

  verifyPassword(password: string): Promise<void> {
    return authMapped(async () => {
      const email = this.sdk.auth.currentUser?.email;
      if (!email) throw new AppError('invalidCredentials');
      // A separate app kept in memory only, so the check never touches the session that Firestore uses.
      const app = initializeApp(this.sdk.app.options, `password-check-${Date.now()}`);
      try {
        const checker = initializeAuth(app, { persistence: inMemoryPersistence });
        if (this.sdk.emulatorHost) {
          connectAuthEmulator(checker, `http://${this.sdk.emulatorHost}:9099`, { disableWarnings: true });
        }
        await signInWithEmailAndPassword(checker, email, password);
      } finally {
        await deleteApp(app);
      }
    });
  }

  reauthenticate(password: string): Promise<void> {
    return authMapped(async () => {
      const user = this.sdk.auth.currentUser;
      if (!user?.email) throw new AppError('invalidCredentials');
      await reauthenticateWithCredential(user, EmailAuthProvider.credential(user.email, password));
    });
  }

  signOut(): Promise<void> {
    return authMapped(() => signOut(this.sdk.auth));
  }

  deleteAccount(): Promise<void> {
    return authMapped(async () => {
      const user = this.sdk.auth.currentUser;
      if (user) await deleteUser(user);
    });
  }
}
