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
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** {@link DepartureLedgerValidator} (legacy {@code $E/dac/detail/control/DepartureLedgerValidator}). */
class DepartureLedgerValidatorTest {

  private final StubReferenceData ref = StubReferenceData.valid();
  private final ArrivalLedgerValidatorTest.StubLedgerLookups ledger = new ArrivalLedgerValidatorTest.StubLedgerLookups();
  private final DetailStore store = new DetailStore(new InMemoryDetailRows(), new RecordingTableApi(),
      new Audit("HBSBATCH"));
  private final Row row = new Row().set("detail_document_number", "DDN1").set("version", 1L)
      .set("scl_rtn_version_state_code", "INC").set("active_version_ind", "Y").set("original_version_ind", "Y")
      .set("scale_event_type_code", "PD").set("scale_site_id_nmbr", "S001").set("timber_mark", "ABC123")
      .set("departure_date", ts("2026-06-01 00:00:00"));
  private DetailDocument doc;

  private DepartureLedgerValidator validator() {
    doc = document(DocType.DEPARTURE_LEDGER, row);
    return new DepartureLedgerValidator(doc, new ValidationContext(ref, store, TallyFixtures.TODAY), ledger);
  }

  private List<String> run() {
    DepartureLedgerValidator d = validator();
    d.copyInputFieldsAndValidate();
    return codes(d.errors());
  }

  @Test
  void cleanLedgerHasNoErrorsAndIsNotBillable() {
    assertThat(run()).isEmpty();
    assertThat(validator().isBillableScaleReturn()).isFalse();
    assertThat(ledger.calls).isZero();
  }

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
  void knownDestinationOk() {
    ref.sites.put("S002", new ScaleSiteRef("S002", 1L, "00012345", "00", "Other", "B2"));
    row.set("destination", "S002");
    assertThat(run()).isEmpty();
  }

  @Test
  void cruiseBasedDestinationIsCbds00() {
    ref.sites.put("S002", new ScaleSiteRef("S002", 1L, "00012345", "00", "XXX Cruise Based Site", "B2"));
    row.set("destination", "S002");
    assertThat(run()).containsExactly("CBDS00");
  }

  @Test
  void unknownDestinationThrowsLikeLegacy() {
    // legacy quirk: DSS01 is recorded, then validateCruiseBasedScaleSite throws on the unknown site
    row.set("destination", "NOPE");
    DepartureLedgerValidator d = validator();
    assertThatThrownBy(d::copyInputFieldsAndValidate).isInstanceOf(IllegalStateException.class);
    assertThat(codes(d.errors())).containsExactly("DSS01");
  }

  @Test
  void inputSourceStillCopiedWhenPresent() {
    row.set("timber_mark", null).set("input_source", "ABC123");
    assertThat(run()).isEmpty();
    assertThat(row.str("timber_mark")).isEqualTo("ABC123");
  }

  @Test
  void futureDepartureIsDd03() {
    row.set("departure_date", ts("2026-06-16 00:00:00"));
    assertThat(run()).containsExactly("DD03");
  }

  @ParameterizedTest
  @CsvSource({"2020-01-01 00:00:00,", "2019-12-31 00:00:00,DD02"})
  void departureLimitIsSixYearsBack(String departure, String expected) {
    row.set("departure_date", ts(departure));
    assertThat(run()).isEqualTo(expected == null ? List.of() : List.of(expected));
  }

  @Test
  void unknownTimberMarkIsTm01() {
    row.set("timber_mark", "ZZZ999");
    assertThat(run()).containsExactly("TM01");
  }

  @Test
  void cruiseBasedScaleSiteIsCbss00() {
    ref.sites.put("S001", new ScaleSiteRef("S001", 1L, "00012345", "00", "XXX Cruise Based Site", "BRAND1"));
    assertThat(run()).containsExactly("CBSS00");
  }

  @Test
  void scaleDateLimitNotAppliedToDepartures() {
    row.set("scale_date", ts("2000-01-01 00:00:00"));
    DepartureLedgerValidator d = validator();
    d.validateEventOrScaleDate();
    assertThat(d.errors()).isEmpty();
  }
}
