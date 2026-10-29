import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

import { useNotification } from './useNotification';

/**
 * Closes an open error / warning toast when the user moves to another page
 * (a new path; a query-string change on the same page keeps it). Renders
 * nothing; it lives inside the router, which NotificationProvider is outside.
 */
export const DismissToastsOnNavigate = () => {
  const { pathname } = useLocation();
  const { dismissStaleProblems } = useNotification();
  useEffect(() => {
    dismissStaleProblems(pathname);
  }, [pathname, dismissStaleProblems]);
  return null;
};

export default DismissToastsOnNavigate;
