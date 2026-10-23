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
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DetailPayloadTest {

  private static DetailDocument doc(DocType type) {
    VersionState vs = VersionState.initialOnline();
    return new DetailDocument(type, new Row(), new DocumentState("OIP", vs),
        new DetailVersion(DetailPayload.blankVersion(type), vs, false), false);
  }

  @Test
  void appliesFieldsUpperCasedAndDates() {
    DetailDocument d = doc(DocType.LOG_TALLY);
    DetailPayload.applyFields(d, Map.of("scaleSiteIdNmbr", "s001", "scaleDate", "2026-10-01",
        "returnNumber", "56", "fieldScaleInd", true, "origRedtagScaleSiteIdNmbr", "s002"));
    Row v = d.version().row();
    assertThat(v.str("scale_site_id_nmbr")).isEqualTo("S001");
    assertThat(v.get("scale_date")).isEqualTo(Timestamp.valueOf("2026-10-01 00:00:00"));
    assertThat(v.str("return_number")).isEqualTo("56");
    assertThat(v.str("field_scale_ind")).isEqualTo("Y");
    assertThat(v.str("orig_redtag_scale_site_id_nmbr")).isEqualTo("S002");
  }

  @Test
  void refusesProtectedAndUnknownColumns() {
    assertThatThrownBy(() -> DetailPayload.applyFields(doc(DocType.LOG_TALLY),
        Map.of("sclRtnVersionStateCode", "RDY")))
        .isInstanceOf(WorkflowValidationException.class);
    assertThatThrownBy(() -> DetailPayload.applyFields(doc(DocType.LOG_TALLY), Map.of("inputSource", "X")))
        .isInstanceOf(WorkflowValidationException.class);
    assertThatThrownBy(() -> DetailPayload.applyFields(doc(DocType.LOG_TALLY), Map.of("nope", "X")))
        .isInstanceOf(WorkflowValidationException.class);
  }

  @Test
  void weighSlipLicencesAreScaleLicencesAndScaleDateKeepsTime() {
    DetailDocument d = doc(DocType.WEIGH_SLIP);
    DetailPayload.applyFields(d, Map.of("primaryLicenseNumber", "1234", "scaleDate", "2026-10-01T13:45"));
    assertThat(d.version().row().str("primary_quant_lic_type_cd")).isEqualTo("S");
    assertThat(d.version().row().get("secondary_quant_lic_type_cd")).isNull();
    assertThat(d.version().row().get("scale_date")).isEqualTo(Timestamp.valueOf("2026-10-01 13:45:00"));
  }

  @Test
  void logsReplaceLinesAndRecomputeVolumeAndDeductions() {
    DetailDocument d = doc(DocType.LOG_TALLY);
    d.version().logs().add(new LogLine(new Row().set("logdtl_id", 5).set("log_number", 1)));
    d.version().logs().add(new LogLine(new Row().set("logdtl_id", 6).set("log_number", 2)));
    DetailPayload.applyLogs(d, List.of(
        Map.of("logdtlId", 5, "fields", Map.of("length", "12.4", "top", 22, "butt", 28),
            "defects", List.of(Map.of("fields", Map.of("defectNumber", 1, "defectType", "rt", "volume", "0.123",
                "descriptor", "Rot")), Map.of("fields", Map.of()))),
        Map.of("fields", Map.of("logNumber", 3, "length", "5.3", "top", 20, "butt", 30))));
    assertThat(d.version().logs()).hasSize(2);
    LogLine first = d.version().logs().get(0);
    assertThat(first.id()).isEqualTo(5L);
    assertThat(first.defects()).hasSize(1);
    assertThat(first.defects().get(0).str("defect_type")).isEqualTo("RT");
    assertThat(first.row().num("net_volume")).isEqualByComparingTo("2.347");
    assertThat(first.row().lng("deduction_count")).isEqualTo(1L);
    LogLine added = d.version().logs().get(1);
    assertThat(added.id()).isNull();
    assertThat(added.row().num("net_volume")).isEqualByComparingTo(new BigDecimal("1.082"));
    assertThat(added.row().str("nmv_ind")).isEqualTo("N");
  }

  @Test
  void rowEmptyKeepsWhitespaceBlankTrims() {
    Row r = new Row().set("a", " ").set("b", "");
    assertThat(r.empty("a")).isFalse(); // StringUtil.empty(" ")
    assertThat(r.blank("a")).isTrue(); // StringUtil.empty(" ", false)
    assertThat(r.empty("b")).isTrue();
    assertThat(r.empty("missing")).isTrue();
  }

  /** Text is taken as posted (Struts forms did not trim); "" is null; trimmed only where the legacy setter did. */
  @Test
  void whitespaceIsKeptExceptInFieldsTheLegacyFormsTrimmed() {
    DetailDocument lt = doc(DocType.LOG_TALLY);
    DetailPayload.applyFields(lt, Map.of("scaleSiteIdNmbr", " ", "cutBlockId", "", "origRedtagScaleSiteIdNmbr",
        "  s2 ", "fieldScaleDeckId", " ", "returnNumber", " "));
    Row v = lt.version().row();
    assertThat(v.str("scale_site_id_nmbr")).isEqualTo(" "); // P552Form: upper-cased, not trimmed
    assertThat(v.get("cut_block_id")).isNull();
    assertThat(v.str("orig_redtag_scale_site_id_nmbr")).isEqualTo("S2"); // P552Form trims
    assertThat(v.get("field_scale_deck_id")).isNull(); // entity setter trims
    assertThat(v.get("return_number")).isNull(); // a number: blank is null

    DetailDocument ws = doc(DocType.WEIGH_SLIP);
    DetailPayload.applyFields(ws, Map.of("scaleSiteIdNmbr", " s1 ", "timberMark", " "));
    assertThat(ws.version().row().str("scale_site_id_nmbr")).isEqualTo("S1"); // P600Form trims
    assertThat(ws.version().row().get("timber_mark")).isNull();
  }

  /** LogDetailParser / SampleDetailParser: product LOG (" "); every parser: empty grade → " ". */
  @Test
  void tallyLinesGetTheParsersSingleSpaceCodes() {
    DetailDocument d = doc(DocType.LOG_TALLY);
    DetailPayload.applyLogs(d, List.of(Map.of("fields", Map.of("logNumber", 1, "scaleSpeciesCode", "fi",
        "scaleGradeCode", "", "length", "5", "top", 20, "butt", 22))));
    Row line = d.version().logs().get(0).row();
    assertThat(line.str("scale_product_code")).isEqualTo(" ");
    assertThat(line.str("scale_grade_code")).isEqualTo(" ");
    assertThat(line.str("scale_species_code")).isEqualTo("FI");

    DetailDocument sfp = doc(DocType.SFP_TALLY);
    DetailPayload.applyLogs(sfp, List.of(
        Map.of("fields", Map.of("sfpNumber", 1, "scaleSpeciesCode", "CE", "scaleProductCode", " ")),
        Map.of("fields", Map.of("sfpNumber", 2, "scaleSpeciesCode", "CE", "scaleProductCode", "sh",
            "scaleGradeCode", "1"))));
    assertThat(sfp.version().logs().get(0).row().str("scale_product_code")).isEqualTo(" ");
    assertThat(sfp.version().logs().get(0).row().str("scale_grade_code")).isEqualTo(" ");
    assertThat(sfp.version().logs().get(1).row().str("scale_product_code")).isEqualTo("SH");
    assertThat(sfp.version().logs().get(1).row().str("scale_grade_code")).isEqualTo("1");
  }

  @Test
  void uncheckedAvailableCheckboxesDefaultToNo() {
    DetailDocument d = doc(DocType.LOG_TALLY);
    d.version().row().set("scale_event_type_code", "PS");
    DetailPayload.defaultCheckboxes(d);
    assertThat(d.version().row().str("beachcomb_ind")).isEqualTo("N");
    assertThat(d.version().row().str("field_scale_ind")).isEqualTo("N");
  }

  @Test
  void camelSnakeRoundTrip() {
    assertThat(DetailPayload.snake("origRedtagScaleSiteIdNmbr")).isEqualTo("orig_redtag_scale_site_id_nmbr");
    assertThat(DetailPayload.camel("orig_redtag_scale_site_id_nmbr")).isEqualTo("origRedtagScaleSiteIdNmbr");
  }
}
