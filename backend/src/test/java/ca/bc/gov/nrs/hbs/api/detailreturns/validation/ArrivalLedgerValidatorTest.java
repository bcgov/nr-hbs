package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.codes;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.document;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.InMemoryDetailRows;
import ca.bc.gov.nrs.hbs.api.detailreturns.StubReferenceData;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link ArrivalLedgerValidator} (legacy {@code $E/dac/detail/control/ArrivalLedgerValidator}). */
class ArrivalLedgerValidatorTest {

  /** In-test {@link LedgerLookups}: the deck/mark pairs that have a field-scale estimate. */
  static final class StubLedgerLookups implements LedgerLookups {
    final Set<String> estimates = new HashSet<>();
    int calls;

    @Override
    public boolean fieldScaleEstimateExists(String fieldScaleDeckId, String timberMark) {
      calls++;
      return estimates.contains(fieldScaleDeckId + "/" + timberMark);
    }
  }

  private final StubReferenceData ref = StubReferenceData.valid();
  private final StubLedgerLookups ledger = new StubLedgerLookups();
  private final DetailStore store = new DetailStore(new InMemoryDetailRows(), new RecordingTableApi(),
      new Audit("HBSBATCH"));
  private Row row = clean();
  private DetailDocument doc;

  static Row clean() {
    return new Row().set("detail_document_number", "DDN1").set("version", 1L)
        .set("scl_rtn_version_state_code", "INC").set("active_version_ind", "Y").set("original_version_ind", "Y")
        .set("scale_event_type_code", "PS").set("scale_site_id_nmbr", "S001").set("timber_mark", "ABC123")
        .set("license_number", "1234").set("quant_lic_type_cd", "S")
        .set("scale_date", ts("2026-06-10 00:00:00")).set("arrival_date", ts("2026-06-01 00:00:00"))
        .set("field_scale_ind", "N");
  }

  private ArrivalLedgerValidator validator() {
    doc = document(DocType.ARRIVAL_LEDGER, row);
    return new ArrivalLedgerValidator(doc, new ValidationContext(ref, store, TallyFixtures.TODAY), ledger);
  }

  private List<String> run() {
    ArrivalLedgerValidator a = validator();
    a.copyInputFieldsAndValidate();
    return codes(a.errors());
  }

  @Test
  void cleanLedgerHasNoErrorsAndIsNotBillable() {
    assertThat(run()).isEmpty();
    assertThat(validator().isBillableScaleReturn()).isFalse();
  }

  // ── validateAndCopyInputFields ────────────────────────────────────

  @Test
  void inputScaleSiteCopiedWhenValid() {
    row.set("scale_site_id_nmbr", null).set("input_scale_site", "S001");
    assertThat(run()).isEmpty();
    assertThat(row.str("scale_site_id_nmbr")).isEqualTo("S001");
  }

  @Test
  void unknownInputScaleSiteIsSs01() {
    row.set("scale_site_id_nmbr", null).set("input_scale_site", "NOPE");
    assertThat(run()).containsExactly("SS01");
  }

  @Test
  void inputSourceBecomesTimberMark() {
    row.set("timber_mark", null).set("input_source", "ABC123");
    assertThat(run()).isEmpty();
    assertThat(row.str("timber_mark")).isEqualTo("ABC123");
  }

  @Test
  void unknownInputSourceIsTm01() {
    row.set("timber_mark", null).set("input_source", "ZZZ999");
    assertThat(run()).containsExactly("TM01");
  }

  @Test
  void scaledBeforeInputSourceIsATimberBrand() {
    row.set("scale_event_type_code", "B4").set("timber_mark", null).set("input_source", "BRAND9");
    ref.sites.put("S002", new ScaleSiteRef("S002", 1L, "00012345", "00", "Other", "BRAND9"));
    assertThat(run()).isEmpty();
    assertThat(row.str("timber_brand")).isEqualTo("BRAND9");
  }

  @Test
  void scaledBeforeBrandOfOwnSiteIsTb02() {
    row.set("scale_event_type_code", "B4").set("timber_mark", null).set("input_source", "BRAND1");
    assertThat(run()).containsExactly("TB02");
    assertThat(row.str("timber_brand")).isNull();
  }

  @Test
  void inputPrimaryScalerGoesToLicenseNumber() {
    row.set("license_number", null).set("quant_lic_type_cd", null).set("input_primary_scaler_lic", "5678");
    assertThat(run()).isEmpty();
    assertThat(row.str("license_number")).isEqualTo("5678");
    assertThat(row.str("quant_lic_type_cd")).isEqualTo("S");
    assertThat(row.has("primary_license_number")).isFalse();
  }

  @Test
  void unknownInputPrimaryScalerIsPsl01() {
    row.set("license_number", null).set("input_primary_scaler_lic", "9999");
    assertThat(run()).containsExactly("PSL01");
    assertThat(row.str("license_number")).isNull();
  }

  @Test
  void inputCutBlockCopiedWithoutCheck() {
    row.set("input_cutblock_id", "CB-X");
    assertThat(run()).isEmpty();
    assertThat(row.str("cut_block_id")).isEqualTo("CB-X");
  }

  @Test
  void existingCutBlockNotOverwritten() {
    row.set("cut_block_id", "CB1").set("input_cutblock_id", "CB-X");
    run();
    assertThat(row.str("cut_block_id")).isEqualTo("CB1");
  }

  // ── validate: dates ───────────────────────────────────────────────

  @Test
  void futureScaleDateIsSd03() {
    row.set("scale_date", ts("2026-06-16 00:00:00")).set("arrival_date", ts("2026-06-01 00:00:00"));
    assertThat(run()).containsExactly("SD03");
  }

  @Test
  void futureArrivalDateIsAd03AndAd04() {
    row.set("arrival_date", ts("2026-06-20 00:00:00"));
    assertThat(run()).containsExactly("AD03", "AD04");
  }

  @ParameterizedTest
  @CsvSource({"2020-01-01 00:00:00,", "2019-12-31 00:00:00,AD02"})
  void arrivalLimitIsSixYearsBack(String arrival, String expected) {
    row.set("arrival_date", ts(arrival)).set("scale_date", null);
    assertThat(run()).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @ParameterizedTest
  @CsvSource({"2026-06-10 00:00:00,", "2026-06-11 00:00:00,AD04"})
  void arrivalAfterScaleDateIsAd04(String arrival, String expected) {
    row.set("arrival_date", ts(arrival));
    assertThat(run()).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @Test
  void noDatesNoDateEdits() {
    row.set("arrival_date", null).set("scale_date", null);
    assertThat(run()).isEmpty();
  }

  // ── validate: brand, sites, mark, weigh slip, parcels ─────────────

  @Test
  void unknownTimberBrandIsTb01() {
    row.set("timber_brand", "NOPE");
    assertThat(run()).containsExactly("TB01");
  }

  @Test
  void knownOrigScaleSiteOk() {
    ref.sites.put("S002", new ScaleSiteRef("S002", 1L, "00012345", "00", "Other", "B2"));
    row.set("orig_scale_site", "S002");
    assertThat(run()).isEmpty();
  }

  @Test
  void cruiseBasedOrigScaleSiteIsCbos00() {
    ref.sites.put("S002", new ScaleSiteRef("S002", 1L, "00012345", "00", "XXX Cruise Based Site", "B2"));
    row.set("orig_scale_site", "S002");
    assertThat(run()).containsExactly("CBOS00");
  }

  @Test
  void unknownOrigScaleSiteThrowsLikeLegacy() {
    // legacy quirk: OSS01 is recorded, then validateCruiseBasedScaleSite throws on the unknown site
    row.set("orig_scale_site", "NOPE");
    ArrivalLedgerValidator a = validator();
    assertThatThrownBy(a::copyInputFieldsAndValidate).isInstanceOf(IllegalStateException.class);
    assertThat(codes(a.errors())).containsExactly("OSS01");
  }

  @Test
  void cruiseBasedScaleSiteIsCbss00() {
    ref.sites.put("S001", new ScaleSiteRef("S001", 1L, "00012345", "00", "XXX Cruise Based Site", "BRAND1"));
    assertThat(run()).containsExactly("CBSS00");
  }

  @Test
  void unknownTimberMarkIsTm01() {
    row.set("timber_mark", "ZZZ999");
    assertThat(run()).containsExactly("TM01");
  }

  @ParameterizedTest
  @CsvSource({"123,", "12A,WSN01", "1 2,WSN01"})
  void weighSlipMustBeNumeric(String slip, String expected) {
    row.set("arrival_ledger_weigh_slip", slip);
    assertThat(run()).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @ParameterizedTest
  @CsvSource({
      "1,2,''", "2,2,''", "3,2,PI01", "0,2,PI02", "-1,2,PI02",
      ",2,PI00", "1,,PC00", "1,0,PC02 PI01", "1,-1,PI01 PC02"})
  void parcels(Long id, Long count, String expected) {
    row.set("parcel_identifier", id).set("parcel_count", count);
    List<String> want = expected.isBlank() ? List.of() : List.of(expected.split(" "));
    assertThat(run()).containsExactlyInAnyOrderElementsOf(want);
  }

  // ── validate: field scale (BT11301) ───────────────────────────────

  @Test
  void fieldScaleWithoutDeckIsFsd01() {
    row.set("field_scale_ind", "Y");
    assertThat(run()).containsExactly("FSD01");
  }

  @Test
  void deckOnNonFieldScaledOriginalIsFsd02() {
    ledger.estimates.add("A/ABC123");
    row.set("field_scale_deck_id", "A");
    assertThat(run()).containsExactly("FSD02");
  }

  @Test
  void deckIdIsTrimmed() {
    ledger.estimates.add("A/ABC123");
    row.set("field_scale_ind", "Y").set("field_scale_deck_id", " A ");
    assertThat(run()).isEmpty();
    assertThat(row.str("field_scale_deck_id")).isEqualTo("A");
  }

  @Test
  void nonAlphanumericDeckIsFsx03() {
    ledger.estimates.add("#/ABC123");
    row.set("field_scale_ind", "Y").set("field_scale_deck_id", "#");
    assertThat(run()).containsExactly("FSX03");
  }

  @Test
  void deckWithoutFieldScaleEstimateIsFsx04() {
    row.set("field_scale_ind", "Y").set("field_scale_deck_id", "A");
    assertThat(run()).containsExactly("FSX04");
  }

  @ParameterizedTest
  @CsvSource({"PS,FSX04", "TD,FSX04", "FD,FSX04", "RR,FSX04", "CS,", "B4,"})
  void deckEditsOnlyForPrimaryDeckAndRemoteRedTagEvents(String event, String expected) {
    row.set("scale_event_type_code", event).set("field_scale_ind", "Y").set("field_scale_deck_id", "A");
    assertThat(run()).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @Test
  void noDeckLookupWithoutTimberMark() {
    row.set("timber_mark", null).set("field_scale_ind", "Y").set("field_scale_deck_id", "A");
    assertThat(run()).isEmpty();
    assertThat(ledger.calls).isZero();
  }

  @ParameterizedTest
  @CsvSource({"A,true", "a1,true", "' ',false", "'',false", "A-1,false"})
  void alphaNumeric(String s, boolean expected) {
    assertThat(ArrivalLedgerValidator.isAlphaNumeric(s)).isEqualTo(expected);
  }
}
