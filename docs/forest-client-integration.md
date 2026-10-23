# Forest Client Integration (nr-forest-client-api)

HBS looks up forest clients — the client picker on every "Client No." field,
and the active organization's name in the header and org picker — through
**nr-forest-client-api**, the ministry's read API over `FOREST_CLIENT` and
`CLIENT_LOCATION`, published on the API Services Portal (api.gov.bc.ca).

It replaces the `PKG_SIL21_CLIENT_SEARCH` proc the app inherited from the
nr-fsp-new template. HBS's proxy user has no EXECUTE grant on that package
(legacy HBS never called it; its popup was the shared `/cli/clientSearch.jsp`),
so every search failed with `PLS-00201`.

## Flow

The SPA never calls the API directly — the API key stays in the backend. The
backend's `ClientApiController` proxies three calls through
`client/ForestClientApiClient`:

| HBS endpoint | Forest Client API call | Used for |
|--------------|------------------------|----------|
| `GET /api/v1/hbs/clients/search?term=` | `GET /api/clients/search/by?name=&acronym=` (ranked: exact acronym, or Jaro-Winkler ≥ 0.8 on the full name); an all-digit term → `GET /api/clients/findByClientNumber/{n}` (zero-padded to 8) | client field type-ahead (`ClientComboBox`) |
| `GET /api/v1/hbs/clients/{n}` | `GET /api/clients/findByClientNumber/{n}` | org names (header, org picker) |
| `GET /api/v1/hbs/clients/{n}/locations` | `findByClientNumber` + `GET /api/clients/{n}/locations` | filling `Loc` after a pick |

Every `client` field is a type-ahead (`ClientComboBox`, after nr-fta's): it
suggests clients by number, acronym or name, and a typed number that isn't
picked is kept as-is. Picking a client with exactly one location also fills the
`Loc` field after it; otherwise the location is entered by hand.

**FOI.** An individual client's (`client_type_code = 'I'`) name and location
name are "Not Releasable" to industry viewers other than that client — the
same rule the HBS queries apply (`QueriesReportsCatalog.releasable`).

**Errors.** Not configured → 503 "Client lookup isn't available"; a rejected
key (401/403) or an unreachable API → 502 with a plain message; an unknown
client number → 404 (the type-ahead just shows no suggestion).

## What stays in SQL

Nothing reads `FOREST_CLIENT` directly, and HBS doesn't need a grant on it.
Client data used inside a query (result-list and report columns, the FOI
severing, the legacy "client / location exists" checks on returns, profiles
and uploads) is joined in SQL from `V_CLIENT_PUBLIC` and `CLIENT_LOCATION`,
both granted to `FSA_HBS_READ_WRITE_ROLE` ([database.md](database.md)). Moving
those to the API would turn one join into a call per row and make the upload
unpack and return edits depend on the API being up.

## Configuration

| Property | Env var | Meaning |
|----------|---------|---------|
| `hbs.forest-client-api.base-url` | `FOREST_CLIENT_API_URL` | scheme + host, e.g. `https://nr-forest-client-api-test.api.gov.bc.ca` (paths start at `/api/clients`) |
| `hbs.forest-client-api.api-key` | `FOREST_CLIENT_API_KEY` | HBS's API key from the API Services Portal, sent as `X-API-KEY` |

Locally: fill both in `application-local.properties`. Deploys:
`FOREST_CLIENT_API_URL` and `FOREST_CLIENT_API_KEY` are GitHub environment
**secrets** (dev / test / prod); `reusable-deploy.yml`
passes them to `backend/openshift.deploy.yml`, which keeps the key in the
backend Secret. Both are optional for a deploy to start.
