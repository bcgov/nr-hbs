package ca.bc.gov.nrs.hbs.api.billing;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Table-driven checks of the P811/P813 and P823 force-date rules (legacy examples). */
class ForceDateRulesTest {

  private static final String M = LegacyMessages.FORCE_DATE_MONTHLY;
  private static final String B = LegacyMessages.FORCE_DATE_BIWEEKLY;
  private static final String D = LegacyMessages.FORCE_DATE_DAILY;
  private static final String Y = LegacyMessages.FORCE_YEAR_INVALID;

  @ParameterizedTest(name = "P811 today {0}, force {1}, {2} -> {3}")
  @CsvSource(nullValues = "-", value = {
      "2026-10-21, 2026-10-21, MTH, -",
      "2026-10-21, 2026-11-18, MTH, -",    // 28 days
      "2026-10-21, 2026-11-19, MTH, M",    // 29 days
      "2026-10-21, 2026-10-20, MTH, M",    // in the past
      "2026-10-21, 2026-11-04, BWK, -",    // 14 days
      "2026-10-21, 2026-11-05, BWK, B",
      "2026-10-21, 2026-10-22, DLY, -",
      "2026-10-21, 2026-10-23, DLY, D",
      "2026-12-20, 2027-01-17, MTH, -",    // Dec -> Jan: 31 + 17 - 20 = 28
      "2026-12-20, 2027-01-18, MTH, M",    // 29
      "2026-12-31, 2027-01-01, DLY, -",    // 31 + 1 - 31 = 1
      "2026-11-30, 2027-01-01, MTH, M",    // later year, not December
      "2026-10-21, 2025-10-21, MTH, M",    // earlier year
      "2026-10-21, 2100-01-01, MTH, Y",
      "2026-10-21, 2026-12-31, XXX, -",    // unknown frequency: no window
  })
  void summarization(LocalDate today, LocalDate force, String frequency, String expected) {
    List<String> errors = ForceDateRules.summarization(today, force, frequency);
    assertThat(errors).containsExactlyElementsOf(expected == null ? List.of() : List.of(message(expected)));
  }

  @ParameterizedTest(name = "P823 today {0}, force {1}, {2} -> {3}")
  @CsvSource(nullValues = "-", value = {
      "2026-10-21, 2026-10-25, MTH, -",
      "2026-10-21, 2026-10-20, DLY, M",    // legacy precedence: any earlier date reports Monthly
      "2026-10-21, 2026-11-19, MTH, M",
      "2026-10-21, 2026-11-19, BWK, B",
      "2026-10-21, 2026-10-23, DLY, D",
      "2026-10-21, 2026-11-19, DLY, D",
      "2026-12-20, 2027-01-18, MTH, M",
      "2026-11-30, 2027-02-01, MTH, -",    // later year, not December: P823 accepted it
      "2026-10-21, 2025-10-25, MTH, -",    // earlier year: no window message on P823
  })
  void ratioComputationWindow(LocalDate today, LocalDate force, String frequency, String expected) {
    List<String> errors = ForceDateRules.ratioComputation(today, force, frequency, null, null);
    assertThat(errors).containsExactlyElementsOf(expected == null ? List.of() : List.of(message(expected)));
  }

  @ParameterizedTest(name = "P823 period: force {0}")
  @CsvSource(nullValues = "-", value = {
      "2026-10-22, -",
      "2026-10-01, START",   // equal to last (00:00) is not after it
      "2026-09-30, START",
      "2026-11-01, END",     // at the next date (00:00)
  })
  void ratioComputationPeriod(LocalDate force, String expected) {
    LocalDate today = LocalDate.of(2026, 9, 20);
    List<String> errors = ForceDateRules.ratioComputation(today, force, "XXX",
        LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0));
    String msg = expected == null ? null
        : "START".equals(expected) ? LegacyMessages.FORCE_BEFORE_PERIOD_START : LegacyMessages.FORCE_AFTER_PERIOD_END;
    assertThat(errors).containsExactlyElementsOf(msg == null ? List.of() : List.of(msg));
  }

  private static String message(String code) {
    return switch (code) {
      case "M" -> M;
      case "B" -> B;
      case "D" -> D;
      default -> Y;
    };
  }
}
