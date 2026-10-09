import { InlineNotification } from '@carbon/react';
import { ArrowLeft } from '@carbon/icons-react';
import type { FC, ReactNode } from 'react';
import { Link } from 'react-router-dom';

import { useNavOrigin, type NavOrigin } from '@/lib/navOrigin';
import PageLayout from '@/pages/PageLayout';
import { AREAS_BY_ID } from '@/screens/registry';
import type { ScreenDef } from '@/screens/types';

interface ScreenLayoutProps {
  screen: ScreenDef;
  /** Screen-level buttons, level with the title (e.g. "Add Scale Site Profile"). */
  actions?: ReactNode;
  /** Shown in place of the back link while a form has unsaved input. */
  backDisabled?: boolean;
  children: ReactNode;
}

/**
 * The page frame for a registry screen: nr-fta's PageLayout (title, subtitle,
 * actions level with the title, a "Back to …" link above it), plus the legacy
 * screen's notes banner. The legacy P-number ends the subtitle, for support
 * staff matching a call to the old screen.
 *
 * The back link returns to wherever the screen was opened from (a search's
 * results, Home's work queues …), carried in router state. A record or form
 * reached any other way — a bookmark, a reload — goes back to its area's
 * landing page. A search screen is itself a menu destination, so it shows a
 * back link only when something opened it.
 */
export const ScreenLayout: FC<ScreenLayoutProps> = ({ screen, actions, backDisabled, children }) => {
  const origin = useNavOrigin();
  const area = AREAS_BY_ID.get(screen.area);
  const back: NavOrigin | null =
    origin ?? (screen.kind === 'search' || !area ? null : { path: `/${area.id}`, label: area.label });

  const legacy = `Legacy screen ${screen.legacy}`;
  const subtitle = screen.description ? `${screen.description} · ${legacy}` : legacy;

  const backLink = back ? (
    backDisabled ? (
      <span className="back-link back-link--disabled" aria-disabled="true">
        <ArrowLeft size={16} /> Back to {back.label}
      </span>
    ) : (
      <Link to={back.path} className="back-link">
        <ArrowLeft size={16} /> Back to {back.label}
      </Link>
    )
  ) : undefined;

  return (
    <PageLayout title={screen.title} subtitle={subtitle} actions={actions} backLink={backLink}>
      {screen.notes && (
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton={false}
          title=""
          subtitle={screen.notes}
          className="hbs-screen__notes"
        />
      )}
      {children}
    </PageLayout>
  );
};

export default ScreenLayout;
