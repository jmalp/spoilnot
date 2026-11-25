// Households, members and joining (DESIGN 5.1), ported from feature/household/domain.
import type { Observable } from 'rxjs';
import type { PendingJoin } from './account';
import type { Actor } from './write';

export type Role = 'owner' | 'member';

export type Household = { id: string; name: string; ownerUid: string; inviteCode: string };

export type Member = { uid: string; displayName: string; role: Role; joinedAt: Date | null };

export type JoinRequestStatus = 'pending' | 'approved' | 'declined';

export type JoinRequest = {
  uid: string;
  displayName: string;
  code: string;
  status: JoinRequestStatus;
  requestedAt: Date | null;
  /** The requester's sign-in address, masked ("ro***@example.com"); null from builds before it was written. */
  emailMasked: string | null;
};

/**
 * The requester's email as the owner sees it on a join request (after v1): the first two characters of the name part
 * (one when it has two or fewer), three stars and the domain, as in "ro***@example.com". The rules compute the same
 * from the sign-in's address. Null for an address the rules might count differently (not one @, or a name starting
 * with characters outside ASCII): the request is then sent without it. EmailMask in the app.
 */
export function maskEmail(email: string | null | undefined): string | null {
  if (!email) return null;
  const at = email.indexOf('@');
  if (at <= 0 || at !== email.lastIndexOf('@') || at === email.length - 1) return null;
  const local = email.slice(0, at);
  const kept = local.slice(0, local.length > 2 ? 2 : 1);
  if (!/^[\x21-\x7e]+$/.test(kept)) return null;
  return `${kept}***${email.slice(at)}`;
}

/** How Clear household data ended: all on the server, or part of it queued until this phone is online. */
export type ResetOutcome = 'cleared' | 'finishesWhenOnline';

/** inviteCodes/{code}: what a person sees after typing a code. */
export type InviteCode = { code: string; householdId: string; householdName: string };

/** The current user's own membership; Gone is confirmed by the server (removed, left, or deleted). */
export type Membership = { kind: 'active'; role: Role } | { kind: 'gone' };

export const HouseholdRules = {
  MAX_NAME_LENGTH: 60,
  /** Stored in English, as in the app. */
  DEFAULT_GROCERY_LIST_NAME: 'Groceries',
  isNameValid(name: string): boolean {
    return name.trim() !== '' && name.trim().length <= this.MAX_NAME_LENGTH;
  },
};

/** Invite codes (DESIGN 5.1, 12.2): 10 characters, about 50 bits, no look-alike characters. */
export const InviteCodes = {
  /** No I, O, 0 or 1. */
  ALPHABET: 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789',
  LENGTH: 10,

  /** [randomIndex] returns 0 until the bound, exclusive; the default is the browser's secure generator. */
  generate(randomIndex: (bound: number) => number = secureIndex): string {
    return Array.from({ length: this.LENGTH }, () => this.ALPHABET[randomIndex(this.ALPHABET.length)]).join('');
  },

  /** Uppercases and drops separators, so pasted "abcde-23456" and typed input end up the same. */
  normalize(input: string): string {
    return input
      .toUpperCase()
      .replace(/[^\p{L}\p{N}]/gu, '')
      .slice(0, this.LENGTH);
  },

  isWellFormed(code: string): boolean {
    return code.length === this.LENGTH && [...code].every((c) => this.ALPHABET.includes(c));
  },

  /** For display: a hyphen after the fifth character. */
  format: (code: string) => (code.length > 5 ? `${code.slice(0, 5)}-${code.slice(5)}` : code),
};

/** An unbiased index from crypto.getRandomValues (rejection sampling). */
function secureIndex(bound: number): number {
  const limit = Math.floor(0x100000000 / bound) * bound;
  const value = new Uint32Array(1);
  do crypto.getRandomValues(value);
  while (value[0]! >= limit);
  return value[0]! % bound;
}

/** Households, membership and joining. Implementations throw AppError. */
export interface HouseholdRepository {
  /** A permission error counts as gone: removed members can no longer read their own document. */
  observeMembership(householdId: string, uid: string): Observable<Membership>;
  observeHousehold(householdId: string): Observable<Household | null>;
  /** Ordered by join date. */
  observeMembers(householdId: string): Observable<Member[]>;
  /** Owner only. */
  observeJoinRequests(householdId: string): Observable<JoinRequest[]>;
  /** The user's own request, or null once the server confirms there is none. */
  observeOwnJoinRequest(householdId: string, uid: string): Observable<JoinRequest | null>;
  /** Writes the household, owner membership, invite code, default list and profile together; returns its id. */
  createHousehold(uid: string, displayName: string, name: string, inviteCode: string): Promise<string>;
  /** Null when no household uses this code; the code must be well-formed. */
  findInviteCode(code: string): Promise<InviteCode | null>;
  /** [email] is the requester's sign-in address; the owner sees it masked ([maskEmail]). */
  requestToJoin(uid: string, displayName: string, invite: InviteCode, email?: string | null): Promise<void>;
  /** After approval: points the profile at the household and removes the request. */
  completeJoin(uid: string, displayName: string, pending: PendingJoin): Promise<void>;
  /** Cancels a waiting request, or dismisses a declined or expired one. */
  abandonJoin(uid: string, pending: PendingJoin): Promise<void>;
  approve(householdId: string, request: JoinRequest): Promise<void>;
  decline(householdId: string, requesterUid: string): Promise<void>;
  rename(household: Household, newName: string): Promise<void>;
  /** A new code for the household; the old one stops working (after v1 there is no Pause joining, R12). */
  regenerateInviteCode(household: Household, uid: string, newCode: string): Promise<void>;
  removeMember(householdId: string, memberUid: string): Promise<void>;
  transferOwnership(householdId: string, fromUid: string, toUid: string): Promise<void>;
  /** Requires a connection: the cache is cleared right after. */
  leave(householdId: string, uid: string): Promise<void>;
  /** Deletes every subcollection, then the household; requires a connection. */
  deleteHousehold(household: Household, uid: string): Promise<void>;
  /**
   * Clear household data (owner, DESIGN 5.1): the pantry, grocery lists, recipes, meals, Recent activity and
   * Insights go for every member; the household, its members and invite code stay. Refuses to start offline (it
   * reads from the server first). A chunk that times out is still queued, so the rest queue after it instead of
   * stopping half done, and the outcome says the clear finishes once online (after v1, SYNC-08).
   */
  resetData(householdId: string, actor: Actor): Promise<ResetOutcome>;
}

/** The phone's copy of household data. */
export interface LocalData {
  /** Sends pending writes if possible, then wipes the cache (DESIGN 12.2, control 6). */
  clear(): Promise<void>;
  /**
   * Waits a few seconds for this phone's unsent changes to reach the server; false when some are still waiting.
   * Clearing now would throw them away (after v1, SYNC-09).
   */
  flushPendingWrites(): Promise<boolean>;
}

/** This phone's device document for digests (DESIGN 10.2, 18.4); the push token itself is DeviceSync's. */
export interface DeviceRegistry {
  sync(uid: string): Promise<void>;
  /** On sign-out; waits a few seconds at most, so offline the delete may not reach the server. */
  unregister(uid: string): Promise<void>;
}
