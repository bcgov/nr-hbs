package ca.bc.gov.nrs.hbs.api.detailreturns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import org.junit.jupiter.api.Test;

/** P554AAction / P604AAction / P654AAction field clearing. */
class EventChangeTest {

  private static DetailDocument doc(DocType type, String event) {
    VersionState vs = new VersionState("INC", true);
    Row v = new Row().set("scale_event_type_code", event).set("timber_mark", "ABC123").set("cut_block_id", "CB1")
        .set("beachcomb_ind", "N").set("field_scale_ind", "N").set("incoming_lds_number", "L1")
        .set("load_arrival_number", "A1").set("orig_redtag_scale_site_id_nmbr", "S002")
        .set("original_ddn", "DDN0").set("orig_chk_scl_license_number", "9999").set("parcel_count", 1)
        .set("transport_identifier", "T1").set("red_tag_weigh_slip", "123").set("log_count", 3)
        .set("destination", "S003").set("outgoing_lds_number", "O1").set("sample_deduction_weight", 5)
        .set("gross_weight", 100).set("tare_weight", 10).set("hbs_sample_type_code", "S")
        .set("population_number", "P001").set("stratum_number", "01").set("sampling_year", 2026);
    DetailVersion ver = new DetailVersion(v, vs, true);
    ver.logs().add(new LogLine(new Row().set("logdtl_id", 1)));
    return new DetailDocument(type, new Row(), new DocumentState("OIP", vs), ver, true);
  }

  @Test
  void sameEventIsRefused() {
    assertThatThrownBy(() -> EventChange.apply(doc(DocType.LOG_TALLY, "PS"), "PS"))
        .isInstanceOf(WorkflowValidationException.class).hasMessage("Cannot change to same Event Type");
  }

  @Test
  void logTallyToCompanyUseClearsMarkLogsAndScaleFields() {
    DetailDocument d = doc(DocType.LOG_TALLY, "PS");
    EventChange.apply(d, "CU");
    Row v = d.version().row();
    assertThat(v.get("timber_mark")).isNull();
    assertThat(v.get("cut_block_id")).isNull();
    assertThat(v.get("beachcomb_ind")).isNull();
    assertThat(v.get("incoming_lds_number")).isNull();
    assertThat(v.get("original_ddn")).isNull();
    assertThat(v.lng("log_count")).isZero();
    assertThat(d.version().logs()).isEmpty();
  }

  @Test
  void logTallyToRemoteRedTagKeepsOriginatingSite() {
    DetailDocument d = doc(DocType.LOG_TALLY, "PS");
    EventChange.apply(d, "RR");
    Row v = d.version().row();
    assertThat(v.str("orig_redtag_scale_site_id_nmbr")).isEqualTo("S002");
    assertThat(v.str("timber_mark")).isEqualTo("ABC123");
    assertThat(v.get("original_ddn")).isNull();
    assertThat(d.version().logs()).hasSize(1);
  }

  /**
   * P554AAction: a submitted version's empty input field gets the original value
   * or " "; an input that already holds " " is not empty ({@code StringUtil.empty}
   * keeps whitespace) and is left alone.
   */
  @Test
  void submittedRemoteRedTagFillsEmptyInputsButKeepsASingleSpace() {
    DetailDocument d = doc(DocType.LOG_TALLY, "PS");
    Row v = d.version().row();
    v.set("batdoc_id", 7L).set("cut_block_id", null).set("input_orig_redtag_scl_site", " ");
    EventChange.apply(d, "RR");
    assertThat(v.str("input_orig_redtag_scl_site")).isEqualTo(" ");
    assertThat(v.str("input_cutblock_id")).isEqualTo(" ");
    assertThat(v.str("input_source")).isEqualTo("ABC123"); // empty input source, brand empty → the mark
  }

  @Test
  void weighSlipToSb4MovesMarkToBrandAndClearsWeights() {
    DetailDocument d = doc(DocType.WEIGH_SLIP, "PS");
    EventChange.apply(d, "4R");
    Row v = d.version().row();
    assertThat(v.str("timber_brand")).isEqualTo("ABC123");
    assertThat(v.get("timber_mark")).isNull();
    assertThat(v.get("gross_weight")).isNull();
    assertThat(v.get("destination")).isNull();
    assertThat(v.get("hbs_sample_type_code")).isNull();
    assertThat(v.str("population_number")).isEqualTo("P001");
  }

  @Test
  void weighSlipToPrimaryScaleDropsDestinationAndGrappleWeight() {
    DetailDocument d = doc(DocType.WEIGH_SLIP, "PD");
    EventChange.apply(d, "PS");
    Row v = d.version().row();
    assertThat(v.get("destination")).isNull();
    assertThat(v.get("sample_deduction_weight")).isNull();
    assertThat(v.lng("gross_weight")).isEqualTo(100L);
  }

  @Test
  void sampleToLocalSampleClearsArrivalAndCheckScaleFields() {
    DetailDocument d = doc(DocType.SAMPLE_LOG_TALLY, "RS");
    EventChange.apply(d, "SS");
    Row v = d.version().row();
    assertThat(v.get("incoming_lds_number")).isNull();
    assertThat(v.get("transport_identifier")).isNull();
    assertThat(v.get("orig_chk_scl_license_number")).isNull();
  }

  @Test
  void ledgersCannotChangeEvent() {
    assertThatThrownBy(() -> EventChange.apply(doc(DocType.ARRIVAL_LEDGER, "PS"), "FD"))
        .isInstanceOf(WorkflowValidationException.class);
  }
}
