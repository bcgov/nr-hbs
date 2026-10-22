package ca.bc.gov.nrs.hbs.api.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

/**
 * The Stratum Planner workflows against an in-memory read side and the
 * {@link RecordingTableApi} (every proc call is checked against the real
 * nr-mof-db parameter lists).
 */
class SamplingPlanServiceTest {

  static final LocalDateTime PLAN_TS = LocalDateTime.of(2026, 9, 1, 10, 15, 30);

  /** Caller facts for {@link SamplingAccess}. */
  static class TestAccess extends SamplingAccess {
    Set<String> roles = Set.of(HbsRoles.SMP_ADMIN);
    String user = "IDIR\\ADMIN";
    String client = "";

    TestAccess(SamplingReads reads) {
      super(reads);
    }

    @Override
    protected boolean hasRole(String role) {
      return roles.contains(role);
    }

    @Override
    protected String userId() {
      return user;
    }

    @Override
    protected String userClientNumber() {
      return client;
    }
  }

  FakeSamplingReads reads;
  RecordingTableApi api;
  TestAccess access;
  SamplingPlanService service;

  @BeforeEach
  void setUp() {
    reads = new FakeSamplingReads();
    api = new RecordingTableApi();
    access = new TestAccess(reads);
    service = new SamplingPlanService(reads, new SamplingWrites(api), new SamplingValidator(reads, access), access,
        new Audit("HBSBATCH"));
    reads.orgUnits.put(10L, new SamplingReads.OrgUnit(10, "DKA", "RNI"));
    reads.orgUnits.put(20L, new SamplingReads.OrgUnit(20, "DSI", "RCO"));
    reads.userDistricts.add(FakeSamplingReads.key("IDIR\\ADMIN", 10L));
    reads.clientLocations.add(FakeSamplingReads.key("00012345", "00"));
    reads.plans.put(500L, plan(500L, SamplePlanRow.IN_PROGRESS, "A1", "N"));
    reads.stratumPlans.put(600L, stratum(600L, 500L, "01", 50000, 2000, 3.0));
    reads.stratumPlans.put(601L, stratum(601L, 500L, "02", 30000, 1000, 4.0));
  }

  // ── fixtures ────────────────────────────────────────────────────

  static SamplePlanRow plan(long id, String status, String abbr, String accepted) {
    SamplePlanRow p = new SamplePlanRow();
    p.planId = id;
    p.samplePlanStatusCode = status;
    p.populationNumber = "1234";
    p.samplingYear = 2026L;
    p.planAbreviation = abbr;
    p.planName = "Plan " + abbr;
    p.ratioTypeCode = "S";
    p.precisionLimit = 1.0;
    p.sampleFloor = 10L;
    p.currentConstraintType = "P";
    p.currentEstimateType = "VL";
    p.acceptedPlanInd = accepted;
    p.optimumPrecisionCalc = 1.0;
    p.targetPrecisionCalc = 1.0;
    p.orgUnitNo = 10L;
    p.clientNumber = "00012345";
    p.clientLocnCode = "00";
    p.effectiveDate = LocalDate.of(2026, 1, 1).atStartOfDay();
    p.expiryDate = LocalDate.of(2026, 12, 31).atStartOfDay();
    p.entryUserid = "IDIR\\OLD";
    p.entryTimestamp = LocalDateTime.of(2026, 1, 5, 9, 0);
    p.updateUserid = "IDIR\\OLD";
    p.updateTimestamp = PLAN_TS;
    return p;
  }

  static StratumPlanRow stratum(long id, long planId, String number, double volume, long loads, double sd) {
    StratumPlanRow s = new StratumPlanRow();
    s.stratumPlanId = id;
    s.planId = planId;
    s.stratumNumber = number;
    s.stratumName = "Stratum " + number;
    s.clientNumber = "00012345";
    s.clientLocnCode = "00";
    s.sampleCountConstraint = 0L;
    s.precisionConstraint = 0.0;
    s.estimatedVolume = volume;
    s.estimatedLoads = loads;
    s.estimatedLoadSize = volume / loads;
    s.estimatedStandardDeviation = sd;
    s.optimumSampleCalc = 0L;
    s.optimumPrecisionCalc = 0.0;
    s.optimumFrequencyCalc = 0L;
    s.targetSampleCalc = 0L;
    s.targetPrecisionCalc = 0.0;
    s.targetFrequencyCalc = 0L;
    s.acceptedStratumPlanInd = "N";
    s.effectiveDate = LocalDate.of(2026, 1, 1).atStartOfDay();
    s.expiryDate = LocalDate.of(2026, 12, 31).atStartOfDay();
    s.entryUserid = "IDIR\\OLD";
    s.entryTimestamp = LocalDateTime.of(2026, 1, 5, 9, 0);
    s.updateTimestamp = LocalDateTime.of(2026, 9, 2, 8, 0);
    s.gradeScheduleCode = "I";
    s.scaleProductCode = "SL";
    return s;
  }

  static Map<String, Object> composition(long stratumPlanId, String species, String grade, String ratio) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("scale_species_code", species);
    c.put("scale_grade_code", grade);
    c.put("stratum_plan_id", BigDecimal.valueOf(stratumPlanId));
    c.put("scale_product_code", "SL");
    c.put("default_ratio", new BigDecimal(ratio));
    c.put("entry_userid", "IDIR\\OLD");
    c.put("entry_timestamp", LocalDateTime.of(2025, 2, 1, 8, 0));
    c.put("update_userid", null);
    c.put("update_timestamp", null);
    return c;
  }

  static Map<String, Object> stratumRow(String number, long stratumPlanId) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("population_number", "1234");
    s.put("sampling_year", BigDecimal.valueOf(2026));
    s.put("stratum_number", number);
    s.put("stratum_plan_id", BigDecimal.valueOf(stratumPlanId));
    s.put("client_number", "00012345");
    s.put("client_locn_code", "00");
    s.put("stratum_name", "Old " + number);
    for (String col : List.of("total_transactions", "sample_loads", "billed_loads", "sampling_frequency",
        "sample_load_size", "sample_standard_deviation", "sample_volume", "billed_volume", "sample_weight",
        "average_sample_weight", "billed_weight", "average_total_weight", "ratio", "stratum_variance",
        "degrees_of_freedom", "precision")) {
      s.put(col, BigDecimal.ZERO);
    }
    s.put("grade_schedule_code", "I");
    s.put("scale_product_code", "SL");
    s.put("ratio_statement_number", "RS000123");
    s.put("effective_date", LocalDate.of(2025, 1, 1).atStartOfDay());
    s.put("expiry_date", LocalDate.of(2025, 12, 31).atStartOfDay());
    s.put("entry_userid", "IDIR\\OLD");
    s.put("entry_timestamp", LocalDateTime.of(2025, 1, 2, 0, 0));
    s.put("update_userid", "IDIR\\OLD");
    s.put("update_timestamp", LocalDateTime.of(2025, 1, 2, 0, 0));
    return s;
  }

  static Map<String, Object> population() {
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("population_number", "1234");
    p.put("sampling_year", BigDecimal.valueOf(2026));
    p.put("org_unit_no", null);
    p.put("client_number", null);
    p.put("client_locn_code", null);
    for (String col : List.of("stratum_count", "estimated_total_volume", "estimated_total_loads",
        "estimated_load_size", "sample_loads", "billed_loads", "sample_load_size", "sample_volume", "billed_volume",
        "sample_weight", "billed_weight", "ratio", "pop_variance", "degrees_of_freedom", "precision")) {
      p.put(col, BigDecimal.ZERO);
    }
    p.put("ratio_type_code", "S");
    p.put("effective_date", null);
    p.put("expiry_date", null);
    p.put("entry_userid", "IDIR\\OLD");
    p.put("entry_timestamp", LocalDateTime.of(2025, 12, 1, 0, 0));
    p.put("update_userid", null);
    p.put("update_timestamp", null);
    return p;
  }

  Map<String, Object> newPlanBody() {
    Map<String, Object> b = new HashMap<>();
    b.put("population", "4321");
    b.put("samplingYear", "2027");
    b.put("planAbbreviation", "b2");
    b.put("planName", "New plan");
    b.put("ratioTypeCode", "S");
    b.put("precisionLimit", "1.5");
    b.put("sampleFloor", "10");
    b.put("clientNumber", "00012345");
    b.put("clientLocnCode", "00");
    b.put("orgUnitNo", "10");
    b.put("effectiveDate", "2027-01-01");
    b.put("expiryDate", "2027-12-31");
    b.put("confirmNewPopulation", "Y");
    return b;
  }

  static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  static List<String> messages(Throwable t) {
    return ((WorkflowValidationException) t).problems().stream().map(p -> p.message()).toList();
  }

  RecordingTableApi.Call only(String proc) {
    List<RecordingTableApi.Call> calls = api.callsTo(proc);
    assertThat(calls).hasSize(1);
    return calls.get(0);
  }

  // ── P891 Add ────────────────────────────────────────────────────

  @Test
  void createPlanCreatesThePopulationThenAnInProgressPlan() {
    Map<String, Object> result = service.createPlan(newPlanBody());
    assertThat(api.names()).containsExactly("HBS_CREATE_POPULATION", "HBS_CREATE_SAMPLE_PLAN");
    var pop = api.calls.get(0);
    assertThat(pop.arg("i_population_number")).isEqualTo("4321");
    assertThat(pop.arg("i_org_unit_no")).isNull();
    assertThat(pop.arg("i_ratio_type_code")).isEqualTo("S");
    var plan = api.calls.get(1);
    assertThat(plan.arg("i_plan_id")).isEqualTo(BigDecimal.valueOf(1001));
    assertThat(plan.arg("i_sample_plan_status_code")).isEqualTo("PRO");
    assertThat(plan.arg("i_plan_abreviation")).isEqualTo("B2");
    assertThat(plan.arg("i_precision_limit")).isEqualTo(BigDecimal.valueOf(1.5));
    assertThat(plan.arg("i_current_constraint_type")).isEqualTo("P");
    assertThat(plan.arg("i_current_estimate_type")).isEqualTo("VL");
    assertThat(plan.arg("i_accepted_plan_ind")).isEqualTo("N");
    assertThat(plan.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2027-01-01 00:00:00"));
    assertThat(plan.arg("i_entry_userid")).isEqualTo("HBSBATCH");
    assertThat(plan.arg("i_update_userid")).isNull();
    assertThat(result).containsEntry("planId", 1001L);
  }

  @Test
  void createPlanAsksToConfirmANewPopulation() {
    Map<String, Object> b = newPlanBody();
    b.remove("confirmNewPopulation");
    assertThatThrownBy(() -> service.createPlan(b)).isInstanceOf(WorkflowValidationException.class)
        .hasMessage("Please confirm you wish to create a new population: 4321 and year: 2027");
    assertThat(api.calls).isEmpty();
  }

  @Test
  void createPlanForAnExistingPopulationDoesNotRecreateIt() {
    reads.populations.put(FakeSamplingReads.key("4321", 2027L), population());
    Map<String, Object> b = newPlanBody();
    b.remove("confirmNewPopulation");
    service.createPlan(b);
    assertThat(api.names()).containsExactly("HBS_CREATE_SAMPLE_PLAN");
  }

  @Test
  void defaultRatioPlansStoreNoPrecisionOrFloor() {
    Map<String, Object> b = newPlanBody();
    b.put("ratioTypeCode", "D");
    b.put("precisionLimit", "99");
    b.put("sampleFloor", "");
    service.createPlan(b);
    var plan = only("HBS_CREATE_SAMPLE_PLAN");
    assertThat(plan.arg("i_precision_limit")).isEqualTo(BigDecimal.valueOf(0.0));
    assertThat(plan.arg("i_sample_floor")).isEqualTo(BigDecimal.ZERO);
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "precisionLimit|6|Precision Limit cannot be greater than 5.0%.",
      "precisionLimit|0.3|Precision Limit cannot be less than 0.5%.",
      "precisionLimit|1.234|Precision Limit must have 2 or less decimal places.",
      "precisionLimit|abc|Precision Limit must be a number.",
      "sampleFloor|1|Sample Floor cannot be less than 2.",
      "sampleFloor|x|Sample Floor must be numeric.",
      "planName||Plan-Name is required.",
      "population|12|Population cannot be less than 4 characters.",
      "samplingYear|2026|The sampling year must be greater than or equal to the year of the population effective date.",
      "effectiveDate|2027-01-02|Population Effective Date must be the first day of a month",
      "expiryDate|2027-12-30|Population Expiry Date must be the last day of a month",
      "effectiveDate|2028-01-01|Population Effective Date must be less than Population Expiry Date",
      "clientLocnCode||Client Location is missing.",
      "clientLocnCode|01|Client Number and Location is invalid.",
      "orgUnitNo|20|User not authorized to add a population for this district.",
      "orgUnitNo|99|Region / District is invalid.",
  })
  void createPlanFieldRules(String field, String value, String message) {
    Map<String, Object> b = newPlanBody();
    b.put(field, value == null ? "" : value);
    assertThatThrownBy(() -> service.createPlan(b)).isInstanceOf(WorkflowValidationException.class)
        .satisfies(t -> assertThat(messages(t)).contains(message));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void createPlanRefusesADuplicateAbbreviationAndARatioTypeChange() {
    SamplePlanRow active = plan(400L, SamplePlanRow.ACTIVE, "B2", "Y");
    active.populationNumber = "4321";
    active.samplingYear = 2027L;
    active.ratioTypeCode = "D";
    reads.plans.put(400L, active);
    assertThatThrownBy(() -> service.createPlan(newPlanBody()))
        .satisfies(t -> assertThat(messages(t)).contains("Plan abbreviation already exists.",
            "A population's ratio type cannot be changed once a plan has been approved."));
  }

  @Test
  void anIndustryAdministratorMayAddPlansForItsOwnClientOnly() {
    access.roles = Set.of(HbsRoles.CLI_SMP_ADMIN);
    access.client = "00012345";
    Map<String, Object> b = newPlanBody();
    b.put("orgUnitNo", "20");
    service.createPlan(b);
    assertThat(api.names()).contains("HBS_CREATE_SAMPLE_PLAN");
    access.client = "00099999";
    assertThatThrownBy(() -> service.createPlan(newPlanBody()))
        .satisfies(t -> assertThat(messages(t)).contains(SamplingMessages.DISTRICT_USER));
  }

  // ── P881 Save & Calculate ───────────────────────────────────────

  @Test
  void calculateStoresEveryStratumThenThePlan() {
    service.calculatePlan(body("planId", "500", "updateTimestamp", "2026-09-01T10:15:30", "planName", "Renamed",
        "estimateType", "VL", "constraintType", "P"));
    assertThat(api.names()).containsExactly("HBS_STORE_STRATUM_PLAN", "HBS_STORE_STRATUM_PLAN",
        "HBS_STORE_SAMPLE_PLAN");
    var s1 = api.calls.get(0);
    assertThat(s1.arg("i_stratum_plan_id")).isEqualTo(BigDecimal.valueOf(600));
    assertThat(s1.arg("i_optimum_sample_calc")).isEqualTo(BigDecimal.valueOf(300));
    assertThat(s1.arg("i_target_precision_calc")).isEqualTo(BigDecimal.valueOf(1.25));
    assertThat(s1.arg("i_update_userid")).isEqualTo("HBSBATCH");
    var p = api.calls.get(2);
    assertThat(p.arg("i_plan_name")).isEqualTo("Renamed");
    assertThat(p.arg("i_optimum_precision_calc")).isEqualTo(BigDecimal.valueOf(1.0));
    assertThat(p.arg("i_target_precision_calc")).isEqualTo(BigDecimal.valueOf(1.0));
    assertThat(p.arg("i_plan_abreviation")).isEqualTo("A1");
    assertThat(p.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
  }

  @Test
  void calculateTakesTheGridValues() {
    service.calculatePlan(body("planId", "500", "strata",
        List.of(Map.of("stratumPlanId", 600, "precisionConstraint", "2.0"))));
    var s1 = api.calls.get(0);
    assertThat(s1.arg("i_sample_count_constraint")).isEqualTo(BigDecimal.valueOf(130));
    assertThat(api.calls.get(1).arg("i_target_sample_calc")).isEqualTo(BigDecimal.valueOf(10));
    assertThat(api.calls.get(2).arg("i_target_precision_calc")).isEqualTo(BigDecimal.valueOf(3.33));
  }

  @Test
  void stratumEstimatesRecalculateThePlan() {
    assertThatThrownBy(() -> service.saveStratumEstimates(body("stratumPlanId", "600",
        "updateTimestamp", "2026-09-02T08:00:00", "estimatedVolume", "9")))
        .satisfies(t -> assertThat(messages(t)).contains("Estimated Volume cannot be less than 10 M3."));
    assertThat(api.calls).isEmpty();
    service.saveStratumEstimates(body("stratumPlanId", "600", "updateTimestamp", "2026-09-02T08:00:00",
        "precisionConstraint", "2.0"));
    assertThat(api.names()).containsExactly("HBS_STORE_STRATUM_PLAN", "HBS_STORE_STRATUM_PLAN",
        "HBS_STORE_SAMPLE_PLAN");
    assertThat(api.calls.get(0).arg("i_sample_count_constraint")).isEqualTo(BigDecimal.valueOf(130));
  }

  @Test
  void calculateReportsEstimateErrorsWithoutWriting() {
    reads.stratumPlans.get(601L).estimatedVolume = 9.0;
    assertThatThrownBy(() -> service.calculatePlan(body("planId", "500")))
        .satisfies(t -> assertThat(messages(t)).contains("Estimated Volume cannot be less than 10 M3."));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void calculateChecksStrataAgainstThePlanPeriodAndStrandedReturns() {
    reads.strandedSingle.put(FakeSamplingReads.key("1234", "02", 2026L), new SamplingReads.DocumentCounts(0, 0, 4, 0));
    assertThatThrownBy(() -> service.calculatePlan(body("planId", "500", "effectiveDate", "2026-02-01")))
        .satisfies(t -> assertThat(messages(t)).contains(
            "The proposed effective period for stratum 02 will strand 4 weight scale summaries",
            "Population Effective Date cannot be greater than Stratum 01 Effective Date"));
  }

  @Test
  void workflowsCheckLockStatusAndAccess() {
    assertThatThrownBy(() -> service.calculatePlan(body("planId", "500", "updateTimestamp", "2026-09-01T10:15:31")))
        .isInstanceOf(StaleUpdateException.class);
    reads.plans.get(500L).samplePlanStatusCode = SamplePlanRow.AWAITING_APPROVAL;
    assertThatThrownBy(() -> service.calculatePlan(body("planId", "500")))
        .isInstanceOf(WorkflowValidationException.class).hasMessageContaining("PRO");
    reads.plans.get(500L).samplePlanStatusCode = SamplePlanRow.IN_PROGRESS;
    access.user = "IDIR\\OTHER";
    assertThatThrownBy(() -> service.calculatePlan(body("planId", "500")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(api.calls).isEmpty();
  }

  // ── Propose ─────────────────────────────────────────────────────

  @Test
  void proposeRecalculatesAndMovesThePlanToAwaitingApproval() {
    reads.plans.get(500L).optimumPrecisionCalc = 1.11;
    Map<String, Object> result = service.proposePlan(body("planId", "500"));
    assertThat(api.names()).containsExactly("HBS_STORE_STRATUM_PLAN", "HBS_STORE_STRATUM_PLAN",
        "HBS_STORE_SAMPLE_PLAN");
    var p = api.calls.get(2);
    assertThat(p.arg("i_sample_plan_status_code")).isEqualTo("AWP");
    // The legacy action stored the plan as loaded before the recalculation.
    assertThat(p.arg("i_optimum_precision_calc")).isEqualTo(BigDecimal.valueOf(1.11));
    assertThat(result).containsEntry("samplePlanStatusCode", "AWP");
  }

  @Test
  void proposeReportsStrandedReturnsAsStratumPlanErrors() {
    reads.strandedMultiple.add(new SamplingReads.Stranded("WS", "01", 3));
    assertThatThrownBy(() -> service.proposePlan(body("planId", "500")))
        .satisfies(t -> assertThat(messages(t)).containsExactly(SamplingMessages.STRATUM_PLAN_ERRORS));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void proposeNeedsACalculatedPlan() {
    reads.plans.get(500L).targetPrecisionCalc = 0.0;
    assertThatThrownBy(() -> service.proposePlan(body("planId", "500")))
        .satisfies(t -> assertThat(messages(t)).containsExactly(SamplingMessages.PRECISION_ZERO));
  }

  @Test
  void proposeRefusesToDropAStratumOfTheActivePlanThatDocumentsUse() {
    reads.plans.put(400L, plan(400L, SamplePlanRow.ACTIVE, "A0", "Y"));
    reads.stratumPlans.put(450L, stratum(450L, 400L, "03", 1000, 100, 2.0));
    reads.documents.put(FakeSamplingReads.key("1234", "03", 2026L), new SamplingReads.DocumentCounts(0, 2, 0, 0));
    assertThatThrownBy(() -> service.proposePlan(body("planId", "500")))
        .satisfies(t -> assertThat(messages(t)).containsExactly(
            "Cannot Propose the Plan. One or more documents exists, that reference stratum 03, which this plan is"
                + " attempting to remove"));
  }

  @Test
  void proposeSpanRules() {
    reads.stratumPlans.get(600L).effectiveDate = LocalDate.of(2006, 1, 1).atStartOfDay();
    reads.stratumPlans.get(600L).expiryDate = LocalDate.of(2006, 12, 31).atStartOfDay();
    assertThatThrownBy(() -> service.proposePlan(body("planId", "500")))
        .satisfies(t -> assertThat(messages(t)).contains(SamplingMessages.firstSpan("01")));
  }

  // ── Approve / Reject / Delete ───────────────────────────────────

  void awaitingApprovalWithActivePlan() {
    reads.plans.get(500L).samplePlanStatusCode = SamplePlanRow.AWAITING_APPROVAL;
    reads.plans.put(400L, plan(400L, SamplePlanRow.ACTIVE, "A0", "Y"));
    reads.stratumPlans.put(450L, stratum(450L, 400L, "01", 1000, 100, 2.0));
    reads.stratumPlans.put(451L, stratum(451L, 400L, "03", 1000, 100, 2.0));
    reads.strata.put(FakeSamplingReads.key("1234", 2026L),
        new ArrayList<>(List.of(stratumRow("01", 450L), stratumRow("03", 451L))));
    reads.populations.put(FakeSamplingReads.key("1234", 2026L), population());
  }

  @Test
  void approveMakesThePlanActiveAndRepointsTheStrata() {
    awaitingApprovalWithActivePlan();
    service.approvePlan(body("planId", "500", "updateTimestamp", "2026-09-01T10:15:30"));
    assertThat(api.names()).containsExactly(
        "HBS_REMOVE_STRATUM",          // 03 is not in the new plan
        "HBS_STORE_STRATUM",           // 01 re-pointed
        "HBS_STORE_STRATUM_PLAN",      // 600 accepted
        "HBS_CREATE_STRATUM",          // 02 new
        "HBS_STORE_STRATUM_PLAN",      // 601 accepted
        "HBS_STORE_STRATUM_PLAN",      // 450 no longer accepted
        "HBS_STORE_STRATUM_PLAN",      // 451 no longer accepted
        "HBS_STORE_SAMPLE_PLAN",       // 400 superseded
        "HBS_STORE_POPULATION",
        "HBS_STORE_SAMPLE_PLAN");      // 500 active
    assertThat(api.calls.get(0).arg("i_stratum_number")).isEqualTo("03");
    var s01 = api.calls.get(1);
    assertThat(s01.arg("i_stratum_plan_id")).isEqualTo(BigDecimal.valueOf(600));
    assertThat(s01.arg("i_statement_number")).isEqualTo("RS000123");
    assertThat(s01.arg("i_stratum_name")).isEqualTo("Stratum 01");
    assertThat(s01.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2026-01-01 00:00:00"));
    assertThat(s01.arg("i_product_schedule_code")).isEqualTo("SL");
    assertThat(api.calls.get(2).arg("i_accepted_stratum_plan_ind")).isEqualTo("Y");
    assertThat(api.calls.get(3).arg("i_stratum_plan_id")).isEqualTo(BigDecimal.valueOf(601));
    assertThat(api.calls.get(5).arg("i_accepted_stratum_plan_ind")).isEqualTo("N");
    var old = api.calls.get(7);
    assertThat(old.arg("i_plan_id")).isEqualTo(BigDecimal.valueOf(400));
    assertThat(old.arg("i_sample_plan_status_code")).isEqualTo("SPR");
    assertThat(old.arg("i_accepted_plan_ind")).isEqualTo("N");
    var pop = api.calls.get(8);
    assertThat(pop.arg("i_estimated_total_volume")).isEqualTo(BigDecimal.valueOf(80000.0));
    assertThat(pop.arg("i_estimated_total_loads")).isEqualTo(BigDecimal.valueOf(3000));
    assertThat(pop.arg("i_estimated_load_size")).isEqualTo(BigDecimal.valueOf(26.667));
    assertThat(pop.arg("i_stratum_count")).isEqualTo(BigDecimal.valueOf(2));
    assertThat(pop.arg("i_org_unit_no")).isEqualTo(BigDecimal.valueOf(10));
    assertThat(pop.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
    var p = api.calls.get(9);
    assertThat(p.arg("i_sample_plan_status_code")).isEqualTo("ACT");
    assertThat(p.arg("i_accepted_plan_ind")).isEqualTo("Y");
  }

  @Test
  void approveRefusesToRemoveAStratumDocumentsUse() {
    awaitingApprovalWithActivePlan();
    reads.documents.put(FakeSamplingReads.key("1234", "03", 2026L), new SamplingReads.DocumentCounts(1, 0, 0, 0));
    assertThatThrownBy(() -> service.approvePlan(body("planId", "500")))
        .satisfies(t -> assertThat(messages(t)).containsExactly(
            "Cannot Approve the Plan. One or more documents exists, that reference stratum 03, which this plan is"
                + " attempting to remove"));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void approveWithoutAnActivePlanCreatesTheStrata() {
    reads.plans.get(500L).samplePlanStatusCode = SamplePlanRow.AWAITING_APPROVAL;
    reads.populations.put(FakeSamplingReads.key("1234", 2026L), population());
    service.approvePlan(body("planId", "500"));
    assertThat(api.names()).containsExactly("HBS_CREATE_STRATUM", "HBS_STORE_STRATUM_PLAN", "HBS_CREATE_STRATUM",
        "HBS_STORE_STRATUM_PLAN", "HBS_STORE_POPULATION", "HBS_STORE_SAMPLE_PLAN");
    var s = api.calls.get(0);
    assertThat(s.arg("i_sample_loads")).isEqualTo(BigDecimal.ZERO);
    assertThat(s.arg("i_grade_schedule_code")).isEqualTo("I");
    assertThat(s.arg("i_statement_number")).isNull();
  }

  @Test
  void approveAndRejectAreForMinistryDistrictAdministrators() {
    reads.plans.get(500L).samplePlanStatusCode = SamplePlanRow.AWAITING_APPROVAL;
    access.roles = Set.of(HbsRoles.CLI_SMP_ADMIN);
    access.client = "00012345";
    assertThatThrownBy(() -> service.approvePlan(body("planId", "500"))).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.rejectPlan(body("planId", "500"))).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void rejectAndDelete() {
    assertThatThrownBy(() -> service.rejectPlan(body("planId", "500")))
        .isInstanceOf(WorkflowValidationException.class);
    service.deletePlan(body("planId", "500"));
    assertThat(only("HBS_STORE_SAMPLE_PLAN").arg("i_sample_plan_status_code")).isEqualTo("DEL");
    api.calls.clear();
    reads.plans.get(500L).samplePlanStatusCode = SamplePlanRow.AWAITING_APPROVAL;
    service.rejectPlan(body("planId", "500"));
    assertThat(only("HBS_STORE_SAMPLE_PLAN").arg("i_sample_plan_status_code")).isEqualTo("REJ");
  }

  @Test
  void storingADefaultRatioPlanZeroesItsPrecisions() {
    reads.plans.get(500L).ratioTypeCode = "D";
    service.deletePlan(body("planId", "500"));
    assertThat(only("HBS_STORE_SAMPLE_PLAN").arg("i_optimum_precision_calc")).isEqualTo(BigDecimal.valueOf(0.0));
  }

  // ── Copy / Create from actuals ──────────────────────────────────

  @Test
  void copyCopiesStrataAndDefaultRatios() {
    reads.compositions.put(600L, List.of(composition(600L, "FI", "J", "1.2")));
    reads.documents.put(FakeSamplingReads.key("1234", "01", 2026L), new SamplingReads.DocumentCounts(0, 0, 1, 0));
    Map<String, Object> b = newPlanBody();
    b.put("sourcePlanId", "500");
    b.put("deleteUnusedStrata", "Y");
    b.put("usePlanEffectiveDate", "Y");
    service.copyPlan(b);
    assertThat(api.names()).containsExactly("HBS_CREATE_POPULATION", "HBS_CREATE_SAMPLE_PLAN",
        "HBS_CREATE_STRATUM_PLAN", "HBS_CREATE_STRATUM_PLN_COMPSTN");
    var plan = api.calls.get(1);
    assertThat(plan.arg("i_precision_limit")).isEqualTo(BigDecimal.valueOf(1.0));
    assertThat(plan.arg("i_optimum_precision_calc")).isEqualTo(BigDecimal.valueOf(0.0));
    var sp = api.calls.get(2);
    assertThat(sp.arg("i_stratum_plan_id")).isEqualTo(BigDecimal.valueOf(1002));
    assertThat(sp.arg("i_plan_id")).isEqualTo(BigDecimal.valueOf(1001));
    assertThat(sp.arg("i_stratum_number")).isEqualTo("01");
    assertThat(sp.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2027-01-01 00:00:00"));
    assertThat(sp.arg("i_expiry_date")).isEqualTo(Timestamp.valueOf("2026-12-31 00:00:00"));
    var c = api.calls.get(3);
    assertThat(c.arg("i_stratum_plan_id")).isEqualTo(BigDecimal.valueOf(1002));
    assertThat(c.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
  }

  @Test
  void copyChecksTheTargetPopulationsRatioType() {
    SamplePlanRow other = plan(401L, SamplePlanRow.ACTIVE, "Z9", "Y");
    other.populationNumber = "4321";
    other.samplingYear = 2027L;
    other.ratioTypeCode = "D";
    reads.plans.put(401L, other);
    Map<String, Object> b = newPlanBody();
    b.put("sourcePlanId", "500");
    assertThatThrownBy(() -> service.copyPlan(b))
        .satisfies(t -> assertThat(messages(t)).contains(SamplingMessages.RATIO_TYPE_CHANGE));
  }

  @Test
  void createFromActualsStartsFromTheSampleStatistics() {
    SamplePlanRow source = reads.plans.get(500L);
    source.samplePlanStatusCode = SamplePlanRow.ACTIVE;
    Map<String, Object> s01 = stratumRow("01", 600L);
    s01.put("sample_loads", BigDecimal.valueOf(5));
    s01.put("sample_load_size", new BigDecimal("28.5"));
    s01.put("sample_standard_deviation", new BigDecimal("3.3"));
    reads.strata.put(FakeSamplingReads.key("1234", 2026L), List.of(s01, stratumRow("02", 601L)));
    Map<String, Object> ratio = new HashMap<>();
    ratio.put("scale_species_code", "HE");
    ratio.put("scale_grade_code", "U");
    ratio.put("scale_product_code", "SL");
    ratio.put("ratio", new BigDecimal("1.55"));
    reads.ratios.put(FakeSamplingReads.key("1234", "01", 2026L), List.of(ratio));
    reads.compositions.put(601L, List.of(composition(601L, "FI", "J", "1.2")));
    Map<String, Object> b = newPlanBody();
    b.put("sourcePlanId", "500");
    service.createPlanFromActuals(b);
    assertThat(api.names()).containsExactly("HBS_CREATE_POPULATION", "HBS_CREATE_SAMPLE_PLAN",
        "HBS_CREATE_STRATUM_PLAN", "HBS_CREATE_STRATUM_PLN_COMPSTN", "HBS_CREATE_STRATUM_PLAN",
        "HBS_CREATE_STRATUM_PLN_COMPSTN");
    var pop = api.calls.get(0);
    assertThat(pop.arg("i_org_unit_no")).isEqualTo(BigDecimal.valueOf(10));
    var sp1 = api.calls.get(2);
    assertThat(sp1.arg("i_estimated_load_size")).isEqualTo(BigDecimal.valueOf(28.5));
    assertThat(sp1.arg("i_estimated_standard_deviation")).isEqualTo(BigDecimal.valueOf(3.3));
    assertThat(sp1.arg("i_estimated_loads")).isEqualTo(BigDecimal.valueOf(10));
    assertThat(sp1.arg("i_estimated_volume")).isEqualTo(BigDecimal.valueOf(285.0));
    assertThat(sp1.arg("i_target_sample_calc")).isEqualTo(BigDecimal.ZERO);
    assertThat(sp1.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2027-01-01 00:00:00"));
    assertThat(sp1.arg("i_expiry_date")).isEqualTo(Timestamp.valueOf("2027-12-31 00:00:00"));
    var c1 = api.calls.get(3);
    assertThat(c1.arg("i_scale_species_code")).isEqualTo("HE");
    assertThat(c1.arg("i_default_ratio")).isEqualTo(new BigDecimal("1.55"));
    var c2 = api.calls.get(5);
    assertThat(c2.arg("i_scale_species_code")).isEqualTo("FI");
    assertThat(c2.arg("i_entry_userid")).isEqualTo("HBSBATCH");
  }

  @Test
  void createFromActualsNeedsAnActivePlan() {
    Map<String, Object> b = newPlanBody();
    b.put("sourcePlanId", "500");
    assertThatThrownBy(() -> service.createPlanFromActuals(b)).isInstanceOf(WorkflowValidationException.class);
  }

  // ── Stratum plans ───────────────────────────────────────────────

  Map<String, Object> stratumBody() {
    return body("planId", "500", "stratumNumber", "03", "stratumName", "Cedar", "clientNumber", "00012345",
        "clientLocnCode", "00", "effectiveDate", "2026-01-01", "expiryDate", "2027-01-31",
        "scaleProductCode", "SL");
  }

  @Test
  void addStratumWidensThePlanAndClearsItsPrecision() {
    service.createStratumPlan(stratumBody());
    assertThat(api.names()).containsExactly("HBS_STORE_SAMPLE_PLAN", "HBS_CREATE_STRATUM_PLAN");
    var p = api.calls.get(0);
    assertThat(p.arg("i_expiry_date")).isEqualTo(Timestamp.valueOf("2027-01-31 00:00:00"));
    assertThat(p.arg("i_optimum_precision_calc")).isEqualTo(BigDecimal.valueOf(0.0));
    var sp = api.calls.get(1);
    assertThat(sp.arg("i_grade_schedule_code")).isEqualTo("I");   // interior region default
    assertThat(sp.arg("i_estimated_volume")).isEqualTo(BigDecimal.valueOf(0.0));
    assertThat(sp.arg("i_accepted_stratum_plan_ind")).isEqualTo("N");
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "stratumNumber|01|Stratum Number already exists in plan.",
      "stratumNumber|0|Stratum Number cannot be less than 1.",
      "stratumNumber|x|Stratum Number must be numeric.",
      "stratumName||Stratum Name is required.",
      "effectiveDate|2026-01-02|Stratum 03 Effective Date must be the first day of a month",
      "expiryDate|2026-12-30|Stratum 03 Expiry Date must be the last day of a month",
      "effectiveDate|2027-03-01|Stratum 03 Effective Date must be less than Stratum Expiry Date",
  })
  void stratumFieldRules(String field, String value, String message) {
    Map<String, Object> b = stratumBody();
    b.put(field, value == null ? "" : value);
    assertThatThrownBy(() -> service.createStratumPlan(b))
        .satisfies(t -> assertThat(messages(t)).contains(message));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void editStratumRechecksSchedulesAndRecreatesTheRatios() {
    reads.compositions.put(600L, List.of(composition(600L, "FI", "J", "1.2")));
    reads.spg.put(FakeSamplingReads.key("FI", "LG", "J"),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
    reads.grades.put(FakeSamplingReads.key("J", "I"),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
    Map<String, Object> b = body("stratumPlanId", "600", "updateTimestamp", "2026-09-02T08:00:00",
        "stratumNumber", "01", "stratumName", "Fir", "clientNumber", "00012345", "clientLocnCode", "00",
        "effectiveDate", "2026-01-01", "expiryDate", "2026-12-31", "scaleProductCode", "LG",
        "gradeScheduleCode", "I");
    service.updateStratumPlan(b);
    assertThat(api.names()).containsExactly("HBS_STORE_SAMPLE_PLAN", "HBS_STORE_STRATUM_PLAN",
        "HBS_REMOVE_STRATUM_PLN_COMPSTN", "HBS_CREATE_STRATUM_PLN_COMPSTN");
    assertThat(api.calls.get(1).arg("i_product_schedule_code")).isEqualTo("LG");
    assertThat(api.calls.get(2).arg("i_scale_product_code")).isEqualTo("SL");
    var c = api.calls.get(3);
    assertThat(c.arg("i_scale_product_code")).isEqualTo("LG");
    assertThat(c.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
    assertThat(c.arg("i_update_userid")).isEqualTo("HBSBATCH");
  }

  @Test
  void editStratumRefusesScheduleChangesOfAnApprovedStratumAndInvalidRatios() {
    reads.plans.put(400L, plan(400L, SamplePlanRow.ACTIVE, "A0", "Y"));
    reads.stratumPlans.put(450L, stratum(450L, 400L, "01", 1000, 100, 2.0));
    reads.compositions.put(600L, List.of(composition(600L, "FI", "J", "1.2")));
    Map<String, Object> b = body("stratumPlanId", "600", "stratumNumber", "01", "stratumName", "Fir",
        "clientNumber", "00012345", "clientLocnCode", "00", "effectiveDate", "2026-01-01",
        "expiryDate", "2026-12-31", "scaleProductCode", "LG", "gradeScheduleCode", "C");
    assertThatThrownBy(() -> service.updateStratumPlan(b))
        .satisfies(t -> assertThat(messages(t)).contains(SamplingMessages.PRODUCT_SCHEDULE_CHANGE,
            SamplingMessages.GRADE_SCHEDULE_CHANGE, SamplingMessages.SPECIES_GRADE_INVALID));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void editStratumMustStayInThePlanPeriod() {
    Map<String, Object> b = body("stratumPlanId", "600", "stratumNumber", "01", "stratumName", "Fir",
        "clientNumber", "00012345", "clientLocnCode", "00", "effectiveDate", "2025-12-01",
        "expiryDate", "2026-12-31", "scaleProductCode", "SL");
    assertThatThrownBy(() -> service.updateStratumPlan(b))
        .satisfies(t -> assertThat(messages(t))
            .contains("Stratum 01 Effective Date cannot be less than Population Effective Date"));
  }

  @Test
  void deleteStratumIsRefusedWhileDocumentsUseIt() {
    reads.documents.put(FakeSamplingReads.key("1234", "02", 2026L), new SamplingReads.DocumentCounts(2, 1, 0, 3));
    assertThatThrownBy(() -> service.deleteStratumPlan(body("stratumPlanId", "601")))
        .satisfies(t -> assertThat(messages(t)).containsExactly(
            "1 Sample Log Tallies prevent the deletion of Stratum 02",
            "2 Sample Scale Summaries prevent the deletion of Stratum 02",
            "3 Weigh Slips prevent the deletion of Stratum 02"));
    reads.documents.clear();
    reads.compositions.put(601L, List.of(composition(601L, "FI", "J", "1.2")));
    service.deleteStratumPlan(body("stratumPlanId", "601"));
    assertThat(api.names()).containsExactly("HBS_STORE_SAMPLE_PLAN", "HBS_REMOVE_STRATUM_PLN_COMPSTN",
        "HBS_REMOVE_STRATUM_PLAN");
  }

  // ── Default ratios (P885) ───────────────────────────────────────

  void validSpg(String species, String grade) {
    reads.spg.put(FakeSamplingReads.key(species, "SL", grade),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
    reads.grades.put(FakeSamplingReads.key(grade, "I"),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
  }

  @Test
  void addDefaultRatio() {
    validSpg("HE", "U");
    service.createComposition(body("stratumPlanId", "600", "scaleSpeciesCode", "he", "scaleGradeCode", "u",
        "defaultRatio", "1.23456"));
    var c = only("HBS_CREATE_STRATUM_PLN_COMPSTN");
    assertThat(c.arg("i_scale_species_code")).isEqualTo("HE");
    assertThat(c.arg("i_scale_grade_code")).isEqualTo("U");
    assertThat(c.arg("i_scale_product_code")).isEqualTo("SL");
    assertThat(c.arg("i_default_ratio")).isEqualTo(BigDecimal.valueOf(1.23456));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "HE|U|0|'Composition Ratio must be greater than zero m3/tonne. '",
      "HE|U|10|Composition Ratio must be less than 10 m3/tonne.",
      "HE|U|1.123456|Ratio must be a number and have 5 or less decimal places.",
      "HE|U|x|Default Ratio must be a numeric value between -10 and 10",
      "HE|U||Default Ratio is required.",
      "FI|J|1|The stratum plan composition you are trying to insert/update already exists.",
      "CE|Z|1|Species, product, and grade combination is invalid.",
      "HE|U|8.9|Total Ratio must be less than 10 m3/tonne.",
  })
  void defaultRatioRules(String species, String grade, String ratio, String message) {
    validSpg("HE", "U");
    validSpg("FI", "J");
    reads.compositions.put(600L, List.of(composition(600L, "FI", "J", "1.2")));
    assertThatThrownBy(() -> service.createComposition(body("stratumPlanId", "600", "scaleSpeciesCode", species,
        "scaleGradeCode", grade, "defaultRatio", ratio == null ? "" : ratio)))
        .satisfies(t -> assertThat(messages(t)).contains(message));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void defaultRatioGradeMustBelongToTheStratumSchedule() {
    reads.spg.put(FakeSamplingReads.key("HE", "SL", "U"),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
    assertThatThrownBy(() -> service.createComposition(body("stratumPlanId", "600", "scaleSpeciesCode", "HE",
        "scaleGradeCode", "U", "defaultRatio", "1")))
        .satisfies(t -> assertThat(messages(t)).containsExactly(SamplingMessages.USE_INTERIOR_GRADE));
  }

  @Test
  void changeAndRemoveDefaultRatio() {
    validSpg("FI", "J");
    validSpg("HE", "U");
    reads.compositions.put(600L, new ArrayList<>(List.of(composition(600L, "FI", "J", "1.2"))));
    service.updateComposition(body("stratumPlanId", "600", "originalScaleSpeciesCode", "FI",
        "originalScaleGradeCode", "J", "scaleSpeciesCode", "HE", "scaleGradeCode", "U", "defaultRatio", "2.5",
        "updateTimestamp", "2025-02-01T08:00:00"));
    assertThat(api.names()).containsExactly("HBS_REMOVE_STRATUM_PLN_COMPSTN", "HBS_CREATE_STRATUM_PLN_COMPSTN");
    var c = api.calls.get(1);
    assertThat(c.arg("i_scale_species_code")).isEqualTo("HE");
    assertThat(c.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
    assertThat(c.arg("i_update_userid")).isEqualTo("HBSBATCH");
    api.calls.clear();
    service.removeComposition(body("stratumPlanId", "600", "scaleSpeciesCode", "FI", "scaleGradeCode", "J"));
    assertThat(only("HBS_REMOVE_STRATUM_PLN_COMPSTN").arg("i_scale_grade_code")).isEqualTo("J");
  }

  @Test
  void aBlankGradeIsStoredAsASpace() {
    reads.spg.put(FakeSamplingReads.key("HE", "SL", " "),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
    reads.grades.put(FakeSamplingReads.key(" ", "I"),
        new SamplingReads.Dates(LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 23, 59, 59)));
    service.createComposition(body("stratumPlanId", "600", "scaleSpeciesCode", "HE", "defaultRatio", "1"));
    assertThat(only("HBS_CREATE_STRATUM_PLN_COMPSTN").arg("i_scale_grade_code")).isEqualTo(" ");
  }
}
