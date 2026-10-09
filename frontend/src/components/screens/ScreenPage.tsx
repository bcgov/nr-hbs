import type { FC } from 'react';
import { useParams } from 'react-router-dom';

import { useScopedUser } from '@/context/org/useOrg';
import ForbiddenPage from '@/pages/ForbiddenPage';
import NotFound from '@/pages/NotFound';
import { can } from '@/routes/access';
import { SCREENS_BY_ID } from '@/screens/registry';

import DetailScreen from './DetailScreen';
import FormScreen from './FormScreen';
import SearchScreen from './SearchScreen';
import './screens.scss';

/**
 * Route element for `/:area/:screenId` — resolves the registry definition,
 * applies the capability guard, and renders the matching template. Each
 * template lays itself out in nr-fta's page frame (ScreenLayout).
 */
const ScreenPage: FC = () => {
  const { area, screenId } = useParams();
  const user = useScopedUser();
  const screen = screenId ? SCREENS_BY_ID.get(screenId) : undefined;

  if (!screen || screen.area !== area) return <NotFound />;
  if (!can(user, screen.capability)) return <ForbiddenPage />;

  switch (screen.kind) {
    case 'search':
      return <SearchScreen key={screen.id} screen={screen} />;
    case 'detail':
      return <DetailScreen key={screen.id} screen={screen} />;
    case 'form':
      return <FormScreen key={screen.id} screen={screen} />;
  }
};

export default ScreenPage;
