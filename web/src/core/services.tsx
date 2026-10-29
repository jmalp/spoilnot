import { createContext, useContext, type ReactNode } from 'react';
import type { AuthRepository, UserRepository } from '../domain/account';
import type { ActivityRepository } from '../domain/home/models';
import type { HouseholdRepository } from '../domain/household';
import type { StatsRepository } from '../domain/insights/insights';
import type { PantryRepository } from '../domain/pantry/repository';
import type { PantryService } from '../domain/pantry/service';
import type { Session, SessionManager } from '../domain/session';
import type { Clock } from '../domain/time';
import type { HouseholdWriter } from '../domain/write';

/** What screens use (the app's Hilt graph): repositories and the session. Tests pass fakes. */
export type Services = {
  session: SessionManager;
  auth: AuthRepository;
  users: UserRepository;
  households: HouseholdRepository;
  writer: HouseholdWriter;
  pantry: PantryRepository;
  pantryService: PantryService;
  activity: ActivityRepository;
  stats: StatsRepository;
  clock: Clock;
};

const Context = createContext<Services | null>(null);

export function ServicesProvider({ services, children }: { services: Services; children: ReactNode }) {
  return <Context.Provider value={services}>{children}</Context.Provider>;
}

export function useServices(): Services {
  const services = useContext(Context);
  if (!services) throw new Error('useServices outside ServicesProvider');
  return services;
}

export type InHousehold = Extract<Session, { kind: 'inHousehold' }>;

const HouseholdSession = createContext<InHousehold | null>(null);

/** The signed-in member's session, below the app shell. */
export const HouseholdSessionProvider = HouseholdSession.Provider;

export function useHouseholdSession(): InHousehold {
  const session = useContext(HouseholdSession);
  if (!session) throw new Error('useHouseholdSession outside the app shell');
  return session;
}
