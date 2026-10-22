package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The legacy Queries-tab search-form validations for the registry queries that
 * replace them — like {@code submissions.SearchCriteriaGuard}, an
 * {@code @Before} advice on {@code QueryService}, so bad criteria are a 400 with
 * the legacy messages instead of an empty or unbounded result:
 *
 * <ul>
 *   <li>{@code harvestHistory.billing/scaling} (P431/P441) —
 *       {@code opq/ftas/MarkMonthlyBillingAction.validateForm};</li>
 *   <li>{@code issuedDocuments.search} (P449/P450) — validation.xml {@code P450Form}
 *       and {@code opq/P450Action.perform};</li>
 *   <li>{@code billingSummary.search} (P421) — validation.xml {@code P421Form},
 *       {@code opq/P421Action.perform};</li>
 *   <li>{@code statementTransmissions.search} (P452) — validation.xml {@code P452Form};</li>
 *   <li>{@code scaleSiteSummary.search} (P401) — {@code opq/dac/P401Action.validateForm};</li>
 *   <li>{@code agedUnbilled.search} (P403) — {@code opq/dac/P403Action.validateForm}
 *       (the rates are report prompts, checked by {@link QueriesReportGuard});</li>
 *   <li>{@code cutToCruise.search} (P490) — validation.xml {@code P490Form}.</li>
 * </ul>
 *
 * <p>Struts ran a field's validators in order and stopped at its first
 * failure; the per-field chains below do the same. Only criteria the new
 * screens offer are checked (radio buttons that chose between inputs are now
 * "whichever input is filled"). "Today" is Pacific time.
 */
@Aspect
@Component
public class QueriesCriteriaGuard {

  static final DateTimeFormatter LEGACY_DATE = HarvestHistoryRules.LEGACY_DATE;
  /** ca.bc.gov.mof.hbs.dac.common.Constants.LimitYear.SEVEN */
  private static final int SEVEN = 7;
  /** P450 statement cut-over (HBSValidator.validateRestrictLimitingDateRange maxLimitDate). */
  private static final LocalDate HBS_STATEMENTS_START = LocalDate.of(2003, 11, 1);
  private static final Set<String> PIECE_DOCUMENTS = Set.of("PSI", "PSV");
  private static final Set<String> GUARDED = Set.of("harvestHistory.billing", "harvestHistory.scaling",
      "issuedDocuments.search", "billingSummary.search", "statementTransmissions.search", "scaleSiteSummary.search",
      "agedUnbilled.search", "cutToCruise.search");

  private final QueryCriteriaReads reads;
  private final Supplier<LocalDate> today;
  /** The caller must be allowed to run the query before any look-up answers (null in unit tests). */
  private final QueryRegistry registry;

  @Autowired
  public QueriesCriteriaGuard(QueryCriteriaReads reads, Audit audit, @Lazy QueryRegistry registry) {
    this.reads = reads;
    this.today = () -> audit.now().toLocalDateTime().toLocalDate();
    this.registry = registry;
  }

  QueriesCriteriaGuard(QueryCriteriaReads reads, Supplier<LocalDate> today) {
    this.reads = reads;
    this.today = today;
    this.registry = null;
  }

  @Before("execution(* ca.bc.gov.nrs.hbs.api.query.QueryService.*(String, java.util.Map, ..))")
  public void guard(JoinPoint jp) {
    Object[] args = jp.getArgs();
    if (args.length < 2 || !(args[0] instanceof String queryId) || !(args[1] instanceof Map<?, ?> raw)) {
      return;
    }
    @SuppressWarnings("unchecked")
    Map<String, ?> params = (Map<String, ?>) raw;
    if (registry != null && GUARDED.contains(queryId)) {
      registry.query(queryId).capability().require();
    }
    validate(queryId, params);
  }

  void validate(String queryId, Map<String, ?> p) {
    List<Problem> errors = new ArrayList<>();
    switch (queryId) {
      case "harvestHistory.billing" -> harvestHistory(p, errors, false);
      case "harvestHistory.scaling" -> harvestHistory(p, errors, true);
      case "issuedDocuments.search" -> issuedDocuments(p, errors);
      case "billingSummary.search" -> billingSummary(p, errors);
      case "statementTransmissions.search" -> transmissions(p, errors);
      case "scaleSiteSummary.search" -> scaleSiteSummary(p, errors);
      case "agedUnbilled.search" -> agedUnbilled(p, errors);
      case "cutToCruise.search" -> cutToCruise(p, errors);
      default -> {
        return;
      }
    }
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
  }

  // ── P431 / P441 ────────────────────────────────────────────────────────────

  /** MarkMonthlyBillingAction.validateForm, in its order. */
  private void harvestHistory(Map<String, ?> p, List<Problem> errors, boolean scaling) {
    String fromKey = scaling ? "scalingFrom" : "billingFrom";
    String toKey = scaling ? "scalingTo" : "billingTo";
    List<Problem> range = HarvestHistoryRules.interval(fromKey, text(p, fromKey), toKey, text(p, toKey), today.get());
    errors.addAll(range);
    LocalDate from = range.isEmpty() ? HarvestHistoryRules.fromDate(text(p, fromKey)) : null;
    LocalDate to = range.isEmpty() ? HarvestHistoryRules.toDate(text(p, toKey)) : null;

    // isValidCliLoc
    String client = text(p, "clientNumber");
    String loc = upper(p, "clientLocation");
    boolean cliLocValid = client != null
        ? (loc != null ? reads.clientLocationExists(client, loc) : reads.clientHasLocations(client))
        : loc == null;
    if (!cliLocValid) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_OR_LOCATION_INVALID));
    }

    // Mark / Licence radio: every mark must exist; the licence must have marks.
    List<String> marks = list(p, "timberMarks");
    List<String> licences = list(p, "forestFileId");
    if (marks.size() > 10 || licences.size() > 10) {
      errors.add(new Problem(marks.size() > 10 ? "timberMarks" : "forestFileId", QueriesMessages.MAX_TEN));
    }
    if (!marks.isEmpty() && !marks.stream().allMatch(reads::billedMarkExists)) {
      errors.add(new Problem("timberMarks", QueriesMessages.MARK_INVALID));
    }
    if (!licences.isEmpty() && !licences.stream().allMatch(reads::licenceHasMarks)) {
      errors.add(new Problem("forestFileId", QueriesMessages.LICENCE_INVALID));
    }

    // isValidSPGCombination: with two or more of species / product / grade given,
    // the combination must be in SPECIES_PRODUCT_GRADE_XREF during the interval.
    String species = upper(p, "species");
    String product = upper(p, "product");
    String grade = upper(p, "grade");
    int given = (species == null ? 0 : 1) + (product == null ? 0 : 1) + (grade == null ? 0 : 1);
    if (given >= 2 && from != null && to != null
        && !reads.speciesProductGradeInEffect(species, product, grade, from, to)) {
      errors.add(new Problem("species", QueriesMessages.SPG_INVALID));
    }

    // BT9281: Mgmt Unit option needs a type.
    if (text(p, "mgmtUnitNo") != null && text(p, "mgmtUnitType") == null) {
      errors.add(new Problem("mgmtUnitType", LegacyMessages.required("Mgmt Unit Type Code")));
    }

    // "criteria selection insufficient" (Ticket 2041 / BT 8973 / BT 7338): no client,
    // no mark / licence / mgmt unit, all file types, all districts harvested and
    // scaled, and (scaling only) no scale site.
    boolean insufficient = client == null && marks.isEmpty() && licences.isEmpty()
        && text(p, "mgmtUnitType") == null && text(p, "mgmtUnitNo") == null
        && isAll(text(p, "fileType")) && isAll(text(p, "regionDistrict"))
        && isAll(text(p, "regionScaled"))
        && (!scaling || text(p, "scaleSite") == null);
    if (insufficient) {
      errors.add(new Problem("criteriaSelection",
          scaling ? QueriesMessages.SCALING_CRITERIA_INSUFFICIENT : QueriesMessages.BILLING_CRITERIA_INSUFFICIENT));
    }
  }

  // ── P449 / P450 ────────────────────────────────────────────────────────────

  private void issuedDocuments(Map<String, ?> p, List<Problem> errors) {
    String type = upper(p, "documentType");
    boolean ratio = "RS".equals(type);
    LocalDate limit = sevenYearLimit().isAfter(HBS_STATEMENTS_START) ? sevenYearLimit() : HBS_STATEMENTS_START;

    statementFromDate(p, errors, "scaleDateFrom", "Scale From Date", "scaleDateTo", "Scale To Date", limit);
    beforeCurrent(p, errors, "scaleDateTo", "Scale To Date");
    statementFromDate(p, errors, "issueDateFrom", "Issue From Date", "issueDateTo", "Issue To Date", limit);
    beforeCurrent(p, errors, "issueDateTo", "Issue To Date");

    // dateRangeRequired (HBSValidator.validateOneOfManyRequired): not for the single-document
    // search (P449) nor the P453 transmission link (FIND_STMTS_LINK); a range needs both ends.
    if (text(p, "statementNumber") == null && text(p, "transmissionId") == null) {
      int scaleEnds = (text(p, "scaleDateFrom") == null ? 0 : 1) + (text(p, "scaleDateTo") == null ? 0 : 1);
      int issueEnds = (text(p, "issueDateFrom") == null ? 0 : 1) + (text(p, "issueDateTo") == null ? 0 : 1);
      if (scaleEnds + issueEnds == 0 || scaleEnds == 1 || issueEnds == 1) {
        rangeRequired(p, errors, QueriesMessages.ISSUE_OR_SCALE_RANGE_REQUIRED,
            "issueDateFrom", "issueDateTo", "scaleDateFrom", "scaleDateTo");
      }
    }

    // clientNumber: clientNumberMissing, clientNumberAndClientLocation, clientNumberValid
    // (the three client-association fields of the new screen each stand for clientNumber).
    String loc = upper(p, "clientLocation");
    String client = firstText(p, "clientNumber", "sendToClientNumber", "copyToClientNumber");
    if (client == null && loc != null) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_WITH_LOCATION));
    } else if (client != null && loc != null && !reads.clientLocationExists(client, loc)) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_LOCATION_INVALID));
    } else if (client != null && !reads.clientHasLocations(client)) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_INVALID));
    }

    // population: populationStratumYearAllowed (populationStratumYearSortAllowed: the sort
    // radio is now a sortable column, not ported).
    String pop = text(p, "population");
    String strat = text(p, "stratum");
    String year = text(p, "samplingYear");
    boolean psyGiven = pop != null || strat != null || year != null;
    if (psyGiven && PIECE_DOCUMENTS.contains(type)) {
      errors.add(new Problem("population", QueriesMessages.PSY_NOT_FOR_PIECE));
    }
    // year: limitSampleYear (BT6020)
    samplingYearLimit(year, errors);

    // searchFieldAllowed + cross references (BT 13241).
    String site = upper(p, "scaleSite");
    if (ratio && site != null) {
      errors.add(new Problem("scaleSite", QueriesMessages.notForRatioStatements("Scale Site")));
    } else if (site != null && !reads.scaleSiteExists(site)) {
      errors.add(new Problem("scaleSite", LegacyMessages.SCALE_SITE_REF));
    }
    if (ratio && text(p, "scaleDateFrom") != null) {
      errors.add(new Problem("scaleDateFrom", QueriesMessages.notForRatioStatements("Scale from Date")));
    }
    if (ratio && text(p, "scaleDateTo") != null) {
      errors.add(new Problem("scaleDateTo", QueriesMessages.notForRatioStatements("Scale to Date")));
    }
    String mark = upper(p, "timberMark");
    if (ratio && mark != null) {
      errors.add(new Problem("timberMark", QueriesMessages.notForRatioStatements("Timber Mark")));
    } else if (mark != null && !reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", QueriesMessages.TIMBER_MARK_REF));
    }
    if (ratio && text(p, "regionHarvested") != null) {
      errors.add(new Problem("regionHarvested", QueriesMessages.notForRatioStatements("Region/ District Harvested")));
    }
    if (ratio && text(p, "regionScaled") != null) {
      errors.add(new Problem("regionScaled", QueriesMessages.notForRatioStatements("Region/District Scaled")));
    }

    // P450Action.perform (DEFECT 4343): pop / strat / year on a list search.
    if (psyGiven && text(p, "statementNumber") == null) {
      if ("PSI".equals(type)) {
        errors.add(new Problem("population", QueriesMessages.PSY_NOT_FOR_PIECE_INVOICES));
      } else {
        popStratYearDigits(pop, strat, year, errors);
        if (!popStratYearCombination(pop, strat, year, type != null && !"PSI".equals(type))) {
          errors.add(new Problem("population", QueriesMessages.PSY_COMBINATION_INVALID));
        }
      }
    }
  }

  /** P450Form scale/issue From: hbsDate, limitingYearRange (BT 16733 wording), fromBeforeToDate, beforeCurrent. */
  private void statementFromDate(Map<String, ?> p, List<Problem> errors, String key, String label,
      String toKey, String toLabel, LocalDate limit) {
    LocalDate from = date(p, key, label, errors);
    if (from == null) return;
    if (from.isBefore(limit)) {
      errors.add(new Problem(key, QueriesMessages.olderThanSevenYears(label)));
      return;
    }
    LocalDate to = quietDate(p, toKey);
    if (to != null && from.isAfter(to)) {
      errors.add(new Problem(key, QueriesMessages.fromBeforeTo(label, toLabel)));
      return;
    }
    if (from.isAfter(today.get())) {
      errors.add(new Problem(key, LegacyMessages.beforeCurrent(label)));
    }
  }

  // ── P421 ───────────────────────────────────────────────────────────────────

  private void billingSummary(Map<String, ?> p, List<Problem> errors) {
    String scaleType = upper(p, "scaleType");
    // chcBillingDateFrom: hbsDate, limitingYear, beforeCurrent, fromBeforeToDate,
    // billingSummaryDateRange, dateRangeRestriction
    billingSummaryFrom(p, errors, "issueDateFrom", "Billing From Date", "issueDateTo", "Billing To Date");
    // dateRangeRequired
    int billingEnds = (text(p, "issueDateFrom") == null ? 0 : 1) + (text(p, "issueDateTo") == null ? 0 : 1);
    int scaleEnds = (text(p, "scaleDateFrom") == null ? 0 : 1) + (text(p, "scaleDateTo") == null ? 0 : 1);
    if (billingEnds + scaleEnds == 0 || billingEnds == 1 || scaleEnds == 1) {
      rangeRequired(p, errors, QueriesMessages.BILLING_OR_SCALE_RANGE_REQUIRED,
          "issueDateFrom", "issueDateTo", "scaleDateFrom", "scaleDateTo");
    }
    beforeCurrent(p, errors, "issueDateTo", "Billing To Date");
    // chcScaleDateFrom: hbsDate, limitingYearRange (P421: 1 January of year − 7), beforeCurrent,
    // fromBeforeToDate, billingSummaryDateRange, dateRangeRestriction
    billingSummaryFrom(p, errors, "scaleDateFrom", "Scale From Date", "scaleDateTo", "Scale To Date");
    beforeCurrent(p, errors, "scaleDateTo", "Scale To Date");

    String licence = upper(p, "forestFileId");
    if (licence != null && !reads.licenceHasMarks(licence)) {
      errors.add(new Problem("forestFileId", QueriesMessages.LICENCE_INVALID));
    }
    String mark = upper(p, "timberMark");
    if (mark != null && !reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", QueriesMessages.TIMBER_MARK_REF));
    }
    String site = upper(p, "scaleSite");
    if (site != null && !reads.scaleSiteExists(site)) {
      errors.add(new Problem("scaleSite", LegacyMessages.SCALE_SITE_REF));
    }
    if (text(p, "mgmtUnitNo") != null && text(p, "mgmtUnitType") == null) {
      errors.add(new Problem("mgmtUnitType", LegacyMessages.required("Mgmt Unit Type")));
    }
    String year = text(p, "samplingYear");
    samplingYearLimit(year, errors);
    String client = firstText(p, "clientNumber", "sendToClientNumber", "copyToClientNumber");
    if (client != null && !reads.clientHasLocations(client)) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_INVALID));
    }
    // P421Action.perform: pop / strat / year digits and combination (weight / sample only).
    String pop = text(p, "population");
    String strat = text(p, "stratum");
    popStratYearDigits(pop, strat, year, errors);
    if (!popStratYearCombination(pop, strat, year, "W".equals(scaleType) || "S".equals(scaleType))) {
      errors.add(new Problem("population", QueriesMessages.PSY_COMBINATION_INVALID));
    }
  }

  private void billingSummaryFrom(Map<String, ?> p, List<Problem> errors, String key, String label,
      String toKey, String toLabel) {
    LocalDate from = date(p, key, label, errors);
    if (from == null) return;
    LocalDate limit = sevenYearLimit();
    if (from.isBefore(limit)) {
      errors.add(new Problem(key, LegacyMessages.limitingDate(label, LEGACY_DATE.format(limit))));
      return;
    }
    if (from.isAfter(today.get())) {
      errors.add(new Problem(key, LegacyMessages.beforeCurrent(label)));
      return;
    }
    LocalDate to = quietDate(p, toKey);
    if (to == null) return;
    if (from.isAfter(to)) {
      errors.add(new Problem(key, QueriesMessages.fromBeforeTo(label, toLabel)));
      return;
    }
    // billingSummaryDateRange: one month when nothing narrows harvest (district / file type)
    // and scale (district / site / pop-strat-year) — HBSValidator.validateBillingSummaryDateRange.
    boolean scaleAll = text(p, "regionScaled") == null && text(p, "scaleSite") == null
        && text(p, "population") == null;
    boolean harvestAll = text(p, "timberMark") == null && text(p, "forestFileId") == null
        && text(p, "mgmtUnitType") == null && text(p, "mgmtUnitNo") == null
        && text(p, "regionHarvested") == null && text(p, "fileType") == null;
    if (scaleAll && harvestAll && !withinMonths(from, to, 1)) {
      errors.add(new Problem(key, QueriesMessages.ONE_MONTH_LIMIT));
      return;
    }
    // dateRangeRestriction
    if (!withinMonths(from, to, 12)) {
      errors.add(new Problem(key, QueriesMessages.ONE_YEAR_LIMIT));
    }
  }

  // ── P452 ───────────────────────────────────────────────────────────────────

  /** P452Form issueDateFrom/To (the Home "recent" search has no dates): limitingYearRange, beforeCurrent, fromBeforeToDate. */
  private void transmissions(Map<String, ?> p, List<Problem> errors) {
    LocalDate from = date(p, "fromDate", "Transmission From Date", errors);
    if (from != null) {
      LocalDate limit = sevenYearLimit().isAfter(HBS_STATEMENTS_START) ? sevenYearLimit() : HBS_STATEMENTS_START;
      LocalDate to = quietDate(p, "toDate");
      if (from.isBefore(limit)) {
        errors.add(new Problem("fromDate", LegacyMessages.limitingDate("Transmission From Date", LEGACY_DATE.format(limit))));
      } else if (from.isAfter(today.get())) {
        errors.add(new Problem("fromDate", LegacyMessages.beforeCurrent("Transmission From Date")));
      } else if (to != null && from.isAfter(to)) {
        errors.add(new Problem("fromDate", QueriesMessages.fromBeforeTo("Transmission From Date", "Transmission To Date")));
      }
    }
    beforeCurrent(p, errors, "toDate", "Transmission To Date");
    // clientLocation (toggle on Single location): clientLocationValid
    String client = text(p, "clientNumber");
    String loc = upper(p, "clientLocation");
    if (client != null && loc != null && !reads.clientLocationExists(client, loc)) {
      errors.add(new Problem("clientLocation", QueriesMessages.CLIENT_NUMBER_LOCATION_INVALID));
    } else if (client != null && !reads.clientHasLocations(client)) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_INVALID));
    }
  }

  // ── P401 ───────────────────────────────────────────────────────────────────

  private void scaleSiteSummary(Map<String, ?> p, List<Problem> errors) {
    String type = upper(p, "returnType");
    LocalDate from = quietDate(p, "scaleDateFrom");
    if (text(p, "scaleDateFrom") == null) {
      errors.add(new Problem("scaleDateFrom", QueriesMessages.SCALE_FROM_REQUIRED));
    } else if (from == null) {
      errors.add(new Problem("scaleDateFrom", QueriesMessages.SCALE_FROM_INVALID));
    } else if (from.isBefore(sevenYearLimit())) {
      errors.add(new Problem("scaleDateFrom",
          LegacyMessages.limitingDate("Scale From Date", LEGACY_DATE.format(sevenYearLimit()))));
    }
    if (text(p, "scaleDateTo") == null) {
      errors.add(new Problem("scaleDateTo", QueriesMessages.SCALE_TO_REQUIRED));
    } else if (quietDate(p, "scaleDateTo") == null) {
      errors.add(new Problem("scaleDateTo", QueriesMessages.SCALE_TO_INVALID));
    }
    String group = upper(p, "groupBy");
    if (group == null) {
      errors.add(new Problem("groupBy", QueriesMessages.GROUPING_REQUIRED));
    }
    if (type == null) {
      errors.add(new Problem("returnType", QueriesMessages.TYPE_REQUIRED));
    }
    if ("P".equals(type) && "SCALESITEPOPULATION".equals(group)) {
      errors.add(new Problem("groupBy", QueriesMessages.GROUPING_NOT_FOR_PIECE));
    }
    String mark = upper(p, "timberMark");
    if (mark != null && !reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", QueriesMessages.MARK_INVALID));
    }
    String site = upper(p, "scaleSite");
    if (site != null && !reads.scaleSiteExists(site)) {
      errors.add(new Problem("scaleSite", QueriesMessages.SCALE_SITE_INVALID));
    }
    // Client association (BT 1172): "All" takes no client; Mark Holder / Site Owner /
    // Stratum Owner need a client number.
    String association = upper(p, "clientAssociation");
    String client = text(p, "clientNumber");
    String loc = text(p, "clientLocation");
    if (association == null || "ALL".equals(association)) {
      if (client != null || loc != null) {
        errors.add(new Problem("clientNumber", QueriesMessages.ASSOCIATION_REQUIRED));
      }
    } else if (client == null) {
      errors.add(new Problem("clientNumber",
          loc != null ? QueriesMessages.CLIENT_REQUIRED : QueriesMessages.CLIENT_INFO_REQUIRED));
    }
    String pop = text(p, "population");
    String strat = text(p, "stratum");
    String year = text(p, "samplingYear");
    if (pop != null || strat != null || year != null) {
      if ("P".equals(type)) {
        errors.add(new Problem("population", QueriesMessages.PSY_NOT_FOR_PIECE_INVOICES));
      } else {
        if (pop != null && !digits(pop, 4)) {
          errors.add(new Problem("population", QueriesMessages.POPULATION_INVALID));
        }
        if (year != null && !digits(year, 4)) {
          errors.add(new Problem("samplingYear", QueriesMessages.POP_YEAR_INVALID));
        } else {
          samplingYearLimit(year, errors);
        }
        if (strat != null && !digits(strat, 2)) {
          errors.add(new Problem("stratum", QueriesMessages.STRATUM_INVALID));
        }
        if (!popStratYearCombination(pop, strat, year, "W".equals(type) || "S".equals(type))) {
          errors.add(new Problem("population", QueriesMessages.PSY_COMBINATION_INVALID));
        }
      }
    }
    String licence = upper(p, "scalerLicence");
    if (licence != null) {
      if ("W".equals(type)) {
        errors.add(new Problem("scalerLicence", QueriesMessages.SCALER_LICENCE_NOT_FOR_WEIGHT));
      } else if (!reads.scalerLicenceExists(licence)) {
        errors.add(new Problem("scalerLicence", QueriesMessages.LICENCE_INVALID));
      }
    }
  }

  // ── P403 ───────────────────────────────────────────────────────────────────

  private void agedUnbilled(Map<String, ?> p, List<Problem> errors) {
    String s = text(p, "scaledPriorTo");
    LocalDate d = quietDate(p, "scaledPriorTo");
    if (s == null) {
      errors.add(new Problem("scaledPriorTo", QueriesMessages.SCALED_PRIOR_TO_REQUIRED));
    } else if (d == null) {
      errors.add(new Problem("scaledPriorTo", QueriesMessages.SCALED_PRIOR_TO_INVALID));
    } else if (d.isBefore(sevenYearLimit())) {
      errors.add(new Problem("scaledPriorTo",
          LegacyMessages.limitingDate("Scaled Prior To Date", LEGACY_DATE.format(sevenYearLimit()))));
    }
  }

  /**
   * P403Action.validateForm rates: each Estimated Average Rate given, &gt; 0,
   * numeric with at most two decimals ({@code validateCurrency}). Used by the
   * report guard for HBS2R411/412 (the rates are report prompts).
   */
  static List<Problem> averageRates(Map<String, String> rates) {
    List<Problem> errors = new ArrayList<>();
    rates.forEach((key, raw) -> {
      String v = raw == null ? "" : raw.trim();
      try {
        BigDecimal rate = v.isEmpty() ? null : new BigDecimal(v);
        if (rate == null || rate.signum() <= 0) {
          errors.add(new Problem(key, QueriesMessages.RATE_REQUIRED));
        } else if (rate.scale() > 2) {
          errors.add(new Problem(key, QueriesMessages.RATE_NOT_NUMERIC));
        }
      } catch (NumberFormatException ex) {
        errors.add(new Problem(key, QueriesMessages.RATE_NOT_NUMERIC));
      }
    });
    return errors;
  }

  // ── P490 ───────────────────────────────────────────────────────────────────

  private void cutToCruise(Map<String, ?> p, List<Problem> errors) {
    String mark = upper(p, "timberMark");
    if (mark != null && !reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", QueriesMessages.TIMBER_MARK_REF));
    }
    String licence = upper(p, "forestFileId");
    if (licence != null && !reads.licenceHasMarks(licence)) {
      errors.add(new Problem("forestFileId", QueriesMessages.LICENCE_INVALID));
    }
    // chcScaleDateFrom (BT11752): expiryNotEmpty, hbsDate, futureExpirationDate
    String expiry = text(p, "expiryDate");
    if (expiry != null) {
      if (mark != null) {
        errors.add(new Problem("expiryDate", QueriesMessages.EXPIRATION_WITH_MARK));
      } else {
        LocalDate d = date(p, "expiryDate", "Expiration Date", errors);
        if (d != null && d.isAfter(today.get())) {
          errors.add(new Problem("expiryDate", QueriesMessages.EXPIRATION_FUTURE));
        }
      }
    }
    // txtClientNo: clientNumberValid, clientNumberAndClientLocation
    String client = text(p, "clientNumber");
    String loc = upper(p, "clientLocation");
    if (client != null && !reads.clientHasLocations(client)) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_INVALID));
    } else if (client != null && loc != null && !reads.clientLocationExists(client, loc)) {
      errors.add(new Problem("clientNumber", QueriesMessages.CLIENT_NUMBER_LOCATION_INVALID));
    }
  }

  // ── shared pieces ──────────────────────────────────────────────────────────

  /** 1 January of (current year − 7) — HBSValidator.validateYearLimit / DacValidator.compareForYearLimit. */
  private LocalDate sevenYearLimit() {
    return LocalDate.of(today.get().getYear() - SEVEN, 1, 1);
  }

  /** limitSampleYear (DacValidator.limitSamplingYear): year ≥ current year − 7. */
  private void samplingYearLimit(String year, List<Problem> errors) {
    if (year == null || !year.chars().allMatch(Character::isDigit) || year.length() > 9) return;
    int limit = today.get().getYear() - SEVEN;
    if (Integer.parseInt(year) < limit) {
      errors.add(new Problem("samplingYear", QueriesMessages.samplingYearLimit(limit)));
    }
  }

  private static void popStratYearDigits(String pop, String strat, String year, List<Problem> errors) {
    if (pop != null && !digits(pop, 4)) {
      errors.add(new Problem("population", QueriesMessages.POPULATION_INVALID));
    }
    if (year != null && !digits(year, 4)) {
      errors.add(new Problem("samplingYear", QueriesMessages.POP_YEAR_INVALID));
    }
    if (strat != null && !digits(strat, 2)) {
      errors.add(new Problem("stratum", QueriesMessages.STRATUM_INVALID));
    }
  }

  /** P401/P421/P450Action.isValidPopStratumYear: P, P/Y or P/S/Y, only where the type allows it. */
  static boolean popStratYearCombination(String pop, String strat, String year, boolean typeAllows) {
    if (pop == null && strat == null && year == null) return true;
    if (!typeAllows) return false;
    if (pop != null && strat != null && year != null) return digits(pop, 4) && digits(strat, 2) && digits(year, 4);
    if (pop != null && strat == null && year != null) return digits(pop, 4) && digits(year, 4);
    if (pop != null && strat == null) return digits(pop, 4);
    return false;
  }

  /** isValidNumberField: exactly n digits, a non-negative integer. */
  static boolean digits(String v, int n) {
    String t = v.trim();
    return t.length() == n && t.chars().allMatch(Character::isDigit);
  }

  /** DacHelper.isDateRangeWithinMonths: to ≤ from + n months. */
  /**
   * "Enter a date range" (dateRangeRequired) against the fields the user must fill:
   * the missing end of a half-filled range, or every end when no range is given.
   * Args are (from, to) pairs.
   */
  private static void rangeRequired(Map<String, ?> p, List<Problem> errors, String message, String... fromToPairs) {
    boolean anyGiven = false;
    for (String key : fromToPairs) {
      anyGiven |= text(p, key) != null;
    }
    for (int i = 0; i < fromToPairs.length; i += 2) {
      String from = fromToPairs[i];
      String to = fromToPairs[i + 1];
      boolean hasFrom = text(p, from) != null;
      boolean hasTo = text(p, to) != null;
      if (!anyGiven || hasFrom != hasTo) {
        if (!hasFrom) errors.add(new Problem(from, message));
        if (!hasTo) errors.add(new Problem(to, message));
      }
    }
  }

  static boolean withinMonths(LocalDate from, LocalDate to, int months) {
    return !from.plusMonths(months).isBefore(to);
  }

  private void beforeCurrent(Map<String, ?> p, List<Problem> errors, String key, String label) {
    LocalDate d = quietDate(p, key);
    if (text(p, key) != null && d == null) {
      errors.add(new Problem(key, LegacyMessages.invalidDate(label)));
    } else if (d != null && d.isAfter(today.get())) {
      errors.add(new Problem(key, LegacyMessages.beforeCurrent(label)));
    }
  }

  private static boolean isAll(String v) {
    return v == null || "ALL".equalsIgnoreCase(v);
  }

  private static LocalDate date(Map<String, ?> p, String key, String label, List<Problem> errors) {
    String s = text(p, key);
    if (s == null) return null;
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
      errors.add(new Problem(key, LegacyMessages.invalidDate(label)));
      return null;
    }
  }

  private static LocalDate quietDate(Map<String, ?> p, String key) {
    String s = text(p, key);
    if (s == null) return null;
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }

  private static List<String> list(Map<String, ?> p, String key) {
    String s = text(p, key);
    if (s == null) return List.of();
    return Arrays.stream(s.split(",")).map(String::trim).filter(v -> !v.isEmpty())
        .map(v -> v.toUpperCase(Locale.ROOT)).toList();
  }

  private static String firstText(Map<String, ?> p, String... keys) {
    for (String k : keys) {
      String v = text(p, k);
      if (v != null) return v;
    }
    return null;
  }

  static String text(Map<String, ?> p, String key) {
    Object v = p.get(key);
    if (v == null) return null;
    String s = v.toString().trim();
    return s.isEmpty() ? null : s;
  }

  private static String upper(Map<String, ?> p, String key) {
    String s = text(p, key);
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }
}
