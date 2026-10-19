package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.query.HarvestHistoryXml.Header;
import ca.bc.gov.nrs.hbs.api.query.HarvestHistoryXml.Kind;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The "Download XML" document must validate against the legacy v3 history schemas. */
class HarvestHistoryXmlTest {

  private static final Header HEADER = new Header(1760600000L, LocalDateTime.of(2026, 10, 16, 14, 5, 9),
      "someone@gov.bc.ca", "Ministry", null, YearMonth.of(2025, 1), YearMonth.of(2025, 12), "All",
      "00001271", null, "All Land", null, null, null, "AB123, CD456", null, null, "All", "All", "All",
      "Normal,Cruise,Waste,Beachcomb");

  private static Map<String, Object> billingRow() {
    Map<String, Object> r = new HashMap<>();
    r.put("timberMark", "AB123");
    r.put("billingYear", new BigDecimal("2025"));
    r.put("billingMonth", new BigDecimal("3"));
    r.put("billingPeriod", "2025-03-01");
    r.put("speciesCode", "FI");
    r.put("speciesDescription", "Fir");
    r.put("productCode", "  ");
    r.put("productDescription", "Logs");
    r.put("gradeCode", "2");
    r.put("gradeDescription", "Sawlog");
    r.put("billingTypeCode", "NP");
    r.put("billingTypeDescription", "Normal");
    r.put("totalVolumeBilled", new BigDecimal("98.7650"));
    r.put("totalAmountBilled", new BigDecimal("1234.50"));
    r.put("regionHarvestedCode", "RCO");
    r.put("regionHarvestedName", "Coast Area");
    r.put("districtHarvestedCode", "DCK");
    r.put("districtHarvestedName", "Chilliwack Natural Resource District");
    r.put("forestFileId", "A12345");
    r.put("fileTypeCode", "A01");
    r.put("fileTypeDescription", "Forest Licence");
    r.put("clientTypeCode", "I");
    r.put("clientTypeDescription", "Individual");
    r.put("clientNumber", "00001271");
    r.put("clientName", "Not Releasable");
    r.put("clientLocnCode", "00");
    r.put("clientLocnName", "Not Releasable");
    return r;
  }

  private static Map<String, Object> scalingRow() {
    Map<String, Object> r = billingRow();
    r.remove("billingYear");
    r.remove("billingMonth");
    r.remove("billingPeriod");
    r.remove("totalVolumeBilled");
    r.remove("totalAmountBilled");
    r.put("scalingPeriod", "2025-04-01");
    r.put("scaleSite", "1234");
    r.put("totalAmount", new BigDecimal("10.00"));
    r.put("volumeScaled", new BigDecimal("5.125"));
    r.put("siteName", "Some Scale Site");
    r.put("siteOwnerClientTypeCode", "C");
    r.put("siteOwnerClientTypeDescription", "Corporation");
    r.put("siteOwnerClientNumber", "00012345");
    r.put("siteOwnerClientName", "SOME MILL LTD");
    r.put("siteOwnerClientLocnCode", "00");
    return r;
  }

  @Test
  void billingDocumentValidatesAndKeepsLegacyFormatting() throws Exception {
    String xml = HarvestHistoryXml.write(Kind.BILLING, HEADER, List.of(billingRow()));
    HarvestHistoryXml.validate(Kind.BILLING, xml);
    assertThat(xml)
        .contains("<hbs-request xmlns=\"gov.bc.ca/forests/hbs/v2\">")
        .contains("<doc-count>1</doc-count>")
        .contains("<datetime-created>2026-10-16T14:05:09</datetime-created>")
        .contains("<requested-from-billing-month>1</requested-from-billing-month>")
        .contains("<billing-month>3</billing-month>")
        .contains("<product-code>  </product-code>")
        .contains("<total-amount-billed>1234.5</total-amount-billed>")
        .contains("<total-volume-billed>98.765</total-volume-billed>")
        .contains("<mark-holder-client-name>CLIENT NAME NOT RELEASABLE</mark-holder-client-name>")
        .contains("CLIENT LOCATION NAME NOT RELEASABLE")
        .contains("<fine-print>Note:  While every effort")
        .doesNotContain("requestor-client>")
        .doesNotContain("mgmt-unit");
  }

  @Test
  void scalingDocumentValidates() throws Exception {
    String xml = HarvestHistoryXml.write(Kind.SCALING, HEADER, List.of(scalingRow()));
    HarvestHistoryXml.validate(Kind.SCALING, xml);
    assertThat(xml).contains("<xml-msh-request-header>")
        .contains("<scaling-month>4</scaling-month>")
        .contains("<scale-site>1234</scale-site>")
        .contains("<total-amount-scaled>10</total-amount-scaled>")
        .contains("<site-owner-client-name>SOME MILL LTD</site-owner-client-name>");
  }

  @Test
  void emptyResultIsStillAValidDocument() {
    String xml = HarvestHistoryXml.write(Kind.BILLING, HEADER, List.of());
    assertThatCode(() -> HarvestHistoryXml.validate(Kind.BILLING, xml)).doesNotThrowAnyException();
    assertThat(xml).contains("<doc-count>0</doc-count>");
  }

  @Test
  void missingRequiredDataFailsValidation() {
    Map<String, Object> row = billingRow();
    row.remove("regionHarvestedCode");
    String xml = HarvestHistoryXml.write(Kind.BILLING, HEADER, List.of(row));
    assertThatThrownBy(() -> HarvestHistoryXml.validate(Kind.BILLING, xml)).isInstanceOf(SAXException.class);
  }
}
