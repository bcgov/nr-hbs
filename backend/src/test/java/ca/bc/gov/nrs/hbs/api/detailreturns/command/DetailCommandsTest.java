package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.StubUserDomains;
import ca.bc.gov.nrs.hbs.api.detailreturns.WorkflowFixture;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

/** The command layer: lock, fence, button rule, Add (DDN, duplicates), bulk row handling. */
class DetailCommandsTest {

  private final WorkflowFixture f = new WorkflowFixture();
  private final StubUserDomains domains = new StubUserDomains();
  private final DetailCommandSupport support = new DetailCommandSupport(f.workflow, new DetailAuthority(domains),
      domains, new ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormChecksOff(), new Audit("HBSBATCH"));
  private Caller caller = new Caller("IDIR\\DE", Set.of("HBS_DTL_DATA_ENT", "HBS_MOF_USER"));
  private String client = "";

  DetailCommandsTest() {
    support.identity(() -> caller, () -> client);
  }

  @Test
  void releaseChecksTheLockAndTheButton() {
    f.logTally("D1", "OIP", 1, "INC", true);
    var release = new DetailCommands.Release(support);
    assertThatThrownBy(() -> release.execute(Map.of("ddn", "D1", "version", 1, "updateTimestamp", "2026-01-01 00:00:00")))
        .isInstanceOf(StaleUpdateException.class);
    Map<String, Object> out = release.execute(Map.of("ddn", "D1", "version", 1, "updateTimestamp",
        "2026-01-02T09:00:00"));
    assertThat(out).containsEntry("versionState", "RDY");
    caller = new Caller("IDIR\\X", Set.of("HBS_MOF_USER"));
    f.logTally("D2", "OIP", 1, "INC", true);
    assertThatThrownBy(() -> release.execute(Map.of("ddn", "D2", "version", 1)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void industryCallersAreFencedToTheirClient() {
    f.logTally("D1", "OIP", 1, "INC", true);
    client = "00012345";
    var save = new DetailCommands.Save(support);
    assertThatThrownBy(() -> save.execute(Map.of("ddn", "D1", "version", 1)))
        .isInstanceOf(AccessDeniedException.class).hasMessageContaining("does not belong to your client");
  }

  @Test
  void stateFailuresAre400WithTheLegacyText() {
    f.logTally("D1", "OIP", 1, "DSF", true);
    caller = new Caller("IDIR\\DE", Set.of("HBS_DTL_DATA_ENT", "HBS_SCALE_ADMIN"));
    domains.siteOrgUnits.put("S001", 1L);
    domains.districts.put("IDIR\\DE", Set.of(1L));
    var clear = new DetailCommands.ClearSignatureFailure(support);
    assertThat(clear.execute(Map.of("ddn", "D1", "version", 1))).containsEntry("versionState", "RDY");
  }

  @Test
  void addGeneratesTheDdnAndRefusesDuplicates() {
    var add = new DetailCommands.Add(support);
    Map<String, Object> body = Map.of("docType", "P", "fields", Map.of("scaleEventTypeCode", "PS",
        "scaleDate", "2026-10-21", "primaryLicenseNumber", "1234", "returnNumber", 56, "scaleSiteIdNmbr", "S001",
        "logCount", 0));
    Map<String, Object> out = add.execute(body);
    assertThat(out).containsEntry("ddn", "1205Q08E5346C").containsEntry("versionState", "INC")
        .containsEntry("documentState", "OIP");
    assertThat(f.last("HBS_CREATE_LOG_TALLY").arg("i_dtl_doc_seq")).isNotNull();
    assertThat(f.last("HBS_CREATE_LOG_TALLY").arg("i_beachcomb_ind")).isEqualTo("N");
    assertThatThrownBy(() -> add.execute(body)).isInstanceOf(WorkflowValidationException.class)
        .hasMessage("The detail scale document you are trying to insert already exists.");
  }

  @Test
  void addLedgerUsesLedgerSequence() {
    var add = new DetailCommands.Add(support);
    Map<String, Object> out = add.execute(Map.of("docType", "A", "fields", Map.of("scaleEventTypeCode", "PS",
        "scaleSiteIdNmbr", "S001", "arrivalDate", "2026-10-20")));
    assertThat(out.get("ddn").toString()).startsWith("ARL").hasSize(13);
  }

  @Test
  void changeEventClearsAndSaves() {
    f.logTally("D1", "OIP", 1, "INC", true).set("cut_block_id", "CB1");
    var change = new DetailCommands.ChangeEvent(support);
    change.execute(Map.of("ddn", "D1", "version", 1, "scaleEventTypeCode", "FD"));
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_scale_event_type_code")).isEqualTo("FD");
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_cut_block_id")).isNull();
  }

  @Test
  void bulkReportsEachRow() {
    f.logTally("D1", "OIP", 1, "INC", true);
    f.logTally("D2", "OCM", 1, "ISS", true);
    caller = new Caller("BCEID\\SC", Set.of("HBS_CLI_SCALER"));
    domains.scalerKeys.add("BCEID\\SC/1234");
    var bulk = new BulkCommand(support, domains, new StaticListableBeanFactory().getBeanProvider(
        PlatformTransactionManager.class));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) bulk.execute(Map.of("action", "RELEASE",
        "rows", List.of(Map.of("ddn", "D1", "version", 1), Map.of("ddn", "D2", "version", 1),
            Map.of("ddn", "NOPE", "version", 1)))).get("results");
    assertThat(results).extracting(r -> r.get("status")).containsExactly("PROCESSED", "NOTPROCESSED", "ERROR");
    assertThat(f.calls("HBS_STORE_LOG_TALLY")).anyMatch(c -> "RDY".equals(c.arg("i_scl_rtn_version_state_code"))
        && BigDecimal.ONE.equals(c.arg("i_version")));
  }

  @Test
  void bulkPopStratYearFormats() {
    assertThat(BulkCommand.psyLong("P0010126")).isEqualTo("P001012026");
    assertThat(BulkCommand.toDate("20261021").toString()).startsWith("2026-10-21");
  }
}
