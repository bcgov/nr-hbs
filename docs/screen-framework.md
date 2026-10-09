# Screen framework (how the ~200 legacy screens are built)

The legacy HBS web tier has ~200 Struts screens (P-numbers, 386 JSPs). Almost
all of them are one of three shapes: **search → list**, **view a record**, or
**add/update a record**. Instead of ~200 bespoke React pages + ~200 bespoke
REST endpoints, each legacy screen is **declared** once on each side and
rendered/served by shared, reviewed infrastructure. Bespoke code is used only
where a screen genuinely needs it (file submission, reports, Home).

```
frontend/src/screens/areas/<area>.ts      ScreenDef[]  (what the user sees)
            │  query / command ids
            ▼
backend/.../catalog/<Area>Catalog.java    QueryDefinition[] + CommandDefinition[]
            │  whitelisted SQL / proc call
            ▼
Oracle THE  (inline SELECTs ported from the legacy *Query classes;
             writes via the legacy HBS_CREATE_* / HBS_STORE_* / HBS_REMOVE_* procs)
```

Both sides are **auto-discovered**: any `@Component` implementing
`QueryCatalog` is registered by `QueryRegistry`; any `src/screens/areas/*.ts`
exporting `screens` is picked up by `src/screens/registry.ts`. Adding an area
never touches the router, the SideNav, or another area's files.

## Backend — `QueryDefinition`

```java
QueryDefinition.builder("timberMarks.detail")        // id the SPA calls
    .legacy("P480/P481")                              // legacy screen(s)
    .capability(Capability.QUERIES_VIEW)              // who may run it
    .sql("""
        SELECT tm.timber_mark, tm.cutting_permit_id, ...
          FROM timber_mark tm
          JOIN ... 
         WHERE 1=1""")                                // SELECT ... WHERE 1=1
    .filter(QueryFilter.required("timberMark", "AND tm.timber_mark = :timberMark", Type.UPPER))
    .filter(QueryFilter.date("issuedFrom", "AND tm.issue_date >= :issuedFrom"))
    .sort("timberMark", "tm.timber_mark")             // sortable JSON key → SQL
    .orderBy("tm.timber_mark")                        // default ORDER BY
    .clientScope("AND tm.client_number = :scopeClientNumber") // industry fence
    .build();
```

Rules:

- **SQL is ported, not invented.** Take it from the legacy DAO / `*Query`
  class / JSP-backing EJB, and verify every table/column against
  `nr-mof-db/scripts/THE/TABLES/*.sql`. No schema prefix (the app uses the
  THE public synonyms, like the legacy app).
- **Column aliases become JSON keys** (snake → camel: `TIMBER_MARK` →
  `timberMark`). Alias deliberately; the frontend `ColumnDef.key` must match.
- **Every user value is a bound named parameter** in a filter fragment. Never
  concatenate. Filter types: `STRING`, `UPPER`, `LIKE` (`%v%`), `PREFIX`
  (`v%`), `DATE` (`>= day`), `DATE_TO` (`< day+1`, i.e. inclusive end date),
  `NUMBER`, `LIST` (comma list → `IN (:p)`).
- **Client fence:** if the rows belong to a forest client (invoices,
  statements, returns, profiles), set `clientScope` — industry users are then
  forced to their active client regardless of what they typed (the legacy app
  often did *not* do this; see roles-and-security.md). Ministry users skip it.
- **FOI severing:** where the legacy screen showed "Not Releasable" for
  individual (`client_type_code = 'I'`) clients to anyone but ministry staff
  and the client itself, reproduce it in SQL with the viewer binds every query
  receives — `:hbsIsMinistry` ('Y'/'N'), `:hbsViewerClient` (active client
  number, '' for ministry), `:hbsUserType` ('MOF'/'CLI'/'SPC') and `:hbsUserId`
  (legacy `HBS_USER.USER_ID` form, e.g. `IDIR\JSMITH`, for the "associated
  sites / districts" data-domain filters):
  `CASE WHEN :hbsIsMinistry = 'Y' OR fc.client_type_code <> 'I' OR
  fc.client_number = :hbsViewerClient THEN fc.client_name ELSE 'Not Releasable' END`.
- Paging is server-side (`OFFSET/FETCH`, `COUNT(*)`), capped by `maxRows`
  (default 5,000 — the legacy screens capped list results similarly).
- A legacy stored procedure that returns a REF CURSOR (the `HBS2R*` /
  `HBS3R*` search procs) is **not** callable through `QueryDefinition`. Either
  port its SELECT (preferred; the procs only build dynamic SQL) or expose the
  matching Jasper report via `ReportLinkDef`.

## Backend — `CommandDefinition`

```java
CommandDefinition.builder("markHolderProfile.create")
    .legacy("P343")
    .capability(Capability.PROFILE_BILLING_ADMIN)
    .procedure("HBS_CREATE_HBS_MARK_PROFILE")
    .sequence("HBS_MARK_HOLDER_PROFILE_SEQ")   // arg 1: new id (echoed back)
    .upper("timberMark")                        // arg 2: body.timberMark
    .clientNumber("clientNumber")               // arg 3: forced for industry
    .text("clientLocnCode")
    .auditUser()                                // ENTRY_USERID
    .now()                                      // ENTRY_TIMESTAMP
    .auditUser()                                // UPDATE_USERID
    .now()                                      // UPDATE_TIMESTAMP
    .build();
```

Arguments are **positional in the proc's declared order** — copy the order
from `nr-mof-db/scripts/THE/PROCEDURES/V7.*__<PROC>.sql`. The legacy app's DB
role only had SELECT on HBS tables; every write went through these procs, so
the new app must too (see database.md).

Commands are for CRUD-shaped writes. Workflow transitions that the legacy app
implemented in Java (summarize, release, cancel/replace, approve corrections,
invoicing) are **not** single proc calls — they are listed in
`docs/legacy-logic-to-port.md` and implemented as dedicated services.

## Frontend — `ScreenDef`

```ts
export const screens: ScreenDef[] = [
  {
    kind: 'search',
    id: 'timber-mark-search',          // URL: /queries/timber-mark-search
    legacy: 'P480',
    area: 'queries',
    title: 'Timber Mark Information',
    capability: 'QUERIES_VIEW',
    query: 'timberMarks.search',
    criteria: [{ name: 'timberMark', label: 'Timber Mark', upper: true, maxLength: 6, required: true }],
    columns: [{ key: 'timberMark', header: 'Timber Mark', sortable: true }, ...],
    rowLink: { screen: 'timber-mark-detail', params: { timberMark: 'timberMark' } },
  },
  {
    kind: 'detail', id: 'timber-mark-detail', nav: false, ...
    query: 'timberMarks.detail', keys: ['timberMark'],
    sections: [{ title: 'Timber Mark Info', fields: [...] }, { title: 'Stumpage Rates', table: {...} }],
  },
];
```

- Labels are the **legacy labels** (from the JSP / `ApplicationResources`),
  sentence-cased only where the legacy text was all-caps.
- `criteria[].name` must equal the backend filter `param`; `columns[].key` the
  SQL alias (camelCase).
- `kind: 'search'` screens appear in the SideNav under their area unless
  `nav: false`; detail/form screens are reached by links.
- Deep links from Home use URL params equal to criteria names
  (`?returnType=P&status=ERR`) — keep the criteria names listed in
  `HomePage.tsx` when building the screen it points at.
- `reports` on a search/detail screen map criteria/record values to the
  legacy Jasper report prompts (`reports/hbs/catalog.json` lists each
  report's prompts).
- Capabilities must exist in both `routes/access.ts` and `query/Capability.java`.

## Look and layout (nr-fta's)

Every page renders in nr-fta's page frame and uses its shared styles
(`frontend/src/styles/_search.scss`, `_detail.scss`, `_tables.scss`, copied
from nr-fta unchanged — the `fsp-search__*` / `fsp-info__*` class names are
shared across the sibling apps on purpose):

| Template | Layout |
|---|---|
| All | `PageLayout`: an optional "Back to …" link, a 2rem `PageTitle` with the screen description as subtitle (ending in the legacy P-number), screen-level buttons level with the title, the legacy notes as an info banner. |
| `search` | Criteria in a white `fsp-search__tile` over the 4-column field grid (a `group` change starts a headed row), Clear all / Search bottom-right; results in a full-bleed grey panel: count banner with report buttons and "Export results to CSV", zebra table with clickable rows, pagination footer (10 / 25 / 50 / 100). Criteria and the last search are kept per tab and re-run on return; errors are toasts. `createLink` is a button level with the title. |
| `detail` | A tombstone of the record's identifiers (its `keys` fields plus its first `status` field), then contained tabs on a grey full-bleed pane: **Details** (each field section an icon-titled white `DetailTile`), then one tab per child table. A record with a single section skips the tab bar. Edit / commands / reports sit level with the title. |
| `form` | One `SectionTile` holding the field grid, Cancel / Save bottom-right; the back link is disabled while there are unsaved changes. |
| Home, area landings | nr-fta's Welcome: title, greeting, `SectionTile`s of quick-link tiles (Home also has the legacy work queues). |

The side nav is nr-fta's: one bold section per legacy tab (plus **General**:
Home, Reports), each destination with an icon (`routes/routePaths.ts`
`iconFor` reads it from the screen title), a 48px icon rail when collapsed.
Opening a row passes its origin in router state, so the record's back link
returns to that search with its results.

## Shared code lists

`CodesCatalog` provides `codes.orgUnits`, `codes.returnTypes`,
`codes.scaleReturnStates`, `codes.versionStates`, `codes.txnTypes`,
`codes.txnStatuses`, `codes.processingStatuses`, `codes.xmlTransSteps`,
`codes.deliveryMethods`, `codes.frequencyTypes`, `codes.sampleTypes`,
`codes.anomalyTypes`, `codes.anomalyStatuses`, `codes.editErrorResponsibility`.
Use them via `FieldDef.codeList`. Area-specific lists go in the area catalog
as `codes.<area>.<name>`.
