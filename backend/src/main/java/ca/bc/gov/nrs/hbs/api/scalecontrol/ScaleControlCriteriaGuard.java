package ca.bc.gov.nrs.hbs.api.scalecontrol;

import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.queries.QueryCriteriaReads;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * The legacy Scale Control search / entry form rules not covered by
 * {@code submissions.SearchCriteriaGuard} — {@code @Before} advices on
 * {@code QueryService} and {@code CommandService}, 400 with the legacy messages:
 *
 * <ul>
 *   <li>{@code scaleControl.anomalies.search} (P911) — a CLI_SCALER without
 *       CLI_SITE_ADMIN is not offered the arrival anomaly types
 *       ({@code AnomalyTypesTag.removeAnomalies}) and searches by their own
 *       licence ({@code P911Action}, BT 10641; the query's industry fence
 *       {@code ScaleControlCatalog.ANOMALY_CLIENT_SCOPE} includes the anomalies
 *       of the caller's own scaler licences).</li>
 *   <li>{@code scaleControl.transport.search} (P971) — validation.xml {@code P971Form}
 *       and {@code LdsHelper.setCriteria} (a blank To number is the From number).</li>
 *   <li>{@code scaleControl.lds.search} (P975) — validation.xml {@code P975Form}.</li>
 *   <li>{@code scaleControl.lds.violations} (P981) — validation.xml {@code P981Form}.</li>
 *   <li>{@code scaleControl.lds.create/update} (P977/P978) — validation.xml {@code P977Form}.</li>
 * </ul>
 */
@Aspect
@Component
public class ScaleControlCriteriaGuard {

  static final DateTimeFormatter LEGACY_DATE = DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.ENGLISH);
  /** validation.xml P971Form mask for cut block, transport id and LDS number. */
  private static final Pattern ASTERISK_FORMAT = Pattern.compile("^[*]?[^\\s*]+[*]?$");
  /** AnomalyTypesTag: types removed from a scaler's list (P911Form ARRIVAL_*, ARR/DEP mismatches). */
  static final Set<String> SCALER_HIDDEN_TYPES =
      Set.of("ARRIVAL", "ARRIVAL_GAP", "ARRIVAL_DUP", "ARR_DEP", "DEP_ARR");
  /** New text: legacy did not offer these types to the role, so it had no message. */
  static final String SCALER_TYPE_NOT_OFFERED = "This anomaly type is not available to scalers.";

  /** P971 transport types (new screen values) → legacy DacConstants.TransportType labels. */
  private static final Map<String, String> TRANSPORT_LABELS = Map.of(
      "CBD", "Cut Block Departures", "ARR", "Arrivals From Other Sites",
      "DEP", "Departures To Other Sites", "INT", "Intra-Site Events");

  private static final Set<String> QUERIES = Set.of("scaleControl.anomalies.search", "scaleControl.transport.search",
      "scaleControl.lds.search", "scaleControl.lds.violations");
  private static final Set<String> COMMANDS = Set.of("scaleControl.lds.create", "scaleControl.lds.update");

  private final QueryCriteriaReads reads;
  private final Supplier<LocalDate> today;
  private final BooleanSupplier scalerOnly;
  private final QueryRegistry registry;

  @Autowired
  public ScaleControlCriteriaGuard(QueryCriteriaReads reads, Audit audit, @Lazy QueryRegistry registry) {
    this.reads = reads;
    this.today = () -> audit.now().toLocalDateTime().toLocalDate();
    this.scalerOnly = () -> RequestUtil.hasRole(HbsRoles.CLI_SCALER) && !RequestUtil.hasRole(HbsRoles.CLI_SITE_ADMIN);
    this.registry = registry;
  }

  ScaleControlCriteriaGuard(QueryCriteriaReads reads, Supplier<LocalDate> today, BooleanSupplier scalerOnly) {
    this.reads = reads;
    this.today = today;
    this.scalerOnly = scalerOnly;
    this.registry = null;
  }

  @Before("execution(* ca.bc.gov.nrs.hbs.api.query.QueryService.*(String, java.util.Map, ..))")
  public void guardQuery(JoinPoint jp) {
    Object[] args = jp.getArgs();
    if (args.length < 2 || !(args[0] instanceof String id) || !QUERIES.contains(id)
        || !(args[1] instanceof Map<?, ?> raw)) {
      return;
    }
    if (registry != null) {
      registry.query(id).capability().require();
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) raw;
    validateQuery(id, params);
  }

  @Before("execution(* ca.bc.gov.nrs.hbs.api.query.CommandService.execute(..))")
  public void guardCommand(JoinPoint jp) {
    Object[] args = jp.getArgs();
    if (args.length < 2 || !(args[0] instanceof String id) || !COMMANDS.contains(id)) {
      return;
    }
    if (registry != null) {
      registry.command(id).capability().require();
    }
    Map<?, ?> body = args[1] instanceof Map<?, ?> m ? m : Map.of();
    @SuppressWarnings("unchecked")
    Map<String, ?> typed = (Map<String, ?>) body;
    validateLdsEntry(typed);
  }

  void validateQuery(String id, Map<String, Object> p) {
    List<Problem> errors = new ArrayList<>();
    switch (id) {
      case "scaleControl.anomalies.search" -> {
        String type = upper(p, "anomalyType");
        if (type != null && SCALER_HIDDEN_TYPES.contains(type) && scalerOnly.getAsBoolean()) {
          errors.add(new Problem("anomalyType", SCALER_TYPE_NOT_OFFERED));
        }
      }
      case "scaleControl.transport.search" -> transport(p, errors);
      case "scaleControl.lds.search" -> ldsSearch(p, errors);
      case "scaleControl.lds.violations" -> ldsViolations(p, errors);
      default -> {
        return;
      }
    }
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
  }

  // ── P971 ───────────────────────────────────────────────────────────────────

  private void transport(Map<String, Object> p, List<Problem> errors) {
    String type = upper(p, "transportType");
    String typeLabel = TRANSPORT_LABELS.getOrDefault(type, type);
    String mark = upper(p, "timberMark");
    String site = upper(p, "scaleSite");
    String origin = upper(p, "originatingSite");
    String destination = upper(p, "destinationSite");

    // transportType: transportFieldRequired (errors.timber-transport.field-combination.required)
    if (("CBD".equals(type) || "INT".equals(type)) && mark == null && site == null) {
      errors.add(new Problem("timberMark", combinationRequired("Timber Mark", "Scale Site", typeLabel)));
    } else if ("ARR".equals(type) && site == null && origin == null) {
      errors.add(new Problem("scaleSite", combinationRequired("Scale Site", "Originating Site", typeLabel)));
    } else if ("DEP".equals(type) && site == null && destination == null) {
      errors.add(new Problem("scaleSite", combinationRequired("Scale Site", "Destination Site", typeLabel)));
    }
    // numberRangeSelect: transportFieldPermitted (errors.field-type.allow / errors.field-type.notAllow)
    if (!"ARR".equals(type) && origin != null) {
      errors.add(new Problem("originatingSite",
          "Originating Site only permitted with " + TRANSPORT_LABELS.get("ARR") + " option."));
    }
    if (!"DEP".equals(type) && destination != null) {
      errors.add(new Problem("destinationSite",
          "Destination Site only permitted with " + TRANSPORT_LABELS.get("DEP") + " option."));
    }
    if ("INT".equals(type)) {
      if (text(p, "transportId") != null) {
        errors.add(new Problem("transportId", "Transport ID option not permitted with " + typeLabel + " selection."));
      }
      if (text(p, "ldsNumber") != null) {
        errors.add(new Problem("ldsNumber", "LDS Number option not permitted with " + typeLabel + " selection."));
      }
    }
    // effective: dateRequired, hbsDate, dateRangeRestriction, limitingYear ("Event From Date"),
    // beforeCurrent (msg error.date.future); expiry: hbsDate, fromDateBeforeToDate.
    LocalDate from = date(p, "fromDate", "From Date", errors);
    LocalDate to = quietDate(p, "toDate");
    if (text(p, "fromDate") == null) {
      errors.add(new Problem("fromDate", LegacyMessages.required("From Date")));
    } else if (from != null) {
      LocalDate effectiveTo = to == null ? from : to;
      LocalDate limit = LocalDate.of(today.get().getYear() - 7, 1, 1);
      if (from.plusMonths(12).isBefore(effectiveTo)) {
        errors.add(new Problem("fromDate", "Date ranges are restricted to one year."));
      } else if (from.isBefore(limit)) {
        errors.add(new Problem("fromDate", LegacyMessages.limitingDate("Event From Date", LEGACY_DATE.format(limit))));
      } else if (from.isAfter(today.get())) {
        errors.add(new Problem("fromDate", LegacyMessages.futureDate("From Date")));
      }
    }
    if (text(p, "toDate") != null && to == null) {
      errors.add(new Problem("toDate", LegacyMessages.invalidDate("To Date")));
    } else if (from != null && to != null && to.isBefore(from)) {
      errors.add(new Problem("toDate", "To Date cannot be before From Date."));
    }
    // txtTimberMark: timbermarkCrossRef; txtCutBlock: mask, timberMarkRequired
    if (mark != null && !reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", TIMBER_MARK_REF));
    }
    String cutBlock = text(p, "cutBlock");
    if (cutBlock != null) {
      if (!ASTERISK_FORMAT.matcher(cutBlock).matches()) {
        errors.add(new Problem("cutBlock", asteriskFormat("Cut Block")));
      } else if (mark == null) {
        errors.add(new Problem("cutBlock", "Cut Block not permitted without Timber Mark."));
      }
    }
    // txtScaleSite / orgScaleSite / destScaleSite: scale site cross references
    if (site != null && !reads.scaleSiteExists(site)) {
      errors.add(new Problem("scaleSite", LegacyMessages.SCALE_SITE_REF));
    }
    if (origin != null && !reads.scaleSiteExists(origin)) {
      errors.add(new Problem("originatingSite", "Originating Scale Site not in Scale Site table."));
    }
    if (destination != null && !reads.scaleSiteExists(destination)) {
      errors.add(new Problem("destinationSite", "Destination Scale Site not in Scale Site table."));
    }
    // load / weigh slip number ranges (toggle, required, validNumber, fromNumberBeforeToNumber)
    numberRange(p, errors, "loadNumberFrom", "From Load Number", "loadNumberTo", "To Load Number");
    numberRange(p, errors, "weighSlipFrom", "From Weigh Slip Number", "weighSlipTo", "To Weigh Slip Number");
    // transId / ldsNumber: mask
    String transportId = text(p, "transportId");
    if (transportId != null && !ASTERISK_FORMAT.matcher(transportId).matches()) {
      errors.add(new Problem("transportId", asteriskFormat("Transport ID")));
    }
    String lds = text(p, "ldsNumber");
    if (lds != null && !ASTERISK_FORMAT.matcher(lds).matches()) {
      errors.add(new Problem("ldsNumber", asteriskFormat("LDS Number")));
    }
  }

  /**
   * One number range: the From number is required when a To number is given
   * (the legacy radio picked the range), both must be numbers, To ≥ From; a
   * blank To is the From number ({@code LdsHelper.setCriteria}).
   */
  private static void numberRange(Map<String, Object> p, List<Problem> errors, String fromKey, String fromLabel,
      String toKey, String toLabel) {
    String from = text(p, fromKey);
    String to = text(p, toKey);
    if (from == null && to == null) {
      return;
    }
    if (from == null) {
      errors.add(new Problem(fromKey, LegacyMessages.required(fromLabel)));
      return;
    }
    Long fromNo = number(from);
    if (fromNo == null) {
      errors.add(new Problem(fromKey, fromLabel + " is not a valid number."));
      return;
    }
    if (to == null) {
      defaultTo(p, toKey, from);
      return;
    }
    Long toNo = number(to);
    if (toNo == null) {
      errors.add(new Problem(toKey, toLabel + " is not a valid number."));
    } else if (toNo < fromNo) {
      errors.add(new Problem(toKey, toLabel + " cannot be less than " + fromLabel + "."));
    }
  }

  /**
   * {@code LdsHelper.setCriteria}: To = From. The criteria map of a search
   * request is mutable; an immutable one (an internal caller) keeps its To blank.
   */
  private static void defaultTo(Map<String, Object> p, String key, String value) {
    try {
      p.put(key, value);
    } catch (UnsupportedOperationException ignored) {
      // internal caller with fixed criteria
    }
  }

  // ── P975 ───────────────────────────────────────────────────────────────────

  private void ldsSearch(Map<String, Object> p, List<Problem> errors) {
    if (text(p, "timberMark") == null && text(p, "cutBlock") == null && text(p, "clientNumber") == null
        && text(p, "licence") == null && text(p, "district") == null) {
      // oneOfManyRequired (message.required)
      errors.add(new Problem("timberMark", "Please enter the Selection Criteria"));
      return;
    }
    String mark = upper(p, "timberMark");
    if (mark != null && !reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", TIMBER_MARK_REF));
    }
    String client = text(p, "clientNumber");
    if (client != null && !client.chars().allMatch(Character::isDigit)) {
      // integer, arg0 "title.client" (resource=false in validation.xml) — the label is used here.
      errors.add(new Problem("clientNumber", LegacyMessages.notNumeric("Client")));
    }
    String district = text(p, "district");
    if (district != null) {
      Long ou = number(district);
      if (ou == null || !reads.orgUnitExists(ou)) {
        errors.add(new Problem("district", LegacyMessages.REGION_DISTRICT_REF));
      }
    }
  }

  // ── P981 ───────────────────────────────────────────────────────────────────

  private void ldsViolations(Map<String, Object> p, List<Problem> errors) {
    String mark = upper(p, "timberMark");
    if (mark == null) {
      errors.add(new Problem("timberMark", LegacyMessages.required("Timber Mark")));
    } else if (!reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", TIMBER_MARK_REF));
    }
    if (text(p, "cutBlock") == null) {
      errors.add(new Problem("cutBlock", LegacyMessages.required("Cut Block")));
    }
    // chcDateFrom: dateRequired, hbsDate, beforeCurrent, limitingYear ("Arrival From Date"), fromBeforeTo
    LocalDate to = quietDate(p, "arrivalDateTo");
    if (text(p, "arrivalDateFrom") == null) {
      errors.add(new Problem("arrivalDateFrom", LegacyMessages.required("From Date")));
    } else {
      LocalDate from = date(p, "arrivalDateFrom", "From Date", errors);
      LocalDate limit = LocalDate.of(today.get().getYear() - 7, 1, 1);
      if (from != null) {
        if (from.isAfter(today.get())) {
          errors.add(new Problem("arrivalDateFrom", LegacyMessages.beforeCurrent("From Date")));
        } else if (from.isBefore(limit)) {
          errors.add(new Problem("arrivalDateFrom",
              LegacyMessages.limitingDate("Arrival From Date", LEGACY_DATE.format(limit))));
        } else if (to != null && from.isAfter(to)) {
          errors.add(new Problem("arrivalDateFrom", LegacyMessages.FROM_BEFORE_TO));
        }
      }
    }
    // chcDateTo: dateRequired, hbsDate, beforeCurrent
    if (text(p, "arrivalDateTo") == null) {
      errors.add(new Problem("arrivalDateTo", LegacyMessages.required("To Date")));
    } else if (to == null) {
      errors.add(new Problem("arrivalDateTo", LegacyMessages.invalidDate("To Date")));
    } else if (to.isAfter(today.get())) {
      errors.add(new Problem("arrivalDateTo", LegacyMessages.beforeCurrent("To Date")));
    }
  }

  // ── P977 / P978 ────────────────────────────────────────────────────────────

  void validateLdsEntry(Map<String, ?> body) {
    List<Problem> errors = new ArrayList<>();
    String mark = upper(body, "timberMark");
    if (mark == null) {
      errors.add(new Problem("timberMark", LegacyMessages.required("Timber Mark")));
    } else if (!reads.billedMarkExists(mark)) {
      errors.add(new Problem("timberMark", TIMBER_MARK_REF));
    }
    if (text(body, "cutBlockId") == null) {
      errors.add(new Problem("cutBlockId", LegacyMessages.required("Cut Block")));
    }
    integer(body, "startNumber", "Starting No", errors);
    integer(body, "endNumber", "Ending No", errors);
    if (!errors.isEmpty()) {
      throw new WorkflowValidationException(errors);
    }
  }

  private static void integer(Map<String, ?> body, String key, String label, List<Problem> errors) {
    String v = text(body, key);
    if (v == null) {
      errors.add(new Problem(key, LegacyMessages.required(label)));
    } else if (!v.matches("-?\\d{1,10}") || number(v) == null || Math.abs(number(v)) > Integer.MAX_VALUE) {
      errors.add(new Problem(key, LegacyMessages.notNumeric(label)));
    }
  }

  // ── shared ─────────────────────────────────────────────────────────────────

  /** errors.timbermark-ref */
  static final String TIMBER_MARK_REF = "Timber mark must be in Timber Mark table and in Mark Billed Client table.";

  /** errors.timber-transport.field-combination.required={0} or {1} required for {2} option. */
  static String combinationRequired(String a, String b, String type) {
    return a + " or " + b + " required for " + type + " option.";
  }

  /** errors.asterisk-format.invalid */
  static String asteriskFormat(String label) {
    return "Invalid " + label + " format (embedded blank or asterisk).";
  }

  private static Long number(String v) {
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException ex) {
      return null;
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

  private static LocalDate quietDate(Map<String, ?> p, String key) {
    String s = text(p, key);
    if (s == null) return null;
    try {
      return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
    } catch (DateTimeParseException ex) {
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
