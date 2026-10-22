package ca.bc.gov.nrs.hbs.api.submissions;

import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * The legacy search-form validations of P041 (validation.xml {@code P032Form}),
 * P024 ({@code P024Form}), P901 ({@code P901Form}) and P911
 * ({@code P911Action.validateForm}) for the registry queries that replace
 * them. A registry query has no validation hook, so — like
 * {@code admin.UserDataDomainGuard} for commands — this is an {@code @Before}
 * advice on {@code QueryService}: bad criteria are a 400 with the legacy
 * messages instead of silently returning no rows.
 *
 * <p>Only criteria the new screens offer are checked; checks of legacy
 * fields that no longer exist (radio-button combinations, Y/M/D drop-downs)
 * are not ported. Dates are compared with today in Pacific time.
 */
@Aspect
@Component
public class SearchCriteriaGuard {

  static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.ENGLISH);
  static final Set<String> ARRIVAL_TYPES = Set.of("ARRIVAL", "ARRIVAL_GAP", "ARRIVAL_DUP");

  private final BatchSlipReads reads;
  private final Supplier<LocalDate> today;

  @org.springframework.beans.factory.annotation.Autowired
  public SearchCriteriaGuard(BatchSlipReads reads, Audit audit) {
    this(reads, () -> audit.now().toLocalDateTime().toLocalDate());
  }

  SearchCriteriaGuard(BatchSlipReads reads, Supplier<LocalDate> today) {
    this.reads = reads;
    this.today = today;
  }

  @Before("execution(* ca.bc.gov.nrs.hbs.api.query.QueryService.*(String, java.util.Map, ..))")
  public void guard(JoinPoint jp) {
    Object[] args = jp.getArgs();
    if (args.length < 2 || !(args[0] instanceof String queryId) || !(args[1] instanceof Map<?, ?> raw)) {
      return;
    }
    @SuppressWarnings("unchecked")
    Map<String, ?> params = (Map<String, ?>) raw;
    validate(queryId, params);
  }

  void validate(String queryId, Map<String, ?> p) {
    List<Problem> errors = new ArrayList<>();
    switch (queryId) {
      case "submissions.transmissions" -> {
        receivedDates(p, errors, "Received From Date", "Received To Date", "Transmission Received From Date");
        scaleSite(p, "scaleSiteNo", errors);
      }
      case "submissions.batches" -> {
        receivedDates(p, errors, "From", "To", "Batch Received From Date");
        scaleSite(p, "scaleSiteNo", errors);
        orgUnit(p, errors);
      }
      case "scaleControl.lateSubmissions.search" -> lateSubmissions(p, errors);
      case "scaleControl.anomalies.search" -> anomalies(p, errors);
      default -> {
        return;
      }
    }
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
  }

  /** P032Form / P024Form received dates: hbsDate, beforeCurrent, limitingYear, fromBeforeTo. */
  private void receivedDates(Map<String, ?> p, List<Problem> errors, String fromLabel, String toLabel,
      String limitLabel) {
    LocalDate from = date(p, "receivedFrom", fromLabel, errors);
    LocalDate to = date(p, "receivedTo", toLabel, errors);
    if (from != null) {
      if (from.isAfter(today.get())) errors.add(new Problem("receivedFrom", LegacyMessages.beforeCurrent(fromLabel)));
      limitingYear(from, "receivedFrom", limitLabel, errors);
      if (to != null && from.isAfter(to)) errors.add(new Problem("receivedFrom", LegacyMessages.FROM_BEFORE_TO));
    }
    if (to != null && to.isAfter(today.get())) {
      errors.add(new Problem("receivedTo", LegacyMessages.beforeCurrent(toLabel)));
    }
  }

  /** P901Form: scale dates, scale site, Days Late Limit 1–731. */
  private void lateSubmissions(Map<String, ?> p, List<Problem> errors) {
    LocalDate from = date(p, "scaleDateFrom", "Scale From Date", errors);
    LocalDate to = date(p, "scaleDateTo", "Scale To Date", errors);
    if (from != null) {
      if (from.isAfter(today.get())) errors.add(new Problem("scaleDateFrom", LegacyMessages.beforeCurrent("Scale From Date")));
      if (to != null && from.isAfter(to)) errors.add(new Problem("scaleDateFrom", LegacyMessages.FROM_BEFORE_TO));
      limitingYear(from, "scaleDateFrom", "Scale From Date", errors);
    }
    if (to != null && to.isAfter(today.get())) {
      errors.add(new Problem("scaleDateTo", LegacyMessages.beforeCurrent("Scale To Date")));
    }
    scaleSite(p, "scaleSite", errors);
    String days = text(p, "daysLateLimit");
    if (days != null) {
      String label = "Days Late Limit";
      if (days.length() > 4) {
        errors.add(new Problem("daysLateLimit", LegacyMessages.maxLength(label, 4)));
      } else if (!days.chars().allMatch(Character::isDigit)) {
        errors.add(new Problem("daysLateLimit", LegacyMessages.notNumeric(label)));
      } else if (Long.parseLong(days) < 1) {
        errors.add(new Problem("daysLateLimit", LegacyMessages.lessThan(label, 1)));
      } else if (Long.parseLong(days) > 731) {
        errors.add(new Problem("daysLateLimit", LegacyMessages.greaterThan(label, 731)));
      }
    }
  }

  /** P911Action.validateForm. */
  private void anomalies(Map<String, ?> p, List<Problem> errors) {
    LocalDate from = date(p, "scaleDateFrom", "Event From Date", errors);
    LocalDate to = date(p, "scaleDateTo", "Event To Date", errors);
    if (from != null) {
      limitingYear(from, "scaleDateFrom", "Event From Date", errors);
      boolean validFrom = true;
      if (from.isAfter(today.get())) {
        validFrom = false;
        errors.add(new Problem("scaleDateFrom", LegacyMessages.EVENT_FROM_FUTURE));
      }
      if (validFrom && to != null && from.isAfter(to)) {
        errors.add(new Problem("scaleDateFrom", LegacyMessages.EVENT_FROM_BEFORE_TO));
      }
    }
    LocalDate assessedFrom = date(p, "assessedFrom", "Assessment From Date", errors);
    LocalDate assessedTo = date(p, "assessedTo", "Assessment To Date", errors);
    boolean assessedValid = true;
    if (assessedFrom != null && assessedFrom.isAfter(today.get())) {
      errors.add(new Problem("assessedFrom", LegacyMessages.futureDate("Assessment From Date")));
      assessedValid = false;
    }
    if (assessedTo != null && assessedTo.isAfter(today.get())) {
      errors.add(new Problem("assessedTo", LegacyMessages.futureDate("Assessment To Date")));
      assessedValid = false;
    }
    if (assessedValid && assessedFrom != null && assessedTo != null && assessedFrom.isAfter(assessedTo)) {
      errors.add(new Problem("assessedFrom", LegacyMessages.ASSESSMENT_FROM_TO));
    }
    String site = upper(p, "scaleSite");
    if (site != null) {
      if (!reads.scaleSiteExists(site)) {
        errors.add(new Problem("scaleSite", LegacyMessages.SCALE_SITE_INVALID));
      } else if (!RequestUtil.isMinistryUser() && !reads.scaleSiteOwnedBy(site, RequestUtil.getCurrentClientNumber())) {
        errors.add(new Problem("scaleSite", LegacyMessages.ANOMALY_USER_ACCESS));
      }
    }
    String licence = upper(p, "primaryLicence");
    if (licence != null) {
      String type = upper(p, "anomalyType");
      if (type != null && ARRIVAL_TYPES.contains(type)) {
        errors.add(new Problem("primaryLicence", LegacyMessages.LICENCE_WITH_ARRIVAL));
      }
      if ("ARR_DEP".equals(type)) {
        errors.add(new Problem("primaryLicence", LegacyMessages.LICENCE_WITH_ARR_DEP));
      }
      if ("DEP_ARR".equals(type)) {
        errors.add(new Problem("primaryLicence", LegacyMessages.LICENCE_WITH_DEP_ARR));
      }
      if (!reads.licenceExists(licence)) {
        errors.add(new Problem("primaryLicence", LegacyMessages.LICENCE_NOT_FOUND));
      }
    }
  }

  private void scaleSite(Map<String, ?> p, String key, List<Problem> errors) {
    String site = upper(p, key);
    if (site != null && !reads.scaleSiteExists(site)) {
      errors.add(new Problem(key, LegacyMessages.SCALE_SITE_REF));
    }
  }

  private void orgUnit(Map<String, ?> p, List<Problem> errors) {
    String ou = text(p, "orgUnitNo");
    if (ou == null) return;
    try {
      if (!reads.orgUnitExists(Long.parseLong(ou))) {
        errors.add(new Problem("orgUnitNo", LegacyMessages.REGION_DISTRICT_REF));
      }
    } catch (NumberFormatException ex) {
      errors.add(new Problem("orgUnitNo", LegacyMessages.REGION_DISTRICT_REF));
    }
  }

  /**
   * HBSValidator.validateYearLimit / DacValidator.compareForYearLimit: on or
   * after 1 January of (current year − 7), errors.limitingdate.
   */
  private void limitingYear(LocalDate date, String key, String label, List<Problem> errors) {
    LocalDate limit = LocalDate.of(today.get().getYear() - 7, 1, 1);
    if (date.isBefore(limit)) {
      errors.add(new Problem(key, LegacyMessages.limitingDate(label, LEGACY_DATE.format(limit))));
    }
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

  private static String text(Map<String, ?> p, String key) {
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
