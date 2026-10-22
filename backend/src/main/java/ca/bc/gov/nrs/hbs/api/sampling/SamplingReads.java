package ca.bc.gov.nrs.hbs.api.sampling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The reads the Stratum Planner workflows make (plain SELECTs, bound
 * parameters). An interface so the workflow tests can run against an
 * in-memory double; {@link JdbcSamplingReads} is the Oracle implementation.
 */
public interface SamplingReads {

  /** Effective / expiry dates of a code row (start / end of day as the legacy DAOs set them). */
  record Dates(LocalDateTime effective, LocalDateTime expiry) {
    /** {@code DateRange.insideRange(effective, expiry, date)} — inclusive. */
    public boolean contains(LocalDateTime date) {
      return date != null && !date.isBefore(effective) && !date.isAfter(expiry);
    }
  }

  /** Counts per document table (sample scale summaries, sample log tallies, weight scale summaries, weigh slips). */
  record DocumentCounts(long sampleScale, long sampleLog, long weightScale, long weighSlips) {
    public boolean any() {
      return sampleScale + sampleLog + weightScale + weighSlips > 0;
    }
  }

  /** One row of the HBS_STRANDED_* multi-stratum cursor: document type (SSS/SLT/WSS/WS), stratum, count. */
  record Stranded(String type, String stratumNumber, long count) {}

  /** ORG_UNIT lookup ({@code OrgUnitHelper.findOrgUnit}). */
  record OrgUnit(long orgUnitNo, String orgUnitCode, String rollupRegionCode) {}

  SamplePlanRow plan(long planId);

  /** Stratum plans of a plan ordered by STRATUM_NUMBER ({@code findStratumPlansBySamplePlanId(id, "STRATUM_NUMBER")}). */
  List<StratumPlanRow> stratumPlans(long planId);

  StratumPlanRow stratumPlan(long stratumPlanId);

  /** STRATUM_PLAN_COMPOSITION rows ({@code SELECT *}, lower-case keys). */
  List<Map<String, Object>> compositions(long stratumPlanId);

  /** POPULATION row ({@code SELECT *}, lower-case keys) or null. */
  Map<String, Object> population(String populationNumber, long samplingYear);

  /**
   * {@code SamplePlanner.findSamplePlansByCriteria(status, pop, year, abbreviation, name, accepted, null)}:
   * null / blank criteria are ignored, as {@code Query.appendWhereClause} did.
   */
  List<SamplePlanRow> plans(String status, String populationNumber, long samplingYear, String planAbbreviation,
      Boolean acceptedPlan);

  /** STRATUM rows of a population / year ({@code SELECT *}, lower-case keys) ordered by STRATUM_NUMBER. */
  List<Map<String, Object>> strata(String populationNumber, long samplingYear);

  /** {@code UssHelper2.findDistrictDataDomainByPrimaryKey(userId, orgUnitNo) != null}. */
  boolean userHasDistrict(String userId, long orgUnitNo);

  OrgUnit orgUnit(long orgUnitNo);

  /** CLIENT_LOCATION exists ({@code validateClientNumberAndClientLocation}). */
  boolean clientLocationExists(String clientNumber, String clientLocnCode);

  /** SPECIES_PRODUCT_GRADE_XREF row ({@code ScaledLogXrefValidator.getSpeciesProductGrade}) or null. */
  Dates speciesProductGrade(String species, String product, String grade);

  /** COAST_ / INTERIOR_SCALE_GRADE_CODE row ({@code SmpHelper.getScaleGradeInfo}) or null. */
  Dates scheduleGrade(String grade, String gradeSchedule);

  /** HBS_UNUSED_STRATA_COUNT with PSR_COUNT = 'N': every document of the stratum, any state. */
  DocumentCounts documentCounts(String populationNumber, String stratumNumber, long samplingYear);

  /** HBS_UNUSED_STRATA_COUNT with PSR_COUNT = 'Y': true when any document references the stratum. */
  default boolean stratumUsed(String populationNumber, String stratumNumber, long samplingYear) {
    return documentCounts(populationNumber, stratumNumber, samplingYear).any();
  }

  /** HBS_STRANDED_* single-stratum mode: open documents outside [effective, expiry]. */
  DocumentCounts strandedSingle(String populationNumber, String stratumNumber, long samplingYear,
      LocalDate effective, LocalDate expiry);

  /** HBS_STRANDED_* multi-stratum (cursor) mode for every stratum plan of the plan(s) with this abbreviation. */
  List<Stranded> strandedMultiple(String planAbbreviation, String populationNumber, long samplingYear);

  /** {@code RatioHistoryQuery.findLatestRatios}: RATIO_HISTORY rows at the latest RATIO_TIMESTAMP. */
  List<Map<String, Object>> latestRatios(String populationNumber, String stratumNumber, long samplingYear);
}
