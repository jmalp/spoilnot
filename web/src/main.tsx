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
import { followSystemTheme } from './theme/follow';
import { applyTextScale } from './theme/textScale';

applyTextScale();
followSystemTheme();
const services = createServices();
registerUpdates();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ServicesProvider services={services}>
      <App />
    </ServicesProvider>
  </StrictMode>,
);
