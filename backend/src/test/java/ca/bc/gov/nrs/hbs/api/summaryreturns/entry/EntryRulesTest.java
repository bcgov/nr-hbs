package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The pure checks of {@link EntryRules}, with the legacy behaviour (defects included). */
class EntryRulesTest {

  @ParameterizedTest(name = "isValidPrecision({0}, {1}) = {2}")
  @CsvSource({
      "1, 3, true", "1.5, 3, true", "1.123, 3, true", "1.1234, 3, false", "1.1230, 3, true",
      // HBSValidatorHelper defect: a trailing zero drops two characters, so 1.12340 passes
      "1.12340, 3, true", "1.25, 1, false", "1.2, 1, true", "12.0, 1, true"})
  void precision(String value, int places, boolean valid) {
    assertThat(EntryRules.isValidPrecision(value, places)).isEqualTo(valid);
  }

  @ParameterizedTest(name = "isNumber({0}) = {1}")
  @CsvSource({"1, true", "0, false", "-1, false", "1.0, false", "abc, false", "99999, true"})
  void isNumber(String value, boolean number) {
    assertThat(EntryRules.isNumber(value)).isEqualTo(number);
  }

  @ParameterizedTest(name = "volume {0} ({1}) → {2}")
  @CsvSource(nullValues = "null", value = {
      "LO, 1.5, null", "LO, 0, null", "LO, null, Volume must be a valid number.",
      "LO, -0.1, Volume must be a value greater than or equal to zero.",
      "LO, 1000000, Volume must be a valid number less than 1000000.00.",
      "LO, 999999.999, null", "LO, 1.0001, Volume must have 3 or less decimal places.",
      "XM, null, null", "XM, 0, null", "XM, 1, Volume must be blank for product type Christmas Trees."})
  void volume(String product, String volume, String message) {
    assertThat(EntryRules.volumeProblem(product, volume)).isEqualTo(message);
  }

  @Test
  void theYearLimitIsFirstOfJanuarySevenYearsAgo() {
    int year = LocalDate.now(ca.bc.gov.nrs.hbs.api.workflow.Audit.ZONE).getYear() - 7;
    assertThat(EntryRules.beforeYearLimit(LocalDate.of(year, 1, 1))).isFalse();
    assertThat(EntryRules.beforeYearLimit(LocalDate.of(year - 1, 12, 31))).isTrue();
  }

  @Test
  void datesAreIsoAndMustExist() {
    assertThat(EntryRules.parseDate("2026-02-28")).isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(EntryRules.parseDate("2026-02-28T00:00:00")).isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(EntryRules.parseDate("2026-02-30")).isNull();
    assertThat(EntryRules.parseDate("28/02/2026")).isNull();
  }

  @Test
  void loadArrivalAndDigits() {
    assertThat(EntryRules.isValidLoadArrival("1")).isTrue();
    assertThat(EntryRules.isValidLoadArrival("0")).isFalse();
    assertThat(EntryRules.isValidLoadArrival("A1")).isFalse();
    assertThat(EntryRules.allNumbers("012")).isTrue();
    assertThat(EntryRules.allNumbers("1 2")).isFalse();
    assertThat(EntryRules.allNumbers(null)).isFalse();
  }

  @Test
  void areaBasedReturnNumbersCountOnFromTheMarksHighest() {
    EntryFixture x = new EntryFixture();
    AddReturnCommands.AddPiece add = x.addPiece();
    x.lookups.maxReturn.put("M1", "41");
    assertThat(add.areaBasedReturnNumber("M1")).isEqualTo("0042");
    assertThat(add.areaBasedReturnNumber("M2")).isEqualTo("0002"); // legacy: no return yet → "1" + 1
  }
}
