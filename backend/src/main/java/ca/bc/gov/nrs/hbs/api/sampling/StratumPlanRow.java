package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import java.time.LocalDateTime;

/**
 * One STRATUM_PLAN row, mutable like the legacy {@code $E/smp/StratumPlan}
 * value object: the Save &amp; Calculate code ({@code P891Action.doCalculations})
 * updates the rows of a plan in place, in stratum-number order, and later rows
 * see the values already recalculated for earlier ones — the port keeps that.
 */
public class StratumPlanRow {

  public Long stratumPlanId;
  public Long planId;
  public String stratumNumber;
  public String stratumName;
  public String clientNumber;
  public String clientLocnCode;
  public Long sampleCountConstraint;
  public Double precisionConstraint;
  public Double estimatedVolume;
  public Long estimatedLoads;
  public Double estimatedLoadSize;
  public Double estimatedStandardDeviation;
  public Long optimumSampleCalc;
  public Double optimumPrecisionCalc;
  public Long optimumFrequencyCalc;
  public Long targetSampleCalc;
  public Double targetPrecisionCalc;
  public Long targetFrequencyCalc;
  public String acceptedStratumPlanInd;
  public LocalDateTime expiryDate;
  public LocalDateTime effectiveDate;
  public String stratumDesc;
  public String timberSourceDesc;
  public String speciesGradeProfileDesc;
  public String entryUserid;
  public LocalDateTime entryTimestamp;
  public String updateUserid;
  public LocalDateTime updateTimestamp;
  /** GRADE_SCHEDULE_CODE: C (coast) / I (interior) — the legacy "grade constraint". */
  public String gradeScheduleCode;
  /** SCALE_PRODUCT_CODE (product schedule; proc parameter i_product_schedule_code). */
  public String scaleProductCode;

  /** Arguments for HBS_CREATE_STRATUM_PLAN / HBS_STORE_STRATUM_PLAN (same parameter list). */
  public ProcArgs toArgs() {
    return ProcArgs.of()
        .put("i_stratum_plan_id", stratumPlanId)
        .put("i_plan_id", planId)
        .put("i_stratum_number", stratumNumber)
        .put("i_stratum_name", stratumName)
        .put("i_client_number", clientNumber)
        .put("i_client_locn_code", clientLocnCode)
        .put("i_sample_count_constraint", sampleCountConstraint)
        .put("i_precision_constraint", precisionConstraint)
        .put("i_estimated_volume", estimatedVolume)
        .put("i_estimated_loads", estimatedLoads)
        .put("i_estimated_load_size", estimatedLoadSize)
        .put("i_estimated_standard_deviation", estimatedStandardDeviation)
        .put("i_optimum_sample_calc", optimumSampleCalc)
        .put("i_optimum_precision_calc", optimumPrecisionCalc)
        .put("i_optimum_frequency_calc", optimumFrequencyCalc)
        .put("i_target_sample_calc", targetSampleCalc)
        .put("i_target_precision_calc", targetPrecisionCalc)
        .put("i_target_frequency_calc", targetFrequencyCalc)
        .put("i_accepted_stratum_plan_ind", acceptedStratumPlanInd)
        .put("i_expiry_date", expiryDate)
        .put("i_effective_date", effectiveDate)
        .put("i_stratum_desc", stratumDesc)
        .put("i_timber_source_desc", timberSourceDesc)
        .put("i_species_grade_profile_desc", speciesGradeProfileDesc)
        .put("i_entry_userid", entryUserid)
        .put("i_entry_timestamp", entryTimestamp)
        .put("i_update_userid", updateUserid)
        .put("i_update_timestamp", updateTimestamp)
        .put("i_grade_schedule_code", gradeScheduleCode)
        .put("i_product_schedule_code", scaleProductCode);
  }

  public StratumPlanRow copy() {
    StratumPlanRow c = new StratumPlanRow();
    c.stratumPlanId = stratumPlanId;
    c.planId = planId;
    c.stratumNumber = stratumNumber;
    c.stratumName = stratumName;
    c.clientNumber = clientNumber;
    c.clientLocnCode = clientLocnCode;
    c.sampleCountConstraint = sampleCountConstraint;
    c.precisionConstraint = precisionConstraint;
    c.estimatedVolume = estimatedVolume;
    c.estimatedLoads = estimatedLoads;
    c.estimatedLoadSize = estimatedLoadSize;
    c.estimatedStandardDeviation = estimatedStandardDeviation;
    c.optimumSampleCalc = optimumSampleCalc;
    c.optimumPrecisionCalc = optimumPrecisionCalc;
    c.optimumFrequencyCalc = optimumFrequencyCalc;
    c.targetSampleCalc = targetSampleCalc;
    c.targetPrecisionCalc = targetPrecisionCalc;
    c.targetFrequencyCalc = targetFrequencyCalc;
    c.acceptedStratumPlanInd = acceptedStratumPlanInd;
    c.expiryDate = expiryDate;
    c.effectiveDate = effectiveDate;
    c.stratumDesc = stratumDesc;
    c.timberSourceDesc = timberSourceDesc;
    c.speciesGradeProfileDesc = speciesGradeProfileDesc;
    c.entryUserid = entryUserid;
    c.entryTimestamp = entryTimestamp;
    c.updateUserid = updateUserid;
    c.updateTimestamp = updateTimestamp;
    c.gradeScheduleCode = gradeScheduleCode;
    c.scaleProductCode = scaleProductCode;
    return c;
  }
}
