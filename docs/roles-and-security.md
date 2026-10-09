# Roles and security

How HBS authenticates users, what each role can do, and how authorization is
enforced front-to-back. Login is the nr-fta / nr-rept setup: **BC Gov SSO
(Keycloak) directly, no AWS Cognito**. The authorization mechanics (JWT →
Spring resource server → `@PreAuthorize` → service-layer fence) are the same
as nr-fsp-new; the role model differs because the legacy HBS role model
differs.

## Authentication

- **BC Gov SSO (Keycloak), standard realm**, through HBS's CSS integration,
  with **IDIR** (`kc_idp_hint=azureidir`, ministry staff) and **Business
  BCeID** (`kc_idp_hint=bceidbusiness`: licensees, scale sites, submission
  agents, scale-software vendors). Roles come from **FAM** as the token's
  `client_roles`.
- **SPA** (`frontend/src/services/keycloak.ts`, `context/auth`): `oidc-client-ts`
  Authorization Code + PKCE, `authority = VITE_KEYCLOAK_URL` (realm issuer),
  `client_id = VITE_KEYCLOAK_CLIENT_ID`, redirect `/authCallback`, tokens in
  `sessionStorage`. One single-flight refresh (`renewOnce`) is shared by
  `apiFetch`, the session-timeout keepalive and the login screen. Logout is
  Keycloak's `end_session_endpoint` with `id_token_hint`, returning to the app
  root. Ported from nr-fta.
- **API** (`HbsSecurityConfig`): a stateless resource server. It checks the
  signature against `<issuer>/protocol/openid-connect/certs` (cached JWKS),
  issuer and expiry. The token's `azp` must equal `KEYCLOAK_CLIENT_ID`
  (`AuthorizedPartyValidator`), since every client in the shared realm is
  signed by the same keys. It must also carry at least one HBS role
  (`HbsRoleValidator`).
- **Identity claims** (`RequestUtil`): `identity_provider` (falling back to the
  `preferred_username` suffix), `idir_username` / `bceid_username`. The audit
  user id is `IDIR\NAME` / `BCEID\NAME`, truncated to the VARCHAR2(30) audit
  columns.
- **No anonymous access.** The legacy app let anonymous users run several
  queries (invoice/statement copies, timber mark, stumpage rates, sampling
  plans) and self-register. That has no equivalent in the FSP model, so it is
  an open decision (`docs/questions.md` Q1).

## Role model — legacy WebADE roles carried over 1:1

The legacy authorization matrix is the WebADE grant script
`trunk/scripts/5.3.0/08/dml/hbs_action_lnk.sql` (25 roles → `web:/<path>`
grants), plus the `include/userAccountAdmin*Roles.jsp` role pickers. Each
legacy role becomes one FAM client role, prefixed `HBS_` unless it already was:

| Legacy role | FAM role | Label | User type | Client-scoped |
|---|---|---|---|---|
| MOF_USER | `HBS_MOF_USER` | Ministry User (base role) | IDIR | — |
| MOF_ADMIN | `HBS_MOF_ADMIN` | Ministry User Administrator | IDIR | — |
| MOF_MGR | `HBS_MOF_MGR` | Ministry User Manager | IDIR | — |
| HBS_RATE_ADMIN | `HBS_RATE_ADMIN` | Rate Administrator | IDIR | — |
| HBS_HELP_DESK | `HBS_HELP_DESK` | Help Desk | IDIR | — |
| HBS_SUMM_DATA_CTL | `HBS_SUMM_DATA_CTL` | Summary Data Control | IDIR | — |
| HBS_SUMM_DATA_CORR | `HBS_SUMM_DATA_CORR` | Summary Data Correction | IDIR | — |
| HBS_DTL_DATA_ENT | `HBS_DTL_DATA_ENT` | Detail Data Entry | IDIR | — |
| HBS_PROD_CTL | `HBS_PROD_CTL` | Production Control | IDIR | — |
| HBS_BILL_ADMIN | `HBS_BILL_ADMIN` | Billing Administrator | IDIR | — |
| HBS_SCALE_ADMIN | `HBS_SCALE_ADMIN` | Scaling Administrator | IDIR | — |
| HBS_SUPER_MGR | `HBS_SUPER_MGR` | Super Manager | IDIR | — |
| HBS_INV_CORR_APP | `HBS_INV_CORR_APP` | Invoice Correction Approver | IDIR | — |
| MOF_SCALER | `HBS_MOF_SCALER` | Ministry Check Scaler | IDIR | — |
| HBS_SMP_ADMIN | `HBS_SMP_ADMIN` | Ministry Sample Plan Administrator | IDIR | — |
| HBS_WRA_ADMIN | `HBS_WRA_ADMIN` | Waste/Residue Rate Administrator | IDIR | — |
| HBS_DISABLED_BYPASS | `HBS_DISABLED_BYPASS` | Disabled-mode bypass | IDIR | — |
| CLI_USER | `HBS_CLI_USER_FOREST_CLIENT-<client>` | Industry User (base role) | BCeID | ✔ |
| CLI_ADMIN | `HBS_CLI_ADMIN_FOREST_CLIENT-<client>` | Industry User Administrator | BCeID | ✔ |
| CLI_SITE_ADMIN | `HBS_CLI_SITE_ADMIN_FOREST_CLIENT-<client>` | Industry Site Administrator | BCeID | ✔ |
| CLI_SCALER | `HBS_CLI_SCALER_FOREST_CLIENT-<client>` | Industry Scaler | BCeID | ✔ |
| CLI_SMP_ADMIN | `HBS_CLI_SMP_ADMIN_FOREST_CLIENT-<client>` | Industry Sample Plan Administrator | BCeID | ✔ |
| CLI_DOC_RCVR | `HBS_CLI_DOC_RCVR_FOREST_CLIENT-<client>` | Industry Document Receiver | BCeID | ✔ |
| CLI_CRUISE_ADMIN | `HBS_CLI_CRUISE_ADMIN_FOREST_CLIENT-<client>` | Industry Cruise Based Administrator | BCeID | ✔ |
| SPC_USER | `HBS_SPC_USER_FOREST_CLIENT-<client>` | Other Industry User (base role) | BCeID | ✔ |
| SPC_ADMIN | `HBS_SPC_ADMIN_FOREST_CLIENT-<client>` | Other Industry Administrator | BCeID | ✔ |
| SPC_SFTWR_VNDR | `HBS_SPC_SFTWR_VNDR_FOREST_CLIENT-<client>` | Software Vendor | BCeID | ✔ |
| SPC_SUBM_AGNT | `HBS_SPC_SUBM_AGNT_FOREST_CLIENT-<client>` | Data Submission Agent | BCeID | ✔ |

FAM puts a client-tied grant's scope in the role name and nowhere else:
`HBS_CLI_SCALER_FOREST_CLIENT-00012345` (`RoleScope`, ported from nr-fta;
`<client>` is the 8-digit forest-client number). FAM bookkeeping roles
(`FAM:EXPIRES:…`) are ignored.

### Roles stack (differs from nr-fsp-new)

nr-fsp-new collapses a user to one effective role. **HBS does not**: the
legacy model gives every user a base role plus any number of functional roles
(e.g. a site administrator holds CLI_USER + CLI_SITE_ADMIN), and capabilities
are the union. `TokenRoles.authoritiesFrom` therefore emits one
`ROLE_HBS_*` authority (the base role, scope stripped) per recognised role.

A BCeID user party to several clients picks an **active client** (FSP's
`OrgSelectionPage`, header `X-HBS-Active-Org-Client-Number`). Client-scoped
roles only apply for the client they were granted for
(`RequestUtil.getCurrentRoles()`, mirrored by `useScopedUser()` in the SPA).

## Capability matrix

Each legacy `web:/<area>/<path>` grant became a named capability. The
backend source of truth is `query/Capability.java` (+ `security/HbsAuthorities`
for `@PreAuthorize`); the SPA mirror is `frontend/src/routes/access.ts`.
Keep the three in sync.

| Capability | Granted to |
|---|---|
| `ANY_USER` | `HBS_MOF_USER`, `HBS_CLI_USER`, `HBS_SPC_USER` |
| `MINISTRY` | `HBS_MOF_USER` |
| `QUERIES_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER` |
| `SCALE_RETURNS_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER`, `HBS_SPC_USER` |
| `SUMMARY_RETURN_EDIT` | `HBS_SUMM_DATA_CTL`, `HBS_SUMM_DATA_CORR`, `HBS_SCALE_ADMIN`, `HBS_BILL_ADMIN`, `HBS_CLI_SCALER`, `HBS_CLI_SITE_ADMIN`, `HBS_CLI_CRUISE_ADMIN`, `HBS_SPC_SUBM_AGNT` |
| `SUMMARY_RETURN_ADMIN` | `HBS_BILL_ADMIN` |
| `DETAIL_RETURN_EDIT` | `HBS_DTL_DATA_ENT`, `HBS_SCALE_ADMIN`, `HBS_MOF_SCALER`, `HBS_CLI_SCALER`, `HBS_CLI_SITE_ADMIN` |
| `DETAIL_RETURN_APPROVE` | `HBS_BILL_ADMIN`, `HBS_INV_CORR_APP` |
| `XML_SUBMIT` | `HBS_DTL_DATA_ENT`, `HBS_MOF_SCALER`, `HBS_SCALE_ADMIN`, `HBS_CLI_SITE_ADMIN`, `HBS_CLI_SCALER`, `HBS_SPC_SFTWR_VNDR`, `HBS_SPC_SUBM_AGNT` |
| `BATCH_SLIP_EDIT` | `HBS_BILL_ADMIN`, `HBS_DTL_DATA_ENT`, `HBS_PROD_CTL`, `HBS_SCALE_ADMIN`, `HBS_SUMM_DATA_CTL` |
| `PRODUCTION_CONTROL` | `HBS_PROD_CTL` |
| `SAMPLING_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER` |
| `SAMPLING_ADMIN` | `HBS_SMP_ADMIN`, `HBS_CLI_SMP_ADMIN` |
| `RATING_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER` |
| `RATING_MINISTRY_VIEW` | `HBS_MOF_USER` |
| `RATE_ADMIN` | `HBS_RATE_ADMIN` |
| `WASTE_RATE_ADMIN` | `HBS_WRA_ADMIN` |
| `BILLING_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER` |
| `BILLING_ADMIN` | `HBS_BILL_ADMIN`, `HBS_SUPER_MGR` |
| `FINAL_BILL_ADMIN` | `HBS_BILL_ADMIN`, `HBS_SUPER_MGR` |
| `FIELD_SCALED_DECKS` | `HBS_BILL_ADMIN`, `HBS_SCALE_ADMIN`, `HBS_SUPER_MGR` |
| `BILLING_FORCE` | `HBS_SUPER_MGR` |
| `PROFILES_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER` |
| `SCALE_SITE_ADMIN` | `HBS_SCALE_ADMIN` |
| `PROFILE_BILLING_ADMIN` | `HBS_BILL_ADMIN` |
| `SCALER_PROFILE_EDIT` | `HBS_SCALE_ADMIN`, `HBS_CLI_SCALER` |
| `SCALE_CONTROL_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER` |
| `SCALE_CONTROL_MINISTRY_VIEW` | `HBS_MOF_USER` |
| `SCALE_CONTROL_ADMIN` | `HBS_SCALE_ADMIN` |
| `ANOMALY_EDIT` | `HBS_SCALE_ADMIN`, `HBS_CLI_SCALER`, `HBS_CLI_SITE_ADMIN` |
| `SOFTWARE_USE_VIEW` | `HBS_MOF_USER`, `HBS_CLI_USER`, `HBS_SPC_SFTWR_VNDR` |
| `USER_ADMIN` | `HBS_MOF_ADMIN`, `HBS_MOF_MGR`, `HBS_CLI_ADMIN`, `HBS_SPC_ADMIN` |
| `CACHE_ADMIN` | `HBS_PROD_CTL` |

## Enforcement, front to back

1. **SPA** hides nav entries, screens and buttons the user can't use
   (`can(user, capability)`). This is an affordance only.
2. **Coarse gate.** Every controller method has an `@PreAuthorize` (any HBS
   base role at minimum).
3. **Per-resource gate.** Every registry query/command carries its own
   capability, checked in `QueryService` / `CommandService`
   (`Capability.require()`). Reports are split into industry-allowed and
   ministry-only (`HbsReportController.INDUSTRY_REPORTS`).
4. **Row-level client fence.** Queries over client-owned data declare
   `clientScope`. For industry users the API appends it bound to their active
   client, **regardless of the criteria they sent**. Ministry users skip it.
   `HbsAccessGuard` provides the same check for bespoke services.
5. **FOI severing.** Individual (`client_type_code = 'I'`) client names and
   addresses are "Not Releasable" to anyone but ministry staff and the client
   itself, done in SQL via the `:hbsIsMinistry` / `:hbsViewerClient` binds.
6. **Identity prompts are never trusted.** Report prompts like
   `*_USERTYPE` / `*_USERCLILOC` and command audit columns are filled from the
   JWT server-side.

### Legacy gaps closed

The legacy app protected only **89 of 386 JSPs** (`<hbs1:pagesecurity/>`),
and its Struts actions almost never checked roles. Menus merely hid links, so
many maintenance screens could be opened by URL. Client users could also list
other clients' documents by editing the pre-filled client number. Both are
closed by (3) and (4). See `docs/pinch-points.md` §13.

## Data domains

Legacy users were also scoped to **districts**, **scale sites** and **client
locations** (`HBS_USER_DIST_DATA_DOMAIN`, `HBS_USER_SITE_DATA_DOMAIN`,
`HBS_USER_CLI_LOC_DATA_DOMAIN`). They drove default criteria ("Sites in
Associated Districts") and some admin write checks. User Services
(enrolment, requests, approvals) is replaced by FAM. The domain maintenance is
kept as Administration › User Data Domains (capability `USER_ADMIN`).

## FAM setup checklist

- [ ] Request the CSS (Keycloak) integration for HBS: standard realm, public
      client with PKCE, identity providers **IDIR - MFA** (`azureidir`) and
      **Business BCeID** (`bceidbusiness`), FAM-managed client roles.
- [ ] Register redirect URIs `<origin>/authCallback` and post-logout URIs
      `<origin>` for each environment, PR slots `nr-hbs-0..49`, and
      `http://localhost:3000` for local development.
- [ ] Create the 17 ministry roles and the 11 client-tied role families
      above in the HBS FAM application.
- [ ] Set the GitHub environment variables `KEYCLOAK_ISSUER_URI`,
      `KEYCLOAK_CLIENT_ID` and `KEYCLOAK_SA_ISSUER_URI` (forests realm, for
      the user-lookup service account).
- [ ] Request an nr-forest-client-api key on the API Services Portal; set
      the GitHub environment secrets `FOREST_CLIENT_API_URL` and
      `FOREST_CLIENT_API_KEY` ([forest-client-integration.md](forest-client-integration.md)).
- [ ] Map existing WebADE role holders (`HBS_USER` + WebADE role tables) to
      FAM groups for cut-over.
