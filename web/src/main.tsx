import '@fontsource-variable/manrope';
import '@fontsource-variable/fredoka';
import './theme/base.css';
import './ui/kit/kit.css';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { ServicesProvider } from './core/services';
import { createServices } from './data/services';
import { registerUpdates } from './core/updates';
import { followTheme } from './theme/follow';
import { applyTextScale } from './theme/textScale';

applyTextScale();
const services = createServices();
followTheme(services.preferences);
// The restock engine runs on every phone while the user is in a household (DESIGN 9.6).
services.restock.start();
// This phone's time zone and push token, for the daily digest (DESIGN 10.2, 18.4).
services.deviceSync.start();
registerUpdates();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ServicesProvider services={services}>
      <App />
    </ServicesProvider>
  </StrictMode>,
);
