import { useEffect, useMemo, useState, useCallback, useRef, type ReactNode } from 'react';

import {
  KC_IDP_HINTS,
  ensureFreshUser,
  forceRenewUser,
  getUserManager,
  loadStoredUser,
  noteSignIn,
} from '@/services/keycloak';

import { AuthContext, type AuthContextType } from './AuthContext';
import { parseToken, type KeycloakProfile } from './authUtils';
import { type FamLoginUser, type LoginProvider } from './types';

/** Set on logout so the next sign-in in this tab re-prompts for credentials. */
const SIGNED_OUT_FLAG = 'hbs.signedOut';

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<FamLoginUser | undefined>(undefined);
  const [accessToken, setAccessToken] = useState<string | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(true);

  // Coalesces concurrent callers so the React state is published once per
  // renewal rather than once per caller. The guarantee that actually matters —
  // that two renewals never race the same rotating refresh token — lives in
  // services/keycloak.ts, which every path shares. A ref, because changing it
  // must not re-render.
  const renewInFlight = useRef<Promise<string | undefined> | null>(null);

  /** Publishes a signed-in session, or clears it when the user is gone. */
  const applyUser = useCallback((oidcUser: { access_token?: string; profile?: unknown } | null) => {
    if (!oidcUser?.access_token) {
      setUser(undefined);
      setAccessToken(undefined);
      return undefined;
    }
    setAccessToken(oidcUser.access_token);
    setUser(parseToken(oidcUser.profile as KeycloakProfile));
    return oidcUser.access_token;
  }, []);

  // ── Initial session bootstrap ──────────────────────────────────────
  // Users without any recognised HBS_* role are kept in state (isLoggedIn=true)
  // so the routing layer can route them to UnauthorizedPage rather than
  // bouncing them back through the IdP.
  useEffect(() => {
    let cancelled = false;

    const bootstrap = async () => {
      try {
        const restored = await loadStoredUser(getUserManager());
        if (!cancelled) applyUser(restored);
      } catch (error) {
        // eslint-disable-next-line no-console
        console.error('[AuthProvider] error loading user:', error);
        if (!cancelled) applyUser(null);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [applyUser]);

  // ── Auth actions ───────────────────────────────────────────────────

  /**
   * After a logout in this tab, the next sign-in asks for credentials again (`prompt=login`,
   * which Keycloak passes on to Microsoft). Logging out of Keycloak reaches Microsoft only
   * while the realm session is alive — an inactivity logout often finds it already expired —
   * and without this the Microsoft session signs the user straight back in.
   */
  const login = useCallback((provider: LoginProvider = 'idir') => {
    let reauth = false;
    try {
      reauth = sessionStorage.getItem(SIGNED_OUT_FLAG) === '1';
      sessionStorage.removeItem(SIGNED_OUT_FLAG);
    } catch {
      /* storage unavailable — sign in as usual */
    }
    void getUserManager().signinRedirect({
      extraQueryParams: { kc_idp_hint: KC_IDP_HINTS[provider] },
      ...(reauth ? { prompt: 'login' } : {}),
    });
  }, []);

  /**
   * Ends the Keycloak session.
   *
   * **The stored user is deliberately left in place.** oidc-client-ts reads
   * `id_token_hint` off it and removes it itself; clearing the tokens first
   * sends a logout Keycloak cannot attribute to a session, so the realm session
   * survives and the next sign-in walks straight back in without a prompt —
   * which is exactly what "logout doesn't work" looks like from the outside.
   * (The Cognito implementation cleared tokens up front because it drove the
   * federated redirect chain by hand; that whole chain is gone.)
   *
   * **And it always finishes.** Callers invoke this as `onClick={logout}`, so a
   * redirect that throws — a silent renewal having already removed the stored
   * user, Keycloak refusing the request — would reject into nothing and leave
   * the app showing a signed-out page on top of a live realm session. The catch
   * drops this browser's tokens and lands on the sign-in screen under our own
   * steam instead.
   */
  const logout = useCallback(async () => {
    try {
      sessionStorage.setItem(SIGNED_OUT_FLAG, '1');
    } catch {
      /* storage unavailable — the next sign-in may reuse the Microsoft session */
    }
    try {
      await getUserManager().signoutRedirect();
    } catch (error) {
      // eslint-disable-next-line no-console
      console.error('[AuthProvider] sign-out redirect failed; clearing locally.', error);
      await getUserManager()
        .removeUser()
        .catch(() => undefined);
      applyUser(null);
      window.location.assign(import.meta.env.BASE_URL || '/');
    }
  }, [applyUser]);

  const userToken = useCallback((): string | undefined => accessToken, [accessToken]);

  /**
   * Returns a usable access token, renewing first if it is at or near expiry.
   *
   * Called before each API request (services/apiFetch.ts). A no-op unless the
   * token is nearly out, so it is cheap to call often.
   *
   * Concurrent callers share one renewal rather than each starting their own:
   * every `signinSilent` rotates the refresh token, and racing rotations against
   * each other is how a session dies while somebody is using it. The Cognito
   * version approximated this with a 5-second cooldown and a re-read of the
   * cookie; sharing the promise is both simpler and actually correct.
   */
  const ensureFreshToken = useCallback(async (): Promise<string | undefined> => {
    if (renewInFlight.current) {
      return renewInFlight.current;
    }

    const attempt = (async () => {
      try {
        const fresh = await ensureFreshUser(getUserManager());
        return applyUser(fresh);
      } catch (error) {
        // The refresh token is gone or was refused — the session is over.
        // eslint-disable-next-line no-console
        console.warn('[AuthProvider] Session expired — signing out.', error);
        applyUser(null);
        void logout();
        return undefined;
      } finally {
        renewInFlight.current = null;
      }
    })();

    renewInFlight.current = attempt;
    return attempt;
  }, [applyUser, logout]);

  /** Renews unconditionally — the session timeout's "Stay logged in". */
  const forceRefreshSession = useCallback(async (): Promise<void> => {
    const fresh = await forceRenewUser(getUserManager());
    if (!applyUser(fresh)) throw new Error('No session to renew');
  }, [applyUser]);

  /** Completes the redirect back from Keycloak. Used only by AuthCallback. */
  const completeLogin = useCallback(async (): Promise<void> => {
    const signedIn = await getUserManager().signinRedirectCallback();
    noteSignIn();
    applyUser(signedIn);
  }, [applyUser]);

  const contextValue: AuthContextType = useMemo(
    () => ({
      user,
      isLoggedIn: !!user,
      isLoading,
      login,
      logout,
      userToken,
      ensureFreshToken,
      forceRefreshSession,
      completeLogin,
    }),
    [
      user,
      isLoading,
      login,
      logout,
      userToken,
      ensureFreshToken,
      forceRefreshSession,
      completeLogin,
    ],
  );

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>;
};
