package ca.bc.gov.nrs.hbs.api.service.v1.report;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Fills the P453 Transmissions of Issued Statements report's ({@code HBS3R453} →
 * {@code HBS3R036}) date prompts the way legacy
 * {@code ReportingFactory2.getIssuedStatementsReportParameters} did.
 *
 * <p>With a From date the proc adds {@code creation_date >= From AND creation_date <= To}
 * ({@code 'yyyy-mm-dd-hh24-mi-ss'}), so a blank To matched nothing. Legacy required To
 * with From; the new screen doesn't, and its search treats a blank To as open-ended. So:
 * <ul>
 *   <li>From → its start of day; To → its end of day (the search's To is inclusive),
 *       or the end of today when blank;</li>
 *   <li>"Recently Issued" (pseudo prompt {@code RB_RECENT} = Y) with no From → the
 *       last 30 days, as the search does; without it the proc applies no dates.</li>
 * </ul>
 */
final class StatementTransmissionPrompts {

  static final String REPORT = "HBS3R453";
  /** Not a proc prompt: the screen's "Recently Issued (last 30 days)" checkbox. */
  static final String RECENT = "RB_RECENT";
  private static final int RECENT_DAYS = 30;
  private static final ZoneId PACIFIC = ZoneId.of("America/Vancouver");

  private StatementTransmissionPrompts() {
  }

  static void apply(String reportId, Map<String, Object> params, String recent) {
    apply(reportId, params, recent, LocalDate.now(PACIFIC));
  }

  static void apply(String reportId, Map<String, Object> params, String recent, LocalDate today) {
    if (!REPORT.equalsIgnoreCase(reportId)) {
      return;
    }
    LocalDate from = day(params.get("RB_FROM_DATE"));
    if (from == null && "Y".equalsIgnoreCase(recent)) {
      from = today.minusDays(RECENT_DAYS);
    }
    if (from == null) {
      return;
    }
    LocalDate to = day(params.get("RB_TO_DATE"));
    params.put("RB_FROM_DATE", from + " 00:00:00");
    params.put("RB_TO_DATE", (to == null ? today : to) + " 23:59:59");
  }

  private static LocalDate day(Object value) {
    if (!(value instanceof String s) || s.trim().length() < 10) {
      return null;
    }
    try {
      return LocalDate.parse(s.trim().substring(0, 10));
    } catch (DateTimeParseException ex) {
      return null;
    }
  }
}
