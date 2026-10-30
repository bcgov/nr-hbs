import { Login } from '@carbon/icons-react';
import {
  Content,
  Header,
  HeaderGlobalAction,
  HeaderGlobalBar,
  HeaderMenuButton,
  HeaderName,
  SkipToContent,
} from '@carbon/react';
import { useEffect, type FC, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useNavigationType } from 'react-router-dom';

import { LayoutSideNav } from '@/components/Layout/LayoutSideNav';
import { ScreenApiContext } from '@/components/screens/screenApi';
import { useAuth } from '@/context/auth/useAuth';
import { LayoutProvider } from '@/context/layout/LayoutProvider';
import { useLayout } from '@/context/layout/useLayout';

import { PUBLIC_MENU } from './publicMenu';
import { publicScreenApi } from './publicScreenApi';
import '@/components/Layout/Layout.css';
import './PublicLayout.scss';

const APP_NAME = (import.meta.env.VITE_APP_NAME as string) || 'Harvest Billing System';

/**
 * Frame for the public (anonymous) pages: the HBS header and side nav — the
 * same LayoutSideNav as the signed-in app, given the public menu — but no
 * profile panel (it needs a login), a "Log in" action, and the anonymous
 * ScreenApi for every screen template rendered inside.
 */
const PublicShell: FC<{ children: ReactNode }> = ({ children }) => {
  const { isLoggedIn } = useAuth();
  const { isSideNavExpanded, toggleSideNav } = useLayout();
  const navigate = useNavigate();
  const { pathname, search } = useLocation();
  const navigationType = useNavigationType();

  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo(0, 0);
  }, [pathname, search, navigationType]);

  return (
    <ScreenApiContext.Provider value={publicScreenApi}>
      <div className={`bc-layout hbs-public${isSideNavExpanded ? ' bc-layout--nav-open' : ''}`}>
        <Header aria-label={APP_NAME} className="bc-header" data-testid="hbs-public__header">
          <SkipToContent />
          <HeaderMenuButton
            aria-label={isSideNavExpanded ? 'Close menu' : 'Open menu'}
            isActive={isSideNavExpanded}
            onClick={toggleSideNav}
          />
          <HeaderName as={Link} to="/public" prefix="HBS">
            {APP_NAME}
          </HeaderName>
          <HeaderGlobalBar>
            <HeaderGlobalAction
              aria-label={isLoggedIn ? 'Go to HBS' : 'Log in'}
              tooltipAlignment="end"
              onClick={() => navigate('/')}
              data-testid="hbs-public__login"
            >
              <Login size={20} />
            </HeaderGlobalAction>
          </HeaderGlobalBar>
          <LayoutSideNav menu={PUBLIC_MENU} />
        </Header>
        <Content className="hbs-public__content">{children}</Content>
      </div>
    </ScreenApiContext.Provider>
  );
};

const PublicLayout: FC<{ children: ReactNode }> = ({ children }) => (
  <LayoutProvider>
    <PublicShell>{children}</PublicShell>
  </LayoutProvider>
);

export default PublicLayout;
