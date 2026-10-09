package ca.bc.gov.nrs.hbs.api.service.v1.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Report procs that paste prompts into SQL unquoted (see {@link ReportPromptRules}):
 * blank required prompts and values that would rewrite the SQL are refused
 * before the proc runs.
 */
class ReportPromptRulesTest {

  private static final HbsReportDefinition R152 =
      new HbsReportDefinition("HBS2R152", "t", "HBS2R152", List.of(), List.of());

  private static Map<String, String> prompts(String... kv) {
    Map<String, String> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
    return m;
  }

  @Test
  void blankRequiredPrompt_isA400NotAnOra00936() {
    // The failure that surfaced this: HBS2R152 run with no document number.
    assertThatThrownBy(() -> ReportPromptRules.check("HBS2R152",
        prompts("SSRETURN_DOCUMENTNUMBER", "", "SSRETURN_VERSIONNUMBER", "1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DOCUMENTNUMBER");
    assertThatCode(() -> ReportPromptRules.check("HBS2R152",
        prompts("SSRETURN_DOCUMENTNUMBER", "123456", "SSRETURN_VERSIONNUMBER", "1")))
        .doesNotThrowAnyException();
  }

  @Test
  void unquotedNumericPrompt_refusesSqlThatPassesTheCharacterWhitelist() {
    // Only letters, digits, spaces and parentheses — allowed by SAFE_PROMPT,
    // but pasted after "DOCUMENT_CONTROL_NUMBER = " it would match every row.
    for (String bad : List.of("1 OR 1 IN (1)", "1 OR 1 IS NOT NULL", "(SELECT MAX(x) FROM t)")) {
      assertThatCode(() -> HbsReportService.safePrompt(R152, "X", bad)).as(bad).doesNotThrowAnyException();
      assertThatThrownBy(() -> ReportPromptRules.check("HBS2R152",
          prompts("SSRETURN_DOCUMENTNUMBER", bad, "SSRETURN_VERSIONNUMBER", "1")))
          .as(bad).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void listsCodesAndOrderBy_takeOnlyTheirCharacters() {
    assertThatCode(() -> ReportPromptRules.check("HBS3R417", prompts("INVOICE_LIST01", "1234, 5678")))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> ReportPromptRules.check("HBS3R417", prompts("INVOICE_LIST01", "1) OR (1 IN (1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReportPromptRules.check("HBS3R417", prompts()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> ReportPromptRules.check("HBS3R421", prompts("RB_FILE_TYPE1", "A01,B07")))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> ReportPromptRules.check("HBS3R421", prompts("RB_FILE_TYPE1", "A01) OR (1 IN (1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReportPromptRules.check("HBS3R211", prompts("PDDR_ORDER_BY", "1, (SELECT 1 FROM dual)")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rowRange_needsAMaxWhenAMinIsGiven_andNeitherOtherwise() {
    assertThatCode(() -> ReportPromptRules.check("HBS2R552", prompts("PSRETURN_VERSIONNUMBER", "1")))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> ReportPromptRules.check("HBS2R552docd", prompts("PSMINREC", "1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MAXREC");
  }

  @Test
  void fileTypeLists_areQuotedForTheProcsInClause() {
    Map<String, Object> p = new HashMap<>(Map.of("RB_FILE_TYPE1", "a01, B07", "RB_FILE_TYPE2", "C10",
        "RB_FILE_TYPE3", "", "RB_LICENCE", "X"));
    ReportPromptRules.quoteLists("HBS3R421", p);
    assertThat(p).containsEntry("RB_FILE_TYPE1", "'A01','B07','C10'")
        .containsEntry("RB_FILE_TYPE2", "").containsEntry("RB_LICENCE", "X");

    Map<String, Object> all = new HashMap<>(Map.of("INVSMRY_FILETYPE1", "ALL"));
    ReportPromptRules.quoteLists("HBS3R441", all);
    assertThat(all).containsEntry("INVSMRY_FILETYPE1", "ALL");
  }

  @Test
  void reportsWithoutRules_areUnaffected() {
    assertThatCode(() -> ReportPromptRules.check("HBS3R755", prompts("ANY", "1 OR 1 IN (1)")))
        .doesNotThrowAnyException();
  }

  /** Guards against a typo in a rule silently leaving a prompt unchecked. */
  @Test
  void everyRulePrompt_isAPromptOfThatReport() throws Exception {
    JsonNode root;
    try (InputStream in = new ClassPathResource("reports/hbs/catalog.json").getInputStream()) {
      root = new ObjectMapper().readTree(in);
    }
    JsonNode reports = root.isArray() ? root : root.get("reports");
    Map<String, Set<String>> params = new HashMap<>();
    for (JsonNode r : reports) {
      Set<String> names = new HashSet<>();
      r.get("parameters").forEach(p -> names.add(p.asText()));
      params.put(r.get("id").asText().toUpperCase(), names);
    }
    for (String report : List.of("HBS2R027", "HBS2R032", "HBS2R033", "HBS2R051", "HBS2R052", "HBS2R101", "HBS2R102",
        "HBS2R151", "HBS2R152", "HBS2R551", "HBS2R552", "HBS2R552DOCD", "HBS2R601", "HBS2R602", "HBS2R602DOCD",
        "HBS2R651", "HBS2R652", "HBS2R652DOCD", "HBS2R701", "HBS2R702", "HBS2R401GROUPDISTSCALE",
        "HBS2R403GROUPSCALETM", "HBS3R211", "HBS3R416", "HBS3R417", "HBS3R421", "HBS3R422", "HBS3R431", "HBS3R441",
        "HBS3R451", "HBS3R461", "HBS3R471", "HBS3R476", "HBS3R829", "HBS3R852", "HBS3R902", "HBS3R976")) {
      ReportPromptRules.Rules rules = ReportPromptRules.forReport(report);
      assertThat(params).as(report).containsKey(report);
      Set<String> named = new HashSet<>(rules.kinds().keySet());
      named.addAll(rules.required());
      named.addAll(rules.requiredWith().keySet());
      named.addAll(rules.requiredWith().values());
      assertThat(named).as(report + " rules").isNotEmpty();
      assertThat(params.get(report)).as(report + " prompts").containsAll(named);
    }
  }
}
