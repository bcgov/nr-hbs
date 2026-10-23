package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link PopStratYear} against the legacy {@code PopStratYearParser} rules. */
class PopStratYearTest {

  static Stream<Arguments> cases() {
    // psy, population, stratum, year (null = InvalidYearException), valid, billableLength, companyStratum
    return Stream.of(
        Arguments.of("ABCD0126", "ABCD", "01", 2026L, true, true, "ABCD0126"),
        Arguments.of("ABCD0197", "ABCD", "01", 1997L, true, true, "ABCD0197"),
        Arguments.of("ABCD0195", "ABCD", "01", 1995L, true, true, "ABCD0195"),
        Arguments.of("ABCD0194", "ABCD", "01", 2094L, true, true, "ABCD0194"),
        Arguments.of("ABCD012026", "ABCD", "01", 2026L, true, true, "ABCD0126"),
        // four-digit year starting with 0: two-digit conversion of the last two digits
        Arguments.of("ABCD010026", "ABCD", "01", 2026L, true, true, "ABCD0126"),
        Arguments.of("ABCD0120X6", "ABCD", "01", null, true, true, "ABCD01X6"),
        Arguments.of("ABCD01XX", "ABCD", "01", null, true, true, "ABCD01XX"),
        // BT 8873: one-digit year, not a billable length
        Arguments.of("ABCD015", "ABCD", "01", 5L, false, false, "ABCD015"),
        Arguments.of("ABCD01X", "ABCD", "01", null, false, false, "ABCD01X"),
        // nine characters: the four-digit cut fails
        Arguments.of("ABCD01202", "ABCD", "01", null, false, false, "ABCD0102"),
        // eleven: still a four-digit year, but not a valid length
        Arguments.of("ABCD0120261", "ABCD", "01", 2026L, false, false, "ABCD0161"),
        Arguments.of("ABCD01", "ABCD", "01", null, false, false, "ABCD01"),
        Arguments.of("ABC", null, null, null, false, false, "ABC"),
        Arguments.of("", null, null, null, false, false, ""),
        Arguments.of(null, null, null, null, false, false, ""));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void splitsLikeTheLegacyParser(String psy, String pop, String strat, Long year, boolean valid,
                                 boolean billableLength, String companyStratum) {
    PopStratYear p = PopStratYear.parse(psy);
    assertThat(p.population()).isEqualTo(pop);
    assertThat(p.stratum()).isEqualTo(strat);
    assertThat(p.yearOrNull()).isEqualTo(year);
    assertThat(p.isValid()).isEqualTo(valid);
    assertThat(p.isBillableLength()).isEqualTo(billableLength);
    assertThat(p.companyStratum()).isEqualTo(companyStratum);
  }

  @Test
  void validityUsesTheUntrimmedLengthButPartsTheTrimmedInput() {
    // " ABCD0126" is 9 characters untrimmed: invalid, although the trimmed PSY is billable
    PopStratYear p = PopStratYear.parse(" ABCD0126");
    assertThat(p.isValid()).isFalse();
    assertThat(p.isBillableLength()).isTrue();
    assertThat(p.population()).isEqualTo("ABCD");
    assertThat(p.yearOrNull()).isEqualTo(2026L);
    assertThat(p.psy()).isEqualTo("ABCD0126");
  }

  @Test
  void yearThrowsForANonNumericYear() {
    assertThatThrownBy(() -> PopStratYear.parse("ABCD01XX").year())
        .isInstanceOf(PopStratYear.InvalidYearException.class).hasMessageContaining("XX");
    assertThatThrownBy(() -> PopStratYear.parse("ABCD0120X6").year())
        .hasMessageContaining("is not numeric and invalid");
  }

  @Test
  void ofJoinsTheParts() {
    assertThat(PopStratYear.of("ABCD", "01", 2026L).psy()).isEqualTo("ABCD012026");
    assertThat(PopStratYear.of("ABCD", "01", 2026L).isValid()).isTrue();
    assertThat(PopStratYear.of("ABCD", null, null).psy()).isEqualTo("ABCD");
    assertThat(PopStratYear.of(null, null, null).psy()).isEmpty();
  }

  @Test
  void psySansCenturyDropsLengthModEightCharacters() {
    assertThat(PopStratYear.parse("ABCD012026").psySansCentury()).isEqualTo("ABCD0126");
    assertThat(PopStratYear.parse("ABCD0126").psySansCentury()).isEqualTo("ABCD0126");
  }

  @Test
  void twoDigitCenturyRules() throws Exception {
    assertThat(PopStratYear.convertYearToLong("99")).isEqualTo(1999L);
    assertThat(PopStratYear.convertYearToLong("00")).isEqualTo(2000L);
    assertThatThrownBy(() -> PopStratYear.convertYearToLong(null))
        .isInstanceOf(PopStratYear.InvalidYearException.class);
  }
}
