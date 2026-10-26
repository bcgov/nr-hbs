package ca.bc.gov.nrs.hbs.api.service.v1.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** HBS3R036 compares creation_date <= To whenever From is set, so a blank To printed nothing. */
class StatementTransmissionPromptsTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

  private static Map<String, Object> prompts(String from, String to) {
    Map<String, Object> m = new HashMap<>();
    m.put("RB_FROM_DATE", from);
    m.put("RB_TO_DATE", to);
    return m;
  }

  @Test
  void fromOnly_toIsEndOfToday() {
    Map<String, Object> p = prompts("2026-09-01", "");
    StatementTransmissionPrompts.apply("HBS3R453", p, "", TODAY);
    assertThat(p).containsEntry("RB_FROM_DATE", "2026-09-01 00:00:00")
        .containsEntry("RB_TO_DATE", "2026-10-23 23:59:59");
  }

  @Test
  void toDateIsInclusive() {
    Map<String, Object> p = prompts("2026-09-01", "2026-09-30");
    StatementTransmissionPrompts.apply("HBS3R453", p, null, TODAY);
    assertThat(p).containsEntry("RB_TO_DATE", "2026-09-30 23:59:59");
  }

  @Test
  void recentWithoutFrom_isLast30Days() {
    Map<String, Object> p = prompts("", "");
    StatementTransmissionPrompts.apply("HBS3R453", p, "Y", TODAY);
    assertThat(p).containsEntry("RB_FROM_DATE", "2026-09-23 00:00:00")
        .containsEntry("RB_TO_DATE", "2026-10-23 23:59:59");
  }

  @Test
  void noDates_leftForTheProc() {
    Map<String, Object> p = prompts("", "");
    StatementTransmissionPrompts.apply("HBS3R453", p, "N", TODAY);
    assertThat(p).containsEntry("RB_FROM_DATE", "").containsEntry("RB_TO_DATE", "");

    Map<String, Object> other = prompts("2026-09-01", "");
    StatementTransmissionPrompts.apply("HBS3R461", other, "", TODAY);
    assertThat(other).containsEntry("RB_TO_DATE", "");
  }
}
