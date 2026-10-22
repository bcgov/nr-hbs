package ca.bc.gov.nrs.hbs.api.sampling;

import static ca.bc.gov.nrs.hbs.api.sampling.SamplingMessages.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * The Struts form validation of the Stratum Planner forms ({@code
 * hbs-web/WEB-INF/validation.xml} P891Form / P886Form / P896Form / P897Form
 * and the {@code HBSValidator} rules they name), plus the checks the actions
 * ran themselves ({@code SamplePlanValidator}, {@code CalculateNewStrataDate},
 * {@code P886Action.validateForm}, {@code P885Action.validateForm}).
 * Field order and the "first failing rule per field" behaviour follow the
 * Struts validator.
 */
@Component
public class SamplingValidator {

  /** Which legacy form is validated (the rule sets differ slightly). */
  enum Form { P891_SAVE, P891_CALCULATE, P896_COPY, P897_CREATE }

  /** Coast regions ({@code presentation.Constants.COASTAL_REGION / SOUTH_COAST_REGION / WEST_COAST_REGION}). */
  static final List<String> COAST_REGIONS = List.of("RCO", "RSC", "RWC");
  static final String COASTAL_TYPE = "C";
  static final String INTERIOR_TYPE = "I";

  private final SamplingReads reads;
  private final SamplingAccess access;

  public SamplingValidator(SamplingReads reads, SamplingAccess access) {
    this.reads = reads;
    this.access = access;
  }

  /** Plan header values typed on P891 / P881 / P896 / P897. */
  static final class Header {
    String population;
    String year;
    String abbreviation;
    String planName;
    String ratioTypeCode;
    String precisionLimit;
    String sampleFloor;
    String clientNumber;
    String clientLocnCode;
    String orgUnitNo;
    LocalDate effective;
    LocalDate expiry;

    Long yearValue() {
      return year == null ? null : Long.valueOf(year);
    }

    Long orgUnitValue() {
      return orgUnitNo == null ? null : Long.valueOf(orgUnitNo);
    }
  }

  /**
   * The P891Form / P896Form / P897Form field rules.
   *
   * @param planId      the plan being edited (P891 calculate), for the abbreviation uniqueness
   * @param sourceRatio the copied plan's ratio type (P896 / P897 {@code ratioTypeChanged})
   * @param strata      the plan's stratum plans (P891 calculate: stranded returns, P881 strata dates)
   */
  void validateHeader(Header h, Form form, Long planId, String sourceRatio, List<StratumPlanRow> strata,
      Problems e) {
    boolean p891 = form == Form.P891_SAVE || form == Form.P891_CALCULATE;
    if (p891) {
      abbreviation(h, planId, strata, form, e);
      if (h.planName == null) e.add("planName", required(L_PLAN_NAME));
      ratioTypeAndLimits(h, form, e);
      population(h, null, e);
      year(h, e);
    } else {
      population(h, sourceRatio, e);
      year(h, e);
      abbreviation(h, null, null, form, e);
      if (h.planName == null) e.add("planName", required(L_PLAN_NAME));
    }
    client(h, e);
    district(h, form != Form.P897_CREATE, e);
    dates(h, form, strata, e);
  }

  private void abbreviation(Header h, Long planId, List<StratumPlanRow> strata, Form form, Problems e) {
    if (h.abbreviation == null) {
      e.add("planAbbreviation", required(L_ABBREVIATION));
      return;
    }
    // HBSValidator.validateUniquePlanAbbreviation: any plan (any status) of the population / year.
    if (h.population != null && h.year != null && isInteger(h.year)) {
      List<SamplePlanRow> same = reads.plans(null, h.population, Long.parseLong(h.year), h.abbreviation, null);
      boolean exists = same.size() > 1 || (same.size() == 1 && (planId == null || !planId.equals(same.get(0).planId)));
      if (exists) {
        e.add("planAbbreviation", PLAN_ABBREVIATION_DUPLICATE);
        return;
      }
    }
    // validateStrandedReturns (P891 View / Save & Calculate): each stratum's stored period.
    if (form == Form.P891_CALCULATE && strata != null) {
      for (StratumPlanRow sp : strata) {
        strandedSingle(h.population, sp.stratumNumber, Long.parseLong(h.year), day(sp.effectiveDate),
            day(sp.expiryDate), e);
      }
    }
  }

  private void ratioTypeAndLimits(Header h, Form form, Problems e) {
    // ratioTypeSelect (Save only): the population's Active plan fixes the ratio type.
    if (form == Form.P891_SAVE && h.population != null && h.year != null && isInteger(h.year)) {
      SamplePlanRow active = lastActive(h.population, Long.parseLong(h.year));
      if (active != null && !active.ratioTypeCode.equals(h.ratioTypeCode)) {
        e.add("ratioTypeCode", RATIO_TYPE_CHANGE);
      }
    }
    // requiredBasedOnRatioType: default-ratio plans skip the precision / floor rules.
    if (SamplePlanRow.DEFAULT_RATIO_TYPE.equals(h.ratioTypeCode)) {
      return;
    }
    if (h.precisionLimit == null) {
      e.add("precisionLimit", required(L_PRECISION_LIMIT));
    } else if (!isFloat(h.precisionLimit)) {
      e.add("precisionLimit", number(L_PRECISION_LIMIT));
    } else if (decimalPlaces(h.precisionLimit) > 2) {
      e.add("precisionLimit", decimals(L_PRECISION_LIMIT, 2));
    } else if (Float.parseFloat(h.precisionLimit) > 5.0f) {
      e.add("precisionLimit", greaterThan(L_PRECISION_LIMIT, "5.0%"));
    } else if (Float.parseFloat(h.precisionLimit) < 0.5f) {
      e.add("precisionLimit", lessThan(L_PRECISION_LIMIT, "0.5%"));
    }
    if (h.sampleFloor == null) {
      e.add("sampleFloor", required(L_SAMPLE_FLOOR));
    } else if (!isInteger(h.sampleFloor)) {
      e.add("sampleFloor", numeric(L_SAMPLE_FLOOR));
    } else if (Long.parseLong(h.sampleFloor) < 2) {
      e.add("sampleFloor", lessThan(L_SAMPLE_FLOOR, "2"));
    }
  }

  private void population(Header h, String sourceRatio, Problems e) {
    if (h.population == null) {
      e.add("population", required(L_POPULATION));
    } else if (!isInteger(h.population)) {
      e.add("population", numeric(L_POPULATION));
    } else if (h.population.length() < 4) {
      e.add("population", minLength(L_POPULATION, 4));
    } else if (sourceRatio != null && h.year != null && isInteger(h.year)) {
      // ratioTypeChanged (P896 / P897): HBSValidatorHelper.isRatioTypeChanged.
      SamplePlanRow active = lastActive(h.population, Long.parseLong(h.year));
      if (active != null && !active.ratioTypeCode.equals(sourceRatio)) {
        e.add("population", RATIO_TYPE_CHANGE);
      }
    }
  }

  private void year(Header h, Problems e) {
    if (h.year == null) {
      e.add("samplingYear", required(L_YEAR));
    } else if (!isInteger(h.year)) {
      e.add("samplingYear", numeric(L_YEAR));
    } else if (h.year.length() < 4) {
      e.add("samplingYear", minLength(L_YEAR, 4));
    } else {
      // HBSValidator.validateSamplingYear / SamplePlanValidator.validateSamplingDates.
      validateSamplingDates(Long.parseLong(h.year), h.effective, h.expiry, e);
    }
  }

  /** {@code SamplePlanValidator.validateSamplingDates}: the year lies within the plan's years. */
  void validateSamplingDates(long year, LocalDate effective, LocalDate expiry, Problems e) {
    if (effective != null && year < effective.getYear()) {
      e.add("samplingYear", SAMPLING_BEFORE_EFFECTIVE);
    }
    if (expiry != null && year > expiry.getYear()) {
      e.add("samplingYear", SAMPLING_AFTER_EXPIRY);
    }
  }

  void client(Header h, Problems e) {
    if (h.clientNumber == null) {
      e.add("clientNumber", required(L_CLIENT));
    } else if (h.clientLocnCode != null && !reads.clientLocationExists(h.clientNumber, h.clientLocnCode)) {
      e.add("clientNumber", CLIENT_LOCATION_INVALID);
    }
    if (h.clientNumber != null && h.clientLocnCode == null) {
      e.add("clientLocnCode", CLIENT_LOCATION_MISSING);
    }
  }

  private void district(Header h, boolean districtUser, Problems e) {
    if (h.orgUnitNo == null) {
      e.add("orgUnitNo", required(L_DISTRICT));
    } else if (!isInteger(h.orgUnitNo) || reads.orgUnit(Long.parseLong(h.orgUnitNo)) == null) {
      e.add("orgUnitNo", REGION_DISTRICT);
    } else if (districtUser && !access.isPlanAdmin(Long.parseLong(h.orgUnitNo), h.clientNumber)) {
      // HBSValidator.validateDistrictUser.
      e.add("orgUnitNo", DISTRICT_USER);
    }
  }

  private void dates(Header h, Form form, List<StratumPlanRow> strata, Problems e) {
    boolean effOk = true;
    if (h.effective == null) {
      e.add("effectiveDate", required(L_EFFECTIVE));
      effOk = false;
    }
    boolean expOk = true;
    if (h.expiry == null) {
      e.add("expiryDate", required(L_EXPIRY));
      expOk = false;
    }
    if (!effOk || !expOk) return;
    // HBSValidator.validateEffectExpDate (popEffectExpDate).
    int before = e.size();
    if (!h.effective.isBefore(h.expiry)) {
      e.add("effectiveDate", POP_EFFECT_LESS_EXP);
    }
    if (h.effective.getDayOfMonth() != 1) {
      e.add("effectiveDate", POP_EFFECT_FIRST_DAY);
    }
    boolean effFieldOk = e.size() == before;
    int beforeExp = e.size();
    if (h.expiry.getDayOfMonth() != h.expiry.lengthOfMonth()) {
      e.add("expiryDate", POP_EXPIRY_LAST_DAY);
    }
    boolean expFieldOk = e.size() == beforeExp;
    // strataEffectExpDate (P891 View / Save & Calculate): CalculateNewStrataDate.validatePopDate(…, "P881").
    if (form == Form.P891_CALCULATE && strata != null) {
      if (effFieldOk) {
        for (StratumPlanRow sp : strata) {
          LocalDate se = day(sp.effectiveDate);
          LocalDate sx = day(sp.expiryDate);
          if (se.isAfter(sx)) e.add("noWhere", strataEffectLessExp(sp.stratumNumber));
          if (se.getDayOfMonth() != 1) e.add("noWhere", strataEffectFirstDay(sp.stratumNumber));
          if (h.effective.isAfter(se)) e.add("effectiveDate", popStrataEffect(sp.stratumNumber));
        }
      }
      if (expFieldOk) {
        for (StratumPlanRow sp : strata) {
          LocalDate sx = day(sp.expiryDate);
          if (sx.getDayOfMonth() != sx.lengthOfMonth()) e.add("noWhere", strataExpiryLastDay(sp.stratumNumber));
          if (h.expiry.isBefore(sx)) e.add("expiryDate", popStrataExpiry(sp.stratumNumber));
        }
      }
    }
  }

  /**
   * P886Form rules + {@code CalculateNewStrataDate.validatePopDate(…, "P886")}.
   *
   * @param planFrom / planTo the plan's dates — given on edit only: on the add path the legacy form did not carry
   *                 them ({@code P886Form.initializeStratumForm}), so the plan-range rules did not run there
   * @param strandedCheck the stranded-return rule runs only where the legacy form carried the population
   *                 (edit; on add {@code txtPopulation} was empty and the procs counted nothing)
   */
  void validateStratumPlanForm(StratumPlanRow sp, String stratumNumberText, SamplePlanRow plan,
      LocalDate planFrom, LocalDate planTo, boolean strandedCheck, Problems e) {
    if (stratumNumberText == null) {
      e.add("stratumNumber", required(L_STRATUM_NUMBER));
    } else if (!isInteger(stratumNumberText)) {
      e.add("stratumNumber", numeric(L_STRATUM_NUMBER));
    } else if (Long.parseLong(stratumNumberText) < 1) {
      e.add("stratumNumber", lessThan(L_STRATUM_NUMBER, "1"));
    } else if (stratumNumberExists(plan.planId, sp.stratumPlanId, stratumNumberText)) {
      e.add("stratumNumber", STRATUM_NUMBER_DUPLICATE);
    } else if (strandedCheck && sp.effectiveDate != null && sp.expiryDate != null) {
      strandedSingle(plan.populationNumber, stratumNumberText, plan.samplingYear, day(sp.effectiveDate),
          day(sp.expiryDate), e);
    }
    if (sp.stratumName == null) e.add("stratumName", required(L_STRATUM_NAME));
    Header h = new Header();
    h.clientNumber = sp.clientNumber;
    h.clientLocnCode = sp.clientLocnCode;
    client(h, e);
    if (sp.effectiveDate == null) e.add("effectiveDate", required(L_EFFECTIVE));
    if (sp.expiryDate == null) e.add("expiryDate", required(L_EXPIRY));
    if (sp.effectiveDate == null || sp.expiryDate == null || stratumNumberText == null) return;
    LocalDate se = day(sp.effectiveDate);
    LocalDate sx = day(sp.expiryDate);
    String s = stratumNumberText;
    boolean effOk = true;
    if (se.isAfter(sx)) {
      e.add("effectiveDate", strataEffectLessExp(s));
      effOk = false;
    }
    if (se.getDayOfMonth() != 1) {
      e.add(effOk ? "effectiveDate" : "noWhere", strataEffectFirstDay(s));
      effOk = false;
    }
    boolean expOk = true;
    if (sx.getDayOfMonth() != sx.lengthOfMonth()) {
      e.add("expiryDate", strataExpiryLastDay(s));
      expOk = false;
    }
    if (planFrom != null && se.isBefore(planFrom)) {
      e.add(effOk ? "effectiveDate" : "noWhere", strataPopEffect(s));
    }
    if (planTo != null && sx.isAfter(planTo)) {
      e.add(expOk ? "expiryDate" : "noWhere", strataPopExpiry(s));
    }
  }

  /** {@code HBSValidator.validateUniqueStratumNumber}. */
  private boolean stratumNumberExists(Long planId, Long stratumPlanId, String number) {
    for (StratumPlanRow other : reads.stratumPlans(planId)) {
      if (stratumPlanId != null && stratumPlanId.equals(other.stratumPlanId)) continue;
      if (number.equals(other.stratumNumber)) return true;
    }
    return false;
  }

  /** {@code CalculateNewStrataDate.validateStrandedReturns} with a stratum number (single mode). */
  void strandedSingle(String pop, String stratum, long year, LocalDate eff, LocalDate exp, Problems e) {
    SamplingReads.DocumentCounts c = reads.strandedSingle(pop, stratum, year, eff, exp);
    if (c.sampleScale() > 0) e.add("noWhere", strandSampleScale(stratum, c.sampleScale()));
    if (c.sampleLog() > 0) e.add("noWhere", strandSampleLog(stratum, c.sampleLog()));
    if (c.weightScale() > 0) e.add("noWhere", strandWeighScale(stratum, c.weightScale()));
    if (c.weighSlips() > 0) e.add("noWhere", strandWeighSlips(stratum, c.weighSlips()));
  }

  /** {@code CalculateNewStrataDate.validateStrandedReturns} without a stratum (plan mode, TreeMap by stratum). */
  void strandedPlan(SamplePlanRow plan, Problems e) {
    List<SamplingReads.Stranded> rows = reads.strandedMultiple(plan.planAbreviation, plan.populationNumber,
        plan.samplingYear);
    for (String type : List.of("SSS", "SLT", "WSS", "WS")) {
      TreeMap<Integer, Long> byStratum = new TreeMap<>();
      for (SamplingReads.Stranded r : rows) {
        if (type.equals(r.type())) byStratum.put(Integer.valueOf(r.stratumNumber().trim()), r.count());
      }
      byStratum.forEach((stratum, count) -> {
        String s = stratum.toString();
        switch (type) {
          case "SSS" -> e.add("noWhere", strandSampleScale(s, count));
          case "SLT" -> e.add("noWhere", strandSampleLog(s, count));
          case "WSS" -> e.add("noWhere", strandWeighScale(s, count));
          default -> e.add("noWhere", strandWeighSlips(s, count));
        }
      });
    }
  }

  /**
   * {@code SamplePlanValidator.validateSamplePlan}: strata may not span the
   * 2006 Interior Grade conversion dates (BT 10348).
   */
  void validateSamplePlanSpans(String rollupRegionCode, List<StratumPlanRow> strata, Problems e) {
    LocalDateTime firstStart = LocalDateTime.of(2006, 3, 31, 23, 59, 59);
    LocalDateTime firstEnd = LocalDateTime.of(2006, 4, 1, 0, 0, 0);
    LocalDateTime secondStart = LocalDateTime.of(2005, 12, 31, 23, 59, 59);
    LocalDateTime secondEnd = LocalDateTime.of(2006, 4, 1, 0, 0, 0);
    LocalDateTime thirdStart = LocalDateTime.of(2006, 3, 31, 23, 59, 59);
    LocalDateTime thirdEnd = LocalDateTime.of(2007, 1, 1, 0, 0, 0);
    boolean coastRegion = rollupRegionCode != null && COAST_REGIONS.contains(rollupRegionCode.toUpperCase());
    int i = 0;
    for (StratumPlanRow sp : strata) {
      LocalDateTime eff = day(sp.effectiveDate).atStartOfDay();
      LocalDateTime exp = day(sp.expiryDate).atTime(23, 59, 59);
      boolean first = coastRegion ? INTERIOR_TYPE.equalsIgnoreCase(sp.gradeScheduleCode) : true;
      boolean secondThird = coastRegion || COASTAL_TYPE.equalsIgnoreCase(sp.gradeScheduleCode);
      if (first && !eff.isAfter(firstStart) && !exp.isBefore(firstEnd)) {
        e.add("stratumPlanError_" + i, firstSpan(sp.stratumNumber));
      }
      if (secondThird && !eff.isAfter(secondStart) && !exp.isBefore(secondEnd)) {
        e.add("stratumPlanError_" + i, secondSpan(sp.stratumNumber));
      }
      if (secondThird && !eff.isAfter(thirdStart) && !exp.isBefore(thirdEnd)) {
        e.add("stratumPlanError_" + i, thirdSpan(sp.stratumNumber));
      }
      i++;
    }
  }

  /** {@code SamplePlanValidator.validateProductAndGradeCode}: an approved stratum keeps its schedules. */
  void validateProductAndGradeCode(SamplePlanRow plan, String stratumNumber, String product, String gradeSchedule,
      Problems e) {
    List<SamplePlanRow> active = reads.plans(SamplePlanRow.ACTIVE, plan.populationNumber, plan.samplingYear, null,
        true);
    if (active.isEmpty()) return;
    for (StratumPlanRow sp : reads.stratumPlans(active.get(0).planId)) {
      if (sp.stratumNumber.equals(stratumNumber)) {
        if (!sp.scaleProductCode.equals(product)) e.add("scaleProductCode", PRODUCT_SCHEDULE_CHANGE);
        if (!sp.gradeScheduleCode.equals(gradeSchedule)) e.add("gradeScheduleCode", GRADE_SCHEDULE_CHANGE);
        break;
      }
    }
  }

  /**
   * The species / product / grade rules shared by P885 (a composition row),
   * P886 {@code validateForm} and the propose check: the combination exists,
   * is in effect on the stratum's dates, and the grade belongs to the
   * stratum's grade schedule and is in effect on those dates.
   *
   * @param p885 the P885 variant: unknown combination = "Species, product, and grade …" (only when a species was
   *             given); otherwise "Species and grade combination is invalid."
   */
  void validateSpg(String species, String grade, String product, String gradeSchedule, LocalDateTime effective,
      LocalDateTime expiry, String field, boolean p885, Problems e) {
    SamplingReads.Dates spg = null;
    if (species != null && !species.isEmpty() && grade != null && !grade.isEmpty()) {
      spg = reads.speciesProductGrade(species, product, grade);
    }
    if (spg == null) {
      if (!p885) {
        e.add(field, SPECIES_GRADE_INVALID);
      } else if (species != null && !species.isEmpty()) {
        e.add(field, SPG_INVALID);
      }
      return;
    }
    boolean validDates = true;
    if (!spg.contains(effective)) {
      e.add(field, SPG_EFFECTIVE);
      validDates = false;
    }
    if (!spg.contains(expiry)) {
      e.add(field, SPG_EXPIRY);
      validDates = false;
    }
    if (!validDates) return;
    SamplingReads.Dates g = reads.scheduleGrade(grade, gradeSchedule);
    boolean coast = COASTAL_TYPE.equalsIgnoreCase(gradeSchedule);
    boolean interior = INTERIOR_TYPE.equalsIgnoreCase(gradeSchedule);
    if (g == null) {
      if (coast) e.add(field, USE_COAST_GRADE);
      else if (interior) e.add(field, USE_INTERIOR_GRADE);
      return;
    }
    if (!g.contains(effective)) {
      if (coast) e.add(field, COAST_FROM);
      else if (interior) e.add(field, INTERIOR_FROM);
    }
    if (!g.contains(expiry)) {
      if (coast) e.add(field, COAST_TO);
      else if (interior) e.add(field, INTERIOR_TO);
    }
  }

  /** Every composition row of a stratum plan against (possibly new) product, schedule and dates. */
  void validateCompositions(long stratumPlanId, String product, String gradeSchedule, LocalDateTime effective,
      LocalDateTime expiry, Problems e) {
    for (Map<String, Object> c : reads.compositions(stratumPlanId)) {
      validateSpg((String) c.get("scale_species_code"), (String) c.get("scale_grade_code"), product, gradeSchedule,
          day(effective).atStartOfDay(), day(expiry).atTime(23, 59, 59), "stratumPlanCompositionRow", false, e);
    }
  }

  /** The population's last Active plan (HBS3R880 search with status ACT). */
  private SamplePlanRow lastActive(String pop, long year) {
    List<SamplePlanRow> act = reads.plans(SamplePlanRow.ACTIVE, pop, year, null, null);
    return act.isEmpty() ? null : act.get(act.size() - 1);
  }

  /** Default grade schedule of a new stratum ({@code P886Action.initializeStratumForm}). */
  String defaultGradeSchedule(Long orgUnitNo) {
    SamplingReads.OrgUnit ou = orgUnitNo == null ? null : reads.orgUnit(orgUnitNo);
    String region = ou == null ? null : ou.rollupRegionCode();
    return region != null && COAST_REGIONS.contains(region.toUpperCase()) ? COASTAL_TYPE : INTERIOR_TYPE;
  }

  // ── helpers ───────────────────────────────────────────────────

  static LocalDate day(LocalDateTime t) {
    return t == null ? null : t.toLocalDate();
  }

  static boolean isInteger(String s) {
    if (s == null || !s.trim().matches("-?\\d+")) return false;
    try {
      Integer.parseInt(s.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  static boolean isFloat(String s) {
    try {
      Float.parseFloat(s.trim());
      return true;
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  static int decimalPlaces(String s) {
    int dot = s.indexOf('.');
    return dot < 0 ? 0 : s.length() - dot - 1;
  }
}
