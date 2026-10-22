# Profiles area (legacy `/cpm/*`)

Files:

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/ProfilesCatalog.java` (client, scale site, population and scaler profiles) and `ProfilesBillingCatalog.java` (mark holder, statement delivery and cruise-based billing profiles)
- Frontend: `frontend/src/screens/areas/profiles.ts`
- Workflows (legacy Java rules before the table-API call): `backend/src/main/java/ca/bc/gov/nrs/hbs/api/profiles/` (see [Ported](#ported-legacy-logic))

Legacy paths below are relative to `hba-archive/hbs/trunk/source/ear/`:

- `E` = `hbs-ejb/src/main/java/ca/bc/gov/mof/hbs/cpm`
- `V` = `hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation/cpm`
- `DDL` = `nr-mof-db/scripts/THE`

## Screen map

| Legacy | Legacy title | New screen id(s) | Query / command ids | Ported from |
|---|---|---|---|---|
| P300 | Client Profile Menu | not carried over | – | It is an orphan menu that no tab links to, and its links are broken. The SideNav replaces it. |
| P310 | Search for a Client Profile | `client-profile-search` | `profiles.client.search` | `V/P310Action`, `legacy.cli.ClientReader.findClientLocation` (CLIENT_LOCATION + FOREST_CLIENT) |
| P311 | Client Profile | `client-profile` | `profiles.client.detail`. Its tables use `profiles.scaleSites.search`, `profiles.markHolders.search`, `profiles.population.search` and `profiles.delivery.search` | `V/ClientProfileTag`. Counts come from `findTPSubmitterCountByCriteria` (HBS2R321), `findMHPCountByCriteria` (HBS3R341), `findProfileCountByCriteria` and `findDeliveryProfileCountByCriteria` (HBS3R371). The legacy count links are replaced by inline tables of the linked profiles. |
| P320 / P321 | Search for / List of Scale Site Profiles | `scale-site-profiles` (Print = HBS2R321) | `profiles.scaleSites.search` | `DDL/PROCEDURES/V7.01526__HBS2R321.sql` (the TPS ∪ SSP SELECT); search types from `V/ScaleSiteConfigurationsTag.java:96-180` |
| P322 | Update Scale Site Profile | `scale-site-profile-update` | `profiles.scaleSites.record`, `profiles.scaleSites.update` (workflow `ScaleSiteProfileUpdate`) → `HBS_STORE_TRA_PART_SUBMITTER` | `V/P322Action`, `E/ClientProfileManagerBean.updateTradingPartnerSubmitter` |
| P323 | Add Scale Site Profile | `scale-site-profile-add` | `profiles.scaleSites.create` (workflow `ScaleSiteProfileCreate`) → `HBS_CREATE_TRA_PART_SUBMITTER` (sequence `TRADING_PARTNER_SUBMITTER_SEQ`, type `TPSS`) | `V/P323Action`, `createAndStoreTradingPartnerSubmitter` |
| P330 / P331 | Search for / List of Population Profiles | `population-profiles` (Print = HBS3R331) | `profiles.population.search` | `DDL/PROCEDURES/V7.01563__HBS3R331.sql`, `E/PopulationProfileQuery` |
| P332 | Update Population Profile | `population-profile` (view + Delete), `population-profile-update` | `profiles.population.record`, `profiles.population.update` (workflow `PopulationProfileUpdate`) → `HBS_STORE_POPULATION_PROFILE`, `profiles.population.delete` (workflow `PopulationProfileDelete`) → `HBS_REMOVE_POPULATION_PROFILE` | `V/P332Action` |
| P333 | Add Population Profile | `population-profile-add` (Add on `population-profiles`) | `profiles.population.create` (workflow `PopulationProfileCreate`) → `HBS_CREATE_POPULATION_PROFILE` (`POPULATION_PROFILE_SEQ`) | `V/P333Action`, validation.xml `P333Form` |
| P340 / P341 | Search for / List of Mark Holder Profiles | `mark-holder-profiles` (Print = HBS3R341) | `profiles.markHolders.search` | `DDL/PROCEDURES/V7.01564__HBS3R341.sql` (the timber-mark search joins MARK_BILLED_CLI), `V/P340Form.doQuery` |
| P342 | Update Mark Holder Profile | `mark-holder-profile` (view), `mark-holder-profile-update` | `profiles.markHolders.record`, `profiles.markHolders.update` (workflow `MarkHolderProfileUpdate`) → `HBS_STORE_HBS_MARK_PROFILE` | `V/P342Action`, `updateMarkHolderProfile` |
| P343 | Add Mark Holder Profile | `mark-holder-profile-add` | `profiles.markHolders.create` (workflow `MarkHolderProfileCreate`) → `HBS_CREATE_HBS_MARK_PROFILE` (`HBS_MARK_HOLDER_PROFILE_SEQ`) | `V/P343Action` |
| P344 | Confirm Mark Holder Profile Delete | The Delete action on `mark-holder-profile` (confirm modal) | `profiles.markHolders.delete` (workflow `MarkHolderProfileDelete`) → `HBS_REMOVE_HBS_MARK_PROFILE` | `V/P344Action` |
| P350–P353 | Summarization Frequency | not carried over | – | These are unwired static mock-ups with no Struts action. Mark Holder Profiles (P34x) superseded them. |
| P361, P330S001, P331S001 | Mock-ups | not carried over | – | They are static prototypes. |
| P360 | Search for Scaler Profile | `scaler-profile-search` (by licence), `scaler-expiring-keys` (by AK expiry) | `profiles.scalers.search`, `profiles.scalers.expiring` | `V/P360Action`. Expiry search from `E/ClientProfileManagerQuery.selectScalerToUpdate` |
| P364 | Select Scaler to Update | `scaler-expiring-keys` | `profiles.scalers.expiring` | `E/ClientProfileManagerQuery.selectScalerToUpdate` (now uses bound parameters) |
| P362 | Update Scaler Profile | `scaler-profile` (licence, current link, key history); Submit = bespoke page `/profiles/scaler-key-request` (`ScalerKeyRequestPage`, nav "Request Authentication Key") | `profiles.scalers.detail`, `profiles.scalers.keyHistory`; load `GET /api/v1/hbs/scaler-keys/request`; submit **`profiles.scalers.requestKey`** → `HBS_STORE_` / `HBS_CREATE_HBS_SCALER_AUTH_KEY` (`HBS_SCALER_AUTH_KEY_SEQ`) | `V/P362Action` (loadForm, submitNewKey, validate*), `E/ClientProfileManagerBean.createAndStoreHbsScalerAuthKey` / `updateHbsScalerAuthKey`, `E/HbsScalerAuthKeyQuery`. The encrypted key is never selected for display. |
| P363 | Authentication Key Confirmation | confirmation tile on `/profiles/scaler-key-request` after Submit | the command result | `V/P363Action`: licence, key (with check digit), active from / to. Shown once; not in a URL, not stored in the browser. |
| P365 | No active Scaler Profile link | the load / submit error on `/profiles/scaler-key-request` | – | `error.authkey.noactivescalerprofilelink` text. |
| P370 / P371 | Search for / List of Statement Delivery Profiles | `delivery-profiles` (Print = HBS3R371) | `profiles.delivery.search` | `DDL/PROCEDURES/V7.01565__HBS3R371.sql`, `V/P370Form.doQuery` |
| P372 | Update Statement Delivery Profile | `delivery-profile` (view), `delivery-profile-update` | `profiles.delivery.record`, `profiles.delivery.update` (workflow `DeliveryProfileUpdate`) → `HBS_STORE_HBS_CLI_DEL_PROFILE` | `V/P372Action` |
| P373 | Add Statement Delivery Profile | `delivery-profile-add` | `profiles.delivery.create` (workflow `DeliveryProfileCreate`) → `HBS_CREATE_HBS_CLI_DEL_PROFILE` (`HBS_CLI_DEL_PROFILE_SEQ`) | `V/P373Action` |
| P374 | Confirm Delivery Profile Delete | The Delete action on `delivery-profile` | `profiles.delivery.delete` → `HBS_REMOVE_HBS_CLI_DEL_PROFILE` | `V/P374Action` |
| P380 / P381 | Search for / List of Cruise Based Billing Profiles | `cruise-based-profiles` | `profiles.cruiseBased.search` | `DDL/PROCEDURES/V7.01566__HBS3R380.sql`. The REF-CURSOR SELECT is ported; no Jasper report exists for it. |
| P385 | View Cruise Based Billing Profile | `cruise-based-profile` | `profiles.cruiseBased.detail`, `profiles.cruiseBased.ratios` | `V/P385Action` (APP_WORKSHEET, MARK_BILLED_CLI, TIMBER_MARK, `findCruiseBasedSpGrRatiosByCriteria`) |
| P383 | Update Cruise Based Billing Profile | `cruise-based-ratios` (grid + row Delete), `cruise-based-ratio-add`, `cruise-based-ratio-update` | `profiles.cruiseBased.ratios`, `profiles.cruiseBased.ratioRecord`, `profiles.cruiseBased.ratio.create` / `.update` (workflows `CruiseBasedRatioCreate` / `CruiseBasedRatioUpdate`) / `.delete` → `HBS_CREATE_` / `HBS_STORE_` / `HBS_REMOVE_CB_SP_GRD_RATIO` | `V/P383Action`. The legacy screen deleted every row and re-inserted them on Save. The new screens edit one row at a time. |
| P801 / P802 | Client Lookup popup | replaced | – | The `client` field type is a type-ahead, `ClientComboBox` (nr-forest-client-api — [forest-client-integration.md](../forest-client-integration.md)); picking a client with one location also fills the `Loc` field. |

## Access (fixes of legacy defects)

- **Reads.** All profile reads use `PROFILES_VIEW` and a `clientScope`. Industry users only see rows for their active client. In the legacy app this was a UI-only restriction or was missing entirely: P340 timber-mark search, P385 via URL, and P311 `SearchLoc`. Client names of individuals are FOI-severed.
- **Writes.** In the legacy app most profile writes had no server-side role check: P332/P333, P342–P344, P372–P374 and P383. The new commands require a capability:
  - Scale site: `SCALE_SITE_ADMIN`, plus the legacy data-domain check (below)
  - Mark holder, delivery and cruise-based ratios: `PROFILE_BILLING_ADMIN`
  - Population add / update / delete: `SAMPLING_ADMIN`. `SAMPLING_ADMIN` includes CLI_SMP_ADMIN, so the
    population workflows also apply the client fence on the population's `CLIENT_NUMBER`.
- **Associated sites / districts.** P322/P323 ran `DacHelper.isScaleSiteAssociatedWithOrgUnit`: an
  HBS_SCALE_ADMIN may only add or change profiles of scale sites whose `SCALE_SITE.ORG_UNIT_NO` is one of their
  `HBS_USER_DIST_DATA_DOMAIN` districts (when they have any), a CLI_SITE_ADMIN only sites in their
  `HBS_USER_SITE_DATA_DOMAIN`. `ScaleSiteProfileRules.associatedWithCaller` ports it, keyed by the legacy
  user id (`IDIR\NAME`, the `:hbsUserId` form).
- **Optimistic lock.** Every update and delete workflow re-reads the row and checks the `updateTimestamp` the
  screen loaded (`RowLock`); the record queries return it.
- **Scaler data.**
  - `profiles.scalers.expiring` (P364) is restricted to `SCALE_SITE_ADMIN` (HBS_SCALE_ADMIN).
  - P360 search, P362 detail and key history use `SCALER_PROFILE_EDIT` with a client fence. A CLI_SCALER only sees scaler clients whose keys are linked to HBS users (`HBS_USER.CLIENT_NUMBER`) of their own active client.
  - Exact "own key only" matching (`HBS_SCALER_AUTH_KEY.USER_ID = caller`) is applied by the key request (`ScalerAuthKeyService`, self service). The read queries could now use the `:hbsUserId` bind for the same rule; not changed here.
- **Industry report prompts.** The Print reports (HBS2R321, HBS3R331, HBS3R341, HBS3R371) take prompts from the criteria. Whether the report endpoint applies the client fence for industry users depends on the shared report service.

## Ported: scaler authentication keys (P362 Submit / P363 / P365)

Package `backend/.../api/signature` (command `profiles.scalers.requestKey`, capability `SCALER_PROFILE_EDIT`):

| Legacy | New | Notes |
|---|---|---|
| `V/P362Action.loadForm` | `ScalerAuthKeyService.load`, `GET /api/v1/hbs/scaler-keys/request` (`ScalerKeyController`) | Managed (HBS_SCALE_ADMIN) by licence: key's HBS user = latest key's, From = a futured key's effective date else tomorrow, "has key". Self service (everyone else): the scaler whose key is active today for the caller's user id, else the P365 message. Also says whether key issuing is configured. |
| `V/P362Action.perform` Submit, `submitNewKey`, `validateUser` / `validateAuthKey` / `validateFromToDates` | `ScalerAuthKeyService.request`, `ScalerKeyRequestCommand` | Legacy messages. Key 7–9 letters/digits, upper case; domain\user must be an `HBS_USER` (both blank ⇒ no user); From in [tomorrow, tomorrow + aging]; To = From + aging. The key active today is expired the day before From (`HBS_STORE_…`, its encrypted bytes re-sent unchanged); a futured key is overwritten (new key, dates, user); otherwise `HBS_CREATE_HBS_SCALER_AUTH_KEY` with `HBS_SCALER_AUTH_KEY_SEQ`. Two keys active today ⇒ the legacy "Multiple scaler auth keys exist…" error. Self service ignores licence/user/From in the body (fence on the loaded rows). One transaction. |
| `util.math.CheckDigit` (`P362Action.suffixAuthKey`) | `AuthKeyCheckDigit` | `'A' + Σ(char × position) mod 26` over licence + requested key. |
| `HbsScalerAuthKeyQuery` `DBMS_CRYPTO.encrypt/decrypt(…, 4096+256+1, '<key>')` | `DbmsCryptoCipher` | AES-128-CBC, PKCS5, zero IV, hex key (first 16 bytes), plaintext bytes = ASCII. Encrypted in Java and passed as RAW to the procs, so the key never appears in SQL. **Needs a TEST round-trip check against Oracle** ([questions.md](../questions.md) Q9). |
| WebADE `dbms-crypto-key`, `HBSConfiguration authkey.aging.days` | `SignatureKeyProperties`: `hbs.signature.dbms-crypto-key` (env `HBS_SIGNATURE_DBMS_CRYPTO_KEY`), `hbs.signature.authkey-aging-days` (365) | Unset key ⇒ 503 after validation, nothing written; the page shows a warning and disables Submit. |
| P363 | `/profiles/scaler-key-request` confirmation tile | The plain key is in the command result only. |

The scaler profile view (`scaler-profile`, P361/P362) links to the request page
("Edit Authentication Key", `?licenceNumber=` pre-filled, as P361's link to P362)
and to "Download HBS Public Key" (P002 offered it beside the Scaler Profile link)
through `extraLinks`.

The key history keeps showing `*********` (the legacy masked view `AB*****YZ` needed a decrypt per row; not carried over).

## Legacy logic not yet ported

**1. Scaler authentication keys — ported (work package 2), waiting for the Secret.** See "Ported: scaler authentication keys" above.

**2. Smaller gaps.**

- ~~P320 pre-checks~~ — ported: `profiles.ScaleSiteProfileSearchGuard` (`@Before` on `QueryService` for
  `profiles.scaleSites.search`, `$V/cpm/P320Action.perform` / `validateClientInput`): scale site exists ("The
  scale site entered does not exist."); trading partner / site owner: number and location both given, client
  exists (V_CLIENT_PUBLIC), the location is a TRADING_PARTNER, and at least one profile (the HBS2R321 count,
  i.e. the same query counted) — the legacy messages come back as a 400.
- ~~P364 "In my Org Unit"~~ — ported: checkbox `inMyOrgUnit` (filter on the caller's `HBS_USER.ORG_UNIT_NO`
  through `:hbsUserId`, `P364Action.loadForm`); the org unit picker stays.
- P383/P385 "Appraised Timber Profile" and stumpage-rate panels come from `OpqHelper.getTimberMarkDatas`. The
  Timber Mark Information query (Queries area) covers them.
- ~~P385 sale method~~ — ported: `profiles.cruiseBased.detail` returns `saleMethodCode` (HARVEST_SALE of the
  mark's licence, as `TimberMarkQuery.SELECT_LOAD`) and `saleMethodDescription` (`CODE_LIST_TABLE`
  `SALE_METHOD_CODE`, an external code list per `CodeLocator`, readable by the proxy user).

## Ported legacy logic

All in `backend/src/main/java/ca/bc/gov/nrs/hbs/api/profiles/`. Each workflow runs in one transaction, re-reads
the record it changes, checks the optimistic lock, and writes only through `TableApi`. Validation failures
return the legacy `ApplicationResources` texts. Tests: `src/test/.../profiles/*Test.java` (`RecordingTableApi`).

| Legacy | Command id | Class | Rules ported |
|---|---|---|---|
| P333 Add Population Profile (`V/P333Action.validateForm` + `hbsPerform`, validation.xml `P333Form`) | `profiles.population.create` | `PopulationProfileCreate` | Population/year exists (`popYearCrossRef`), numeric population and year, dates required and valid, effective ≤ expiry; effective year 2000–2099 and the 1st of a month; effective not before now; expiry year 2000–2099 and the last day of a month; first ratio statement not after expiry; no overlap with another profile of the population/year (`PopulationProfileQuery.countedByCriteria`). Ratio schedule: next = end of the effective month (MTH), the 15th (BWK) or the effective day (DLY) **at 23:59:59**; last = effective date; start = today 00:00; expiry at 23:59:59 (`PopulationProfileRules`). |
| P332 Save (`V/P332Action`) | `profiles.population.update` | `PopulationProfileUpdate` | Expiry valid, year 2000–2099, not before the stored next ratio statement date, last day of a month, no overlap (excluding itself). Only the expiry changes (stored at 23:59:59); the ratio dates are re-sent from the row with their time of day. |
| P332 Delete | `profiles.population.delete` | `PopulationProfileDelete` | Client fence and lock (no legacy rule). |
| P323 Add Scale Site Profile (`V/P323Action`) | `profiles.scaleSites.create` | `ScaleSiteProfileCreate` | Scale site exists; associated site / district data domain; Detail ⇒ blank client and location, Summary ⇒ a TRADING_PARTNER client location; dates valid (year ≥ 1900); expiry ≥ effective; effective not inside an existing SSP ∪ TPS range (`TradingPartnerSubmitterQuery.selectByCriteria`); expiry before the next profile (`getNextEffectiveDate`). Site / client / location zero-padded as the legacy form did. |
| P322 Update Scale Site Profile (`V/P322Action`) | `profiles.scaleSites.update` | `ScaleSiteProfileUpdate` | Expiry valid (year ≥ 1900); associated site / district; expiry ≥ effective; expiry before the next profile. Only the expiry changes. |
| P343 Add Mark Holder Profile (`V/P343Action`) | `profiles.markHolders.create` | `MarkHolderProfileCreate` | Client location exists; return type and frequency given; effective valid (≥ 1900), in the future, the 1st of a month; expiry valid, not before today, the last day of a month; expiry ≥ effective; effective not inside an existing profile of the client location / return type; expiry before the next one (`returnTypeForSummarization`: SFP 'F' shares 'P'). |
| P342 Update Mark Holder Profile (`V/P342Action`) | `profiles.markHolders.update` | `MarkHolderProfileUpdate` | Effective date after today (`P342Form.getIsFutureEffectiveDate`); expiry valid (≥ 1900), ≥ effective, not before today, the last day of a month, before the next profile. Only the expiry changes; the forced summarization date is cleared as the legacy update did. |
| P344 Delete (`V/P344Action`, P342 button gate) | `profiles.markHolders.delete` | `MarkHolderProfileDelete` | The P342 page only offered Delete while the effective date was after today; now enforced server-side. |
| P373 Add Statement Delivery Profile (`V/P373Action`) | `profiles.delivery.create` | `DeliveryProfileCreate` | Client location exists; E-mail/FTP needs a user with the location in `HBS_USER_CLI_LOC_DATA_DOMAIN` who is an `ACT` HBS user; effective valid (≥ 1900) and in the future; expiry valid and not before today; expiry ≥ effective; effective not inside an existing profile; expiry before the next one. |
| P372 Update Statement Delivery Profile (`V/P372Action`) | `profiles.delivery.update` | `DeliveryProfileUpdate` | Document receivers (E-mail/FTP); dates valid (≥ 1900); expiry ≥ effective; next-profile check as written (skips the profile itself). Client and location come from the row. |
| P383 row add / change (`V/P383Action.validateInputs`) | `profiles.cruiseBased.ratio.create` / `.update` | `CruiseBasedRatioCreate` / `CruiseBasedRatioUpdate` (`CruiseBasedRatioRules`) | Worksheet exists ("Upstream data problem"); species and grade given; species/grade in SPECIES_PRODUCT_GRADE_XREF; coniferous species on APP_SPECIES, deciduous species needs an appraised deciduous volume; Interior ('I') appraisal ⇒ grade 7 or 8; 0 < m3/Ha < 1000; no duplicate species/grade. |

The "update commands re-send immutable columns" gap is closed for these writes: the workflows build the STORE
arguments from the row they re-read (`TableApi.argsFromRow`) and only apply the editable fields, so keys,
client, entry audit values, and the time of day of `ENTRY_TIMESTAMP` and the ratio dates are preserved. The
remaining registry commands (`profiles.delivery.delete`, `profiles.cruiseBased.ratio.delete`) are single
REMOVE calls with no legacy rule.

### Deliberate differences from the legacy code

- **P333 inserted despite an error.** The legacy action showed "Expiry Date must be after nextRun date." but
  still inserted the profile (it only skipped the insert on an overlap). Any failure now rejects the add.
- **P383 coastal appraisals.** The legacy worksheet lookup built coastal species as `ApprisalSpecieCoast`, which
  P383's `instanceof ApprisalSpecie` test never matched, so every coniferous species was rejected on a Coastal
  appraisal. Both read `APP_SPECIES`; the port checks `APP_SPECIES` for every appraisal method. Confirm with the
  business (see open questions).
- **P383 per row.** The legacy screen validated and re-inserted the whole grid; the new screens add or change one
  row, so the duplicate check compares the new row with the stored ones.

### Ported as is (legacy quirks kept)

- The expiry-year check of P332/P333 shows "Expiry Date must be after nextRun date.": the message key
  `error.populationprofileexpirydate.invalid` is defined twice in ApplicationResources and the last one wins.
- P342 clears `FORCED_SUMMARIZATION_DATE` on update.
- P372's next-profile check skips the first profile at or after the new effective date when it is the profile
  being edited, instead of looking past it.
- A P383 species in neither legacy list (coniferous / deciduous) skips the grade-7/8, m3/Ha and duplicate checks.

### Legacy reference cache

The legacy WebLogic cache (`infrastructure/cache/Cache.java`, never invalidated — [coexistence.md](../coexistence.md))
only wraps `ScaleSiteReader.getScaleSite` (SCALE_SITE), `TimberMarkReader.getTimberMarkByMark` /
`getTimberMarkDetail`, `findStratumByPrimaryKey` (STRATUM) and `QuantLicenseManager.findQuantLicenseByPrimaryKey` /
`isValidLicense` (`*Handler.java`). These workflows write TRADING_PARTNER_SUBMITTER, POPULATION_PROFILE,
HBS_MARK_HOLDER_PROFILE, HBS_CLIENT_DELIVERY_PROFILE and CRUISE_BASED_SP_GRD_RATIO, none of which is cached,
so legacy batch sees profile edits on its next read. (Scaler key writes, owned by work package 2, touch
HBS_SCALER_AUTH_KEY, not QUANT_LICENSE.)

## Open questions / capability gaps

- P383 coastal appraisals: should a coniferous species be accepted on a Coastal ('C') appraisal when it is on
  the worksheet? The legacy screen rejected all of them (a type-check defect); the port accepts them.
- P333/P332 require the population's year range 2000–2099 and the P343/P373 effective date "in the future"
  compares midnight with the current time, so a profile can't start today. Both are legacy behaviour.

- The legacy P331 links and P332 were gated on HBS_SMP_ADMIN only, but `SAMPLING_ADMIN` also grants CLI_SMP_ADMIN. There is no ministry-only sampling capability, and commands have no client fence.
- Cruise-based profile writes were legacy HBS_RATE_ADMIN, HBS_BILL_ADMIN or HBS_SCALE_ADMIN. No existing capability covers all three, so `PROFILE_BILLING_ADMIN` (HBS_BILL_ADMIN) is used.
- P342 is now shown to everyone with `PROFILES_VIEW`, with update and delete for `PROFILE_BILLING_ADMIN`. The legacy app had no grant at all for P344.
