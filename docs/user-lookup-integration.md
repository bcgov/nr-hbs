# User Directory Integration (nr-user-lookup-api)

HBS resolves IDIR (and, in contract, Business BCeID) user details — display
name, email — from **nr-user-lookup-api**, the shared BC Gov identity-lookup
service. This **replaces the FAM identity-lookup integration** nr-fsp-new used
previously: the app no longer calls FAM for user/identity lookups. (BC Gov SSO /
Keycloak with FAM roles is the *authentication* provider — see
[roles-and-security.md](roles-and-security.md) — just not the directory.)

All calls go through one client, `client/UserLookupClient`, against base path
`/api/v1/user-lookup`:

| Method | Endpoint | Used for |
|--------|----------|----------|
| `searchIdir(userId, firstName, lastName)` | `POST /idir-users/search` | partial-match IDIR user search (District Notification "Add user" picker) |
| `getIdirDetail(userId)` | `GET /idir-account-detail` | exact IDIR lookup by username (`findByUserId`, digest email resolution) |
| `getBusinessBceid(searchBy, searchValue)` | `GET /businessBceid` | Business BCeID lookup — present for contract completeness; no in-tree caller yet |

Lookups are **best-effort**: any upstream error resolves to an empty list /
`Optional.empty()` rather than failing the request.

## Authentication — one strategy: service account

The old FAM integration used **two** auth strategies: it forwarded the caller's
user JWT for in-request lookups, and only fell back to a service account for
the background digest. That distinction is gone.

**Every** nr-user-lookup-api call now authenticates with HBS's own Keycloak
**`grant_type=client_credentials`** bearer token (`ClientCredentialsTokenSource`,
which caches the access token until ~60s before expiry). The caller's Keycloak JWT
is **not** forwarded — nr-user-lookup-api validates the service account's default
client scopes, not an end-user token. So the in-request picker and the scheduled
digest share the same client and the same identity.

If the token-url / client-id / client-secret trio is unset the client has no
credentials and calls go out unauthenticated (best-effort → empty results);
`UserLookupClient` logs its active mode at startup.

## Consumers

| Consumer | Context | What it does |
|----------|---------|--------------|
| `service/v1/UserDirectoryService` | in-request | IDIR **user search** (`GET /api/v1/hbs/users/search`), `findByUserId`, and the batched **display-name resolve** (`POST /api/v1/hbs/users/resolve`) behind the SPA's `<UserName>` component — used to show names instead of raw `ENTRY_USERID` / `UPDATE_USERID` audit values |

It is backed by `UserLookupClient`, so it never carries the user's token. HBS
audit columns are stored AD-prefixed (`IDIR\NAME`, `BCEID\name`); the prefix
is stripped before the lookup.

## Configuration

All keys are optional (`USER_LOOKUP_*` env vars in the deployment):

| Property | Env | Purpose |
|----------|-----|---------|
| `hbs.user-lookup.base-url` | `USER_LOOKUP_BASE_URL` | scheme + host of nr-user-lookup-api |
| `hbs.user-lookup.token-url` | `USER_LOOKUP_TOKEN_URL` | Keycloak token endpoint for `client_credentials`, in the **forests** realm (`<KEYCLOAK_SA_ISSUER_URI>/protocol/openid-connect/token`), not the standard realm users log in to |
| `hbs.user-lookup.client-id` | `USER_LOOKUP_CLIENT_ID` | HBS's confidential service-account client id |
| `hbs.user-lookup.client-secret` | `USER_LOOKUP_CLIENT_SECRET` | service-account secret |
| `hbs.user-lookup.scope` | `USER_LOOKUP_SCOPE` | optional; the scopes are DEFAULT client scopes on the account, so normally left blank |
| `hbs.user-lookup.connect-timeout` / `.read-timeout` | — | `RestClient` timeouts (default 5s / 10s) |
