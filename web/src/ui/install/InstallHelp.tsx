import { useState } from 'react';
import { str } from '../../core/i18n';
import { canAddToHomeScreen } from '../../core/install';
import { usePreferences, useServices } from '../../core/services';
import { Button, Card, Icon } from '../kit';
import './install.css';

/** The welcome screen's line in Safari on an iPhone: how to install before signing in (DESIGN 18.1). */
export function WelcomeInstallLine() {
  const [shown] = useState(canAddToHomeScreen);
  if (!shown) return null;
  return (
    <p className="sn-install-line t-body-medium" data-testid="install_help">
      <Icon name="round/ios_share" size={20} />
      <span>{str('web_install_welcome')}</span>
    </p>
  );
}

/** Home's card in Safari on an iPhone, until it is installed or the user says Not now. */
export function InstallCard() {
  const { preferences } = useServices();
  const { installHelpDismissed } = usePreferences();
  const [possible] = useState(canAddToHomeScreen);
  if (!possible || installHelpDismissed) return null;
  return (
    <Card data-testid="card_install">
      <div className="sn-home-card">
        <div className="sn-home-card__header">
          <span className="c-primary">
            <Icon name="round/ios_share" />
          </span>
          <h2 className="t-title-medium">{str('web_install_title')}</h2>
        </div>
        <p className="t-body-medium c-on-surface-variant sn-install-card__body">{str('web_install_body')}</p>
        <Button
          variant="text"
          className="sn-install-card__later"
          onClick={() => preferences.set({ installHelpDismissed: true })}
        >
          {str('web_install_later')}
        </Button>
      </div>
    </Card>
  );
}
