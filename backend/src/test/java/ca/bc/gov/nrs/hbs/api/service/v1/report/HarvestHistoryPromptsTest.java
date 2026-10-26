package ca.bc.gov.nrs.hbs.api.service.v1.report;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HBS00001R / HBS3R441R build their SQL by concatenation: a blank group type
 * or a mark-type with no marks produced ORA-00900 (see {@link HarvestHistoryPrompts}).
 */
class HarvestHistoryPromptsTest {

  private static Map<String, Object> prompts(String... kv) {
    Map<String, Object> m = new HashMap<>();
    for (String p : new String[] {"INVSMRY_GRPTYPE", "INVSMRY_DISPDTLS", "INVSMRY_DISPSPG", "INVSMRY_LICMARK01",
        "INVSMRY_LICMARKTYPE", "INVSMRY_FILETYPE1", "INVSMRY_MGMTUNITTYPECODE", "INVSMRY_MGMTUNITNO"}) {
      m.put(p, "");
    }
    for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
    return m;
  }

  @Test
  void clientOnlySelection_defaultsGroupingAndSendsNoMarkType() {
    // The failing request: client number only, group type and detail lines blank.
    Map<String, Object> p = prompts("INVSMRY_LICMARKTYPE", "2");
    HarvestHistoryPrompts.apply("HBS3R431", p, Map.of());
    assertThat(p).containsEntry("INVSMRY_GRPTYPE", "1")
        .containsEntry("INVSMRY_DISPDTLS", "TTT")
        .containsEntry("INVSMRY_DISPSPG", "FFF")
        .containsEntry("INVSMRY_LICMARKTYPE", "")
        .containsEntry("INVSMRY_LICMARK01", "");
  }

  @Test
  void intervalIsWholeMonths() {
    // A To date inside the month dropped that month's rows (BILLING_PERIOD <= INVSMRY_TO).
    Map<String, Object> p = prompts("INVSMRY_FROM", "2026-10-15", "INVSMRY_TO", "2026-10-23");
    HarvestHistoryPrompts.apply("HBS3R431", p, Map.of());
    assertThat(p).containsEntry("INVSMRY_FROM", "2026-10-01").containsEntry("INVSMRY_TO", "2026-10-31");

    Map<String, Object> scaling = prompts("INVSMRY_FROM", "2024-01-31", "INVSMRY_TO", "2024-02-01");
    HarvestHistoryPrompts.apply("HBS3R441", scaling, Map.of());
    assertThat(scaling).containsEntry("INVSMRY_FROM", "2024-01-01").containsEntry("INVSMRY_TO", "2024-02-29");
  }

  @Test
  void keepsChosenGrouping() {
    Map<String, Object> p = prompts("INVSMRY_GRPTYPE", "4", "INVSMRY_DISPDTLS", "TFF");
    HarvestHistoryPrompts.apply("HBS3R441", p, Map.of());
    assertThat(p).containsEntry("INVSMRY_GRPTYPE", "4").containsEntry("INVSMRY_DISPDTLS", "TFF");
  }

  @Test
  void splitsMarksIntoSlots() {
    Map<String, Object> p = prompts("INVSMRY_LICMARK01", "ab123, CD456 ,");
    HarvestHistoryPrompts.apply("HBS3R431", p, Map.of());
    assertThat(p).containsEntry("INVSMRY_LICMARKTYPE", "2")
        .containsEntry("INVSMRY_LICMARK01", "AB123")
        .containsEntry("INVSMRY_LICMARK02", "CD456")
        .containsEntry("INVSMRY_LICMARK03", "");
  }

  @Test
  void licencesUseTheSameSlotsWhenNoMarks() {
    Map<String, Object> p = prompts();
    HarvestHistoryPrompts.apply("HBS3R431", p, Map.of(HarvestHistoryPrompts.LICENCES, "A12345,TFL0001"));
    assertThat(p).containsEntry("INVSMRY_LICMARKTYPE", "1")
        .containsEntry("INVSMRY_LICMARK01", "A12345")
        .containsEntry("INVSMRY_LICMARK02", "TFL0001");
  }

  @Test
  void fileTypeThenMgmtUnitSelectTheirType() {
    Map<String, Object> files = prompts("INVSMRY_FILETYPE1", "A01");
    HarvestHistoryPrompts.apply("HBS3R431", files, Map.of());
    assertThat(files).containsEntry("INVSMRY_LICMARKTYPE", "3");

    Map<String, Object> mgmt = prompts("INVSMRY_MGMTUNITTYPECODE", "U");
    HarvestHistoryPrompts.apply("HBS3R431", mgmt, Map.of());
    assertThat(mgmt).containsEntry("INVSMRY_LICMARKTYPE", "4");
  }

  @Test
  void rejectsMoreThanTenOrOverlongValues() {
    assertThatThrownBy(() -> HarvestHistoryPrompts.apply("HBS3R431",
        prompts("INVSMRY_LICMARK01", "A,B,C,D,E,F,G,H,I,J,K"), Map.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("up to 10");
    assertThatThrownBy(() -> HarvestHistoryPrompts.apply("HBS3R431",
        prompts("INVSMRY_LICMARK01", "ABCDEFGHIJK"), Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void otherReportsUntouched() {
    Map<String, Object> p = prompts("INVSMRY_LICMARKTYPE", "2");
    HarvestHistoryPrompts.apply("HBS3R421", p, Map.of());
    assertThat(p).containsEntry("INVSMRY_GRPTYPE", "").containsEntry("INVSMRY_LICMARKTYPE", "2");
  }
}
