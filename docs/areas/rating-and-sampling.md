# Rating and Stratum Planner

Legacy modules `plu` (Rating tab, menu P199) and `smp` (Stratum Planner tab, menu P850).

- Backend: `backend/src/main/java/ca/bc/gov/nrs/hbs/api/catalog/RatingCatalog.java` (`rating.*`, `codes.rating.*`),
  `SamplingCatalog.java` (`sampling.*`, `codes.sampling.*`)
- Frontend: `frontend/src/screens/areas/rating.ts` (area `rating`), `stratum-planner.ts` (area `stratum-planner`)
- Legacy paths below are relative to `hba-archive/hbs/trunk/source/ear/`: `$E` = `hbs-ejb/src/main/java/ca/bc/gov/mof/hbs`,
  `$V` = `hbs-view/src/main/java/ca/bc/gov/mof/hbs/presentation`, `$W` = `hbs-web/src/main/webapp`.
  Procs: `nr-mof-db/scripts/THE/PROCEDURES/V7.*__<NAME>.sql`.

## Rating (P199)

| Legacy screen | New screen id(s) | Query / command ids | Ported from |
|---|---|---|---|
| P199 Rate Management Menu | — (SideNav "Rating" group) | — | Menu only; replaced by the SideNav. |
| P200/P201 Search for / List of Override Rules | `override-rules` | `rating.overrideRules.search`, `codes.rating.ruleStatuses`; report HBS2R201 | `$E/plu/OverrideRateRuleQuery.selectByDate` (+ `Query.appendWhereClause` interval-overlap semantics), columns from `HBS2R201` |
| P202 View Override Rate Rule | `override-rule` | `rating.overrideRules.detail`, `rating.overrideRates.list`; `rating.overrideRule.deactivate` (HBS_STORE_OVERRIDE_RULE, status INA) | `OverrideRateRuleQuery.selectByPK`; return counts via `HBS_GET_OVRRATE_RETURN_COUNT` (as in HBS2R201); `RateManagerBean.disableOverrideRule` |
| P206/P208 View Override Date Rule | `override-rule` | same as P202 | same |
| P203 Add Override Date Rule | `override-date-rule-add` | `rating.overrideRule.createDateRule` (HBS_CREATE_OVERRIDE_RULE, RTDTRL, category PR, status ACT) | `$V/plu/P203Action`, `RuleHelper.createOverrideDateRule` |
| P204 Add Override Rate Rule | `override-rate-rule-add` → `override-rule` | `rating.overrideRule.createRateRule` (HBS_CREATE_OVERRIDE_RULE, OVRRDRULE, PND); `rating.overrideRate.create` (HBS_CREATE_OVERRIDE_RATE); `rating.overrideRule.activate` (HBS_STORE_OVERRIDE_RULE, ACT) | `$V/plu/P204Action`, `RuleHelper.createOverrideRateRule/createOverrideRate` |
| P207 Update Override Rate Rule | `override-rule`, `override-rate`, `override-rate-edit` | `rating.overrideRate.create/update/remove` (HBS_CREATE/STORE/REMOVE_OVERRIDE_RATE), `rating.overrideRule.activate` | `$V/plu/P207Action` |
| P210/P211 Search for / List of District Default Rates | `district-default-rates` | `rating.districtRates.search`, `codes.rating.districts`, `codes.rating.appraisalMethods`; report HBS3R211 | `$E/plu/HbsDistrictDefaultRateQuery.selectByCriteria(orgUnitNo, method, eff, exp)`, joins from `HBS3R211` |
| P212 Add Default Rate | `district-default-rate-add` | `rating.districtRate.create` (HBS_CREATE_HBS_DISTRICT_RATE) | `$V/plu/P212Action` |
| P213 Update Default Rate | `district-default-rate-edit` | `rating.districtRates.detail`, `rating.districtRate.update` (HBS_STORE_HBS_DISTRICT_RATE) | `HbsDistrictDefaultRateQuery.selectByPK`, `$V/plu/P213Action` |
| P220/P221 Search for / List of Waste & Residue Rates | `waste-residue-rates` | `rating.wasteRates.search`; report HBS3R221 | `$E/plu/WasteResidueRateQuery.selectByCriteria(mark, cutBlock, eff, exp)`, joins from `HBS3R221` |
| P222 Add A Waste & Residue Rate | `waste-residue-rate-add` | `rating.wasteRate.create` (HBS_CREATE_WASTE_RESIDUE_RATE; product ' ', active/avoidable Y, expiry = effective) | `$V/plu/P222Action`, `RateHelper.createWasteResidueRate` |
| P223 Update A Waste & Residue Rate | `waste-residue-rate` (view + Delete) | `rating.wasteRates.detail`, `rating.wasteRate.deactivate` (HBS_STORE_WASTE_RESIDUE_RATE, ACTIVE_IND N) | `$V/plu/P223Action` (Delete branch). **Save not carried over** — see below. |
| `plu/P222.jsp` (orphan copy) | not carried over | — | Dead page: no Struts forward reaches it. |
| P230/P231 Search for Stumpage Rate | **not carried over yet** | — | Needs `HBS_RATES.Get_Override_And_Appraisal` + `Retrieve_Normal/Beachcomb/Waste_Rate` (OUT-parameter procedures, not SELECT-able). See below. |
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
| P865 List of Samples in a Stratum | **not carried over yet** | — | `$V/smp/RetrieveStratumSamplesTag` → `findSampleSummaries` (sample summaries + stratum header); needs its SQL ported. |
| P870 Sample Categories in a Stratum | **not carried over yet** | — | `$V/smp/RetrieveStratumCategorizationTag`, `$E/vrc/StratumStatisticsQuery` categorization SQL. |
| P871/P872/P873 Species / Grades / Segregations in a Sample | **not carried over yet** | — | `$V/smp/P871Action`. |
| P875/P876/P877 Stratum charts | **not carried over** | — | Cewolf charts (`$W/smp/P875–P877.jsp`, `RetrieveSampleLoadsTag`, `RetrieveStratumStatisticsTag`); need a chart component + data queries. |
| Charts on P861/P866/P871 (`include/graph.jsp`) | not carried over | — | Tables are carried over; the 3D bar chart is not. |
| P891/P892 Add Sampling Plan / Confirm Add Population | **not carried over yet** | — | Java workflow (see below). |
| P881 Edit Sampling Plan ("Save & Calculate") | **not carried over yet** | — | Java workflow. |
| P886/P887/P898 Add / Edit / Delete Stratum Plan | **not carried over yet** | — | Java workflow. |
| P885 Edit Default Ratios | **not carried over yet** | — | Java workflow. |
| P895 Confirm Propose/Approve/Reject/Delete | **not carried over yet** | — | Java workflow. |
| P896 Copy Sampling Plan | **not carried over yet** | — | Java workflow. |
| P897 Create Sampling Plan From Actuals | **not carried over yet** | — | Java workflow. |
| P855 Add A Population | not carried over | — | Orphan (no link; populations are created by the P891/P892 flow). |

## Behaviour changes (deliberate)

- No anonymous access: the legacy P230 and all non-admin `smp` pages were public. Rating screens use
  `RATING_VIEW` / `RATING_MINISTRY_VIEW`; Stratum Planner screens use `SAMPLING_VIEW`.
- Industry plan visibility from HBS3R880 is reproduced in SQL with the viewer binds: industry users see ACT plans plus
  their own client's plans in any status (applies to plan, stratum-plan, composition and comparison queries).
- All legacy string-concatenated SQL (HBS2R201/HBS3R211/HBS3R221/HBS3R880/HBS3R852/HBS3R860 dynamic SQL, the
  `harvest_history` query) is replaced by bound parameters.
- HBS2R201 print: the `FIELD_SCALE_DECKID` prompt is mapped from the single-deck field, so "Rules for All Decks"
  prints regular rules (the proc needs the literal `All`, which a report link cannot derive from the radio).

## Legacy logic not yet ported

Rating (validations the Struts actions ran before the procs — the commands above do not enforce them):

1. **Override rule overlap check** before activating (P203 Activate, P204/P207 Activate): `RateManagerBean.findOverLapCountByCriteria`
   → `$E/plu/OverrideRateRuleQuery.countOverlapByCriteria` (ACT rules for the mark + deck whose scale and processing
   intervals both overlap). Messages "Rule cannot be activated because its scaling interval and its processing interval
   overlap with another rule for this timber mark [and field scale deck ID]." Also "At least one override rate is
   required." on activate (P204/P207), and the status gating (Activate only for PND, De-activate only for ACT —
   `$W/plu/P202.jsp`, `admin/P207.jsp`). Implement as a `RatingService` pre-check, then call the same procs.
2. **Override rule/rate field rules** (`$V/plu/P203Action`, `P204Action`, `P207Action`, `RuleHelper.isValidTimberMark`):
   scale/override dates ≥ today − 7 years, process From ≥ today, years 2000–2099, From ≤ To, deck alphanumeric;
   rate row rules (all fields; species/grade "All" only for Logs; valid SPG; 0 < rate ≤ 9,999.99, 2 decimals).
3. **District default rate checks** (`$V/plu/P212Action`, `P213Action`): 7-year rule on new and stored dates; valid SPG;
   for Logs the grade must exist in the coast/interior schedule and be effective; interval overlap with rates for the
   same district/method/SPG (`HbsDistrictDefaultRateQuery.selectByCRange`, excluding itself on update).
4. **Waste rate checks** (`$V/plu/P222Action`): cut block characters and existence on the mark's cut block table, 7-year
   rule, valid SPG, duplicate rejection ("Record already exists…").
5. **P223 Save** (`$V/plu/P223Action` + `RateHelper.update`, `$V/plu/RateHelper.java` ~l.460–490): inactivates the matching
   existing rows (sets ACTIVE_IND N), checks date overlap ("Date overlapping."), refuses inactive rows and stored dates
   older than 7 years, then `updateWasteResidueRate`. Multi-row — needs a service.
6. **P230/P231 Stumpage rate lookup** (`$E/plu/RateManagerBean.findSegregationRate`, `SegregationRateRequest*`,
   `$V/plu/SegregationRateResultsTag`): calls `HBS_RATES.Get_Override_And_Appraisal` then `Retrieve_Normal_Rate` /
   `Retrieve_Beachcomb_Rate` / `Retrieve_Waste_Rate` (OUT params), plus `OpqHelper.getTimberMarkInfo` for the appraisal
   block, and the input validation (beachcomb mark rules, cut block for Waste, SPG/grade schedule effective on the scale
   date, scale date ≤ today). Needs a `StumpageRateService` with a `CallableStatement`.
7. **P202 return-count drill-down links** to the summary scale return search (`$W/plu/P202.jsp`) — counts are shown, links are not.

Stratum Planner (all writes; each needs a `SamplingPlanService`):

8. **Authorization** (`$V/smp/SamplePlanFunctionsTag`, `SmpHelper.isMinistryAdmin/isIndustryAdmin`): ministry admin =
   HBS_SMP_ADMIN and the plan's district in `HBS_USER_DIST_DATA_DOMAIN`; industry admin = CLI_SMP_ADMIN and plan owner
   = the user's client. This is why no plan write is a plain command.
9. **Add plan** (`$V/smp/P891Action` Save/Continue): create POPULATION if missing (P892 confirm) via
   `SamplePlannerBean.createAndStorePopulation`, then SAMPLE_PLAN status PRO (`HBS_CREATE_SAMPLE_PLAN`); validations
   `uniquePlanAbbreviation`, `validateStrandedReturns`, `popEffectExpDate`, ratio-type rules (precision 0.5–5.0, floor ≥ 2).
10. **Edit / Save & Calculate** (`P891Action` Edit, `doCalculations`): optimum/target sample, precision and frequency per
    stratum from the estimate and constraint methods; updates SAMPLE_PLAN and STRATUM_PLAN rows.
11. **Stratum plan add/edit/delete** (`$V/smp/P886Action`): uniqueStratumNumber, stranded-return checks
    (`SrtsManagerBean.findStrandedSingleReturns` → procs `HBS_STRANDED_SAMPLE_SCALE/_LOG`, `HBS_STRANDED_WEIGH_SCALE`,
    `HBS_STRANDED_WEIGHSLIPS`), grade-schedule re-validation of compositions, delete blocked by referencing documents.
12. **Default ratios** (`$V/smp/P885Action`): species/grade/product validity per schedule, 0 < ratio < 10, total < 10,
    5 decimals, duplicate check; STRATUM_PLAN_COMPOSITION create/store/remove.
13. **Propose / Approve / Reject / Delete** (`P891Action.performActionPropose/performActionApprove`, P895): stranded
    returns (`CalculateNewStrataDate.validateStrandedReturns`); approve creates/updates STRATUM rows, removes unplanned
    strata unless documents reference them, sets the prior ACT plan for the population/year to SPR, sets
    `accepted_plan_ind`, updates POPULATION. Reject → REJ; Delete → DEL (soft).
14. **Copy / Create from actuals** (`$V/smp/P896Action`, `P897Action`): new PRO plan + copies of STRATUM_PLAN and
    compositions, optional unused-strata removal (`HBS_UNUSED_STRATA_COUNT`), "apply population values to strata".
15. **HBS3R889 FOI suppression** for anonymous users (BT17247) — not needed while there is no anonymous access.

## Open items

- **`districtScope=ASSOCIATED`** (Home "Sampling Plans" block for HBS_SMP_ADMIN): HBS3R880 filters
  `sp.org_unit_no IN (SELECT org_unit_no FROM hbs_user_dist_data_domain WHERE user_id = <user>)`. The query framework
  has no viewer user-id bind, so the option is shown but not applied. Needed: a `:hbsUserId` viewer bind in
  `QueryService` (legacy `USER_ID` format, upper-cased, e.g. `IDIR\JSMITH`), then the filter
  `AND (:districtScope <> 'ASSOCIATED' OR sp.org_unit_no IN (SELECT dd.org_unit_no FROM hbs_user_dist_data_domain dd WHERE dd.user_id = :hbsUserId))`.
- STORE-based commands (`rating.overrideRule.activate/deactivate`, `rating.districtRate.update`,
  `rating.wasteRate.deactivate`) re-send the row's entry user/timestamp from the client (the procs overwrite every
  column); the entry timestamp loses its time-of-day.
- `HBS3R211` concatenates `PDDR_DISTRICT`/`PDDR_ORDER_BY` into dynamic SQL; the report endpoint passes request values
  straight through — worth a shared prompt-validation rule in `HbsReportService`.
