import { useEffect, useState } from 'react';
import { str } from '../../core/i18n';
import { APP_BUILD } from '../../core/version';
import { Button, Dialog, ListItem, Progress } from '../kit';
import { DetailScaffold } from '../Scaffold';
import './settings.css';

/** public/licenses.json, written by scripts/gen-licenses.ts. */
type Library = { name: string; version: string; license: string; url: string | null; text: number | null };
type Licenses = { texts: string[]; libraries: Library[] };

/**
 * Settings > About: version, privacy summary and open source licenses (DESIGN 5.6). The licenses are fetched when
 * the screen opens; the service worker keeps them for offline use. The app's seven taps for a test crash have no web
 * counterpart: the web app sends no crash reports.
 */
export function AboutScreen({ onBack }: { onBack: () => void }) {
  const [licenses, setLicenses] = useState<Licenses | null>(null);
  const [open, setOpen] = useState<Library | null>(null);
  useEffect(() => {
    let active = true;
    fetch(`${import.meta.env.BASE_URL}licenses.json`)
      .then((response) => response.json() as Promise<Licenses>)
      .then(
        (value) => active && setLicenses(value),
        () => undefined,
      );
    return () => {
      active = false;
    };
  }, []);
  return (
    <DetailScaffold title={str('settings_about')} onBack={onBack} testId="screen_about">
      <div className="sn-about">
        <p className="t-body-large" data-testid="about_version">
          {str('web_about_version', APP_BUILD)}
        </p>
        <h2 className="t-title-medium">{str('about_privacy_title')}</h2>
        <p className="t-body-large">{str('web_about_privacy_body')}</p>
        <h2 className="t-title-medium sn-about__licenses-title">{str('about_licenses')}</h2>
      </div>
      {licenses ? (
        <div className="sn-licenses" data-testid="licenses">
          {licenses.libraries.map((library) => (
            <ListItem
              key={`${library.name}@${library.version}`}
              headline={library.name}
              supporting={`${library.version} · ${library.license}`}
              onClick={() => setOpen(library)}
            />
          ))}
        </div>
      ) : (
        <Progress />
      )}
      {open && licenses && (
        <Dialog
          title={open.name}
          onDismiss={() => setOpen(null)}
          actions={
            <>
              {open.url && (
                <Button variant="text" onClick={() => window.open(open.url!, '_blank', 'noopener,noreferrer')}>
                  {str('web_license_link')}
                </Button>
              )}
              <Button variant="text" onClick={() => setOpen(null)}>
                {str('action_ok')}
              </Button>
            </>
          }
        >
          {open.text !== null ? (
            <pre className="sn-license-text">{licenses.texts[open.text]}</pre>
          ) : (
            <p>{open.license}</p>
          )}
        </Dialog>
      )}
    </DetailScaffold>
  );
}
