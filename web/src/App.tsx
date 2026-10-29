import { NavProvider } from './core/NavProvider';
import { AppShell } from './ui/AppShell';

/** The web app's root (DESIGN 18). Sign-in and the household gate come in W1; until then the shell shows. */
export function App() {
  return (
    <NavProvider>
      <AppShell />
    </NavProvider>
  );
}
