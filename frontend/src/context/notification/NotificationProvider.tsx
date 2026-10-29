import { ToastNotification } from '@carbon/react';
import { useState, useEffect, useRef, type ReactNode, useCallback } from 'react';

import { safeErrorMessage } from '@/lib/errorMessage';
import { NotificationContext, type NotificationContent } from './NotificationContext';

/**
 * Mirrors nr-rept's NotificationProvider verbatim. The toast slides
 * in from the top-right of the viewport (styled in src/styles/index.scss),
 * stays for `timeout` ms, then slides out. Callers fire it via
 * useNotification().display({ kind, title, subtitle, timeout }).
 *
 * Error and warning toasts stay until closed, but not across pages: each
 * remembers the page it was raised on, and DismissToastsOnNavigate closes it
 * once the user moves to another page.
 */
export const NotificationProvider = ({ children }: { children: ReactNode }) => {
  const [notificationContent, setNotificationContent] = useState<NotificationContent | null>(null);
  const [notificationClass, setNotificationClass] = useState<string>('slide-in');
  // The page an error / warning toast was raised on (null for other kinds).
  const problemPath = useRef<string | null>(null);

  const display = useCallback((content: NotificationContent) => {
    // Error / warning toasts stay until the user closes them. The
    // caller's timeout is ignored for those kinds so an information
    // error doesn't disappear before the user has read it. Success
    // and info toasts still auto-dismiss after their requested
    // timeout (typical 5-6 s).
    const isProblem =
      content.kind === 'error'
      || content.kind === 'warning'
      || content.kind === 'warning-alt';
    // Never let a raw backend/DB error (ORA-…, SQL, stack trace, error
    // JSON) reach a toast — swap any technical subtitle for a friendly
    // line. Clean messages (validation, etc.) pass through untouched.
    const safe =
      isProblem && content.subtitle !== undefined
        ? { ...content, subtitle: safeErrorMessage(content.subtitle) }
        : content;
    problemPath.current = isProblem ? window.location.pathname : null;
    setNotificationClass('slide-in');
    setNotificationContent(isProblem ? { ...safe, timeout: 0 } : safe);
  }, []);

  const dismissStaleProblems = useCallback((pathname: string) => {
    if (problemPath.current === null || problemPath.current === pathname) return;
    problemPath.current = null;
    setNotificationContent(null);
  }, []);

  const onClose = useCallback(() => {
    problemPath.current = null;
    setNotificationClass('slide-out');
    notificationContent?.onClose?.();
    setNotificationContent(null);
  }, [notificationContent]);

  useEffect(() => {
    if (notificationContent && notificationContent.timeout > 0) {
      if (notificationClass === 'slide-in') {
        const timer = setTimeout(() => {
          setNotificationClass('slide-out');
        }, notificationContent.timeout - 300);
        return () => clearTimeout(timer);
      }
    }
  }, [notificationClass, notificationContent]);

  return (
    <NotificationContext.Provider value={{ display, dismissStaleProblems }}>
      {children}
      {notificationContent && (
        <ToastNotification
          className={notificationClass}
          lowContrast
          aria-label="closes notification"
          caption={notificationContent.caption}
          kind={notificationContent.kind}
          onClose={onClose}
          onCloseButtonClick={notificationContent.onCloseButtonClick}
          role="status"
          statusIconDescription="notification"
          subtitle={notificationContent.subtitle}
          timeout={notificationContent.timeout}
          title={notificationContent.title}
        >
          {notificationContent.children}
        </ToastNotification>
      )}
    </NotificationContext.Provider>
  );
};

export default NotificationProvider;
