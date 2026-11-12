import { useEffect, useMemo } from 'react';
import { drafts } from './core/drafts';
import { useObservable } from './core/hooks';
import { str } from './core/i18n';
import { NavProvider } from './core/NavProvider';
import { quotaSignal } from './domain/quota';
import { HouseholdSessionProvider, useServices } from './core/services';
import { AppShell } from './ui/AppShell';
import { AuthFlow } from './ui/auth/AuthFlow';
import { GateScreen } from './ui/household/GateScreen';
import { Progress } from './ui/kit';
import { QuotaBanner } from './ui/Scaffold';

/** Chooses the top-level screen from the session (SpoilNotRoot): sign-in, the household gate, or the app. */
export function App() {
  const quotaReached = useObservable(quotaSignal.banner$, quotaSignal.isExhausted());
  // The quota banner (DESIGN 6.7) above whichever top-level screen shows (after v1, WEB-09): inside the shell it
  // could not show while the session reconnected, and the gate or sign-in had none.
  return (
    <div className={quotaReached ? 'sn-root sn-root--quota' : 'sn-root'}>
      {quotaReached && <QuotaBanner />}
      <div className="sn-root__screen">
        <RootScreen />
      </div>
    </div>
  );
}

function RootScreen() {
  const { session: manager } = useServices();
  const session = useObservable(
    useMemo(() => manager.session$, [manager]),
    manager.session,
  );
  // A sheet's draft kept through a reload (core/drafts.ts) belongs to the household it was typed in.
  const outside = session.kind === 'signedOut' || session.kind === 'noHousehold';
  useEffect(() => {
    if (outside) drafts.clear();
  }, [outside]);
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
