import { Content, HeaderContainer } from '@carbon/react';
import { useEffect, type FC, type ReactNode } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';
import { LayoutProvider } from '@/context/layout/LayoutProvider';
import { useLayout } from '@/context/layout/useLayout';
import { LayoutHeader } from './LayoutHeader';
import './Layout.css';

/**
 * Wraps the Carbon shell so the page content slides right when the
 * SideNav drawer is open (CSS keys off the .bc-layout--nav-open class
 * to push .cds--content rather than letting the drawer overlay it).
 */
const LayoutShell: FC<{ children: ReactNode }> = ({ children }) => {
  const { isSideNavExpanded } = useLayout();
  const { pathname, search } = useLocation();
  const navigationType = useNavigationType();

  // A newly opened page starts at its top — opening a record from far down a
  // results table would otherwise land mid-page. Back / forward (POP) keeps
  // the browser's own scroll restoration.
  useEffect(() => {
    if (navigationType !== 'POP') window.scrollTo(0, 0);
  }, [pathname, search, navigationType]);

  return (
    <div
      className={`bc-layout${isSideNavExpanded ? ' bc-layout--nav-open' : ''}`}
    >
      <HeaderContainer render={LayoutHeader} />
      <Content>{children}</Content>
    </div>
  );
};

const Layout: FC<{ children: ReactNode }> = ({ children }) => {
  return (
    <LayoutProvider>
      <LayoutShell>{children}</LayoutShell>
    </LayoutProvider>
  );
};

export default Layout;
