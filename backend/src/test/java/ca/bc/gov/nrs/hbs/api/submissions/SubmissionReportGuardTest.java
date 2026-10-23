package ca.bc.gov.nrs.hbs.api.submissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Industry fence on the P032 / P027 drill-down reports HBS2R032 / HBS2R033. */
class SubmissionReportGuardTest {

  private final SubmissionReportGuard.Lookups lookups = new SubmissionReportGuard.Lookups() {
    @Override
    public boolean ownTransmission(long id, String client) {
      return id == 10 && "00012345".equals(client);
    }

    @Override
    public boolean ownBatch(long id, String client) {
      return id == 20 && "00012345".equals(client);
    }
  };

  private HbsReportRequestDto run(boolean ministry, String report, Map<String, String> p) {
    return new SubmissionReportGuard(lookups, () -> ministry, () -> "00012345")
        .apply(report, new HbsReportRequestDto(p, null));
  }

  @Test
  void ministryAndOtherReports_pass() {
    assertThat(run(true, "HBS2R033", Map.of("RB_TRANSMISSION_ID", "99"))).isNotNull();
    assertThat(run(false, "HBS2R027", Map.of())).isNotNull();
  }

  @Test
  void industry_ownTransmissionOrBatchOnly() {
    assertThat(run(false, "HBS2R033", Map.of("RB_TRANSMISSION_ID", "10"))).isNotNull();
    assertThatThrownBy(() -> run(false, "HBS2R033", Map.of("RB_TRANSMISSION_ID", "11")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(run(false, "hbs2r032", Map.of("RB_TRANSMISSION_ID", "99", "RB_BATCH_ID", "20"))).isNotNull();
    assertThatThrownBy(() -> run(false, "HBS2R032", Map.of("RB_TRANSMISSION_ID", "10", "RB_BATCH_ID", "21")))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> run(false, "HBS2R032", Map.of()))
        .isInstanceOf(AccessDeniedException.class);
  }
}
