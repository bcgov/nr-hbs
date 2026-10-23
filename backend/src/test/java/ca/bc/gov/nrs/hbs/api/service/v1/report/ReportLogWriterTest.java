package ca.bc.gov.nrs.hbs.api.service.v1.report;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.legacyrules.FixedAudit;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportFormat;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** PresentationHelper.logReport → HBS_CREATE_REPORT_LOG. */
class ReportLogWriterTest {

  @Test
  void writesTheLegacyAuditRow() {
    RecordingTableApi api = new RecordingTableApi();
    new ReportLogWriter(api, new FixedAudit(LocalDateTime.of(2026, 10, 21, 9, 30))).record("HBS3R431", HbsReportFormat.CSV);
    assertThat(api.names()).containsExactly("HBS_CREATE_REPORT_LOG");
    var call = api.calls.get(0);
    assertThat(call.arg("i_request_id").toString()).isEqualTo("1001");
    assertThat(call.arg("i_user_id")).isEqualTo(FixedAudit.USER);
    assertThat(call.arg("i_hbs_report_code")).isEqualTo("HBS3R431");
    assertThat(call.arg("i_hbs_report_status_code")).isEqualTo("SUB");
    assertThat(call.arg("i_mime_type_code")).isEqualTo("CSV");
    assertThat(call.arg("i_report_url").toString()).startsWith("Job Number: ");
  }

  @Test
  void aFailedWriteDoesNotFailTheReport() {
    RecordingTableApi api = new RecordingTableApi();
    api.answer("HBS_CREATE_REPORT_LOG", c -> {
      throw new org.springframework.dao.DataIntegrityViolationException("FK HBS_HRL_HBS_HRC_FK");
    });
    new ReportLogWriter(api, new FixedAudit(LocalDateTime.of(2026, 10, 21, 9, 30))).record("HBS2R552docd", null);
    assertThat(api.names()).containsExactly("HBS_CREATE_REPORT_LOG");
  }
}
