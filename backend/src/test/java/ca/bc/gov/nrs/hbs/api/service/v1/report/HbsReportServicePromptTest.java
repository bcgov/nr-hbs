package ca.bc.gov.nrs.hbs.api.service.v1.report;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Several legacy report procs concatenate prompt values into dynamic SQL, so
 * HbsReportService whitelists prompt characters before filling a report.
 */
class HbsReportServicePromptTest {

  private static final HbsReportDefinition R051 =
      new HbsReportDefinition("HBS2R051", "t", "HBS2R051", List.of("PSR_TIMBERMARK"), List.of());
  private static final HbsReportDefinition R972 =
      new HbsReportDefinition("HBS3R972", "t", "HBS3R972", List.of("CBD_FROMDATE"), List.of());

  @Test
  void acceptsOrdinaryLegacyValues() {
    assertThat(HbsReportService.safePrompt(R051, "PSR_TIMBERMARK", " ab123 ")).isEqualTo("ab123");
    assertThat(HbsReportService.safePrompt(R051, "PSR_LIST", "P,W,S")).isEqualTo("P,W,S");
    assertThat(HbsReportService.safePrompt(R051, "PSR_FROM", "2024-01-31")).isEqualTo("2024-01-31");
    assertThat(HbsReportService.safePrompt(R051, "PSR_EMPTY", null)).isEmpty();
  }

  @Test
  void rejectsSqlInjectionAttempts() {
    for (String bad : List.of("x' OR '1'='1", "a;DROP TABLE t", "a -- comment", "\"q\"")) {
      assertThatThrownBy(() -> HbsReportService.safePrompt(R051, "PSR_TIMBERMARK", bad))
          .as(bad).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void reformatsDatesForMonDateReports() {
    assertThat(HbsReportService.safePrompt(R972, "CBD_FROMDATE", "2024-03-05")).isEqualTo("2024-Mar-05");
    assertThat(HbsReportService.safePrompt(R051, "PSR_FROM", "2024-03-05")).isEqualTo("2024-03-05");
  }

  @Test
  void numericPromptsAreConverted() throws Exception {
    // HBS2R411 declares Avg_Rate_* as Double: a String failed with ClassCastException while filling.
    JasperReport report;
    try (InputStream in = getClass().getResourceAsStream("/reports/hbs/HBS2R411/HBS2R411.jrxml")) {
      report = JasperCompileManager.compileReport(in);
    }
    Map<String, Object> params = new HashMap<>();
    params.put("Avg_Rate_Coast", " 12.5 ");
    params.put("Avg_Rate_NInterior", "");
    params.put("PSAGED_SCALED_PRIORTO", "2026-01-01");
    HbsReportService.coerce(report, params);
    assertThat(params).containsEntry("Avg_Rate_Coast", 12.5)
        .containsEntry("Avg_Rate_NInterior", null)
        .containsEntry("PSAGED_SCALED_PRIORTO", "2026-01-01");

    params.put("Avg_Rate_SInterior", "abc");
    assertThatThrownBy(() -> HbsReportService.coerce(report, params)).isInstanceOf(IllegalArgumentException.class);
  }
}
