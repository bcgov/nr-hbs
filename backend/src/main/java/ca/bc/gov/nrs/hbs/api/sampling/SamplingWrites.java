package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The Stratum Planner writes, each one legacy table-API proc
 * ({@code SamplePlannerBean} / {@code SrtsManagerBean} entity creates,
 * stores and removes) — HBS_CREATE_/HBS_STORE_/HBS_REMOVE_ SAMPLE_PLAN,
 * STRATUM_PLAN, STRATUM_PLN_COMPSTN, POPULATION and STRATUM.
 */
@Component
public class SamplingWrites {

  public static final String SAMPLE_PLAN_SEQ = "SAMPLE_PLAN_SEQ";
  public static final String STRATUM_PLAN_SEQ = "STRATUM_PLAN_SEQ";

  private final TableApi api;

  public SamplingWrites(TableApi api) {
    this.api = api;
  }

  long nextPlanId() {
    return api.nextId(SAMPLE_PLAN_SEQ);
  }

  long nextStratumPlanId() {
    return api.nextId(STRATUM_PLAN_SEQ);
  }

  void createPlan(SamplePlanRow p) {
    api.call("HBS_CREATE_SAMPLE_PLAN", p.toArgs());
  }

  void storePlan(SamplePlanRow p) {
    api.call("HBS_STORE_SAMPLE_PLAN", p.toArgs());
  }

  void createStratumPlan(StratumPlanRow s) {
    api.call("HBS_CREATE_STRATUM_PLAN", s.toArgs());
  }

  void storeStratumPlan(StratumPlanRow s) {
    api.call("HBS_STORE_STRATUM_PLAN", s.toArgs());
  }

  /** {@code SamplePlannerBean.removeStratumPlan}: its compositions first, then the row. */
  void removeStratumPlan(long stratumPlanId, Iterable<Map<String, Object>> compositions) {
    for (Map<String, Object> c : compositions) {
      removeComposition(c);
    }
    api.call("HBS_REMOVE_STRATUM_PLAN", ProcArgs.of().put("i_stratum_plan_id", stratumPlanId));
  }

  void createComposition(String species, String grade, long stratumPlanId, String product, Object ratio,
      String entryUser, Object entryTimestamp, String updateUser, Object updateTimestamp) {
    api.call("HBS_CREATE_STRATUM_PLN_COMPSTN", ProcArgs.of()
        .put("i_scale_species_code", species)
        .put("i_scale_grade_code", grade)
        .put("i_stratum_plan_id", stratumPlanId)
        .put("i_scale_product_code", product)
        .put("i_default_ratio", ratio)
        .put("i_entry_userid", entryUser)
        .put("i_entry_timestamp", entryTimestamp)
        .put("i_update_userid", updateUser)
        .put("i_update_timestamp", updateTimestamp));
  }

  void removeComposition(Map<String, Object> c) {
    api.call("HBS_REMOVE_STRATUM_PLN_COMPSTN", ProcArgs.of()
        .put("i_scale_species_code", c.get("scale_species_code"))
        .put("i_scale_grade_code", c.get("scale_grade_code"))
        .put("i_stratum_plan_id", c.get("stratum_plan_id"))
        .put("i_scale_product_code", c.get("scale_product_code")));
  }

  /**
   * New POPULATION with zero statistics ({@code P891Action.createPopulation},
   * {@code P896Action.performAddPopulation}, {@code P897Action.performAddPopulation}).
   */
  void createPopulation(String pop, long year, Long orgUnitNo, String client, String locn, LocalDateTime effective,
      LocalDateTime expiry, String user, LocalDateTime now, String ratioTypeCode) {
    api.call("HBS_CREATE_POPULATION", ProcArgs.of()
        .put("i_population_number", pop)
        .put("i_sampling_year", year)
        .put("i_org_unit_no", orgUnitNo)
        .put("i_client_number", client)
        .put("i_client_locn_code", locn)
        .put("i_stratum_count", 0)
        .put("i_estimated_total_volume", 0.0)
        .put("i_estimated_total_loads", 0)
        .put("i_estimated_load_size", 0.0)
        .put("i_billed_loads", 0)
        .put("i_billed_volume", 0.0)
        .put("i_billed_weight", 0)
        .put("i_sample_loads", 0)
        .put("i_sample_load_size", 0.0)
        .put("i_sample_volume", 0.0)
        .put("i_sample_weight", 0)
        .put("i_ratio", 0.0)
        .put("i_pop_variance", 0.0)
        .put("i_degrees_of_freedom", 0)
        .put("i_precision", 0.0)
        .put("i_effective_date", effective)
        .put("i_expiry_date", expiry)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", null)
        .put("i_update_timestamp", null)
        .put("i_ratio_type_code", ratioTypeCode));
  }

  /** HBS_STORE_POPULATION from the current row with the given column changes. */
  void storePopulation(Map<String, Object> row, Map<String, Object> changes) {
    ProcArgs args = api.argsFromRow("HBS_STORE_POPULATION", row);
    changes.forEach((col, v) -> args.put("i_" + col, v));
    api.call("HBS_STORE_POPULATION", args);
  }

  /** New STRATUM with zero statistics ({@code P891Action.createStratum} / {@code matchStratumNumbers}). */
  void createStratum(String pop, long year, StratumPlanRow sp, String user, LocalDateTime now) {
    api.call("HBS_CREATE_STRATUM", ProcArgs.of()
        .put("i_population_number", pop)
        .put("i_sampling_year", year)
        .put("i_stratum_number", sp.stratumNumber)
        .put("i_stratum_plan_id", sp.stratumPlanId)
        .put("i_client_number", sp.clientNumber)
        .put("i_client_locn_code", sp.clientLocnCode)
        .put("i_stratum_name", sp.stratumName)
        .put("i_total_transactions", 0)
        .put("i_sample_loads", 0)
        .put("i_sampling_frequency", 0)
        .put("i_sample_load_size", 0.0)
        .put("i_sample_standard_deviation", 0.0)
        .put("i_sample_volume", 0.0)
        .put("i_sample_weight", 0)
        .put("i_average_sample_weight", 0)
        .put("i_billed_loads", 0)
        .put("i_billed_volume", 0.0)
        .put("i_billed_weight", 0)
        .put("i_average_total_weight", 0)
        .put("i_ratio", 0.0)
        .put("i_stratum_variance", 0.0)
        .put("i_degrees_of_freedom", 0)
        .put("i_precision", 0.0)
        .put("i_effective_date", sp.effectiveDate)
        .put("i_expiry_date", sp.expiryDate)
        .put("i_grade_schedule_code", sp.gradeScheduleCode)
        .put("i_statement_number", null)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", user)
        .put("i_update_timestamp", now)
        .put("i_product_schedule_code", sp.scaleProductCode));
  }

  /**
   * HBS_STORE_STRATUM from the current row with the given column changes. The
   * proc names two parameters differently from their columns
   * (i_statement_number = RATIO_STATEMENT_NUMBER, i_product_schedule_code =
   * SCALE_PRODUCT_CODE); both are carried over from the row so legacy B2024's
   * ratio statement link is not lost.
   */
  void storeStratum(Map<String, Object> row, Map<String, Object> changes) {
    ProcArgs args = api.argsFromRow("HBS_STORE_STRATUM", row);
    args.put("i_statement_number", row.get("ratio_statement_number"));
    args.put("i_product_schedule_code", row.get("scale_product_code"));
    changes.forEach((col, v) -> args.put("scale_product_code".equals(col) ? "i_product_schedule_code" : "i_" + col, v));
    api.call("HBS_STORE_STRATUM", args);
  }

  void removeStratum(String pop, long year, String stratumNumber) {
    api.call("HBS_REMOVE_STRATUM", ProcArgs.of()
        .put("i_population_number", pop)
        .put("i_sampling_year", year)
        .put("i_stratum_number", stratumNumber));
  }
}
