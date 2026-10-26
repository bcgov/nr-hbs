package ca.bc.gov.nrs.hbs.api.service.v1.report;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills the harvest-history reports' ({@code HBS3R431} → {@code HBS00001R},
 * {@code HBS3R441} → {@code HBS3R441R}) selection prompts the way legacy
 * {@code ReportingFactory.getPDFInvoiceSummaryReportParameters} did.
 *
 * <p>Both procs concatenate their SQL, so a missing prompt breaks it:
 * <ul>
 *   <li>blank {@code INVSMRY_GRPTYPE} skips every {@code SELECT} branch (ORA-00900);</li>
 *   <li>{@code INVSMRY_LICMARKTYPE} 1/2 with no marks leaves {@code IN ' )'} dangling;</li>
 *   <li>each {@code INVSMRY_LICMARKnn} is one mark or licence ({@code VARRAY OF VARCHAR(10)}).</li>
 * </ul>
 *
 * <p>The interval is whole months, as legacy's month drop-downs sent it
 * ({@code TimberMarkHelper.getBillingDateFrom/To}): the procs compare
 * {@code BILLING_PERIOD} / {@code SCALING_PERIOD} {@code <= INVSMRY_TO}, so a To
 * date inside the month dropped that month's rows.
 *
 * <p>The screen sends timber marks as one comma list in {@code INVSMRY_LICMARK01}
 * and forest file ids in the pseudo prompt {@code INVSMRY_LICENCES}. The
 * selection type is derived like legacy's radio button: marks (2), else
 * licences (1), else file types (3), else management unit (4), else none.
 */
final class HarvestHistoryPrompts {

  static final Set<String> REPORTS = Set.of("HBS3R431", "HBS3R441");
  /** Not a proc prompt: forest file ids from the screen, mapped onto the LICMARK slots. */
  static final String LICENCES = "INVSMRY_LICENCES";

  private static final int SLOTS = 10;
  private static final int SLOT_LENGTH = 10;

  private HarvestHistoryPrompts() {
  }

  static void apply(String reportId, Map<String, Object> params, Map<String, String> supplied) {
    if (!REPORTS.contains(reportId.toUpperCase())) {
      return;
    }
    defaultIfBlank(params, "INVSMRY_GRPTYPE", "1");
    defaultIfBlank(params, "INVSMRY_DISPDTLS", "TTT");
    defaultIfBlank(params, "INVSMRY_DISPSPG", "FFF");
    wholeMonth(params, "INVSMRY_FROM", false);
    wholeMonth(params, "INVSMRY_TO", true);

    List<String> marks = split(text(params, "INVSMRY_LICMARK01"));
    List<String> licences = split(supplied.getOrDefault(LICENCES, ""));
    String type;
    List<String> slots;
    if (!marks.isEmpty()) {
      type = "2";
      slots = marks;
    } else if (!licences.isEmpty()) {
      type = "1";
      slots = licences;
    } else {
      type = !text(params, "INVSMRY_FILETYPE1").isEmpty() ? "3"
          : !text(params, "INVSMRY_MGMTUNITTYPECODE").isEmpty() || !text(params, "INVSMRY_MGMTUNITNO").isEmpty() ? "4"
          : "";
      slots = List.of();
    }
    if (slots.size() > SLOTS) {
      throw new IllegalArgumentException("Enter up to " + SLOTS + " timber marks or forest file IDs");
    }
    for (int i = 1; i <= SLOTS; i++) {
      String value = i <= slots.size() ? slots.get(i - 1) : "";
      if (value.length() > SLOT_LENGTH) {
        throw new IllegalArgumentException("'" + value + "' is longer than " + SLOT_LENGTH + " characters");
      }
      params.put(String.format("INVSMRY_LICMARK%02d", i), value);
    }
    params.put("INVSMRY_LICMARKTYPE", type);
  }

  /** From → first day of its month, To → last day (yyyy-mm-dd, as the procs parse it). */
  private static void wholeMonth(Map<String, Object> params, String name, boolean end) {
    String value = text(params, name);
    if (value.length() < 10) {
      return;
    }
    try {
      LocalDate day = LocalDate.parse(value.substring(0, 10));
      params.put(name, (end ? day.withDayOfMonth(day.lengthOfMonth()) : day.withDayOfMonth(1)).toString());
    } catch (DateTimeParseException ex) {
      // Left as entered: the screen's guard has already rejected bad dates.
    }
  }

  private static void defaultIfBlank(Map<String, Object> params, String name, String value) {
    if (params.containsKey(name) && text(params, name).isEmpty()) {
      params.put(name, value);
    }
  }

  private static String text(Map<String, Object> params, String name) {
    return params.get(name) instanceof String s ? s.trim() : "";
  }

  private static List<String> split(String list) {
    return Arrays.stream(list.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(String::toUpperCase)
        .toList();
  }
}
