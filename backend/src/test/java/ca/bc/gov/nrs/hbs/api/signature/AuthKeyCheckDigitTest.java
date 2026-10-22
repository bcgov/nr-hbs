package ca.bc.gov.nrs.hbs.api.signature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Legacy CheckDigit.aKChkDgt: 'A' + (Σ char × position) mod 26, worked by hand. */
class AuthKeyCheckDigitTest {

  @ParameterizedTest
  @CsvSource({
      "1234, ABCDEFG, ABCDEFGE",
      "A123, ZZ99XY7, ZZ99XY7V",
      "S001, KEY12345, KEY12345T",
  })
  void appendsLegacyCheckDigit(String licence, String requested, String expected) {
    assertThat(AuthKeyCheckDigit.suffix(licence, requested)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
      "A, N",   // 65 x 1 = 65; 65 % 26 = 13 -> 'N'
      "AB, P",  // 65 + 66 x 2 = 197; 197 % 26 = 15 -> 'P'
  })
  void singleValues(String domain, char expected) {
    assertThat(AuthKeyCheckDigit.of(domain)).isEqualTo(expected);
  }
}
