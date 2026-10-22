package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ClientLocationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.ScaleSiteRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.TimberMarkRef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The legacy predicates on the {@link ReferenceData} records ({@code TimberMark}, {@code ScaleSite}, …). */
class ReferenceDataTest {

  static TimberMarkRef mark(String fileType, String cruise, String payment) {
    return new TimberMarkRef("ABC123", fileType, cruise, payment, "00012345", "00", 1L, 1L, "RCO", "A", "HI",
        null, null, null);
  }

  /** {@code TimberMark.isCashSale/isCruiseBased/isAreaBased/isPrivateMark/isBeachComb/getTimberMarkType}. */
  @ParameterizedTest(name = "{0} cruise={1} pay={2}")
  @CsvSource(nullValues = "null", value = {
      // fileType, cruise, payment, cash, cruiseBased, areaBased, private, beach, type
      "A01, N, B,    false, false, false, false, false, 0",
      "A01, N, C,    true,  false, false, false, false, 1",
      "A01, N, ' C ', true, false, false, false, false, 1",
      "A01, N, null, false, false, false, false, false, 0",
      "A01, Y, B,    false, true,  false, false, false, 2",
      "A01, Y, C,    true,  true,  false, false, false, 1",
      "A11, Y, B,    false, false, true,  false, false, 3",
      "A11, N, B,    false, false, false, false, false, 0",
      "B08, N, B,    false, false, false, true,  false, 4",
      "B16, N, B,    false, false, false, true,  false, 4",
      "' B13 ', N, B, false, false, false, true, false, 4",
      "B07, N, B,    false, false, false, false, false, 0",
      "B17, N, B,    false, false, false, false, true,  0",
      "B09, Y, B,    false, true,  false, true,  false, 2"})
  void timberMarkPredicates(String fileType, String cruise, String payment, boolean cash, boolean cruiseBased,
                            boolean areaBased, boolean privateMark, boolean beach, int type) {
    TimberMarkRef m = mark(fileType, cruise, payment);
    assertThat(m.isCashSale()).isEqualTo(cash);
    assertThat(m.isCruiseBased()).isEqualTo(cruiseBased);
    assertThat(m.isAreaBased()).isEqualTo(areaBased);
    assertThat(m.isPrivateMark()).isEqualTo(privateMark);
    assertThat(m.isBeachComb()).isEqualTo(beach);
    assertThat(m.timberMarkType()).isEqualTo(type);
  }

  /** {@code ScaleSite.isDedicatedCruiseBasedSite}: characters 5-21 of the name. */
  @ParameterizedTest(name = "''{0}'' → {1}")
  @CsvSource(nullValues = "null", value = {
      "'1234Cruise Based Site', true",
      "'ABC CRUISE BASED SITE and more', true",
      "'Cruise Based Site', false",
      "'1234Cruise Based Sit', false",
      "'', false",
      "null, false"})
  void dedicatedCruiseBasedSite(String name, boolean expected) {
    ScaleSiteRef site = new ScaleSiteRef("S001", 1L, "00012345", "00", name, null);
    assertThat(site.isDedicatedCruiseBasedSite()).isEqualTo(expected);
  }

  /** {@code Client.isDeactivated} / {@code ClientLocation.isExpired}. */
  @ParameterizedTest(name = "{0}/{1}")
  @CsvSource(nullValues = "null", value = {
      "ACT, N, false, false",
      "DAC, N, true,  false",
      "ACT, Y, false, true",
      "null, null, false, false"})
  void clientLocationPredicates(String status, String expired, boolean deactivated, boolean isExpired) {
    ClientLocationRef loc = new ClientLocationRef("00012345", "00", status, expired);
    assertThat(loc.isDeactivated()).isEqualTo(deactivated);
    assertThat(loc.isExpired()).isEqualTo(isExpired);
  }
}
