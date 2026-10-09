import { ClickableTile } from '@carbon/react';
import type { FC } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { EmptyState } from '@/components/EmptyState/EmptyState';
import SectionTile from '@/components/SectionTile';
import { useScopedUser } from '@/context/org/useOrg';
import { can } from '@/routes/access';
import { AREA_ICONS, getMenuSections } from '@/routes/routePaths';
import { AREAS_BY_ID, SCREENS_BY_ID } from '@/screens/registry';
import type { ScreenArea } from '@/screens/types';

import ForbiddenPage from './ForbiddenPage';
import NotFound from './NotFound';
import PageLayout from './PageLayout';
import './HomePage.scss';

/**
 * A legacy tab landing page (P400 Queries Menu, P002 Scale Return Menu, P850,
 * P199, P800, P900 …), laid out as nr-fta's landing pages: the area's
 * screens the user may open, as quick-link tiles in one section — the same
 * destinations, icons and order as the area's section of the side nav.
 */
const AreaLandingPage: FC = () => {
  const { area } = useParams();
  const navigate = useNavigate();
  const user = useScopedUser();
  const def = AREAS_BY_ID.get(area as ScreenArea);
  if (!def) return <NotFound />;
  if (!can(user, def.capability)) return <ForbiddenPage />;

  const items = getMenuSections(user).find((s) => s.id === def.id)?.items ?? [];

  return (
    <PageLayout title={def.label} subtitle={`${def.description} · Legacy menu ${def.legacy}`}>
      <SectionTile title="Screens" icon={AREA_ICONS[def.id]}>
        {items.length === 0 && (
          <EmptyState
            title="Nothing here for your role"
            body="None of this area's screens are available to your account. Your HBS administrator can grant access in FAM."
          />
        )}
        <div className="welcome__tiles">
          {items.map(({ id, label, path, icon: Icon }) => {
            const description = SCREENS_BY_ID.get(id)?.description;
            return (
              <ClickableTile key={id} onClick={() => navigate(path)} className="welcome__tile">
                {Icon ? <Icon size={24} /> : null}
                <span className="welcome__tile-label">{label}</span>
                {description && <span className="welcome__tile-desc">{description}</span>}
              </ClickableTile>
            );
          })}
        </div>
      </SectionTile>
    </PageLayout>
  );
};

export default AreaLandingPage;
