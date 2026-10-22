package ca.bc.gov.nrs.hbs.api.intake.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.submission.ScaleDataXmlValidator;
import java.util.Calendar;
import java.util.GregorianCalendar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The Castor-mapping replacement, the hash totals and the volume edits. */
class SubmissionUnmarshallerTest {

  private final SubmissionUnmarshaller unmarshaller = new SubmissionUnmarshaller();

  @Test
  void signedFixtureIsValidAgainstTheP505Schema() throws Exception {
    String xml = ScaleXmlFixtures.signed("all-types-v3.xml");
    assertThat(new ScaleDataXmlValidator().validate(ScaleXmlFixtures.bytes(xml))).isEmpty();
  }

  @Test
  void readsEveryBatchTypeWithTheV3Mapping() throws Exception {
    Submission s = unmarshaller.unmarshal(ScaleXmlFixtures.bytes(ScaleXmlFixtures.signed("all-types-v3.xml")));
    assertThat(s.schemaVersion).isEqualTo("V3");
    assertThat(s.batchCount()).isEqualTo(9);
    assertThat(s.recordCount()).isEqualTo(9);
    assertThat(s.transmission.isProduction()).isTrue();
    assertThat(s.transmission.clientLocationNumber()).isEqualTo("00123456");
    assertThat(s.transmission.clientLocationCode()).isEqualTo("00");
    assertThat(s.transmission.software.product).isEqualTo("SCALEPRO");

    var wsb = (WeighSlipDocument.Billable) s.weighSlipBillables.get(0).documents.get(0);
    assertThat(wsb.detailDocumentNumber).isEqualTo("WS00000000001");
    assertThat(wsb.common.scaleTimeValue).isEqualTo(81530);
    assertThat(wsb.common.signature.identifier).isEqualTo("01");
    assertThat(wsb.weights.sampleDeduction).isNull();
    assertThat(wsb.sampleType).isEqualTo('N');

    var pps = (LogTallyDocument.PieceScale) s.pieceScales.get(0).documents.get(0);
    assertThat(pps.pieceScaleMeasure.logs).hasSize(2);
    assertThat(pps.pieceScaleMeasure.logs.get(1).defects).hasSize(1);
    assertThat(pps.splitLoad.parcelCount).isEqualTo((byte) 2);
    assertThat(pps.beachcomb).isNull();

    var sfp = (LogTallyDocument.SpecialForestProduct) s.specialForestProducts.get(0).documents.get(0);
    assertThat(sfp.measure.stacks).hasSize(1);
    assertThat(sfp.measure.bundles).isNull();

    var arl = (LedgerEntry.ArrivalEntry) s.arrivalLedgers.get(0).entries.get(0);
    assertThat(arl.arrival.arrivalDateString).isEqualTo("20261003");
    assertThat(arl.scale).isNull();
    assertThat(s.departureLedgers.get(0).entries).hasSize(1);
    assertThat(s.weighSlipBillables.get(0).header.fromScaleDate)
        .isEqualTo(new GregorianCalendar(2026, Calendar.OCTOBER, 1).getTime());
  }

  @Test
  void versionComesFromTheSchemaLocationThenTheNamespace() throws Exception {
    assertThat(SubmissionUnmarshaller.xsdVersion("<HBS xsi:schemaLocation=\"gov.bc.ca/forests/hbs/v2 x.xsd\"/>"))
        .isEqualTo("V2");
    assertThat(SubmissionUnmarshaller.xsdVersion("<HBS xsi:schemaLocation=\"gov.bc.ca/forests/hbs/v1 x.xsd\"/>"))
        .isEqualTo("V1");
    assertThat(SubmissionUnmarshaller.xsdVersion("<HBS/>")).isEqualTo("default");
    String noLocation = ScaleXmlFixtures.signed("all-types-v3.xml")
        .replace("xsi:schemaLocation=\"gov.bc.ca/forests/hbs/v3 HBS_Schema_V6_1b.xsd\"", "");
    assertThat(unmarshaller.unmarshal(ScaleXmlFixtures.bytes(noLocation)).schemaVersion).isEqualTo("V3");
  }

  @Test
  void defaultMappingReadsCompactDatesCrcDigestAndNumericTime() throws Exception {
    String v2 = """
        <HBS xmlns="gov.bc.ca/forests/hbs/v2" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="gov.bc.ca/forests/hbs/v2 HBS_Schema_V5_0c.xsd">
          <transmission><BatchCount>1</BatchCount><Type>T</Type><Creator>0012345600</Creator></transmission>
          <WSO><BatchHeader><ScaleSite>S123</ScaleSite><FromScaleDate>20261001</FromScaleDate>
            <ToScaleDate>2026-10-01</ToScaleDate><DocumentCount>1</DocumentCount><BatchControlTotal>1</BatchControlTotal></BatchHeader>
            <WSODocument DDN="W1" DV="2"><EventType>CU</EventType><WSCommon><ScaleSite>S123</ScaleSite>
              <ScaleDate>20261001</ScaleDate><ScaleTime>81530</ScaleTime><WeighSlipNumber>7</WeighSlipNumber>
              <PrimaryScalerLicence>AB12</PrimaryScalerLicence>
              <EC_Digest><Method>CRC32</Method><EC_HT>-12345</EC_HT></EC_Digest>
              <EC_Signature><SigningScalerLicence>AB12</SigningScalerLicence><EC_AKEHT>99</EC_AKEHT><Method>CRC32</Method></EC_Signature>
            </WSCommon></WSODocument></WSO>
        </HBS>""";
    Submission s = unmarshaller.unmarshal(ScaleXmlFixtures.bytes(v2));
    assertThat(s.schemaVersion).isEqualTo("V2");
    assertThat(s.transmission.isTest()).isTrue();
    Submission.BatchHeader h = s.weighSlipOthers.get(0).header;
    assertThat(h.fromScaleDate).isEqualTo(new GregorianCalendar(2026, Calendar.OCTOBER, 1).getTime());
    // SimpleDateFormat("yyyyMMdd") is lenient: "2026-10-01" reads as 2026, month -1, day 01 → 2025-10-31
    assertThat(h.toScaleDate).isEqualTo(new GregorianCalendar(2025, Calendar.OCTOBER, 31).getTime());
    var d = (WeighSlipDocument.Other) s.weighSlipOthers.get(0).documents.get(0);
    assertThat(d.common.digest.hashValue()).isEqualTo("-12345");
    assertThat(d.common.signature.authenticationKeyEncryptedHashTotal).isEqualTo(99L);
    assertThat(d.common.scaleTime2()).isEqualTo("081530"); // CRC32 form
  }

  @Test
  void unreadableFilesAreFormatEditFxa00() {
    assertThatThrownBy(() -> unmarshaller.unmarshal(ScaleXmlFixtures.bytes("<HBS><transmission>")))
        .isInstanceOf(FormatEditException.class).hasMessageContaining("FXA00");
    String badType = ScaleXmlFixtures.raw("all-types-v3.xml").replace("<Type>P</Type>", "<Type>X</Type>");
    assertThatThrownBy(() -> unmarshaller.unmarshal(ScaleXmlFixtures.bytes(badType)))
        .isInstanceOf(FormatEditException.class);
    String doctype = "<?xml version=\"1.0\"?><!DOCTYPE HBS [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><HBS>&x;</HBS>";
    assertThatThrownBy(() -> unmarshaller.unmarshal(ScaleXmlFixtures.bytes(doctype)))
        .isInstanceOf(FormatEditException.class);
  }

  @Test
  void weighSlipHashStringFollowsTheLegacyFieldOrder() throws Exception {
    Submission s = unmarshaller.unmarshal(ScaleXmlFixtures.bytes(ScaleXmlFixtures.raw("all-types-v3.xml")));
    Document wsb = s.weighSlipBillables.get(0).documents.get(0);
    // EventType, site, slip, date, time, LDS, transport, gross, tare, source, cutblock, field-scale flag,
    // PSY, sample type, comment, software, DDN, DV, primary scaler, signing scaler ("" for RSA).
    String expected = "PS" + "S123" + "1001" + "2026-10-01" + "08:15:30" + "LDS1" + "TRUCK7" + "42000" + "15000"
        + "TM123" + "cb1" + "N" + "P1-2026" + "N" + "first load" + "SCALEPRO" + "6" + "1b" + "WS00000000001" + "1"
        + "AB12";
    assertThat(wsb.hashString()).isEqualTo(expected);
    assertThat(wsb.hashValue()).hasSize(64).isUpperCase();
  }

  @Test
  void howellCrcTableAndPrintableOnlyRule() {
    long a = HashValues.howellCrc32("AB".getBytes());
    long b = HashValues.howellCrc32("A\nB\t".getBytes());
    assertThat(b).isEqualTo(a); // non-printable bytes are skipped
    assertThat(HashValues.howellCrc32(new byte[0])).isZero();
    assertThat(HashValues.hashValue(HashValues.CRC32, "AB")).isEqualTo(Long.toString(a));
  }

  @ParameterizedTest(name = "top {0} butt {1} length {2} defects {3} → {4}")
  @CsvSource({"20,24,5.0,0,0.767", "20,24,5.0,0.1,0.667", "30,30,10.0,0,2.827"})
  void logNetVolumeMatchesLegacyLogUtil(int top, int butt, float length, float defects, float expected) {
    assertThat(LogVolume.netVolume(top, butt, length, defects)).isEqualTo(expected);
  }

  @Test
  void stackVolumeMatchesLegacyVolumeUtil() {
    assertThat(LogVolume.stackVolume(100, 100, 100, 0.80)).isEqualTo(0.8f);
    assertThat(LogVolume.stackVolume(250, 120, 110, 0.75)).isEqualTo(2.475f);
  }
}
