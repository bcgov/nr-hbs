package ca.bc.gov.nrs.hbs.api.detailreturns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** Expected DDNs computed independently from the legacy KeyGenerator* algorithms on epoch milliseconds of Pacific local times (so daylight saving shifts the time part, as in legacy). */
class KeyGeneratorTest {

  @ParameterizedTest
  @CsvSource({
      "1234, 56,   2026-10-21T00:00, 1205Q08E5346C",
      "A12,  7,    2010-07-01T13:45, 0A0CL03A01279",
      "9,    1234, 2000-01-01T00:00, 0010020030940"
  })
  void tallyDdn(String licence, long returnNumber, String scaleDate, String expected) {
    assertThat(KeyGenerator.tally(licence, returnNumber, LocalDateTime.parse(scaleDate))).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
      "S001, 123456789, 2026-10-21T00:00, S04570167J89Y",
      "AB,   42,        2026-07-01T10:30, AB0070000G42V",
      "S001, 1,         2026-01-15T00:00, S00070100C018"
  })
  void weighSlipDdn(String site, String slip, String scaleDate, String expected) {
    assertThat(KeyGenerator.weighSlip(site, slip, LocalDateTime.parse(scaleDate))).isEqualTo(expected);
  }

  @Test
  void ledgerDdn() {
    assertThat(KeyGenerator.ledger(true, 42)).isEqualTo("ARL0000000042");
    assertThat(KeyGenerator.ledger(false, 1234567890L)).isEqualTo("DEP1234567890");
  }

  @Test
  void refusesTooLongParts() {
    assertThatThrownBy(() -> KeyGenerator.tally("12345", 1, LocalDateTime.of(2020, 1, 1, 0, 0)))
        .hasMessage("Primary license number has exceeded max length allowed: 4; value = 12345");
    assertThatThrownBy(() -> KeyGenerator.tally("1234", 12345, LocalDateTime.of(2020, 1, 1, 0, 0)))
        .hasMessage("Primary return number has exceeded max length allowed: 4; value = 12345");
    assertThatThrownBy(() -> KeyGenerator.weighSlip("S00012", "1", LocalDateTime.of(2020, 1, 1, 0, 0)))
        .hasMessageStartingWith("Scale site has exceeded max length allowed: 4");
  }
}
