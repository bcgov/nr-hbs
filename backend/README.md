# HBS Backend (API)

Spring Boot API for the Harvest Billing System — the nr-fsp-new backend
architecture applied to the legacy HBS Oracle data. See
[../docs/architecture.md](../docs/architecture.md) and
[../docs/database.md](../docs/database.md).

- **Java 21**, **Spring Boot 3.5**, Undertow, JDBC (`ojdbc11`), Validation, AOP, Cache, Mail, Actuator
- OAuth2 **resource server** (BC Gov SSO / Keycloak JWTs, `azp` checked) + method security (`@PreAuthorize`)
- Registry-driven screen queries/commands (no ORM) — [../docs/screen-framework.md](../docs/screen-framework.md)
- In-process JasperReports for the 79 legacy reports — [../docs/reports.md](../docs/reports.md)
- ShedLock-coordinated batch jobs (off by default) — [../docs/batch-jobs.md](../docs/batch-jobs.md)
- OpenAPI via springdoc

## Prerequisites

There is **no local database** — the API connects to the shared BC Gov Oracle
(`THE` schema), exactly like nr-fsp-new:

1. **BC Gov VPN** connected.
2. `src/main/resources/application-local.properties` (gitignored) with the DB
   credentials, Keycloak issuer and client id (`hbs.keycloak.client-id`) — start from [`.env.example`](.env.example)
   for the variable names. For local uploads set `hbs.clamav.fail-open=true`
   and `hbs.storage.xml-root=/tmp/hbs-xml`.
3. `src/main/resources/cert/jssecacerts` — the Oracle truststore (copy it from
   a running pod, same procedure as nr-fsp-new).

## Run

```bash
docker compose up                 # from the repo root: backend :8080 + frontend :3000
mvn spring-boot:run -Dspring-boot.run.profiles=local   # natively
```

- OpenAPI UI: <http://localhost:8080/swagger-ui>
- Health: `/actuator/health` · Metrics: `/actuator/prometheus`

## Test

```bash
mvn test
```

| Test | Guards |
|---|---|
| `CatalogIntegrityTest` | every registered query/command: unique ids, SELECT-only, each filter binds only its own parameter, client scope binds `:scopeClientNumber`, safe proc names |
| `ReportTemplatesCompileTest` | all 79 vendored JCRS reports + subreports compile with the embedded engine |
| `HbsReportServicePromptTest` | report prompt whitelist (SQL-injection guard), date reformatting |
| `HbsRolesTest`, `TokenValidatorsTest`, `RequestUtilTest` | role mapping + stacking, `azp`/role validators, Keycloak claims → audit id + active client |
| `LoginSecurityTest` | security filter chain end to end with `client_roles` tokens |
| `QueryServiceTest` | filter type conversion, camelCase mapping, builder guards |
| `SubmissionStorageTest`, `ScaleDataXmlValidatorTest` | storage path safety, XSD validation + XXE hardening |
| copied from nr-fsp-new | ClamAV client, user-lookup client, virus scanner, user directory, pageable, token validator |

Nothing in the suite needs Oracle. Every catalog query is static-checked but
**not executed** — run the e2e suite against TEST (read-only) to exercise SQL.

## Structure

```
src/main/java/ca/bc/gov/nrs/hbs/api/
├── query/          QueryDefinition, CommandDefinition, Capability, QueryRegistry, Query/CommandService
├── catalog/        <Area>Catalog — one QueryCatalog per functional area (auto-registered)
├── controller/     QueryController (/queries, /commands), HbsReportController, UserApiController, ClientApiController
├── submission/     P505 XML upload: SubmissionController/Service, ScaleDataXmlValidator, SubmissionStorage
├── queries/        StatementDocumentController (issued invoice/statement documents)
├── admin/          UserDataDomainGuard (fences client admins' data-domain writes)
├── batch/          BatchConfig (ShedLock), BatchJobRunner, jobs
├── service/v1/     report/ (HbsReportService), ClientSearchService, UserDirectoryService, VirusScanner
├── security/       Keycloak validators, TokenRoles, RoleScope, HbsRoles, HbsAuthorities, HbsAccessGuard
├── dao/v1/         AbstractStoredProcedureDao (bespoke proc DAOs)
├── client/         ClamAV, nr-user-lookup-api, nr-forest-client-api
├── notification/   EmailNotificationService + templates
├── exception/      exception→HTTP mapping; ProcErrorMessages (ORA-* → friendly)
├── util/           RequestUtil (audit user, stacked roles, active client)
└── config/         security + MVC + async
```

## Conventions

- **Reads** = reviewed SQL in a `QueryDefinition`, values always bound.
  **Writes** = a `CommandDefinition` calling the legacy `HBS_CREATE/STORE/REMOVE`
  proc with positional args (the app role has SELECT only on HBS tables).
- Immutable columns on updates come from the DB (`existingRow` + `existing`),
  never from the request.
- Business workflows that are more than one proc call are **dedicated services**,
  not commands — see [../docs/legacy-logic-to-port.md](../docs/legacy-logic-to-port.md).
