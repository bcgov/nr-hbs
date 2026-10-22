package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import java.time.LocalDateTime;

/**
 * One SAMPLE_PLAN row, mutable like the legacy {@code $E/smp/SamplePlan}
 * value object the P891/P896/P897 actions edited before storing it. Every
 * column is carried so a STORE (which overwrites every column) re-sends what
 * it did not change.
 */
public class SamplePlanRow {

  /** {@code SamplePlan} status / type constants. */
  public static final String ACTIVE = "ACT";
  public static final String IN_PROGRESS = "PRO";
  public static final String AWAITING_APPROVAL = "AWP";
  public static final String REJECTED = "REJ";
  public static final String SUPERCEDED = "SPR";
  public static final String DELETED = "DEL";
  public static final String SAMPLES_CONSTRAINT = "S";
  public static final String PRECISION_CONSTRAINT = "P";
  public static final String VOLUME_LOADS_EST = "VL";
  public static final String VOLUME_SIZE_EST = "VS";
  public static final String LOADS_SIZE_EST = "LS";
  /** {@code SmpConstants}: sample-based and default (no sampling) ratio types. */
  public static final String SAMPLE_RATIO_TYPE = "S";
  public static final String DEFAULT_RATIO_TYPE = "D";

  public Long planId;
  public String samplePlanStatusCode;
  public String populationNumber;
  public Long samplingYear;
  public String planAbreviation;
  public String planName;
  public String ratioTypeCode;
  public Double precisionLimit;
  public Long sampleFloor;
  public String currentConstraintType;
  public String currentEstimateType;
  public String acceptedPlanInd;
  public Double optimumPrecisionCalc;
  public Double targetPrecisionCalc;
  public Long orgUnitNo;
  public String clientNumber;
  public String clientLocnCode;
  public LocalDateTime effectiveDate;
  public LocalDateTime expiryDate;
  public String entryUserid;
  public LocalDateTime entryTimestamp;
  public String updateUserid;
  public LocalDateTime updateTimestamp;

  public boolean isDefaultRatio() {
    return DEFAULT_RATIO_TYPE.equals(ratioTypeCode);
  }

  public boolean isSampleRatio() {
    return SAMPLE_RATIO_TYPE.equals(ratioTypeCode);
  }

  /** Arguments for HBS_CREATE_SAMPLE_PLAN / HBS_STORE_SAMPLE_PLAN (same parameter list). */
  public ProcArgs toArgs() {
    return ProcArgs.of()
        .put("i_plan_id", planId)
        .put("i_sample_plan_status_code", samplePlanStatusCode)
        .put("i_population_number", populationNumber)
        .put("i_sampling_year", samplingYear)
        .put("i_plan_abreviation", planAbreviation)
        .put("i_plan_name", planName)
        .put("i_precision_limit", precisionLimit)
        .put("i_sample_floor", sampleFloor)
        .put("i_current_constraint_type", currentConstraintType)
        .put("i_current_estimate_type", currentEstimateType)
        .put("i_accepted_plan_ind", acceptedPlanInd)
        .put("i_optimum_precision_calc", optimumPrecisionCalc)
        .put("i_target_precision_calc", targetPrecisionCalc)
        .put("i_org_unit_no", orgUnitNo)
        .put("i_client_number", clientNumber)
        .put("i_client_locn_code", clientLocnCode)
        .put("i_effective_date", effectiveDate)
        .put("i_expiry_date", expiryDate)
        .put("i_entry_userid", entryUserid)
        .put("i_entry_timestamp", entryTimestamp)
        .put("i_update_userid", updateUserid)
        .put("i_update_timestamp", updateTimestamp)
        .put("i_ratio_type_code", ratioTypeCode);
  }

  public SamplePlanRow copy() {
    SamplePlanRow c = new SamplePlanRow();
    c.planId = planId;
    c.samplePlanStatusCode = samplePlanStatusCode;
    c.populationNumber = populationNumber;
    c.samplingYear = samplingYear;
    c.planAbreviation = planAbreviation;
    c.planName = planName;
    c.ratioTypeCode = ratioTypeCode;
    c.precisionLimit = precisionLimit;
    c.sampleFloor = sampleFloor;
    c.currentConstraintType = currentConstraintType;
    c.currentEstimateType = currentEstimateType;
    c.acceptedPlanInd = acceptedPlanInd;
    c.optimumPrecisionCalc = optimumPrecisionCalc;
    c.targetPrecisionCalc = targetPrecisionCalc;
    c.orgUnitNo = orgUnitNo;
    c.clientNumber = clientNumber;
    c.clientLocnCode = clientLocnCode;
    c.effectiveDate = effectiveDate;
    c.expiryDate = expiryDate;
    c.entryUserid = entryUserid;
    c.entryTimestamp = entryTimestamp;
    c.updateUserid = updateUserid;
    c.updateTimestamp = updateTimestamp;
    return c;
  }
}
