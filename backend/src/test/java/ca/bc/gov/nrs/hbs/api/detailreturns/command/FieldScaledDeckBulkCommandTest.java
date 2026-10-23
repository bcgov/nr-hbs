package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.StubUserDomains;
import ca.bc.gov.nrs.hbs.api.detailreturns.WorkflowFixture;
import ca.bc.gov.nrs.hbs.api.detailreturns.command.FieldScaledDeckReturns.Found;
import ca.bc.gov.nrs.hbs.api.detailreturns.command.FieldScaledDeckReturns.Selection;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

/** P842–P844 bulk actions (P840Action.doCancelReturn / doClearFiledScaleFlag). */
class FieldScaledDeckBulkCommandTest {

  private final WorkflowFixture f = new WorkflowFixture();
  private final StubUserDomains domains = new StubUserDomains();
  private final DetailCommandSupport support = new DetailCommandSupport(f.workflow, new DetailAuthority(domains),
      domains, new ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.FormChecksOff(), new Audit("HBSBATCH"));
  private final Map<String, List<Found>> found = new HashMap<>();
  private final List<Selection> asked = new ArrayList<>();
  private final FieldScaledDeckBulkCommand bulk = new FieldScaledDeckBulkCommand(support, s -> {
    asked.add(s);
    return found.getOrDefault(s.psyRow() ? String.valueOf(s.population()) : s.eventCode(), List.of());
  }, new StaticListableBeanFactory().getBeanProvider(PlatformTransactionManager.class));

  FieldScaledDeckBulkCommandTest() {
    support.identity(() -> new Caller("IDIR\\BA", Set.of("HBS_BILL_ADMIN")), () -> "");
    domains.markDistricts.put("ABC123", 7L);
    domains.districts.put("IDIR\\BA", Set.of(7L));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> run(String docType, Map<String, Object>... rows) {
    return (List<Map<String, Object>>) bulk.execute(Map.of("docType", docType, "timberMark", "abc123",
        "fieldScaleDeckId", "*", "rows", List.of(rows))).get("results");
  }

  @Test
  void clearFlagHoldsReleasesWithFieldScaleOff() {
    f.logTally("D1", "OIP", 1, "RDY", true).set("field_scale_ind", "Y").set("field_scale_deck_id", "DK1");
    found.put("PS", List.of(new Found("D1", 1)));
    List<Map<String, Object>> out = run("P", Map.of("eventCode", "PS"));
    assertThat(out).singleElement().satisfies(r -> assertThat(r).containsEntry("status", "PROCESSED"));
    assertThat(f.last("HBS_CREATE_LOG_TALLY").arg("i_scl_rtn_version_state_code")).isEqualTo("HLD");
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_field_scale_ind")).isEqualTo("N");
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_scl_rtn_version_state_code")).isEqualTo("RDY");
    assertThat(asked.get(0).timberMark()).isEqualTo("ABC123");
    assertThat(asked.get(0).deckId()).isEqualTo("*");
  }

  @Test
  void aHeldReturnIsJustClearedAndReleased() {
    f.logTally("D1", "OIP", 1, "HLD", true).set("field_scale_ind", "Y");
    found.put("PS", List.of(new Found("D1", 1)));
    assertThat(run("P", Map.of("eventCode", "PS"))).extracting(r -> r.get("status")).containsExactly("PROCESSED");
    assertThat(f.calls("HBS_CREATE_LOG_TALLY")).isEmpty();
    assertThat(f.last("HBS_STORE_LOG_TALLY").arg("i_field_scale_ind")).isEqualTo("N");
  }

  @Test
  void otherStatesAreNotProcessedAndMissingReturnsAreErrors() {
    f.logTally("D1", "OCM", 1, "ISS", true).set("field_scale_ind", "Y");
    found.put("PS", List.of(new Found("D1", 1), new Found("NOPE", 1)));
    assertThat(run("P", Map.of("eventCode", "PS"))).extracting(r -> r.get("status"))
        .containsExactly("NOTPROCESSED", "ERROR");
  }

  @Test
  void issuedSampleTalliesAreCancelled() {
    f.rows.addDocument("S1", DocType.SAMPLE_LOG_TALLY, "OCM");
    f.rows.addVersion("S1", 1, "ISS", true).set("timber_mark", "ABC123").set("scale_event_type_code", "SS");
    found.put("1001", List.of(new Found("S1", 1)));
    List<Map<String, Object>> out = run("S", Map.of("psy", "1001-01-2026"));
    assertThat(out).extracting(r -> r.get("status")).containsExactly("PROCESSED");
    assertThat(f.last("HBS_STORE_SAMPLE_LOG_TALLY").arg("i_scl_rtn_version_state_code")).isEqualTo("AWP");
    Selection s = asked.get(0);
    assertThat(s.population()).isEqualTo("1001");
    assertThat(s.stratum()).isEqualTo("01");
    assertThat(s.samplingYear()).isEqualTo(2026L);
  }

  @Test
  void aRowWithNoReturnsIsReported() {
    assertThat(run("W", Map.of("psy", " Unknown"))).singleElement()
        .satisfies(r -> assertThat(r).containsEntry("status", "NOTPROCESSED").containsEntry("message",
            "No returns found."));
    assertThat(asked.get(0).psyRow()).isTrue();
    assertThat(asked.get(0).population()).isNull();
  }

  @Test
  void theMarkMustBeInAnAssociatedDistrict() {
    domains.districts.put("IDIR\\BA", Set.of(8L));
    assertThatThrownBy(() -> run("P", Map.of("eventCode", "PS"))).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void selectionSqlFollowsTheLegacyCriteria() {
    String sample = FieldScaledDeckReturns.sql(new Selection("S", "M", "*", true, "1001", "01", 2026L, null));
    assertThat(sample).contains("FROM sample_log_tally a", "scl_rtn_version_state_code = 'ISS'",
        "IN ('SS', 'RS', 'CS')", "check_replaces_original_ind", "a.population_number = :population");
    String unknown = FieldScaledDeckReturns.sql(new Selection("W", "M", "D1", true, null, null, null, null));
    assertThat(unknown).contains("FROM weigh_slip a", "field_scale_ind = 'Y'", "IN ('PS', 'SS', 'RS', 'PD')",
        "a.population_number IS NULL AND a.stratum_number IS NULL AND a.sampling_year IS NULL")
        .doesNotContain("check_replaces_original_ind");
    String log = FieldScaledDeckReturns.sql(new Selection("P", "M", "*", false, null, null, null, "RT"));
    assertThat(log).contains("FROM log_tally a", "a.scale_event_type_code = :eventCode",
        "(:deckId = '*' OR a.field_scale_deck_id = :deckId)").doesNotContain("population_number");
  }
}
