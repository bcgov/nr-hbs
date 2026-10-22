package ca.bc.gov.nrs.hbs.api.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TableApiTest {

  private final RecordingTableApi api = new RecordingTableApi();

  private ProcArgs transmission() {
    ProcArgs a = ProcArgs.of();
    api.signatures().get("HBS_STORE_DTL_SCL_DOC_TRANS").params().forEach(p -> a.put(p.name(), null));
    return a;
  }

  @Test
  void signaturesLoadFromNrMofDb() {
    assertThat(api.signatures().size()).isGreaterThan(400);
    assertThat(api.signatures().get("THE.hbs_store_dtl_scl_doc_trans").params()).hasSize(24);
  }

  @Test
  void bindsByNameInDeclaredOrderAndConverts() {
    api.call("HBS_STORE_DTL_SCL_DOC_TRANS", transmission()
        .put("i_transmission_id", 42)
        .put("i_file_datetime", LocalDate.of(2026, 10, 1))
        .put("I_FILE_NAME", "a.xml"));
    var call = api.calls.get(0);
    assertThat(call.arg("i_transmission_id")).isEqualTo(BigDecimal.valueOf(42));
    assertThat(call.arg("i_file_datetime")).isEqualTo(Timestamp.valueOf("2026-10-01 00:00:00"));
    assertThat(call.arg("i_file_name")).isEqualTo("a.xml");
    assertThat(call.args().keySet()).first().isEqualTo("i_transmission_id");
  }

  @Test
  void refusesUnknownAndMissingParameters() {
    assertThatThrownBy(() -> api.call("HBS_STORE_DTL_SCL_DOC_TRANS", transmission().put("i_nope", 1)))
        .hasMessageContaining("i_nope");
    assertThatThrownBy(() -> api.call("HBS_STORE_DTL_SCL_DOC_TRANS", ProcArgs.of().put("i_transmission_id", 1)))
        .hasMessageContaining("needs parameter(s)");
    assertThatThrownBy(() -> api.call("HBS_NOT_A_PROC", ProcArgs.of()))
        .hasMessageContaining("not an HBS procedure");
  }

  @Test
  void refusesBooleansForIndicators() {
    assertThatThrownBy(() -> api.call("HBS_STORE_DTL_SCL_DOC_TRANS", transmission().put("i_accepted_ind", true)))
        .hasMessageContaining("i_accepted_ind");
  }

  @Test
  void argsFromRowMapsColumnsToIParams() {
    var args = api.argsFromRow("HBS_STORE_DTL_SCL_DOC_TRANS",
        java.util.Map.of("TRANSMISSION_ID", 7, "FILE_NAME", "x.xml", "UNRELATED", "z"));
    assertThat(args.get("i_transmission_id")).isEqualTo(7);
    assertThat(args.get("i_file_name")).isEqualTo("x.xml");
    assertThat(args.has("unrelated")).isFalse();
  }

  @Test
  void rowLockComparesToTheSecond() {
    RowLock.check(Timestamp.valueOf("2026-10-01 10:00:00.4"), "2026-10-01T10:00:00");
    RowLock.check(Timestamp.valueOf("2026-10-01 10:00:00"), null);
    RowLock.check(Timestamp.valueOf("2026-10-01 00:00:00"), "2026-10-01");
    assertThatThrownBy(() -> RowLock.check(Timestamp.valueOf("2026-10-01 10:00:01"), "2026-10-01 10:00:00"))
        .isInstanceOf(StaleUpdateException.class);
  }
}
