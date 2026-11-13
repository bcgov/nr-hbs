# API gateway configuration: scale data API

Kong configuration for the [API Services Portal](https://api.gov.bc.ca) (APS)
that exposes the scale data API (the FTP replacement) to external clients.
Background: [docs/scale-data-api-hosting.md](../docs/scale-data-api-hosting.md).

| File | HBS environment | Public host | Upstream (in-cluster) | Token issuer |
|---|---|---|---|---|
| `test/gw-config.yaml` | TEST | `nr-hbs-scale-data-test.api.gov.bc.ca` | `nr-hbs-backend-test.<licence>-test.svc:8080` | `https://test.loginproxy.gov.bc.ca/auth/realms/standard` |
| `prod/gw-config.yaml` | PROD | `nr-hbs-scale-data.api.gov.bc.ca` | `nr-hbs-backend-prod.<licence>-prod.svc:8080` | `https://loginproxy.gov.bc.ca/auth/realms/standard` |

Both are published to the production APS (`api.gov.bc.ca`), one Gateway, split
by the qualifier tags `ns.<gatewayId>.test` and `ns.<gatewayId>.prod` (APS
"CI/CD integration", split configuration by environment). Publishing one file
replaces only the services carrying its tag.

## What is exposed

| Route | Methods | Path | Gateway checks |
|---|---|---|---|
| `…-api` | GET, POST | `/api/v1/hbs/integrations/scale-data/` (prefix) | `jwt-keycloak`: signature, issuer, expiry. Body up to 11 MB. |
| `…-openapi` | GET | `/v3/api-docs/scale-data` (exact) | none (the document is public) |

On the whole service: per-IP rate limit, `X-Request-ID` correlation id, and the
APS global error rewrite turned off so the API's own error bodies pass through.
Nothing else on the backend (staff API, actuator, Swagger UI, the staff OpenAPI
group) has a route, so the gateway answers 404 for it.

The backend does not rely on the gateway: it validates the token again and
checks the mapped client id, the `HBS_SUBMISSION_API` role and each endpoint's
scope. Roles and scopes are not checked here because the SSO standard realm
puts client roles in a `client_roles` claim, which `jwt-keycloak` does not read.

## Placeholders

- `<gatewayId>`: from `gwa gateway create` (step 1 below).
- `<licence>`: the OpenShift licence plate of the HBS namespaces (the
  `oc_namespace` secret without its `-test`/`-prod` suffix).

## One-time setup

1. **Create the Gateway** (IDIR):
   ```sh
   gwa config set host api.gov.bc.ca
   gwa login
   gwa gateway create -g        # prints the gatewayId
   ```
2. **Ask APS for the Gold data plane and DNS.** HBS runs on Gold, so APS must
   configure the Gateway for Gold and add DNS for both hosts in the table above
   (the `*.api.gov.bc.ca` wildcard points at Silver). Contact them with the
   gatewayId and the two hosts (APS "Get Support").
3. **Open the backend to the gateway.** Set the GitHub environment variable
   `SCALE_DATA_GATEWAY_ENABLED=true` in `test` and `prod`. The next deploy
   applies `backend/openshift.scale-data-gateway.yml` (APS's Gold namespace
   `b8840c` → backend pods, port 8080).
4. **Set the public base URL** so the OpenAPI document names the gateway:
   `SCALE_DATA_API_PUBLIC_BASE_URL=https://nr-hbs-scale-data-test.api.gov.bc.ca`
   (TEST) and `https://nr-hbs-scale-data.api.gov.bc.ca` (PROD).
5. **Fill in the placeholders and publish:**
   ```sh
   gwa config set gateway <gatewayId>
   gwa apply -i .gwa/test/gw-config.yaml
   gwa status                   # the upstream should show as reachable
   ```
   Then the same for `.gwa/prod/gw-config.yaml`.

## Check it

```sh
H=https://nr-hbs-scale-data-test.api.gov.bc.ca
curl -s $H/v3/api-docs/scale-data | jq .info.title          # "HBS Scale Data API"
curl -si $H/api/v1/hbs/integrations/scale-data/submissions   # 401 from the gateway
curl -si $H/api/v1/hbs/queries/x                             # 404: no route
curl -si $H/actuator/health                                  # 404: no route
# With a client-credentials token from a mapped account:
curl -si -H "Authorization: Bearer $TOKEN" $H/api/v1/hbs/integrations/scale-data/submissions
```

The last call reaching the backend (200, or the backend's own 403 JSON body)
also confirms that `jwt-keycloak` passed `Authorization` through.

## Client accounts

Accounts are not issued through the APS portal. Each client gets a
client-credentials integration in the SSO standard realm (CSS) with the
`HBS_SUBMISSION_API` role, plus a line in `SCALE_DATA_API_ACCOUNTS`
(see [docs/roles-and-security.md](../docs/roles-and-security.md), "Service
accounts: scale data API"). Listing the API in the APS API Directory (a
DraftDataset and a Product) is optional and not set up here.
