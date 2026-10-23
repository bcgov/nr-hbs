package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups.ScaleGradeRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups.SpeciesProductGradeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScaledLogXrefTest {

  private final StubTallyLookups lookups = new StubTallyLookups();

  @ParameterizedTest
  @CsvSource(value = {"FI|' '|J|true", "FI||J|false", "|X|J|false", "FI|X||false", "' '|' '|' '|true"},
      delimiter = '|', emptyValue = "")
  void validationNeededWhenNoneEmptyWhitespaceCounts(String species, String product, String grade,
                                                     boolean needed) {
    assertThat(ScaledLogXref.isInputSpeciesProductGradeValidationNeeded(species, product, grade))
        .isEqualTo(needed);
  }

  @Test
  void validationNotNeededOnNull() {
    assertThat(ScaledLogXref.isInputSpeciesProductGradeValidationNeeded(null, " ", "J")).isFalse();
  }

  @Test
  void speciesProductGradeWidenedToWholeDays() {
    lookups.addSpg("FI", " ", "J", ts("2020-03-05 10:15:00"), ts("2025-07-09 08:00:00"));
    SpeciesProductGradeRef spg = ScaledLogXref.getSpeciesProductGrade(lookups, "FI", " ", "J");
    assertThat(spg.effectiveDate()).isEqualTo(ts("2020-03-05 00:00:00"));
    assertThat(spg.expiryDate()).isEqualTo(ts("2025-07-09 23:59:59"));
    assertThat(ScaledLogXref.isSpeciesProductGradeEffective(ts("2025-07-09 18:00:00"), spg)).isTrue();
    assertThat(ScaledLogXref.isSpeciesProductGradeEffective(ts("2025-07-10 00:00:00"), spg)).isFalse();
    assertThat(ScaledLogXref.isSpeciesProductGradeEffective(ts("2020-03-05 00:00:00"), spg)).isTrue();
    assertThat(ScaledLogXref.isSpeciesProductGradeEffective(ts("2020-03-04 23:59:59"), spg)).isFalse();
  }

  @Test
  void speciesProductGradeNotFoundIsNull() {
    assertThat(ScaledLogXref.getSpeciesProductGrade(lookups, "FI", " ", "J")).isNull();
  }

  @ParameterizedTest
  @CsvSource({"C,C,true", "c,C,true", "I,I,true", "i,I,true", "C,I,false", "X,C,false", ",C,false"})
  void gradeSubInfoByScheduleCaseInsensitive(String requested, String stored, boolean found) {
    lookups.addGrade(stored, "J", ts("2020-01-01 12:00:00"), ts("2030-01-01 12:00:00"));
    ScaleGradeRef g = ScaledLogXref.findGradeSubInfo(lookups, "J", requested);
    if (found) {
      assertThat(g).isNotNull();
      assertThat(g.effectiveDate()).isEqualTo(ts("2020-01-01 00:00:00"));
      assertThat(g.expiryDate()).isEqualTo(ts("2030-01-01 23:59:59"));
    } else {
      assertThat(g).isNull();
    }
  }

  @ParameterizedTest(name = "{0} in [{1}, {2}] = {3}")
  @CsvSource({
      "2026-06-01 00:00:00, 2026-01-01 00:00:00, 2026-12-31 00:00:00, true",
      "2026-01-01 00:00:00, 2026-01-01 00:00:00, 2026-12-31 00:00:00, true",
      "2026-12-31 00:00:00, 2026-01-01 00:00:00, 2026-12-31 00:00:00, true",
      "2027-01-01 00:00:00, 2026-01-01 00:00:00, 2026-12-31 00:00:00, false",
      "2025-12-31 00:00:00, 2026-01-01 00:00:00, 2026-12-31 00:00:00, false",
      "2026-06-01 00:00:00, 2026-12-31 00:00:00, 2026-01-01 00:00:00, false",
      "2026-06-01 00:00:00, ,                    2026-12-31 00:00:00, false",
      "2026-06-01 00:00:00, 2026-01-01 00:00:00, ,                    false",
      ",                    2026-01-01 00:00:00, 2026-12-31 00:00:00, false",
  })
  void insideDateRange(String date, String effective, String expiry, boolean inside) {
    assertThat(ScaledLogXref.insideDateRange(date == null ? null : ts(date),
        effective == null ? null : ts(effective), expiry == null ? null : ts(expiry))).isEqualTo(inside);
  }

  @Test
  void dayBoundsKeepNull() {
    assertThat(ScaledLogXref.startDay(null)).isNull();
    assertThat(ScaledLogXref.endDay(null)).isNull();
  }

  @Test
  void legacyEmptyKeepsWhitespace() {
    assertThat(ScaledLogXref.legacyEmpty(null)).isTrue();
    assertThat(ScaledLogXref.legacyEmpty("")).isTrue();
    assertThat(ScaledLogXref.legacyEmpty(" ")).isFalse();
  }
}
