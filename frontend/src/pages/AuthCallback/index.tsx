import { Loading } from '@carbon/react';
import { useEffect, useRef, type FC } from 'react';

import { useAuth } from '@/context/auth/useAuth';

/**
 * Where Keycloak returns the browser after a successful sign-in.
 *
 * New with the move off Cognito: Amplify processed the `?code=…&state=…`
 * callback implicitly during `Amplify.configure()`, so there was no route to
 * land on — `/auth/callback` existed only as a Cognito-registered URL that the
 * app immediately redirected away from. oidc-client-ts makes the exchange
 * explicit, which is the reason this page exists.
 *
 * It renders only a spinner; the exchange is quick, and then the browser is
 * sent to /home with a real navigation.
 *
 * **It must be a real navigation, not `history.replaceState`.** That was the
 * original implementation and it hung: `replaceState` updates the address bar
 * without firing `popstate`, which is the only thing React Router listens for.
 * The URL read /home while this component stayed mounted showing its
 * spinner — indefinitely, with no error anywhere, and F5 "fixed" it because a
 * reload rebuilds the router from the current URL.
 *
 * The comment that used to sit here assumed App swapping its route table would
 * take over once the auth state flipped. It does re-render, but nothing
 * re-navigates: the router's own location is still /authCallback, because the
 * address-bar change was invisible to it. Rather than depend on that, hand the
 * browser a genuine navigation and let everything rebuild from the correct URL
 * — exactly what the failure path below already does, and what F5 was doing by
 * hand. The cost is one page load, once, at sign-in.
 *
 * nr-rept had the identical bug and the identical fix (166dc00).
 */
const AuthCallback: FC = () => {
  const { completeLogin } = useAuth();

  // StrictMode mounts effects twice in development. The authorization code is
  // single-use and its state entry is consumed by the first exchange, so a
  // second call fails on a perfectly good sign-in.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    const run = async () => {
      try {
        await completeLogin();
        // `replace`, not `assign`: the callback URL carries a spent
        // authorization code, so it must not be left in history for Back.
        window.location.replace(`${import.meta.env.BASE_URL || '/'}home`);
      } catch (error) {
        // A spent or tampered callback is not something the user can act on —
        // send them back to the sign-in screen rather than showing an error
        // page for a URL they never typed.
        // eslint-disable-next-line no-console
        console.error('[AuthCallback] sign-in could not be completed:', error);
        window.location.replace(import.meta.env.BASE_URL || '/');
      }
    };

    void run();
  }, [completeLogin]);

  return <Loading data-testid="auth-callback-loading" withOverlay={true} />;
};

export default AuthCallback;
