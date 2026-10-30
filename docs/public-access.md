# Public (anonymous) read-only area

**Decision (2026-10-21, [questions.md](questions.md) Q1):** keep a public
read-only area that replaces the legacy app's anonymous pages. Anyone can use
it without logging in. Everything else in HBS still needs an IDIR or Business
BCeID login and an HBS role ([roles-and-security.md](roles-and-security.md)).

- SPA: every public page is under `/public`: `/public` (home),
  `/public/<screen-id>`, `/public/access` and `/public/invoice-information`
  (the statement e-mails' "Explanation of Symbols"; the old
  `/invoice-information` redirects there). The public layout has its own side
  nav (`pages/public/publicMenu.ts`, rendered by the shared `LayoutSideNav`).
  The login page links only to "How to get access" (`/public/access`), not to
  the public queries; the "Access not granted" page links to both.
- API: `GET /api/v1/hbs/public/**` only. No token is needed, and any token sent
  is ignored.

## What the legacy app exposed

Legacy HBS ran behind Siteminder at `/ext/hbs`, and WebADE let a request
through with no user. `UssHelper.getUserFromSession` turned such a request
into the `Public_User` (user type `PUB`). The JSPs then showed it
`include/mainmenu_public.jsp` and `tab_public.jsp`. These are picked by
`include/mainmenu.jsp` whenever the user holds none of `MOF_USER`,
`CLI_USER` or `SPC_USER`, so a logged-in user with no HBS role saw the public
menu too.

`hbs_action_lnk.sql` has no grants for the public user. Instead, the public
pages had no `<hbs1:pagesecurity/>`, and nor did 297 of the 386 JSPs. No
`web.xml` security constraint applied (the WebADE filter only resolved the
user), and there was no `/pub` vs `/ext` split in the app itself.

| Legacy public menu entry | Legacy screens | New public screen | Legacy rule for the anonymous viewer → new behaviour |
|---|---|---|---|
| Harvest Reports › By Date of Invoice (Billing History) | P431/P432 (`opq/ftas/invoiceSummary.do`) | `/public/billing-history` | `HBS_GET_HISTORY` BT17247 DECODE and `DocumentDeliveryManagerBean.processHarvestData` (`Public_User`): an individual mark holder's / site owner's name and location name are NOT RELEASABLE → same, through the `:hbsIsMinistry`/`:hbsViewerClient` CASE. `MarkMonthlyBillingAction` range rules are enforced server-side (below). |
| Harvest Reports › By Date of Scale (Scaling History) | P441/P442 | `/public/scaling-history` | same |
| Invoice Copies › Single Invoice; Ratio, Sample, and Volume Statement Copies › Single Statement | P449 | `/public/issued-document` | Anyone could list any document. The download was severed by `TransactionStatementManagerBean.freedomOfInformationCheck`: for the public, the send-to / copy-to block of an Individual client becomes NOT RELEASABLE (`freedom-of-info-{send,copy}-to.xsl`). → same (`StatementDocumentService`), 7-year rule kept |
| … › Multiple Invoices / Multiple Statements | P450 → P451/P461/P456/P466/P471/P476 | `/public/issued-documents` (+ Download XML = P451 "Generate XML", ≤ 200, severed, header relationship `Public`) | "Print" / "Print Client Register" were blocked for the public when any listed party was an Individual (`P450Action.canShowInPublic`, "The report contains personal information, and is temporarily unavailable for public viewing") → the reports are **not offered** publicly at all. "Transmission ID" (reached only from the logged-in P453) is not a public criterion |
| Other Queries › Timber Mark Information | P480 / P481 | `/public/timber-mark-search`, `/public/timber-mark` | `TimberMarkReaderBean.isSevered`: an individual holder's name, location name and address are Not Releasable → same, in SQL |
| Other Queries › Stumpage Rate Search | P230 / P231 | **not yet** | P230 is not ported yet (work package 6, `HBS_RATES` OUT-parameter procs). The timber-mark page shows a mark's appraisal stumpage and SPG rates (P481) |
| Other Queries › Stratum Advisor Menu | P850 → P854/P880, P883, P888/P884, P882, P851/P852, P853/P860, P861–P868 | `/public/sampling-plans`, `sampling-plan`, `stratum-plan`, `sampling-populations`, `sampling-strata`, `sampling-population-stats`, `sampling-stratum-stats` | HBS3R880 / `P854Action` forced the anonymous viewer to **Active** plans → same (`PLAN_VISIBLE` with no viewer client). HBS3R889 printing was blocked when individuals were involved (BT17247, `P883.jsp`) → reports not offered publicly |
| Public User services › Register ("on-line HBS enrolment and client verification") | `uss/public/restrictedClientAdminVer.jsp` → `/uss/Enrollment` (`ClientAdminVerifyAction`, `EnrollmentRequestAction`) | `/public/access` ("How to get access") | See below |
| Scale Returns (Single/Multiple Detail Return, Single/Multiple Summary Return) | P048, `searchGeneralForFS`, `summaryScaleReturnSearch` | **not public** | Not in the Q1 decision. Scale-return data is client-fenced in the new app; making it public needs a separate decision |
| Special Reports (List of Invoices and Volume Statements, Scale Site Summary, Cut to Cruise Comparison) | P421, P401, P490 | **not public** | Not in the Q1 decision. P421 is client-fenced in the new app. P401/P490 are Jasper reports with dynamic SQL in their procs, so they stay logged-in only |
| Tab bar FAQ / Glossary / HBS Information / Manuals | `tab_public.jsp` | — | Static MOF web pages, outside the app |

### Self-registration

The legacy "Register" link worked like this:

1. A client administrator verified their client with Client No/Loc and a
   **Registration Key**. `ValidateAuthenticateKey.getKey` computes the key from
   the client number and location, which makes it guessable.
2. They then filled the enrolment form.
3. `HBSUserManagerBean.createUserRequestPublic` wrote an `HBS_USER_REQUEST`
   (status NEW) and e-mailed it.
4. A ministry user administrator approved it, creating the `HBS_USER` and its
   WebADE roles.

The new app has no HBS user accounts: users log in with IDIR or Business BCeID
and get roles in FAM. So `/public/access` explains how to get access, with the
legacy wording adapted (Client No/Loc, roles, help desk). Nothing is written to
`HBS_USER_REQUEST`, and no business process reads it outside User Services.

**Dependency found:** legacy batch statement delivery (B2065,
`DocumentDeliveryTransmissionManagerBean`, ~l.666) e-mails statements to the
active `HBS_USER`s holding `CLI_DOC_RCVR` for the client and location. Users
who get `HBS_CLI_DOC_RCVR` only in FAM are not in `HBS_USER`, so the
still-running legacy batch will not e-mail them. Fix this in the FAM role
migration (Q2) or the statement-delivery port (work package 1). It does not
come from the public area.

## Rules (what the code enforces)

| Rule | Where |
|---|---|
| Only `GET /api/v1/hbs/public/**` is anonymous. Every other method on those paths is denied, and every other path keeps the default chain (bearer token required) | `config/PublicAccessSecurityConfig` (own filter chain, `@Order(1)`; `HbsApiResourceApplication`'s chain unchanged) |
| Every public row and document is produced for `Viewer.ANONYMOUS`: `:hbsIsMinistry='N'`, `:hbsViewerClient=''`, `:hbsUserType='PUB'`, `:hbsUserId=''`. Every FOI CASE severs, and a token sent on a public path is ignored | `query/Viewer`, `QueryService.search/list/count(QueryDefinition, Viewer, …)`, `publicaccess/PublicController` |
| Only whitelisted queries run. Each one is derived from the registry query of the same id (same reviewed SQL), with criteria narrowed and `maxRows` capped. A client-fenced query is refused at start-up unless its entry drops the fence on purpose (issued documents only: client numbers, no names) | `publicaccess/PublicQueries` |
| Statement/invoice copies: the same service as the logged-in download (lookup, 7-year rule, FOI severing). PDF goes through the same seam (`StatementDocumentService.pdf`), so the public PDF is always rendered from the severed XML | `queries/StatementDocumentService` (shared with `StatementDocumentController`) |
| Row links (`documentUrl` / `documentXmlUrl`) are rewritten to `/api/v1/hbs/public/statements/…` | `PublicController.publicLinks` |
| Legacy form rules bind public searches. Issued documents need a date range or document number ("You must specify either issue or scale date range for your search."). Harvest history: From ≤ To, at most one year, not a future month, From on or after max(today − 15 years, 1995-01-01), with `MarkMonthlyBillingAction` messages | `PublicQueries.validate` / `historyRange` |
| No Jasper reports, no commands, no history "Send XML", no client picker (it needs a login) | `pages/public/publicScreens.ts` (and the backend has no public report endpoint) |

## Rate limits and caps

| Setting (`application.properties`) | Default | Meaning |
|---|---|---|
| `hbs.public.enabled` (`HBS_PUBLIC_ENABLED`) | `true` | `false` answers 404 on every public path (kill switch, no build) |
| `hbs.public.max-rows` (`HBS_PUBLIC_MAX_ROWS`) | `1000` | Most rows any public query pages through, lists or exports |
| `hbs.public.rate-limit.capacity` | `30` | Burst per client IP (token bucket) |
| `hbs.public.rate-limit.refill-per-minute` | `60` | Sustained requests per minute per client IP. Over the limit: 429 + `Retry-After` |
| `hbs.public.rate-limit.max-concurrent` | `4` | Public requests running at once per pod. Anonymous traffic can't take the 10-connection JDBC pool; no slot in 2 s: 503 |
| `hbs.public.rate-limit.client-ip-header` | `X-Real-IP` | Header with the real client IP. Blank uses the socket address |

- The filter (`publicaccess/PublicRateLimitFilter`) keeps its buckets in
  memory per pod, so N replicas allow N times the limit.
- **Client IP:** the OpenShift router appends the caller to `X-Forwarded-For`.
  Caddy (`frontend/Caddyfile`) trusts the private ranges, reads that list
  right-to-left (`trusted_proxies_strict`), and sends the first untrusted
  address as `X-Real-IP`. The backend accepts traffic only from the frontend pod
  (NetworkPolicy `*-backend-*-ingress`), so a caller can't forge `X-Real-IP`.
- **Proxy limitation:** callers whose own address is private (10.x / 172.16.x,
  e.g. on the government network behind a NAT) fall back to the next hop.

**WAF (Coraza):** the public paths are ordinary GETs under `/api`, the same as
the logged-in ones, so no rule change is needed.

- Rules 1001/1002/1005 (SQLi/XSS on ARGS) apply to anonymous criteria. That is
  intended.
- If business users report false positives on free-text criteria, add a narrow
  `ctl:ruleRemoveTargetById` for that argument, not a path exclusion.
- Caddy has no built-in rate limiter (it would need the `caddy-ratelimit`
  plugin), so per-IP limiting stays in the backend. A router-level limit
  (`haproxy.router.openshift.io/rate-limit-connections*` on the frontend
  Route) can be added as a coarse outer guard if needed.

## How to add a public page

1. Make sure the logged-in screen and its registry query exist. The query's SQL
   must sever every individual client's name, location name and address
   through the viewer binds (`QueriesReportsCatalog.sever`).
   `PublicQueriesTest.everyPersonalColumnIsSevered` checks the SQL.
2. Add an entry to `PublicQueries.ENTRIES`: the query id, the allowed criteria,
   any required criteria, and any legacy form rule. Add the code-list queries
   its criteria use as well.
3. Mirror the ids in `frontend/src/pages/public/publicScreens.ts`:
   - `PUBLIC_QUERIES`
   - `PUBLIC_SCREEN_IDS`
   - `DROPPED_CRITERIA` / `PUBLIC_NOTES` if needed

   The public screen is derived from the logged-in one, so labels stay the
   legacy labels.
4. Add a tile to `PublicHomePage` under its legacy public-menu heading.
5. Add a row to the table above and a test in `PublicAccessSecurityTest` /
   `PublicQueriesTest`.

## Not done / follow-ups

- P230 Stumpage Rate Search: add it publicly once work package 6 ports it
  (anonymous viewer, same service).
- Statement PDF: work package 1 implements `StatementDocumentService.pdf`.
  The public path picks it up unchanged. Until then `.pdf` answers 501 on both
  paths.
- TEST regression ([coexistence.md](coexistence.md) row 9). Compare legacy
  anonymous pages and the new public pages on the same records, especially:
  - individual-client severing in history, timber marks and statement XML;
  - Active-only sampling plans;
  - 429/503 behaviour behind the real router.
