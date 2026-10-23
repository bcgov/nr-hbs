import { Login } from '@carbon/icons-react';
import { Content, Header, HeaderGlobalAction, HeaderGlobalBar, HeaderName, SkipToContent } from '@carbon/react';
import { useEffect, type FC, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useNavigationType } from 'react-router-dom';

import { ScreenApiContext } from '@/components/screens/screenApi';
import { useAuth } from '@/context/auth/useAuth';

import { publicScreenApi } from './publicScreenApi';
import './PublicLayout.scss';

const APP_NAME = (import.meta.env.VITE_APP_NAME as string) || 'Harvest Billing System';

/**
 * Frame for the public (anonymous) pages: the HBS header without the side
 * nav or the profile panel (both need a login and roles), a "Log in" action,
 * and the anonymous ScreenApi for every screen template rendered inside.
 */
const PublicLayout: FC<{ children: ReactNode }> = ({ children }) => {
  const { isLoggedIn } = useAuth();
  const navigate = useNavigate();
  const { pathname, search } = useLocation();
  const navigationType = useNavigationType();

  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo(0, 0);
  }, [pathname, search, navigationType]);

  return (
    <ScreenApiContext.Provider value={publicScreenApi}>
      <div className="bc-layout hbs-public">
        <Header aria-label={APP_NAME} className="bc-header" data-testid="hbs-public__header">
          <SkipToContent />
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
        </Header>
        <Content className="hbs-public__content">{children}</Content>
      </div>
    </ScreenApiContext.Provider>
  );
};

export default PublicLayout;
