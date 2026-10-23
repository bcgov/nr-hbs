package ca.bc.gov.nrs.hbs.api.sampling;

import static ca.bc.gov.nrs.hbs.api.sampling.SamplingMessages.*;
import static ca.bc.gov.nrs.hbs.api.sampling.SamplingValidator.day;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * The Stratum Planner (module {@code smp}) write workflows, ported from the
 * Struts actions {@code $V/smp/P891Action} (add / Save &amp; Calculate /
 * propose / approve / reject / delete), {@code P886Action} (stratum plan
 * add / edit / delete), {@code P885Action} (default ratios), {@code P896Action}
 * (copy) and {@code P897Action} (create from actuals), and the EJB
 * {@code $E/smp/SamplePlannerBean} / {@code SrtsManagerBean} they called.
 *
 * <p>Each public method is one {@code WorkflowCommand} and therefore one
 * transaction: the legacy actions autocommitted every entity write, so a
 * failure half-way (e.g. a span error after the strata were already
 * rewritten during an approval) left partial rows that legacy batch
 * B2022/B2024 would pick up; here a failure rolls everything back.
 *
 * <p>Deliberate differences, all noted in docs/areas/rating-and-sampling.md:
 * every row written gets UPDATE_USERID / UPDATE_TIMESTAMP stamped (some legacy
 * stores re-sent the loaded values, which defeats {@link RowLock}); approval
 * with no prior Active plan is refused when validation fails (the legacy action
 * showed the errors but approved anyway).
 */
@Service
public class SamplingPlanService {

  private final SamplingReads reads;
  private final SamplingWrites writes;
  private final SamplingValidator validator;
  private final SamplingAccess access;
  private final Audit audit;

  public SamplingPlanService(SamplingReads reads, SamplingWrites writes, SamplingValidator validator,
      SamplingAccess access, Audit audit) {
    this.reads = reads;
    this.writes = writes;
    this.validator = validator;
    this.access = access;
    this.audit = audit;
  }

  // ═══ Plans ═══════════════════════════════════════════════════════

  /**
   * P891 "Save" (+ P892 "Continue"): a new In Progress plan, creating the
   * POPULATION first when it does not exist and the user confirmed it.
   */
  public Map<String, Object> createPlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplingValidator.Header h = header(b, null);
    h.ratioTypeCode = b.upper("ratioTypeCode");
    Problems e = new Problems();
    validator.validateHeader(h, SamplingValidator.Form.P891_SAVE, null, null, null, e);
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    long year = h.yearValue();
    if (reads.population(h.population, year) == null) {
      requirePopulationConfirmed(b, h.population, year);
      // P891Action.createPopulation: no district / client / dates on the new population.
      writes.createPopulation(h.population, year, null, null, null, null, null, user, now, h.ratioTypeCode);
    }

    SamplePlanRow p = new SamplePlanRow();
    p.planId = writes.nextPlanId();
    p.samplePlanStatusCode = SamplePlanRow.IN_PROGRESS;
    p.populationNumber = h.population;
    p.samplingYear = year;
    p.planAbreviation = h.abbreviation;
    p.planName = h.planName;
    p.ratioTypeCode = h.ratioTypeCode;
    if (SamplePlanRow.DEFAULT_RATIO_TYPE.equals(h.ratioTypeCode)) {
      p.precisionLimit = 0.0;
      p.sampleFloor = 0L;
    } else {
      p.precisionLimit = SamplingStatistics.round(Double.parseDouble(h.precisionLimit), 2);
      p.sampleFloor = Long.parseLong(h.sampleFloor);
    }
    p.currentConstraintType = SamplePlanRow.PRECISION_CONSTRAINT;
    p.currentEstimateType = SamplePlanRow.VOLUME_LOADS_EST;
    p.acceptedPlanInd = "N";
    p.optimumPrecisionCalc = 0.0;
    p.targetPrecisionCalc = 0.0;
    p.orgUnitNo = h.orgUnitValue();
    p.clientNumber = h.clientNumber;
    p.clientLocnCode = h.clientLocnCode;
    p.effectiveDate = h.effective.atStartOfDay();
    p.expiryDate = h.expiry.atStartOfDay();
    p.entryUserid = user;
    p.entryTimestamp = now;
    writes.createPlan(p);
    return Map.of("planId", p.planId);
  }

  /**
   * P881 "Save &amp; Calculate" ({@code P891Action.updatePlan(…, zeroOut=false,
   * ignoreSetStratum=false, validate=true)}): header changes, estimate / constraint
   * method, the strata's estimates (optional {@code strata} rows), then every
   * stratum plan and the plan's precisions are recalculated and stored.
   */
  public Map<String, Object> calculatePlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow plan = loadPlan(b.id("planId"));
    RowLock.check(plan.updateTimestamp, b.raw("updateTimestamp"));
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "edited");
    requirePlanAdmin(plan);
    calculate(plan, b, b.list("strata"));
    return Map.of("planId", plan.planId);
  }

  /** P881 grid row for one stratum: its estimates, then the whole plan is recalculated as P881 did. */
  public Map<String, Object> saveStratumEstimates(Map<String, Object> in) {
    Body b = new Body(in);
    StratumPlanRow sp = loadStratumPlan(b.id("stratumPlanId"));
    RowLock.check(sp.updateTimestamp, b.raw("updateTimestamp"));
    SamplePlanRow plan = loadPlan(sp.planId);
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "edited");
    requirePlanAdmin(plan);
    Map<String, Object> row = new LinkedHashMap<>(in);
    row.put("stratumPlanId", sp.stratumPlanId);
    calculate(plan, new Body(Map.of()), List.of(row));
    return Map.of("planId", plan.planId, "stratumPlanId", sp.stratumPlanId);
  }

  private void calculate(SamplePlanRow plan, Body b, List<Map<String, Object>> strataInput) {
    SamplingValidator.Header h = header(b, plan);
    String estimateType = or(b.upper("estimateType"), plan.currentEstimateType);
    String constraintType = or(b.upper("constraintType"), plan.currentConstraintType);
    List<StratumPlanRow> strata = reads.stratumPlans(plan.planId);
    Problems e = new Problems();
    validator.validateHeader(h, SamplingValidator.Form.P891_CALCULATE, plan.planId, null, strata, e);
    e.throwIfAny();

    applyEstimates(strata, strataInput);
    PlanCalculator.PlanInputs inputs = inputs(h, plan, estimateType, constraintType);
    for (int row = 0; row < strata.size(); row++) {
      PlanCalculator.validateEstimates(strata.get(row), row, inputs, e);
    }
    e.throwIfAny();
    PlanCalculator.calculateAll(strata, inputs, e);
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    for (StratumPlanRow sp : strata) {
      stamp(sp, user, now);
      writes.storeStratumPlan(sp);
    }
    Double[] precision = PlanCalculator.populationPrecisions(strata);
    plan.optimumPrecisionCalc = precision[0];
    plan.targetPrecisionCalc = precision[1];
    applyHeader(plan, h, estimateType, constraintType);
    storePlan(plan, user, now);
  }

  /**
   * P883 "Propose" → P895 "Yes" ({@code P891Action.performActionPropose}): the
   * plan goes to Awaiting Approval (AWP) after the stranded-return, stratum,
   * span, sampling-year and precision checks, and — when the population already
   * has an Active plan — the removed-strata and ratio-type checks.
   */
  public Map<String, Object> proposePlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow plan = loadPlan(b.id("planId"));
    RowLock.check(plan.updateTimestamp, b.raw("updateTimestamp"));
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "proposed");
    requirePlanAdmin(plan);
    // The legacy action stored the SAMPLE_PLAN it had loaded before recalculating, so the stored
    // optimum / target precisions are the ones validatePrecision checked (identical unless the
    // estimates changed without a Save & Calculate).
    SamplePlanRow before = plan.copy();

    Problems e = new Problems();
    validator.strandedPlan(plan, e);
    List<StratumPlanRow> strata = reads.stratumPlans(plan.planId);
    PlanCalculator.PlanInputs inputs = inputs(null, plan, plan.currentEstimateType, plan.currentConstraintType);
    for (int row = 0; row < strata.size(); row++) {
      StratumPlanRow sp = strata.get(row);
      PlanCalculator.validateEstimates(sp, row, inputs, e);
      // Propose + Yes: compositions must still be valid for the stratum (SamplePlanValidator.validateStratumPlan).
      validator.validateCompositions(sp.stratumPlanId, sp.scaleProductCode, sp.gradeScheduleCode, sp.effectiveDate,
          sp.expiryDate, e);
    }
    if (!e.isEmpty()) {
      // P891Action.updatePlan (BT-10348): any error so far is reported as this single message.
      e.clear();
      e.add("noWhere", STRATUM_PLAN_ERRORS);
    }
    validator.validateSamplePlanSpans(rollupRegion(plan.orgUnitNo), strata, e);
    if (e.isEmpty()) {
      PlanCalculator.calculateAll(strata, inputs, e);
    }
    validator.validateSamplingDates(plan.samplingYear, day(plan.effectiveDate), day(plan.expiryDate), e);
    boolean validPrecision = true;
    if (before.isSampleRatio() && (before.optimumPrecisionCalc == null || before.targetPrecisionCalc == null
        || before.optimumPrecisionCalc == 0.0 || before.targetPrecisionCalc == 0.0)) {
      e.add("noWhere", PRECISION_ZERO);
      validPrecision = false;
    }
    if (validPrecision) {
      List<SamplePlanRow> active = activePlans(plan);
      if (active.size() == 1) {
        SamplePlanRow activePlan = active.get(0);
        for (StratumPlanRow orphan : orphans(strata, reads.stratumPlans(activePlan.planId))) {
          if (documentsExist(plan, orphan.stratumNumber)) {
            e.add("noWhere", activityAssociated("Propose", orphan.stratumNumber));
          }
        }
        if (!activePlan.ratioTypeCode.equals(plan.ratioTypeCode)) {
          e.add("noWhere", RATIO_TYPE_CHANGE);
        }
      }
    }
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    for (StratumPlanRow sp : strata) {
      stamp(sp, user, now);
      writes.storeStratumPlan(sp);
    }
    before.samplePlanStatusCode = SamplePlanRow.AWAITING_APPROVAL;
    storePlan(before, user, now);
    return Map.of("planId", plan.planId, "samplePlanStatusCode", SamplePlanRow.AWAITING_APPROVAL);
  }

  /**
   * P883 "Approve" → P895 "Yes" ({@code P891Action.performActionApprove} +
   * {@code matchStratumNumbers} + {@code updatePopulation}): the plan becomes
   * the population's Active plan. STRATUM rows are created / re-pointed for its
   * strata, strata the previous Active plan had and this one drops are removed
   * (refused when documents reference them), the previous plan becomes
   * Superceded (SPR), and POPULATION takes the plan's owner, district, dates and
   * estimate totals.
   */
  public Map<String, Object> approvePlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow plan = loadPlan(b.id("planId"));
    RowLock.check(plan.updateTimestamp, b.raw("updateTimestamp"));
    requireStatus(plan, SamplePlanRow.AWAITING_APPROVAL, "approved");
    requireMinistryAdmin(plan);

    List<SamplePlanRow> active = activePlans(plan);
    if (active.size() > 1) {
      throw new IllegalStateException("Multiple Sample plans, with active status,  exist for population "
          + plan.populationNumber + " and sampling year " + plan.samplingYear);
    }
    Problems e = new Problems();
    validator.validateSamplingDates(plan.samplingYear, day(plan.effectiveDate), day(plan.expiryDate), e);
    validator.strandedPlan(plan, e);
    List<StratumPlanRow> strata = reads.stratumPlans(plan.planId);
    SamplePlanRow activePlan = active.isEmpty() ? null : active.get(0);
    List<StratumPlanRow> activeStrata = activePlan == null ? List.of() : reads.stratumPlans(activePlan.planId);
    List<StratumPlanRow> removed = new ArrayList<>();
    if (activePlan != null) {
      if (!activePlan.ratioTypeCode.equals(plan.ratioTypeCode)) {
        e.add("noWhere", RATIO_TYPE_CHANGE);
      }
      for (StratumPlanRow orphan : orphans(strata, activeStrata)) {
        if (documentsExist(plan, orphan.stratumNumber)) {
          e.add("noWhere", activityAssociated("Approve", orphan.stratumNumber));
        } else {
          removed.add(orphan);
        }
      }
      validator.validateSamplePlanSpans(rollupRegion(plan.orgUnitNo), strata, e);
    }
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    Map<String, Map<String, Object>> existing = new HashMap<>();
    for (Map<String, Object> s : reads.strata(plan.populationNumber, plan.samplingYear)) {
      existing.put((String) s.get("stratum_number"), s);
    }
    for (StratumPlanRow orphan : removed) {
      if (existing.remove(orphan.stratumNumber) != null) {
        writes.removeStratum(plan.populationNumber, plan.samplingYear, orphan.stratumNumber);
      }
    }
    for (StratumPlanRow sp : strata) {
      Map<String, Object> stratum = existing.get(sp.stratumNumber);
      if (stratum == null) {
        writes.createStratum(plan.populationNumber, plan.samplingYear, sp, user, now);
      } else {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("stratum_plan_id", sp.stratumPlanId);
        changes.put("client_number", sp.clientNumber);
        changes.put("client_locn_code", sp.clientLocnCode);
        changes.put("effective_date", sp.effectiveDate);
        changes.put("expiry_date", sp.expiryDate);
        changes.put("stratum_name", sp.stratumName);
        changes.put("grade_schedule_code", sp.gradeScheduleCode);
        changes.put("scale_product_code", sp.scaleProductCode);
        changes.put("update_userid", user);
        changes.put("update_timestamp", now);
        writes.storeStratum(stratum, changes);
      }
      sp.acceptedStratumPlanInd = "Y";
      stamp(sp, user, now);
      writes.storeStratumPlan(sp);
    }
    if (activePlan != null) {
      for (StratumPlanRow old : activeStrata) {
        old.acceptedStratumPlanInd = "N";
        stamp(old, user, now);
        writes.storeStratumPlan(old);
      }
      activePlan.samplePlanStatusCode = SamplePlanRow.SUPERCEDED;
      activePlan.acceptedPlanInd = "N";
      storePlan(activePlan, user, now);
    }
    updatePopulation(plan, strata, user, now);
    plan.samplePlanStatusCode = SamplePlanRow.ACTIVE;
    plan.acceptedPlanInd = "Y";
    storePlan(plan, user, now);
    return Map.of("planId", plan.planId, "samplePlanStatusCode", SamplePlanRow.ACTIVE);
  }

  /** P883 "Reject" → P895 "Yes": Awaiting Approval → Rejected (REJ). */
  public Map<String, Object> rejectPlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow plan = loadPlan(b.id("planId"));
    RowLock.check(plan.updateTimestamp, b.raw("updateTimestamp"));
    requireStatus(plan, SamplePlanRow.AWAITING_APPROVAL, "rejected");
    requireMinistryAdmin(plan);
    plan.samplePlanStatusCode = SamplePlanRow.REJECTED;
    storePlan(plan, audit.user(), now());
    return Map.of("planId", plan.planId, "samplePlanStatusCode", SamplePlanRow.REJECTED);
  }

  /** P883 "Delete" → P895 "Yes": In Progress → Deleted (DEL, a soft delete; the rows stay). */
  public Map<String, Object> deletePlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow plan = loadPlan(b.id("planId"));
    RowLock.check(plan.updateTimestamp, b.raw("updateTimestamp"));
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "deleted");
    requirePlanAdmin(plan);
    plan.samplePlanStatusCode = SamplePlanRow.DELETED;
    storePlan(plan, audit.user(), now());
    return Map.of("planId", plan.planId, "samplePlanStatusCode", SamplePlanRow.DELETED);
  }

  /**
   * P896 "Copy" ({@code P896Action.addSamplePlan} / {@code copyStratumPlans}):
   * a new In Progress plan for the given population / year with copies of the
   * source plan's stratum plans and default ratios; options apply the new
   * plan's dates / owner to the strata and drop strata no document uses.
   */
  public Map<String, Object> copyPlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow source = loadPlan(b.id("sourcePlanId"));
    if (!access.isPlanAdmin(source.orgUnitNo, source.clientNumber)) {
      throw new AccessDeniedException("Not a Stratum Planner administrator for this plan's district or client.");
    }
    SamplingValidator.Header h = header(b, null);
    Problems e = new Problems();
    validator.validateHeader(h, SamplingValidator.Form.P896_COPY, null, source.ratioTypeCode, null, e);
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    long year = h.yearValue();
    if (reads.population(h.population, year) == null) {
      requirePopulationConfirmed(b, h.population, year);
      writes.createPopulation(h.population, year, null, null, null, null, null, user, now, source.ratioTypeCode);
    }
    SamplePlanRow p = newPlanFrom(source, h, user, now);
    writes.createPlan(p);

    for (StratumPlanRow old : reads.stratumPlans(source.planId)) {
      // Unused strata (no document of any kind) are left out when asked (HBS_UNUSED_STRATA_COUNT 'Y').
      if (b.flag("deleteUnusedStrata")
          && !reads.stratumUsed(source.populationNumber, old.stratumNumber, source.samplingYear)) {
        continue;
      }
      List<Map<String, Object>> compositions = reads.compositions(old.stratumPlanId);
      StratumPlanRow sp = old.copy();
      sp.stratumPlanId = writes.nextStratumPlanId();
      sp.planId = p.planId;
      sp.acceptedStratumPlanInd = "N";
      if (b.flag("usePlanEffectiveDate")) sp.effectiveDate = p.effectiveDate;
      if (b.flag("usePlanExpiryDate")) sp.expiryDate = p.expiryDate;
      if (b.flag("usePlanClient")) {
        sp.clientNumber = p.clientNumber;
        sp.clientLocnCode = p.clientLocnCode;
      }
      sp.entryUserid = user;
      sp.entryTimestamp = now;
      sp.updateUserid = null;
      sp.updateTimestamp = null;
      writes.createStratumPlan(sp);
      for (Map<String, Object> c : compositions) {
        // Copies keep the original entry / update stamps (P896Action.copyStratumPlans).
        writes.createComposition((String) c.get("scale_species_code"), (String) c.get("scale_grade_code"),
            sp.stratumPlanId, (String) c.get("scale_product_code"), c.get("default_ratio"),
            (String) c.get("entry_userid"), c.get("entry_timestamp"), (String) c.get("update_userid"),
            c.get("update_timestamp"));
      }
    }
    return Map.of("planId", p.planId);
  }

  /**
   * P882 "Create New Plan From Actuals" → P897 "Create"
   * ({@code P897Action.performCreateSamplingPlan}): like a copy of an Active
   * plan, but each stratum's estimates start from its actual sample statistics
   * (STRATUM sample load size / standard deviation, loads = sample floor) and its
   * default ratios from the latest RATIO_HISTORY ratios; stratum dates are
   * scaled into the new plan's period unless the plan dates are applied.
   */
  public Map<String, Object> createPlanFromActuals(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow source = loadPlan(b.id("sourcePlanId"));
    requireStatus(source, SamplePlanRow.ACTIVE, "used to create a plan from actuals");
    if (!access.isPlanAdmin(source.orgUnitNo, source.clientNumber)) {
      throw new AccessDeniedException("Not a Stratum Planner administrator for this plan's district or client.");
    }
    SamplingValidator.Header h = header(b, null);
    Problems e = new Problems();
    validator.validateHeader(h, SamplingValidator.Form.P897_CREATE, null, source.ratioTypeCode, null, e);
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    long year = h.yearValue();
    if (reads.population(h.population, year) == null) {
      requirePopulationConfirmed(b, h.population, year);
      // P897Action.performAddPopulation carries the district, owner and dates.
      writes.createPopulation(h.population, year, h.orgUnitValue(), h.clientNumber, h.clientLocnCode,
          h.effective.atStartOfDay(), h.expiry.atStartOfDay(), user, now, source.ratioTypeCode);
    }
    SamplePlanRow p = newPlanFrom(source, h, user, now);
    writes.createPlan(p);

    Map<String, Map<String, Object>> oldStrata = new HashMap<>();
    for (Map<String, Object> s : reads.strata(source.populationNumber, source.samplingYear)) {
      oldStrata.put((String) s.get("stratum_number"), s);
    }
    for (StratumPlanRow old : reads.stratumPlans(source.planId)) {
      Map<String, Object> oldStratum = oldStrata.get(old.stratumNumber);
      if (oldStratum == null) {
        throw new WorkflowValidationException("sourcePlanId", "Stratum " + old.stratumNumber + " of population "
            + source.populationNumber + " / " + source.samplingYear + " has no actuals (no STRATUM row).");
      }
      if (b.flag("deleteUnusedStrata")
          && !reads.stratumUsed(source.populationNumber, old.stratumNumber, source.samplingYear)) {
        continue;
      }
      StratumPlanRow sp = old.copy();
      sp.stratumPlanId = writes.nextStratumPlanId();
      sp.planId = p.planId;
      sp.acceptedStratumPlanInd = "N";
      boolean sampled = num(oldStratum.get("sample_loads")) != 0;
      if (sampled) {
        sp.estimatedLoadSize = dbl(oldStratum.get("sample_load_size"));
        sp.estimatedStandardDeviation = dbl(oldStratum.get("sample_standard_deviation"));
      }
      sp.estimatedLoads = p.sampleFloor;
      sp.sampleCountConstraint = 0L;
      sp.precisionConstraint = 0.0;
      // NumericConversionUtil.formatDouble(…, "3") — DecimalFormat, HALF_EVEN.
      sp.estimatedVolume = BigDecimal.valueOf(sp.estimatedLoadSize * sp.estimatedLoads)
          .setScale(3, RoundingMode.HALF_EVEN).doubleValue();
      sp.optimumSampleCalc = 0L;
      sp.optimumPrecisionCalc = 0.0;
      sp.optimumFrequencyCalc = 0L;
      sp.targetSampleCalc = 0L;
      sp.targetPrecisionCalc = 0.0;
      sp.targetFrequencyCalc = 0L;
      LocalDate newEff = h.effective;
      LocalDate newExp = h.expiry;
      if (b.flag("usePlanEffectiveDate")) {
        sp.effectiveDate = p.effectiveDate;
      } else {
        sp.effectiveDate = StrataDates.scaledDate(newEff, newExp, day(source.effectiveDate),
            day(source.expiryDate), day(old.effectiveDate), day(old.expiryDate), true).atStartOfDay();
      }
      if (b.flag("usePlanExpiryDate")) {
        sp.expiryDate = p.expiryDate;
      } else {
        sp.expiryDate = StrataDates.scaledDate(newEff, newExp, day(source.effectiveDate),
            day(source.expiryDate), day(sp.effectiveDate), day(old.expiryDate), false).atStartOfDay();
      }
      if (b.flag("usePlanClient")) {
        sp.clientNumber = p.clientNumber;
        sp.clientLocnCode = p.clientLocnCode;
      }
      sp.entryUserid = user;
      sp.entryTimestamp = now;
      sp.updateUserid = null;
      sp.updateTimestamp = null;
      writes.createStratumPlan(sp);

      List<Map<String, Object>> ratios = sampled
          ? reads.latestRatios(source.populationNumber, old.stratumNumber, source.samplingYear) : List.of();
      if (!ratios.isEmpty()) {
        for (Map<String, Object> r : ratios) {
          writes.createComposition((String) r.get("scale_species_code"), (String) r.get("scale_grade_code"),
              sp.stratumPlanId, (String) r.get("scale_product_code"), r.get("ratio"), user, now, null, null);
        }
      } else {
        for (Map<String, Object> c : reads.compositions(old.stratumPlanId)) {
          writes.createComposition((String) c.get("scale_species_code"), (String) c.get("scale_grade_code"),
              sp.stratumPlanId, (String) c.get("scale_product_code"), c.get("default_ratio"), user, now, null,
              null);
        }
      }
    }
    return Map.of("planId", p.planId);
  }

  // ═══ Stratum plans ═══════════════════════════════════════════════

  /**
   * P881 "Add Stratum" → P886 "Save" ({@code P886Action.performAddStratumPlan}):
   * a new stratum plan with zero estimates. A stratum period starting before /
   * ending after the plan's widens the plan (as P886 did on the add path).
   */
  public Map<String, Object> createStratumPlan(Map<String, Object> in) {
    Body b = new Body(in);
    SamplePlanRow plan = loadPlan(b.id("planId"));
    RowLock.check(plan.updateTimestamp, b.raw("planUpdateTimestamp"));
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "edited");
    requirePlanAdmin(plan);

    StratumPlanRow sp = new StratumPlanRow();
    sp.planId = plan.planId;
    applyStratumForm(sp, b, plan);
    Problems e = new Problems();
    validator.validateStratumPlanForm(sp, b.text("stratumNumber"), plan, null, null, false, e);
    e.throwIfAny();
    validator.validateProductAndGradeCode(plan, sp.stratumNumber, sp.scaleProductCode, sp.gradeScheduleCode, e);
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    sp.stratumPlanId = writes.nextStratumPlanId();
    sp.sampleCountConstraint = 0L;
    sp.precisionConstraint = 0.0;
    sp.estimatedVolume = 0.0;
    sp.estimatedLoads = 0L;
    sp.estimatedLoadSize = 0.0;
    sp.estimatedStandardDeviation = 0.0;
    sp.optimumSampleCalc = 0L;
    sp.optimumPrecisionCalc = 0.0;
    sp.optimumFrequencyCalc = 0L;
    sp.targetSampleCalc = 0L;
    sp.targetPrecisionCalc = 0.0;
    sp.targetFrequencyCalc = 0L;
    sp.acceptedStratumPlanInd = "N";
    sp.entryUserid = user;
    sp.entryTimestamp = now;
    widenPlanAndZeroPrecision(plan, sp, user, now);
    writes.createStratumPlan(sp);
    return Map.of("planId", plan.planId, "stratumPlanId", sp.stratumPlanId);
  }

  /**
   * P886 "Save" on an existing stratum ({@code P886Action.validateForm},
   * {@code performUpdateStratumPlan}, {@code updateProductSchedule}): the
   * stratum's compositions are re-created under its (possibly new) product.
   */
  public Map<String, Object> updateStratumPlan(Map<String, Object> in) {
    Body b = new Body(in);
    StratumPlanRow sp = loadStratumPlan(b.id("stratumPlanId"));
    RowLock.check(sp.updateTimestamp, b.raw("updateTimestamp"));
    SamplePlanRow plan = loadPlan(sp.planId);
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "edited");
    requirePlanAdmin(plan);

    applyStratumForm(sp, b, plan);
    Problems e = new Problems();
    validator.validateStratumPlanForm(sp, b.text("stratumNumber"), plan, day(plan.effectiveDate),
        day(plan.expiryDate), true, e);
    e.throwIfAny();
    validator.validateProductAndGradeCode(plan, sp.stratumNumber, sp.scaleProductCode, sp.gradeScheduleCode, e);
    validator.validateCompositions(sp.stratumPlanId, sp.scaleProductCode, sp.gradeScheduleCode, sp.effectiveDate,
        sp.expiryDate, e);
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    widenPlanAndZeroPrecision(plan, sp, user, now);
    stamp(sp, user, now);
    writes.storeStratumPlan(sp);
    for (Map<String, Object> c : reads.compositions(sp.stratumPlanId)) {
      writes.removeComposition(c);
      writes.createComposition((String) c.get("scale_species_code"), (String) c.get("scale_grade_code"),
          sp.stratumPlanId, sp.scaleProductCode, c.get("default_ratio"), (String) c.get("entry_userid"),
          c.get("entry_timestamp"), user, now);
    }
    return Map.of("planId", plan.planId, "stratumPlanId", sp.stratumPlanId);
  }

  /** P886 "Delete" → P898 "Yes": refused while documents reference the stratum (HBS_UNUSED_STRATA_COUNT 'N'). */
  public Map<String, Object> deleteStratumPlan(Map<String, Object> in) {
    Body b = new Body(in);
    StratumPlanRow sp = loadStratumPlan(b.id("stratumPlanId"));
    RowLock.check(sp.updateTimestamp, b.raw("updateTimestamp"));
    SamplePlanRow plan = loadPlan(sp.planId);
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "edited");
    requirePlanAdmin(plan);

    SamplingReads.DocumentCounts c = reads.documentCounts(plan.populationNumber, sp.stratumNumber,
        plan.samplingYear);
    Problems e = new Problems();
    if (c.sampleLog() > 0) e.add("noWhere", deleteSampleLog(sp.stratumNumber, c.sampleLog()));
    if (c.sampleScale() > 0) e.add("noWhere", deleteSampleScale(sp.stratumNumber, c.sampleScale()));
    if (c.weightScale() > 0) e.add("noWhere", deleteWeighScale(sp.stratumNumber, c.weightScale()));
    if (c.weighSlips() > 0) e.add("noWhere", deleteWeighSlips(sp.stratumNumber, c.weighSlips()));
    e.throwIfAny();

    String user = audit.user();
    LocalDateTime now = now();
    zeroPrecision(plan, user, now);
    writes.removeStratumPlan(sp.stratumPlanId, reads.compositions(sp.stratumPlanId));
    return Map.of("planId", plan.planId);
  }

  // ═══ Default ratios (P885) ═══════════════════════════════════════

  /** P885 "Save" with a new row (species, grade, ratio). */
  public Map<String, Object> createComposition(Map<String, Object> in) {
    Body b = new Body(in);
    StratumPlanRow sp = compositionStratum(b);
    String species = b.upper("scaleSpeciesCode");
    String grade = gradeOrBlank(b);
    String ratioText = b.text("defaultRatio");
    List<Map<String, Object>> rows = reads.compositions(sp.stratumPlanId);
    Problems e = new Problems();
    validateCompositionRow(sp, species, grade, ratioText, e);
    if (findComposition(rows, species, grade) != null) {
      e.add("stratumPlanCompositionRow", SPG_DUPLICATE);
    }
    e.throwIfAny();
    checkTotal(rows, null, Double.parseDouble(ratioText), e);
    e.throwIfAny();
    writes.createComposition(species, grade, sp.stratumPlanId, sp.scaleProductCode, Double.valueOf(ratioText),
        audit.user(), now(), null, null);
    return Map.of("stratumPlanId", sp.stratumPlanId);
  }

  /**
   * P885 "Save" with a changed row: the row is removed and re-created
   * ({@code performUpdateStratumPlanComposition}), keeping its entry stamps.
   */
  public Map<String, Object> updateComposition(Map<String, Object> in) {
    Body b = new Body(in);
    StratumPlanRow sp = compositionStratum(b);
    List<Map<String, Object>> rows = reads.compositions(sp.stratumPlanId);
    Map<String, Object> current = findComposition(rows, b.upper("originalScaleSpeciesCode"),
        gradeOf(b.untrimmed("originalScaleGradeCode")));
    if (current == null) {
      throw new EntityNotFoundException(SamplingPlanService.class, "stratumPlanComposition",
          b.text("originalScaleSpeciesCode") + "/" + b.text("originalScaleGradeCode"));
    }
    RowLock.check(current.get("update_timestamp") != null ? current.get("update_timestamp")
        : current.get("entry_timestamp"), b.raw("updateTimestamp"));
    String species = b.upper("scaleSpeciesCode");
    String grade = gradeOrBlank(b);
    String ratioText = b.text("defaultRatio");
    Problems e = new Problems();
    validateCompositionRow(sp, species, grade, ratioText, e);
    Map<String, Object> clash = findComposition(rows, species, grade);
    if (clash != null && clash != current) {
      e.add("stratumPlanCompositionRow", SPG_DUPLICATE);
    }
    e.throwIfAny();
    checkTotal(rows, current, Double.parseDouble(ratioText), e);
    e.throwIfAny();
    writes.removeComposition(current);
    writes.createComposition(species, grade, sp.stratumPlanId, sp.scaleProductCode, Double.valueOf(ratioText),
        (String) current.get("entry_userid"), current.get("entry_timestamp"), audit.user(), now());
    return Map.of("stratumPlanId", sp.stratumPlanId);
  }

  /** P885 "Save" with a row's delete box ticked. */
  public Map<String, Object> removeComposition(Map<String, Object> in) {
    Body b = new Body(in);
    StratumPlanRow sp = compositionStratum(b);
    Map<String, Object> current = findComposition(reads.compositions(sp.stratumPlanId), b.upper("scaleSpeciesCode"),
        gradeOrBlank(b));
    if (current == null) {
      throw new EntityNotFoundException(SamplingPlanService.class, "stratumPlanComposition",
          b.text("scaleSpeciesCode") + "/" + b.text("scaleGradeCode"));
    }
    writes.removeComposition(current);
    return Map.of("stratumPlanId", sp.stratumPlanId);
  }

  // ═══ helpers ════════════════════════════════════════════════════

  private StratumPlanRow compositionStratum(Body b) {
    StratumPlanRow sp = loadStratumPlan(b.id("stratumPlanId"));
    SamplePlanRow plan = loadPlan(sp.planId);
    requireStatus(plan, SamplePlanRow.IN_PROGRESS, "edited");
    requirePlanAdmin(plan);
    return sp;
  }

  /** {@code P885Action.validateStratumPlanComposition} for one row. */
  private void validateCompositionRow(StratumPlanRow sp, String species, String grade, String ratioText, Problems e) {
    if (species == null) {
      e.add("scaleSpeciesCode", SPECIES_REQUIRED);
    }
    if (ratioText == null) {
      e.add("defaultRatio", RATIO_REQUIRED);
    } else {
      try {
        double ratio = Double.parseDouble(ratioText);
        if (ratio <= 0.0) e.add("defaultRatio", RATIO_GREATER);
        if (ratio >= 10.0) e.add("defaultRatio", RATIO_LESSER);
        if (!PlanCalculator.decimalsOk(ratio, 5)) e.add("defaultRatio", numberDecimals("Ratio", 5));
      } catch (NumberFormatException ex) {
        e.add("defaultRatio", RATIO_NUMERIC);
      }
    }
    validator.validateSpg(species, grade, sp.scaleProductCode, sp.gradeScheduleCode, sp.effectiveDate,
        sp.expiryDate, "stratumPlanCompositionRow", true, e);
  }

  /** P885: the ratios of the stratum (after the change) must total less than 10. */
  private static void checkTotal(List<Map<String, Object>> rows, Map<String, Object> replaced, double newRatio,
      Problems e) {
    double sum = newRatio;
    for (Map<String, Object> r : rows) {
      if (r == replaced) continue;
      sum += new BigDecimal(String.valueOf(r.get("default_ratio"))).doubleValue();
    }
    if (sum >= 10.0) {
      e.add("totalRatio", TOTAL_RATIO_LESSER);
    }
  }

  private static Map<String, Object> findComposition(List<Map<String, Object>> rows, String species, String grade) {
    for (Map<String, Object> r : rows) {
      if (String.valueOf(r.get("scale_species_code")).equalsIgnoreCase(String.valueOf(species))
          && String.valueOf(r.get("scale_grade_code")).equalsIgnoreCase(String.valueOf(grade))) {
        return r;
      }
    }
    return null;
  }

  /** P885: an empty grade is stored as a single space. */
  private static String gradeOrBlank(Body b) {
    return gradeOf(b.untrimmed("scaleGradeCode"));
  }

  private static String gradeOf(String raw) {
    String g = raw == null ? "" : raw.trim().toUpperCase();
    return g.isEmpty() ? " " : g;
  }

  /** The P886 form fields onto a stratum plan row. */
  private void applyStratumForm(StratumPlanRow sp, Body b, SamplePlanRow plan) {
    sp.stratumNumber = b.text("stratumNumber");
    sp.stratumName = b.text("stratumName");
    sp.clientNumber = b.text("clientNumber");
    sp.clientLocnCode = b.upper("clientLocnCode");
    LocalDate eff = b.date("effectiveDate");
    LocalDate exp = b.date("expiryDate");
    sp.effectiveDate = eff == null ? null : eff.atStartOfDay();
    sp.expiryDate = exp == null ? null : exp.atStartOfDay();
    sp.stratumDesc = b.text("stratumDesc");
    sp.timberSourceDesc = b.text("timberSourceDesc");
    sp.speciesGradeProfileDesc = b.text("speciesGradeProfileDesc");
    String schedule = b.upper("gradeScheduleCode");
    sp.gradeScheduleCode = schedule != null ? schedule : validator.defaultGradeSchedule(plan.orgUnitNo);
    sp.scaleProductCode = b.upper("scaleProductCode");
    if (sp.scaleProductCode == null) {
      throw new WorkflowValidationException("scaleProductCode", required("Product Schedule"));
    }
  }

  /**
   * P886 Save: a stratum starting before / ending after the plan widens the plan
   * (compared as yyyyMMdd). Every stratum change also clears the plan's
   * precisions — the P881 "Add Stratum" and stratum links ran {@code updatePlan}
   * with zeroOutPrecision — so the plan must be recalculated before proposing.
   */
  private void widenPlanAndZeroPrecision(SamplePlanRow plan, StratumPlanRow sp, String user, LocalDateTime now) {
    if (day(sp.effectiveDate).isBefore(day(plan.effectiveDate))) {
      plan.effectiveDate = sp.effectiveDate;
    }
    if (day(sp.expiryDate).isAfter(day(plan.expiryDate))) {
      plan.expiryDate = sp.expiryDate;
    }
    zeroPrecision(plan, user, now);
  }

  private void zeroPrecision(SamplePlanRow plan, String user, LocalDateTime now) {
    plan.optimumPrecisionCalc = 0.0;
    plan.targetPrecisionCalc = 0.0;
    storePlan(plan, user, now);
  }

  /**
   * {@code P891Action.updateSamplePlan}: stamps and stores; a default-ratio
   * plan always has its precisions zeroed.
   */
  private void storePlan(SamplePlanRow plan, String user, LocalDateTime now) {
    if (plan.isDefaultRatio()) {
      plan.optimumPrecisionCalc = 0.0;
      plan.targetPrecisionCalc = 0.0;
    }
    plan.updateUserid = user;
    plan.updateTimestamp = now;
    writes.storePlan(plan);
  }

  private static void stamp(StratumPlanRow sp, String user, LocalDateTime now) {
    sp.updateUserid = user;
    sp.updateTimestamp = now;
  }

  /** {@code P891Action.updatePopulation}. */
  private void updatePopulation(SamplePlanRow plan, List<StratumPlanRow> strata, String user, LocalDateTime now) {
    Map<String, Object> pop = reads.population(plan.populationNumber, plan.samplingYear);
    if (pop == null) {
      throw new IllegalStateException("No POPULATION " + plan.populationNumber + " / " + plan.samplingYear);
    }
    double totalVolume = 0.0;
    long totalLoads = 0;
    for (StratumPlanRow sp : strata) {
      totalVolume += sp.estimatedVolume;
      totalLoads += sp.estimatedLoads;
    }
    BigDecimal loadSize = new BigDecimal("0.0");
    if (totalLoads > 0) {
      loadSize = new BigDecimal(totalVolume).divide(new BigDecimal(totalLoads), 3, RoundingMode.HALF_UP);
    }
    Map<String, Object> changes = new LinkedHashMap<>();
    changes.put("client_number", plan.clientNumber);
    changes.put("client_locn_code", plan.clientLocnCode);
    changes.put("org_unit_no", plan.orgUnitNo);
    changes.put("effective_date", plan.effectiveDate);
    changes.put("expiry_date", plan.expiryDate);
    changes.put("estimated_load_size", loadSize.doubleValue());
    changes.put("stratum_count", strata.size());
    changes.put("estimated_total_volume", totalVolume);
    changes.put("estimated_total_loads", totalLoads);
    changes.put("update_userid", user);
    changes.put("update_timestamp", now);
    changes.put("ratio_type_code", plan.ratioTypeCode);
    writes.storePopulation(pop, changes);
  }

  /** New plan for P896 / P897 from the source plan's settings and the typed header. */
  private SamplePlanRow newPlanFrom(SamplePlanRow source, SamplingValidator.Header h, String user,
      LocalDateTime now) {
    SamplePlanRow p = new SamplePlanRow();
    p.planId = writes.nextPlanId();
    p.samplePlanStatusCode = SamplePlanRow.IN_PROGRESS;
    p.populationNumber = h.population;
    p.samplingYear = h.yearValue();
    p.planAbreviation = h.abbreviation;
    p.planName = h.planName;
    p.precisionLimit = source.precisionLimit;
    p.sampleFloor = source.sampleFloor;
    p.currentConstraintType = source.currentConstraintType;
    p.currentEstimateType = source.currentEstimateType;
    p.acceptedPlanInd = "N";
    p.optimumPrecisionCalc = 0.0;
    p.targetPrecisionCalc = 0.0;
    p.orgUnitNo = h.orgUnitValue();
    p.clientNumber = h.clientNumber;
    p.clientLocnCode = h.clientLocnCode;
    p.effectiveDate = h.effective.atStartOfDay();
    p.expiryDate = h.expiry.atStartOfDay();
    p.entryUserid = user;
    p.entryTimestamp = now;
    p.ratioTypeCode = source.ratioTypeCode;
    return p;
  }

  /** Header fields from the body; on edit, absent fields keep the plan's values. */
  private static SamplingValidator.Header header(Body b, SamplePlanRow plan) {
    SamplingValidator.Header h = new SamplingValidator.Header();
    if (plan == null) {
      h.population = b.text("population");
      h.year = b.text("samplingYear");
      h.abbreviation = b.upper("planAbbreviation");
      h.planName = b.text("planName");
      h.precisionLimit = b.text("precisionLimit");
      h.sampleFloor = b.text("sampleFloor");
      h.clientNumber = b.text("clientNumber");
      h.clientLocnCode = b.upper("clientLocnCode");
      h.orgUnitNo = b.text("orgUnitNo");
      h.effective = b.date("effectiveDate");
      h.expiry = b.date("expiryDate");
      return h;
    }
    h.population = plan.populationNumber;
    h.year = String.valueOf(plan.samplingYear);
    h.abbreviation = plan.planAbreviation;
    h.ratioTypeCode = plan.ratioTypeCode;
    h.planName = has(b, "planName") ? b.text("planName") : plan.planName;
    h.precisionLimit = has(b, "precisionLimit") ? b.text("precisionLimit") : plain(plan.precisionLimit);
    h.sampleFloor = has(b, "sampleFloor") ? b.text("sampleFloor") : plain(plan.sampleFloor);
    h.clientNumber = has(b, "clientNumber") ? b.text("clientNumber") : plan.clientNumber;
    h.clientLocnCode = has(b, "clientLocnCode") ? b.upper("clientLocnCode") : plan.clientLocnCode;
    h.orgUnitNo = has(b, "orgUnitNo") ? b.text("orgUnitNo") : plain(plan.orgUnitNo);
    h.effective = has(b, "effectiveDate") ? b.date("effectiveDate") : day(plan.effectiveDate);
    h.expiry = has(b, "expiryDate") ? b.date("expiryDate") : day(plan.expiryDate);
    return h;
  }

  private static boolean has(Body b, String name) {
    return b.raw(name) != null;
  }

  private static String plain(Object n) {
    return n == null ? null : (n instanceof Double d ? BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
        : n.toString());
  }

  /** {@code P891Action.updateSamplePlan} field updates (abbreviation, population, year, ratio type stay). */
  private static void applyHeader(SamplePlanRow plan, SamplingValidator.Header h, String estimateType,
      String constraintType) {
    plan.planName = h.planName;
    if (!plan.isDefaultRatio()) {
      plan.precisionLimit = SamplingStatistics.round(Double.parseDouble(h.precisionLimit), 2);
      plan.sampleFloor = Long.parseLong(h.sampleFloor);
    }
    plan.currentConstraintType = constraintType;
    plan.currentEstimateType = estimateType;
    plan.orgUnitNo = h.orgUnitValue();
    plan.clientNumber = h.clientNumber;
    plan.clientLocnCode = h.clientLocnCode;
    plan.effectiveDate = h.effective.atStartOfDay();
    plan.expiryDate = h.expiry.atStartOfDay();
  }

  private static PlanCalculator.PlanInputs inputs(SamplingValidator.Header h, SamplePlanRow plan,
      String estimateType, String constraintType) {
    double limit;
    long floor;
    if (plan.isDefaultRatio()) {
      limit = 0.0;
      floor = 0;
    } else if (h != null) {
      limit = Double.parseDouble(h.precisionLimit);
      floor = Long.parseLong(h.sampleFloor);
    } else {
      limit = plan.precisionLimit;
      floor = plan.sampleFloor;
    }
    return new PlanCalculator.PlanInputs(estimateType, constraintType, limit, floor, plan.ratioTypeCode);
  }

  /**
   * {@code P891Action.setStratumPlan}: the P881 grid values for the given rows
   * (an unparsable entry becomes null and is reported by the estimate rules).
   */
  private static void applyEstimates(List<StratumPlanRow> strata, List<Map<String, Object>> rows) {
    for (Map<String, Object> r : rows) {
      Body rb = new Body(r);
      long id = rb.id("stratumPlanId");
      StratumPlanRow sp = strata.stream().filter(s -> s.stratumPlanId == id).findFirst()
          .orElseThrow(() -> new WorkflowValidationException("strata", "Stratum plan " + id + " is not in this plan."));
      if (rb.raw("estimatedVolume") != null) sp.estimatedVolume = parseDouble(rb.text("estimatedVolume"));
      if (rb.raw("estimatedLoads") != null) sp.estimatedLoads = parseLong(rb.text("estimatedLoads"));
      if (rb.raw("estimatedLoadSize") != null) sp.estimatedLoadSize = parseDouble(rb.text("estimatedLoadSize"));
      if (rb.raw("estimatedStandardDeviation") != null) {
        sp.estimatedStandardDeviation = parseDouble(rb.text("estimatedStandardDeviation"));
      }
      if (rb.raw("sampleCountConstraint") != null) {
        sp.sampleCountConstraint = parseLong(rb.text("sampleCountConstraint"));
      }
      if (rb.raw("precisionConstraint") != null) {
        sp.precisionConstraint = parseDouble(rb.text("precisionConstraint"));
      }
    }
  }

  private static Double parseDouble(String s) {
    try {
      return s == null ? null : Double.valueOf(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Long parseLong(String s) {
    try {
      return s == null ? null : Long.valueOf(s.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private List<SamplePlanRow> activePlans(SamplePlanRow plan) {
    return reads.plans(SamplePlanRow.ACTIVE, plan.populationNumber, plan.samplingYear, null, true);
  }

  /** Strata of the Active plan this plan does not have (by stratum number). */
  private static List<StratumPlanRow> orphans(List<StratumPlanRow> newStrata, List<StratumPlanRow> oldStrata) {
    List<StratumPlanRow> out = new ArrayList<>();
    for (StratumPlanRow old : oldStrata) {
      boolean found = newStrata.stream().anyMatch(n -> old.stratumNumber.equals(n.stratumNumber));
      if (!found) out.add(old);
    }
    return out;
  }

  /** {@code SmpHelper.isWeightScaleSummaryExist || isSampleLogTallyExist || isSampleScaleSummaryExist || isWeighSlipExist}. */
  private boolean documentsExist(SamplePlanRow plan, String stratumNumber) {
    return reads.documentCounts(plan.populationNumber, stratumNumber, plan.samplingYear).any();
  }

  private String rollupRegion(Long orgUnitNo) {
    SamplingReads.OrgUnit ou = orgUnitNo == null ? null : reads.orgUnit(orgUnitNo);
    return ou == null ? null : ou.rollupRegionCode();
  }

  private void requirePopulationConfirmed(Body b, String pop, long year) {
    if (!b.flag("confirmNewPopulation")) {
      throw new WorkflowValidationException("confirmNewPopulation", confirmPopulation(pop, year));
    }
  }

  private SamplePlanRow loadPlan(long planId) {
    SamplePlanRow plan = reads.plan(planId);
    if (plan == null) throw new EntityNotFoundException(SamplePlanRow.class, "planId", String.valueOf(planId));
    return plan;
  }

  private StratumPlanRow loadStratumPlan(long id) {
    StratumPlanRow sp = reads.stratumPlan(id);
    if (sp == null) throw new EntityNotFoundException(StratumPlanRow.class, "stratumPlanId", String.valueOf(id));
    return sp;
  }

  /** {@code SamplePlanFunctionsTag}: P883 offered each function only in one status. */
  private static void requireStatus(SamplePlanRow plan, String status, String verb) {
    if (!status.equals(plan.samplePlanStatusCode)) {
      throw new WorkflowValidationException("planId", "Only a plan with status " + status + " can be " + verb
          + " (this plan is " + plan.samplePlanStatusCode + ").");
    }
  }

  /** EDIT / PROPOSE / DELETE: ministry admin of the district or industry admin of the owner. */
  private void requirePlanAdmin(SamplePlanRow plan) {
    if (!access.isPlanAdmin(plan.orgUnitNo, plan.clientNumber)) {
      throw new AccessDeniedException("Not a Stratum Planner administrator for this plan's district or client.");
    }
  }

  /** APPROVE / REJECT: ministry admin of the plan's district only. */
  private void requireMinistryAdmin(SamplePlanRow plan) {
    if (!access.isMinistryAdmin(plan.orgUnitNo)) {
      throw new AccessDeniedException("Only a ministry Stratum Planner administrator for this plan's district"
          + " can approve or reject it.");
    }
  }

  private LocalDateTime now() {
    return audit.now().toLocalDateTime();
  }

  private static String or(String a, String b) {
    return a != null ? a : b;
  }

  private static long num(Object v) {
    return v == null ? 0 : new BigDecimal(v.toString()).longValue();
  }

  private static Double dbl(Object v) {
    return v == null ? null : new BigDecimal(v.toString()).doubleValue();
  }
}
