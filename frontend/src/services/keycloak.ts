/**
 * BC Gov SSO (Keycloak) OIDC client.
 *
 * Replaces the AWS Amplify / Cognito configuration that used to live in
 * `config/fam/config.ts`. Authorization Code + PKCE against the standard realm;
 * there is no client secret, because this is a public browser client.
 *
 * Configuration comes from `env` (Vite build-time values merged with the runtime
 * `window.config` that the container entrypoint renders), so one built image is
 * still promotable between environments.
 */

import {
  UserManager,
  WebStorageStateStore,
  type User,
  type UserManagerSettings,
} from 'oidc-client-ts';

import { env } from '@/env';

/**
 * The realm's identity-provider aliases, passed as `kc_idp_hint` so the user
 * goes straight to IDIR or Business BCeID instead of Keycloak's
 * provider-selection screen. (nr-fta / nr-rept are IDIR-only; HBS also serves
 * industry clients, scale sites and submission agents on BCeID Business.)
 *
 * **IDIR is `azureidir`, not `idir`.** The standard realm's *IDIR - MFA*
 * integration federates IDIR through Azure AD under that alias — it is what
 * comes back in `identity_provider`, and what the backend normalises to `IDIR`
 * for the audit columns. Business BCeID is `bceidbusiness`.
 *
 * Getting one wrong is not an error, which is what makes it worth stating: an
 * alias the realm does not recognise is silently ignored and Keycloak shows its
 * provider-selection screen (or falls through to the client's only provider).
 *
 * Both are constants across environments — unlike Cognito's per-environment
 * provider names (`DEV-IDIR`, `TEST-IDIR`, `IDIR`), which needed `VITE_ZONE`.
 */
export const KC_IDP_HINTS = {
  idir: 'azureidir',
  bceid: 'bceidbusiness',
} as const;

export type LoginProvider = keyof typeof KC_IDP_HINTS;

/** Path the realm redirects back to after a successful sign-in. */
export const AUTH_CALLBACK_PATH = '/authCallback';

/**
 * How close to expiry an access token may get before it is renewed.
 *
 * Renewing only once a token has *already* expired leaves a window where every
 * request carries a token the backend refuses. The access token lives five
 * minutes on this realm, so a minute of headroom is a fifth of its life — enough
 * to cover a slow renewal and clock skew between browser and Keycloak, without
 * renewing so eagerly that the refresh token rotates for no reason.
 *
 * Widened from the 30 seconds the Cognito code used: the refresh token behind it
 * is half the life it was (30 minutes, not 60), so there is less room to absorb
 * a renewal that arrives late.
 */
export const RENEW_WHEN_SECONDS_LEFT = 60;

let userManager: UserManager | null = null;

/**
 * The renewal currently in flight, shared by every caller.
 *
 * **oidc-client-ts does not deduplicate `signinSilent` itself** — each call goes
 * straight to the token endpoint with whatever refresh token is stored. The
 * realm rotates that token on use and invalidates its predecessor, so two
 * renewals racing each other is not merely wasteful: the second presents a token
 * the first has already spent, is refused, and the session dies underneath
 * somebody who is actively using the app.
 *
 * That race is easy to reach here, because a single screen fires several API
 * calls at once and every one of them passes through `ensureFreshUser` (see
 * services/apiFetch.ts). Cognito had no equivalent exposure: Amplify serialised
 * its own refresh internally, which is why the code this replaced needed only a
 * crude five-second cooldown.
 */
let renewInFlight: Promise<User | null> | null = null;

/**
 * When this tab last got fresh tokens (sign-in or renewal), in epoch ms; 0 when
 * unknown, as for a session restored from storage. The session timeout uses it
 * to keep the 30-minute refresh token younger than the idle window.
 */
let lastRenewalAt = 0;

/**
 * Renews once, however many callers ask at the same moment.
 *
 * A rejection is shared too, which is correct: if the refresh token is spent,
 * every waiting caller's session is equally over.
 */
const renewOnce = (manager: Pick<UserManager, 'signinSilent'>): Promise<User | null> => {
  if (renewInFlight) {
    return renewInFlight;
  }
  const attempt = manager
    .signinSilent()
    .then((user) => {
      lastRenewalAt = Date.now();
      return user;
    })
    .finally(() => {
      renewInFlight = null;
    });
  renewInFlight = attempt;
  return attempt;
};

const buildSettings = (): UserManagerSettings => {
  const basePath = (env.VITE_BASE_PATH ?? '').replace(/\/$/, '');
  const appOrigin = `${window.location.origin}${basePath}`;

  return {
    authority: env.VITE_KEYCLOAK_URL,
    client_id: env.VITE_KEYCLOAK_CLIENT_ID,
    // Derived from the runtime origin rather than configured, which is what lets
    // one built image serve PR previews, TEST and PROD. Both of these have to be
    // registered on the CSS integration for each environment.
    redirect_uri: `${appOrigin}${AUTH_CALLBACK_PATH}`,
    post_logout_redirect_uri: appOrigin || `${window.location.origin}/`,
    response_type: 'code',
    scope: 'openid profile email',

    // Tokens survive a page reload but not a closed tab. localStorage would
    // leave them readable to any script on the origin for longer than the
    // session needs. (The Cognito code kept them in cookies only because
    // Amplify's storage had to be installed before `configure()` ran.)
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),

    // HBS renews on demand, when an API call needs a token. No background poll,
    // so an idle user times out rather than being kept alive by a timer.
    automaticSilentRenew: false,

    // Everything HBS needs is on the access token now — see the claim table in
    // backend/src/main/java/.../util/JwtPrincipalUtil.java. A /userinfo
    // round-trip per sign-in would buy nothing.
    loadUserInfo: false,
  };
};

/**
 * The shared {@link UserManager}, created on first use rather than at module
 * evaluation: `window.config` is injected by the container entrypoint and is not
 * guaranteed to be present when this module is first imported.
 */
export const getUserManager = (): UserManager => {
  if (!userManager) {
    userManager = new UserManager(buildSettings());
  }
  return userManager;
};

/** Only for tests, which build a fresh manager per case. */
export const resetUserManager = (): void => {
  userManager = null;
  renewInFlight = null;
  lastRenewalAt = 0;
};

/** Records a sign-in, whose tokens are as fresh as a renewal's. */
export const noteSignIn = (): void => {
  lastRenewalAt = Date.now();
};

/** Milliseconds since this tab last got fresh tokens; Infinity when unknown. */
export const msSinceRenewal = (): number =>
  lastRenewalAt === 0 ? Number.POSITIVE_INFINITY : Date.now() - lastRenewalAt;

/**
 * Whether this token is expired, or close enough that it soon will be.
 *
 * The two fields are not independent: oidc-client-ts derives `expired` from
 * `expires_in`, so an explicit `false` already means there is life left even when
 * the remaining seconds are not to hand. Only when neither says anything is
 * renewing the safer guess — an unknown expiry is exactly the case where being
 * wrong costs a refused request.
 */
export const needsRenewal = (user: Pick<User, 'expired' | 'expires_in'>): boolean => {
  const secondsLeft = user.expires_in;
  if (secondsLeft === undefined) {
    return user.expired !== false;
  }
  return secondsLeft <= RENEW_WHEN_SECONDS_LEFT;
};

/**
 * The stored user, silently renewed if their access token has expired.
 *
 * Returns null when nobody is signed in, rather than attempting a renewal — and
 * that distinction matters more than it looks. `signinSilent` renews from the
 * stored refresh token (no iframe, unaffected by third-party cookie rules) but
 * only when there is a stored user to renew from. With nothing stored,
 * oidc-client-ts falls back to a hidden-iframe flow, and since no
 * `silent_redirect_uri` is configured that attempt cannot succeed: it runs until
 * `silentRequestTimeoutInSeconds` expires, which defaults to 10.
 *
 * Every first-time visitor to the Landing page has nothing stored, so calling it
 * unconditionally would hold the page behind a spinner for ten seconds before
 * the sign-in button appeared.
 */
export const loadStoredUser = async (
  manager: Pick<UserManager, 'getUser' | 'signinSilent'>,
): Promise<User | null> => {
  const user = await manager.getUser();

  // Never signed in: there is no session to restore and nothing to renew.
  if (!user) {
    return null;
  }

  if (!needsRenewal(user)) {
    return user;
  }

  return await renewOnce(manager);
};

/**
 * The stored user, renewed only if the access token is at or near expiry.
 *
 * Cheap to call often — a no-op until the token is nearly out — which is what
 * lets it sit at the top of every API request. When it is not a no-op,
 * {@link renewOnce} makes sure the concurrent callers behind it share one
 * renewal rather than racing several.
 */
export const ensureFreshUser = async (
  manager: Pick<UserManager, 'getUser' | 'signinSilent'>,
): Promise<User | null> => {
  const user = await manager.getUser();
  if (!user) {
    return null;
  }
  return needsRenewal(user) ? await renewOnce(manager) : user;
};

/**
 * Renews now, whatever the access token's expiry — shared with any renewal
 * already in flight. Rejects when the refresh token is spent.
 */
export const forceRenewUser = (manager: Pick<UserManager, 'signinSilent'>): Promise<User | null> =>
  renewOnce(manager);
