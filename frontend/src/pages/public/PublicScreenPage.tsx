import type { FC } from 'react';
import { useParams } from 'react-router-dom';

import DetailScreen from '@/components/screens/DetailScreen';
import SearchScreen from '@/components/screens/SearchScreen';
import '@/components/screens/screens.scss';
import NotFound from '@/pages/NotFound';

import { PUBLIC_SCREENS_BY_ID } from './publicScreens';

/**
 * Route element for `/public/:screenId` — one of the public screens, rendered
 * by the shared templates (PublicLayout supplies the anonymous ScreenApi).
 * There is no capability check: the backend serves only whitelisted queries,
 * always as the anonymous viewer.
 */
const PublicScreenPage: FC = () => {
  const { screenId } = useParams();
  const screen = screenId ? PUBLIC_SCREENS_BY_ID.get(screenId) : undefined;
  if (!screen) return <NotFound />;
  switch (screen.kind) {
    case 'search':
      return <SearchScreen key={screen.id} screen={screen} />;
    case 'detail':
      return <DetailScreen key={screen.id} screen={screen} />;
    default:
      return <NotFound />;
  }
};

export default PublicScreenPage;
