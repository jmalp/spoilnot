import { useMemo } from 'react';
import { useObservable } from './core/hooks';
import { str } from './core/i18n';
import { NavProvider } from './core/NavProvider';
import { HouseholdSessionProvider, useServices } from './core/services';
import { AppShell } from './ui/AppShell';
import { AuthFlow } from './ui/auth/AuthFlow';
import { GateScreen } from './ui/household/GateScreen';
import { Progress } from './ui/kit';

/** Chooses the top-level screen from the session (SpoilNotRoot): sign-in, the household gate, or the app. */
export function App() {
  const { session: manager } = useServices();
  const session = useObservable(
    useMemo(() => manager.session$, [manager]),
    manager.session,
  );
  switch (session.kind) {
    case 'loading':
      return (
        <div className="sn-centered sn-full" data-testid="screen_loading">
          <Progress label={str('loading')} />
        </div>
      );
    case 'signedOut':
      return <AuthFlow />;
    case 'noHousehold':
      return <GateScreen key={session.user.uid} session={session} />;
    case 'inHousehold':
      // Keyed so nothing from a previous household or account survives (DESIGN 9.1).
      return (
        <HouseholdSessionProvider key={`${session.user.uid}/${session.householdId}`} value={session}>
          <NavProvider>
            <AppShell />
          </NavProvider>
        </HouseholdSessionProvider>
      );
  }
}
