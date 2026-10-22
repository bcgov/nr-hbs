# Rating and Stratum Planner

Legacy modules `plu` (Rating tab, menu P199) and `smp` (Stratum Planner tab, menu P850).

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/RatingCatalog.java` (reads `rating.*`, `codes.rating.*`),
  `backend/.../api/rating/` (Rating workflow commands + stumpage lookup),
  `SamplingCatalog.java` (`sampling.*`, `codes.sampling.*`)
- Frontend: `frontend/src/screens/areas/rating.ts` (area `rating`), `frontend/src/pages/StumpageRatePage.tsx`
  (`/rating/stumpage-rate`), `stratum-planner.ts` (area `stratum-planner`)
- Legacy paths below are relative to `hba-archive/hbs/trunk/source/ear/`: `$E` = `hbs-ejb/src/main/java/ca/bc/gov/mof/hbs`,
  `$V` = `hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation`, `$W` = `hbs-web/src/main/webapp`.
  Procs: `nr-mof-db/scripts/THE/PROCEDURES/V7.*__<NAME>.sql`.

## Rating (P199)

| Legacy screen | New screen id(s) | Query / command ids | Ported from |
|---|---|---|---|
| P199 Rate Management Menu | — (SideNav "Rating" group) | — | Menu only; replaced by the SideNav. |
| P200/P201 Search for / List of Override Rules | `override-rules` | `rating.overrideRules.search`, `codes.rating.ruleStatuses`; report HBS2R201 | `$E/plu/OverrideRateRuleQuery.selectByDate` (+ `Query.appendWhereClause` interval-overlap semantics), columns from `HBS2R201` |
| P202 View Override Rate Rule | `override-rule` | `rating.overrideRules.detail`, `rating.overrideRates.list`; `rating.overrideRule.deactivate` (workflow; HBS_STORE_OVERRIDE_RULE, status INA) | `OverrideRateRuleQuery.selectByPK`; return counts via `HBS_GET_OVRRATE_RETURN_COUNT` (as in HBS2R201); `RateManagerBean.disableOverrideRule` |
| P206/P208 View Override Date Rule | `override-rule` | same as P202 | same |
| P203 Add Override Date Rule | `override-date-rule-add` | `rating.overrideRule.createDateRule` (workflow; HBS_CREATE_OVERRIDE_RULE, RTDTRL, category PR, status ACT) | `$V/plu/P203Action`, `RuleHelper.createOverrideDateRule` |
| P204 Add Override Rate Rule | `override-rate-rule-add` → `override-rule` | `rating.overrideRule.createRateRule` (HBS_CREATE_OVERRIDE_RULE, OVRRDRULE, PND); `rating.overrideRate.create` (HBS_CREATE_OVERRIDE_RATE); `rating.overrideRule.activate` (HBS_STORE_OVERRIDE_RULE, ACT) — all workflows | `$V/plu/P204Action`, `RuleHelper.createOverrideRateRule/createOverrideRate` |
| P207 Update Override Rate Rule | `override-rule`, `override-rule-edit`, `override-rate`, `override-rate-edit` | `rating.overrideRule.update` (header Save, HBS_STORE_OVERRIDE_RULE, PND), `rating.overrideRate.create/update/remove` (HBS_CREATE/STORE/REMOVE_OVERRIDE_RATE), `rating.overrideRule.activate` — all workflows | `$V/plu/P207Action` |
| P210/P211 Search for / List of District Default Rates | `district-default-rates` | `rating.districtRates.search`, `codes.rating.districts`, `codes.rating.appraisalMethods`; report HBS3R211 | `$E/plu/HbsDistrictDefaultRateQuery.selectByCriteria(orgUnitNo, method, eff, exp)`, joins from `HBS3R211` |
| P212 Add Default Rate | `district-default-rate-add` | `rating.districtRate.create` (workflow; HBS_CREATE_HBS_DISTRICT_RATE) | `$V/plu/P212Action` |
| P213 Update Default Rate | `district-default-rate-edit` | `rating.districtRates.detail`, `rating.districtRate.update` (workflow; HBS_STORE_HBS_DISTRICT_RATE) | `HbsDistrictDefaultRateQuery.selectByPK`, `$V/plu/P213Action` |
| P220/P221 Search for / List of Waste & Residue Rates | `waste-residue-rates` | `rating.wasteRates.search`; report HBS3R221 | `$E/plu/WasteResidueRateQuery.selectByCriteria(mark, cutBlock, eff, exp)`, joins from `HBS3R221` |
| P222 Add A Waste & Residue Rate | `waste-residue-rate-add` | `rating.wasteRate.create` (workflow; HBS_CREATE_WASTE_RESIDUE_RATE; product ' ', active/avoidable Y, expiry = effective) | `$V/plu/P222Action`, `RateHelper.createWasteResidueRate` |
| P223 Update A Waste & Residue Rate | `waste-residue-rate` (view + Delete), `waste-residue-rate-edit` (Save) | `rating.wasteRates.detail`, `rating.wasteRate.update` (HBS_STORE_WASTE_RESIDUE_RATE ×1–2), `rating.wasteRate.deactivate` (HBS_STORE_WASTE_RESIDUE_RATE, ACTIVE_IND N) — workflows | `$V/plu/P223Action`, `RateHelper.update` |
| `plu/P222.jsp` (orphan copy) | not carried over | — | Dead page: no Struts forward reaches it. |
| P230/P231 Search for Stumpage Rate | `/rating/stumpage-rate` (bespoke page) | `GET /api/v1/hbs/rating/stumpage-rate` (`StumpageRateController`) | `$E/plu/RateManagerBean.findSegregationRate` (`HBS_RATES.Get_Override_And_Appraisal`, `Retrieve_Normal/Beachcomb/Waste_Rate`), `$V/plu/P230Action.validateForm`, `SegregationRateResultsTag`, `OpqHelper.getTimberMarkInfo` |
| P240/P241 Compute Waste & Residue Average Rate | `waste-residue-average-rate` | `rating.wasteAverageRate`, `codes.rating.coniferousSpecies` | `$E/plu/RateManagerBean.findHarvestRates` + `WasteResidueRateQuery.selectByHarvestCriteria` (now bound, aggregated in SQL). The P241 popup "Return" into P222/P223 is replaced by a separate screen (the XSS-prone `formName`/`averageRate` echo is gone). |

## Stratum Planner (P850)

| Legacy screen | New screen id(s) | Query ids | Ported from |
|---|---|---|---|
| P850 Stratum Advisor Menu | — (SideNav group) | — | Menu only. "Population Profile" links belong to the Profiles area (`/cpm/P330`, `P333`). |
| P854/P880 Search For / List of Sampling Plans | `sampling-plans` (Home deep links `planStatus`, `currentYear`, `districtScope`) | `sampling.plans.search`, `codes.sampling.planStatuses`, `codes.orgUnits` | `HBS3R880` (via `$E/smp/SamplePlanEntityBean.ejbFindByCriteria`) and `$V/smp/P854Action` (Submit preset: `currentYear=Y` → plans in effect today) |
| P883 View Sampling Plan (planlayout) | `sampling-plan` | `sampling.plans.detail`, `sampling.stratumPlans.list`; reports HBS3R888, HBS3R889 | `$E/smp/SamplePlanQuery`, `StratumPlanQuery`; `$W/smp/include/planlayout.jsp` |
| P882 Sampling Plan Comparison | `sampling-plan` (section "Sampling Plan Comparison") | `sampling.planComparison.list` | `$V/smp/RetrieveStratumActualPlanDataTag` |
| P888 View Stratum Plan Details | `stratum-plan` | `sampling.stratumPlans.detail`; report HBS3R889 | `StratumPlanQuery.selectByPK` |
| P884 View Default Ratios | `stratum-plan` (section "Default Ratios") | `sampling.stratumComposition.list` | `$E/smp/StratumPlanCompositionQuery`, `hbs3:calculateFraction` |
| P851/P852 Search For / List of Populations | `sampling-populations` | `sampling.populations.search`; report HBS3R852 | `HBS3R852` |
| P853/P860 Search For / List of Strata in a Population | `sampling-strata` | `sampling.strata.search`; report HBS3R860 | `HBS3R860` (plan header not repeated: open the plan from `sampling-plans`) |
| P861/P862/P863 Species / Grades / Segregations in Population | `sampling-population-stats` (`type` SPC/GRD/SGR) | `sampling.populationStats.search` | `$E/vrc/PopulationStatisticsQuery` |
| P866/P867/P868 Species / Grades / Segregations in Stratum | `sampling-stratum-stats` | `sampling.stratumStats.search` | `$E/vrc/StratumStatisticsQuery` |
| P865 List of Samples in a Stratum | `stratum-samples` (row link of `sampling-strata`; header buttons to P866–P868) | `sampling.stratum.detail`, `sampling.stratumSamples.list` | `$W/smp/P865.jsp`; `RetrieveStratumStatisticsTag` (SAMPLE) → `StatisticsManagerBean.findSampleCompilations` → `SampleCompilationDAO.SELECT_ISSUED_TXNS` + `loadSampleCompilation` (`SummarySampleScaleReturnVersion.getTotalSampleVolume`, `StatisticsCalculator.calculateRatioPerTonne`, SAMPLE_CATEGORIZATION). Stratum owner name FOI-severed. |
| P870 Sample Categories in a Stratum | `stratum-samples` sections "Sample Categories by Species / Grade Predominance" | `sampling.stratumCategories.list` (`type` SPC / GRD) | `RetrieveStratumCategorizationTag` → `StratumStatisticsDAO.selectStratum{Species,Grade}Categories` (`StratumStatisticsQuery.summarize*Categorization`, `VrcSql`; fractions = `calculateVolumeFraction` against STRATUM.SAMPLE_VOLUME). |
| P871/P872/P873 Species / Grades / Segregations in a Sample | `sample-statistics` (row link of the P865 samples; all three lists as sections, so no `type` switch) | `sampling.sample.detail`, `sampling.sampleStats.list` | `$V/smp/P871Action.setSamples`, `SampleStatisticsQuery` (SAMPLE_SPECIES/GRADE/SEGREGATION_STATS by TRX_ID). The Previous / Next sample arrows are not reproduced (go back to the list). |
| P875 Chart of Sample Loads vs Scale Month | `stratum-samples` section "Sample Loads by Scale Month" (the chart data as a table) | `sampling.stratumLoadsByMonth.list` | `RetrieveSampleLoadsTag` (effective month to expiry month + 3, at least 12 months; `DacHelper.findSampleLoadCountByDate`). **No chart library** is installed in the SPA (only Carbon), so the chart is shown as its data. |
| P876 / P877 Ratio vs Scale Date, Volume vs Weight charts | `stratum-samples` "Samples" table (scale date, volume, weight, ratio columns) | `sampling.stratumSamples.list` | The scatter plots' points are the sample rows; shown as a table for the same reason. |
| Charts on P861/P866/P871 (`include/graph.jsp`) | not carried over | — | Tables are carried over; the 3D bar chart is not. |
| P891/P892 Add Sampling Plan / Confirm Add Population | `sampling-plan-add` | `sampling.plan.create` | `$V/smp/P891Action` (Save/Continue), `performAddSamplePlan`, `createPopulation`; P891Form rules in `validation.xml` |
| P881 Edit Sampling Plan ("Save & Calculate") | `sampling-plan-edit`; stratum "Estimates" action on `stratum-plan` | `sampling.plans.edit`, `sampling.plan.calculate`, `sampling.stratumPlan.estimates` | `P891Action.updatePlan` / `doCalculations` / `updateSamplePlan`, `$E/util/math/Statistics` |
| P886/P887/P898 Add / Edit / Delete Stratum Plan | `sampling-plan` action "Add Stratum", `stratum-plan-edit`, `stratum-plan` action "Delete" | `sampling.stratumPlan.create/update/delete`, `codes.sampling.productSchedules` | `$V/smp/P886Action`, P886Form rules, `SamplePlanValidator.validateProductAndGradeCode` |
| P885 Edit Default Ratios | `stratum-plan` action "Add Default Ratio", `stratum-composition`, `stratum-composition-edit` | `sampling.stratumComposition.create/update/remove` | `$V/smp/P885Action` |
| P895 Confirm Propose/Approve/Reject/Delete | `sampling-plan` actions (confirm dialogs) | `sampling.plan.propose/approve/reject/delete` | `P891Action.performActionPropose/performActionApprove/matchStratumNumbers/updatePopulation` |
| P896 Copy Sampling Plan | `sampling-plan` action "Copy" | `sampling.plan.copy` | `$V/smp/P896Action` |
| P897 Create Sampling Plan From Actuals | `sampling-plan` action "Create New Plan From Actuals" | `sampling.plan.createFromActuals` | `$V/smp/P897Action`, `CalculateNewStrataDate.getPlanEffectExpDate`, `RatioHistoryQuery.findLatestRatios` |
| P855 Add A Population | not carried over | — | Orphan (no link; populations are created by the P891/P892 flow). |

## Behaviour changes (deliberate)

- The legacy P230 and all non-admin `smp` pages were public. Logged-in rating screens use
  `RATING_VIEW` / `RATING_MINISTRY_VIEW` and Stratum Planner screens use `SAMPLING_VIEW`. The read-only `smp` pages are
  also served by the public area (`/public/sampling-plans` …, Active plans only, no reports; [public-access.md](../public-access.md)).
  P230 joins it once ported.
- Industry plan visibility from HBS3R880 is reproduced in SQL with the viewer binds: industry users see ACT plans plus
  their own client's plans in any status (applies to plan, stratum-plan, composition and comparison queries).
- All legacy string-concatenated SQL (HBS2R201/HBS3R211/HBS3R221/HBS3R880/HBS3R852/HBS3R860 dynamic SQL, the
  `harvest_history` query) is replaced by bound parameters.
- HBS2R201 print: the `FIELD_SCALE_DECKID` prompt is mapped from the single-deck field, so "Rules for All Decks"
  prints regular rules (the proc needs the literal `All`, which a report link cannot derive from the radio).

## Ported (leftovers package)

- **P202 return-count drill-down** (`$W/plu/P202.jsp`): the "Returns" section of `override-rule` is a table
  (`rating.overrideRules.returnCounts`, counts from `HBS_GET_OVRRATE_RETURN_COUNT`) whose rows open
  `summary-returns`: confirmed returns by `overrideRatingRuleId` (new criterion "Override Rule No") + scale
  dates + field deck; candidate returns by timber mark with `status=OVRRULE`, which `summaryReturns.search`
  reads as HBS2R051/101 `strAndVersionStatusCodeOvr` (not LCK / ISS / CAN / DEL, category not WA).
- **P865 / P870 / P871–P873 / P875–P877**: see the Stratum Planner table.
- **HBS3R889 FOI suppression** for anonymous users (BT17247) — not needed: the public area offers no reports.

## Ported — Rating (workflow commands, `backend/.../api/rating/`)

All Rating writes are `WorkflowCommand` beans (one transaction each, `RowLock` on `UPDATE_TIMESTAMP`, legacy
message texts in `RatingMessages`); `RatingCatalog` has no registry commands any more. Struts ran validation.xml
first and the action's `validateForm` only when it passed — the commands keep those two phases.

| Command id | Class | Legacy | Rules ported |
|---|---|---|---|
| `rating.overrideRule.createDateRule` | `OverrideRuleCreateCommand.DateRule` | `P203Action`, `RuleHelper.createOverrideDateRule` | Timber mark required/existing (P200 gate); dates valid (4-digit year ≤ 2099, process years ≥ 2000); From ≤ To; scale From and override rate date ≥ today − 7 years; process From ≥ today; deck letter/digit; overlap with an ACT rule (both intervals, same mark + deck / regular) → `creationoverlaperror[withFSDeck]`. Created ACT. |
| `rating.overrideRule.createRateRule` | `OverrideRuleCreateCommand.RateRule` | `P204Action` Save (new rule) | Same header rules (no year-2000 rule) incl. the overlap check; created PND, category PR. |
| `rating.overrideRule.update` (new) | `OverrideRuleStatusCommands.Update` | `P207Action` Save | Pending rules only; header dates + reason re-validated, no overlap check; stays PND. |
| `rating.overrideRule.activate` | `OverrideRuleStatusCommands.Activate` | `P204/P207Action` Activate | Pending only; stored header re-validated; overlap; "At least one override rate is required."; STORE ACT. |
| `rating.overrideRule.deactivate` | `OverrideRuleStatusCommands.Deactivate` | `P202/P206Action`, `RateManagerBean.disableOverrideRule` | Active only; STORE INA. |
| `rating.overrideRate.create/update/remove` | `OverrideRateCommands.*` | `P204/P207Action.validateRate`, `RuleHelper.create/updateOverrideRate`, `deleteRate` | Rule must be Pending; product and rate required; species/grade "All" only for logs; SPG in `SPECIES_PRODUCT_GRADE_XREF`; rate ≥ 0, < 10,000, ≤ 2 decimals. |
| `rating.districtRate.create` | `DistrictRateCommands.Create` | `P212Action`, validation.xml P212Form | Dates required/valid, eff ≤ exp; district/product required; rate 0–9,999.99, 2 dp; eff/exp ≥ today − 7 years; BT8425 interval checks vs active rates of the same district/method/SPG (3 messages); SPG xref (species given); grade must be in the Coast/Interior grade table of the method, for logs in effect on both dates (ticket 7012). |
| `rating.districtRate.update` | `DistrictRateCommands.Update` | `P213Action`, validation.xml P213Form | Rate/eff/exp only; dates on file older than 7 years can't change (BT2973), new dates not older than 7 years; logs grade schedule; interval checks excluding itself. |
| `rating.wasteRate.create` | `WasteRateCommands.Create` | `P222Action`, validation.xml P222Form | Timber mark in TIMBER_MARK + MARK_BILLED_CLI; cut block on the mark's cut blocks (or, when it has none, letters/digits/`< > : - ( / '`); scale date ≥ today − 7 years; rate 0–999.99, 2 dp; "Record already exists…" when exactly one identical active rate (legacy `== 1`, ported as written); SPG xref (logs). |
| `rating.wasteRate.update` (new) | `WasteRateCommands.Update` | `P223Action` Save, `RateHelper.update` | Active rows only; scale date on file > 7 years can't change, new date ≥ today − 7 years; SPG; then the first other active avoidable log rate of the mark/cut block/species/grade in effect on the new date is set inactive (legacy `updateWasteResidueRate1` stored only `rates[0]`), and the edited row is stored active with expiry = effective. The legacy overlap rejection is commented out (BT 5705) and not ported. |
| `rating.wasteRate.deactivate` | `WasteRateCommands.Deactivate` | `P223Action` Delete | Active rows only ("Inactive waste residue rates cannot be updated."); STORE ACTIVE_IND N. |
| `GET /rating/stumpage-rate` | `StumpageRateService`, `StumpageRateController` | `P230Action`, `RateManagerBean.findSegregationRate`, `SegregationRateResultsTag`, `OpqHelper.getTimberMarkInfo` | Scale date required, valid, not future; mark in TIMBER_MARK + MARK_BILLED_CLI; return-type rules (Beachcomb marks = file type B17; cut block only for Waste and on the mark; segregation mark BCOMB1–3 or existing); SPG in effect on the scale date; for logs the grade of the mark's schedule (CNF appraisal method, else region) in effect. Private marks (B08/B09/B13–B16) → zero "Private Mark" rate; otherwise the `HBS_RATES` procs via `TableApi` (named, signature-checked); overlapping rules or no rate → "Not Found". |

Deliberate differences: the status gating that the legacy JSPs did by hiding buttons is enforced server-side
(new message texts, marked in `RatingMessages`); every write stamps UPDATE_USERID/UPDATE_TIMESTAMP (P212 passed
null; P223 re-stored the inactivated row's old values) so `RowLock` sees each change; P203/P204 reached from a
validated P200 timber mark — the SPA forms take the mark directly, so the commands check it; the "first matching
row" of P223 is ordered by `WSTRT_ID` (legacy: no ORDER BY). The stumpage endpoint needs EXECUTE on `HBS_RATES`
for the proxy user (the legacy app had it).

District rate update (P213) interval check: the rate's species and grade are passed as stored (untrimmed, so the
one-space grade is kept), as P213 passed `rate.getScaleGradeCode()`; they were trimmed before.
Open point for a TEST check: legacy `selectByCRange` added no criterion for a null species/grade (P213 on an "All"
rate therefore compared against every species), and P212 passed the raw "all" option (matching nothing); the port
compares null with the "All" rows in both.

## Ported — Stratum Planner (workflow commands, `backend/.../api/sampling/`)

`SamplingPlanService`, beans in `SamplingCommands`. One transaction per command; writes only through the
table-API procs (no proposed DDL: `HBS_STRANDED_*` / `HBS_UNUSED_STRATA_COUNT`, REF-CURSOR procs built from
concatenated SQL, are reproduced as bound SELECTs). The rows feed legacy B2022/B2024.

- **Authorization** (`SamplingAccess`, from `SmpHelper.isMinistryAdmin/isIndustryAdmin`, `SamplePlanFunctionsTag`, `HBSValidator.validateDistrictUser`): ministry admin = HBS_SMP_ADMIN with the plan's district in HBS_USER_DIST_DATA_DOMAIN; industry admin = HBS_CLI_SMP_ADMIN whose active client owns the plan. Edit / Propose / Delete / stratum and ratio edits: In Progress (PRO), either admin. Approve / Reject: Awaiting Approval (AWP), ministry admin only. Copy: either admin of the source plan. Create from actuals: Active (ACT) source plan.
- **Add plan** — `sampling.plan.create` (P891/P892): P891Form rules (unique abbreviation, Plan-Name, ratio type vs the population's Active plan, precision limit 0.5–5.0 with ≤ 2 decimals and floor ≥ 2 unless ratio type D, population / year 4 digits, sampling year within the plan years, client / location, district exists and user authorized, effective = first day, expiry = last day, effective < expiry). A missing POPULATION is created only with `confirmNewPopulation=Y` (P892 text otherwise). Plan saved PRO, constraint P, estimate VL, precision 0.
- **Save & Calculate** — `sampling.plan.calculate`, `sampling.stratumPlan.estimates` (P881): header rules plus stranded returns per stratum and P881 stratum-vs-plan date rules; per-stratum estimate rules; `doCalculations` in stratum-number order (later strata see earlier results, as legacy); result-size rules; every STRATUM_PLAN and the SAMPLE_PLAN (population optimum / target precision; zero for ratio type D) stored.
- **Stratum plans** — `sampling.stratumPlan.create/update/delete` (P886/P898): unique stratum number ≥ 1, name, client, stratum dates (first / last day; inside the plan on edit), stranded returns on edit, product / grade schedule unchanged vs the Active plan's stratum, default ratios re-checked (species-product-grade xref, coast / interior grade codes); the add path widens the plan dates as P886 did; edit re-creates the compositions under the new product; delete refused while sample scale summaries, sample log tallies, weight scale summaries or weigh slips reference the stratum. Every stratum change sets the plan's optimum / target precision to 0, so Propose demands a recalculation.
- **Default ratios** — `sampling.stratumComposition.create/update/remove` (P885): species required; ratio numeric, > 0, < 10, ≤ 5 decimals; combination exists and is in effect on the stratum dates; grade in the stratum's grade schedule; no duplicate; stratum total < 10. Blank grade stored as ' '. Update = remove + create keeping the entry stamps.
- **Propose / Approve / Reject / Delete** — `sampling.plan.propose/approve/reject/delete`. Propose: stranded returns, stratum estimate / composition rules ("Stratum Plan errors must be corrected…"), 2006 grade-conversion span rules, sampling year, stored precision not 0, and vs the Active plan: dropped strata unused and the same ratio type; strata recalculated and stored, plan → AWP. Approve: sampling year, stranded returns, ratio type, dropped strata unused, spans; then dropped STRATUM rows removed, STRATUM created or re-pointed (stratum plan id, owner, dates, name, schedules; RATIO_STATEMENT_NUMBER kept), stratum plans accepted, the previous Active plan → SPR (its stratum plans not accepted), POPULATION updated (owner, district, dates, stratum count, estimate totals, load size), plan → ACT accepted. Reject → REJ; Delete → DEL (soft).
- **Copy / Create from actuals** — `sampling.plan.copy` (P896), `sampling.plan.createFromActuals` (P897): P896/P897 rules (incl. ratio type vs the target population's Active plan); population created on confirmation; new PRO plan with the source's limits and methods, precision 0; strata and ratios copied, unused strata optionally dropped, optional plan dates / owner for all strata. From actuals: estimates from the STRATUM sample size / std dev, loads = sample floor, volume = size × loads, calculations 0, dates scaled into the new period unless the plan dates are applied, ratios from the latest RATIO_HISTORY (else the old default ratios).

Deliberate differences: every row written is stamped with UPDATE_USERID / UPDATE_TIMESTAMP so RowLock sees
changes; a failed approval writes nothing (legacy autocommitted the strata changes made before the span check);
approval with no prior Active plan is refused when validation fails (legacy showed the errors and approved
anyway); propose stores the plan with the precisions it had before the recalculation, as legacy did;
status-check messages are new (legacy hid the buttons — the SPA shows them to every SAMPLING_ADMIN user and the
backend refuses); create from actuals refuses a source stratum with no STRATUM row (legacy threw an NPE).

## Open items

- Action buttons are not hidden by record status (`ActionDef` has no status condition); the workflows refuse the
  wrong status with a message. A `showWhen` on `ActionDef` would reproduce the legacy JSP gating exactly.
- `RowLock` cannot parse a midnight `UPDATE_TIMESTAMP` as the query layer serialises it (`yyyy-MM-dd`); the
  rating commands normalise it (`RatingInput.checkLock`), the sampling ones do not yet — fix belongs in `RowLock`.

- ~~`districtScope=ASSOCIATED`~~ — ported: `sampling.plans.search` filter `districtScope`
  (HBS3R880: `sp.org_unit_no IN` HBS_USER_DIST_DATA_DOMAIN of `:hbsUserId`), used by the Home "Sampling Plans"
  links for HBS_SMP_ADMIN.
- `HBS3R211` concatenates `PDDR_DISTRICT`/`PDDR_ORDER_BY` into dynamic SQL; the report endpoint passes request values
  straight through — worth a shared prompt-validation rule in `HbsReportService`.
