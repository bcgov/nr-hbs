package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.CruiseRatio;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.junit.jupiter.api.Test;

class AreaBasedSegregationsTest {

  private final EditsFixture f = new EditsFixture();

  private SummaryReturn areaBasedReturn() {
    f.pieceVersion.set("pssmry_type", "AREAEST").set("scl_rtn_category_code", "CR").set("area_cut", new BigDecimal("12.5"))
        .set("log_count", 0L);
    f.lookups.marks.put(EditsFixture.MARK, EditsFixture.mark("A01", "Y", "S"));
    f.lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "DKA Cruise Based Site", 10L));
    f.lookups.cruiseRatios.add(new CruiseRatio("FI", "U", new BigDecimal("80.12")));
    f.lookups.cruiseRatios.add(new CruiseRatio("HE", "U", new BigDecimal("20")));
    f.noPieceSegs = true;
    return f.piece();
  }

  @Test
  void replacesTheSegregationsWithTheCruiseRatiosTimesTheAreaCut() {
    SummaryReturn ret = areaBasedReturn();
    ret.segregations().add(EditsFixture.pieceSeg(11, "XX", "U", 3));
    List<EditError> errors = f.edits.generateAreaBasedSegregations(ret, "IDIR\\TESTER");
    assertThat(errors).isEmpty();
    assertThat(ret.segregations()).hasSize(2);
    Row fi = ret.segregations().get(0);
    assertThat(fi.get("segregation_id")).isNull();
    assertThat(fi.str("psseg_type")).isEqualTo("PRIMARY");
    assertThat(fi.str("scale_species_code")).isEqualTo("FI");
    assertThat(fi.str("scale_product_code")).isEqualTo(" ");
    assertThat(fi.str("scale_grade_code")).isEqualTo("U");
    assertThat(fi.lng("number_of_pieces")).isEqualTo(1L);
    assertThat(fi.num("volume")).isEqualByComparingTo("1001.5"); // 80.12 × 12.5
    assertThat(fi.str("nmv_ind")).isEqualTo("N");
    assertThat(fi.str("entry_userid")).isEqualTo("IDIR\\TESTER");
    assertThat(fi.date("entry_timestamp")).isEqualTo(Timestamp.valueOf(EditsFixture.NOW));
    assertThat(ret.segregations().get(1).num("volume")).isEqualByComparingTo("250");
    assertThat(ret.version().lng("log_count")).isEqualTo(2L);
  }

  @Test
  void addsNotation50() {
    SummaryReturn ret = areaBasedReturn();
    f.edits.generateAreaBasedSegregations(ret, "IDIR\\TESTER");
    assertThat(ret.notations()).hasSize(1);
    Row n = ret.notations().get(0);
    assertThat(n.get("ntn_id")).isNull();
    assertThat(n.str("notation_type_code")).isEqualTo("NOT");
    assertThat(n.lng("notation_number")).isEqualTo(50L);
    assertThat(n.lng("document_control_number")).isEqualTo(1001L);
    assertThat(n.str("text")).isEqualTo("12.5 Ha on this invoice; 40.0 Ha Cut to date; Appraised Area: 100.0 Ha");
  }

  @Test
  void updatesAnExistingNotation50() {
    SummaryReturn ret = areaBasedReturn();
    ret.notations().add(new Row().set("ntn_id", 77L).set("notation_number", 50L).set("text", "old"));
    ret.notations().add(new Row().set("ntn_id", 78L).set("notation_number", 1L).set("text", "other"));
    f.lookups.totalAreaCut = 52.5;
    f.edits.generateAreaBasedSegregations(ret, "IDIR\\TESTER");
    assertThat(ret.notations()).hasSize(2);
    assertThat(ret.notations().get(0).str("text"))
        .isEqualTo("12.5 Ha on this invoice; 52.5 Ha Cut to date; Appraised Area: 100.0 Ha");
    assertThat(ret.notations().get(1).str("text")).isEqualTo("other");
  }

  @Test
  void noRatiosMeansNoSegregationsNoNotationAndSg04() {
    SummaryReturn ret = areaBasedReturn();
    f.lookups.cruiseRatios.clear();
    List<EditError> errors = f.edits.generateAreaBasedSegregations(ret, "IDIR\\TESTER");
    assertThat(ret.segregations()).isEmpty();
    assertThat(ret.version().lng("log_count")).isZero();
    assertThat(ret.notations()).isEmpty();
    assertThat(errors).containsExactly(EditError.version("FMT", "SG04"));
  }

  @Test
  void generatedSegregationErrorsCarryNoIdUntilSaved() {
    SummaryReturn ret = areaBasedReturn();
    ret.version().set("scl_rtn_category_code", "PR");
    f.lookups.cruiseRatios.clear();
    f.lookups.cruiseRatios.add(new CruiseRatio("FI", "7", BigDecimal.TEN));
    List<EditError> errors = f.edits.generateAreaBasedSegregations(ret, "IDIR\\TESTER");
    assertThat(errors).containsExactly(new EditError(EditError.Target.SEGREGATION, "SCL", "ISB789", null, null));
    ret.segregations().get(0).set("segregation_id", 5001L);
    assertThat(f.edits.validateGeneratedSegregations(ret)).containsExactly(EditError.segregation(5001, "SCL", "ISB789"));
  }

  @Test
  void otherReturnsAreLeftAlone() {
    SummaryReturn ret = f.piece();
    assertThat(f.edits.generateAreaBasedSegregations(ret, "IDIR\\TESTER")).isEmpty();
    assertThat(ret.segregations()).hasSize(1);
    assertThat(ret.segregations().get(0).lng("segregation_id")).isEqualTo(11L);
  }
}
