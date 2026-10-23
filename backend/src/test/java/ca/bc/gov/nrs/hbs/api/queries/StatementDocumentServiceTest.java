package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.Viewer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** freedomOfInformationCheck for each kind of viewer, including the anonymous public one. */
class StatementDocumentServiceTest {

  private static final String XML = """
      <?xml version="1.0" encoding="UTF-8"?>
      <piece-scale-invoice xmlns="gov.bc.ca/forests/hbs/statement/v1">
        <invoice-to><client-number>00011111</client-number><client-code>00</client-code>
          <name>JANE DOE</name><address1>1 MAIN ST</address1><city>VICTORIA</city></invoice-to>
        <copy-to><client-number>00022222</client-number><client-code>00</client-code>
          <name>ACME FOREST LTD</name><address1>2 MILL RD</address1></copy-to>
      </piece-scale-invoice>""".strip();

  /** Send-to is an Individual, copy-to a corporation. */
  private static final StatementDocumentService.Stored STORED = new StatementDocumentService.Stored(
      "00011111", "00022222", LocalDate.now(), "I", "C", XML);

  @Test
  void anonymousViewerGetsIndividualsSevered() {
    String out = StatementDocumentService.applyFreedomOfInformation(STORED, Viewer.ANONYMOUS);
    assertThat(out).doesNotContain("JANE DOE").doesNotContain("1 MAIN ST").doesNotContain("VICTORIA")
        .contains("NOT RELEASABLE").contains("00011111")
        // the corporate copy-to party is releasable (BT17247: show company info on the public site)
        .contains("ACME FOREST LTD").contains("2 MILL RD");
  }

  @Test
  void ministryAndPartiesSeeEverything() {
    assertThat(StatementDocumentService.applyFreedomOfInformation(STORED, new Viewer(true, "", "MOF", "IDIR\\A")))
        .isEqualTo(XML);
    assertThat(StatementDocumentService.applyFreedomOfInformation(STORED,
        new Viewer(false, "00022222", "CLI", "BCEID\\B"))).isEqualTo(XML);
  }

  @Test
  void otherIndustryClientIsSeveredLikeThePublic() {
    String out = StatementDocumentService.applyFreedomOfInformation(STORED,
        new Viewer(false, "00099999", "CLI", "BCEID\\C"));
    assertThat(out).doesNotContain("JANE DOE").contains("ACME FOREST LTD");
  }
}
