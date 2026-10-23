package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.RateCheckRules.OverrideRule;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.RateCheckRules.WorksheetPeriod;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.RateCheck;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link RateCheckRules}: the {@code RateManagerBean.hasRate} decisions. */
class RateCheckRulesTest {

  static final String MARK = "ABC123";
  static final LocalDate SCALE = LocalDate.of(2024, 5, 15);
  static final LocalDate RATE_DATE = LocalDate.of(2023, 1, 10);
  static final LocalDate TODAY = LocalDate.of(2024, 6, 1);

  /** In-memory source recording what it was asked. */
  static final class FakeSource implements RateCheckRules.Source {
    List<OverrideRule> rules = List.of();
    final Map<LocalDate, List<WorksheetPeriod>> sheets = new HashMap<>();
    int stumpage = 1;
    final List<String> calls = new ArrayList<>();

    @Override
    public List<OverrideRule> activeOverrideRules(String timberMark, LocalDate scaleDay, LocalDate processingDay,
                                                  String fieldScaleDeckId) {
      calls.add("rules " + scaleDay + " " + processingDay + " " + fieldScaleDeckId);
      return rules;
    }

    @Override
    public List<WorksheetPeriod> confirmedWorksheets(String timberMark, LocalDate day) {
      calls.add("sheets " + day);
      return sheets.getOrDefault(day, List.of());
    }

    @Override
    public int stumpageRateCount(String timberMark, LocalDate appEffectiveDay) {
      calls.add("stumpage " + appEffectiveDay);
      return stumpage;
    }
  }

  static WorksheetPeriod sheet(LocalDate from, LocalDate to) {
    return new WorksheetPeriod(from, to);
  }

  static WorksheetPeriod around(LocalDate d) {
    return sheet(d.minusDays(30), d.plusDays(30));
  }

  static Stream<Arguments> cases() {
    OverrideRule override = new OverrideRule(RateCheckRules.TYPE_OVERRIDE_RULE, null);
    OverrideRule rateDate = new OverrideRule(RateCheckRules.TYPE_RATEDATE, RATE_DATE);
    return Stream.of(
        // name, rules, sheets on scale day, sheets on rate date, stumpage count, expected
        Arguments.of("override rule: rate, no appraisal needed", List.of(override), List.of(), List.of(), 0,
            RateCheck.HAS_RATE),
        Arguments.of("two rules overlap", List.of(override, rateDate), List.of(around(SCALE)), List.of(), 1,
            RateCheck.OVERLAPPING_RULES),
        Arguments.of("no rule, appraisal + stumpage", List.of(), List.of(around(SCALE)), List.of(), 1,
            RateCheck.HAS_RATE),
        Arguments.of("no rule, no appraisal", List.of(), List.of(), List.of(), 1, RateCheck.NO_RATE),
        Arguments.of("no rule, overlapping appraisals", List.of(), List.of(around(SCALE), around(SCALE)),
            List.of(), 1, RateCheck.NO_RATE),
        Arguments.of("no rule, appraisal without stumpage", List.of(), List.of(around(SCALE)), List.of(), 0,
            RateCheck.NO_STUMPAGE),
        Arguments.of("rate date rule uses the rule date", List.of(rateDate), List.of(), List.of(around(RATE_DATE)),
            1, RateCheck.HAS_RATE),
        Arguments.of("rate date rule, no appraisal on the rule date", List.of(rateDate), List.of(around(SCALE)),
            List.of(), 1, RateCheck.NO_RATE),
        Arguments.of("rate date rule, no stumpage", List.of(rateDate), List.of(), List.of(around(RATE_DATE)), 0,
            RateCheck.NO_STUMPAGE),
        Arguments.of("unknown rule type falls through to the scale date", List.of(new OverrideRule("OTHER", null)),
            List.of(around(SCALE)), List.of(), 1, RateCheck.HAS_RATE),
        Arguments.of("appraisal on its first day", List.of(), List.of(sheet(SCALE, SCALE.plusDays(5))), List.of(), 1,
            RateCheck.HAS_RATE),
        Arguments.of("appraisal on its last day", List.of(), List.of(sheet(SCALE.minusDays(5), SCALE)), List.of(), 1,
            RateCheck.HAS_RATE));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void hasRate(String name, List<OverrideRule> rules, List<WorksheetPeriod> onScale, List<WorksheetPeriod> onRate,
               int stumpage, RateCheck expected) {
    FakeSource src = new FakeSource();
    src.rules = rules;
    src.sheets.put(SCALE, onScale);
    src.sheets.put(RATE_DATE, onRate);
    src.stumpage = stumpage;

    assertThat(RateCheckRules.hasRate(src, MARK, SCALE, null, TODAY)).isEqualTo(expected);
  }

  @Test
  void rulesAreLookedUpForScaleDayTodayAndDeck() {
    FakeSource src = new FakeSource();
    src.sheets.put(SCALE, List.of(around(SCALE)));

    RateCheckRules.hasRate(src, MARK, SCALE, "A", TODAY);

    assertThat(src.calls).containsExactly("rules 2024-05-15 2024-06-01 A", "sheets 2024-05-15",
        "stumpage 2024-04-15");
  }

  @Test
  void overrideRuleSkipsTheAppraisalLookups() {
    FakeSource src = new FakeSource();
    src.rules = List.of(new OverrideRule(RateCheckRules.TYPE_OVERRIDE_RULE, null));

    RateCheckRules.hasRate(src, MARK, SCALE, null, TODAY);

    assertThat(src.calls).hasSize(1);
  }

  @Test
  void nullScaleDateRaisesNoRateError() {
    // legacy NPE in startDayToEndDay, swallowed by validateRate
    FakeSource src = new FakeSource();
    assertThat(RateCheckRules.hasRate(src, MARK, null, null, TODAY)).isEqualTo(RateCheck.HAS_RATE);
    assertThat(src.calls).isEmpty();
  }

  @Test
  void nullMarkHasNoRate() {
    assertThat(RateCheckRules.hasRate(new FakeSource(), null, SCALE, null, TODAY)).isEqualTo(RateCheck.NO_RATE);
  }

  @Test
  void rateDateRuleWithoutDateRaisesNoRateError() {
    FakeSource src = new FakeSource();
    src.rules = List.of(new OverrideRule(RateCheckRules.TYPE_RATEDATE, null));
    assertThat(RateCheckRules.hasRate(src, MARK, SCALE, null, TODAY)).isEqualTo(RateCheck.HAS_RATE);
  }

  @Test
  void worksheetNotEncompassingTheDayIsASplitRate() {
    FakeSource src = new FakeSource();
    src.sheets.put(SCALE, List.of(sheet(SCALE.plusDays(1), SCALE.plusDays(9))));

    assertThatThrownBy(() -> RateCheckRules.hasRate(src, MARK, SCALE, null, TODAY))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("SplitRateException");
  }

  @Test
  void encompassesIgnoreTime() {
    assertThat(RateCheckRules.encompassesIgnoreTime(sheet(SCALE, SCALE), SCALE)).isTrue();
    assertThat(RateCheckRules.encompassesIgnoreTime(sheet(SCALE.plusDays(1), SCALE.plusDays(2)), SCALE)).isFalse();
    assertThat(RateCheckRules.encompassesIgnoreTime(sheet(SCALE.minusDays(2), SCALE.minusDays(1)), SCALE)).isFalse();
    assertThat(RateCheckRules.encompassesIgnoreTime(sheet(SCALE.plusDays(1), SCALE.minusDays(1)), SCALE)).isFalse();
    assertThat(RateCheckRules.encompassesIgnoreTime(sheet(null, SCALE), SCALE)).isFalse();
  }

  @Test
  void dayIsThePacificCalendarDay() {
    // 2024-05-16 06:30 UTC is 2024-05-15 23:30 in Vancouver (PDT)
    Date late = Date.from(LocalDateTime.of(2024, 5, 16, 6, 30).toInstant(ZoneOffset.UTC));
    assertThat(RateCheckRules.day(late)).isEqualTo(SCALE);
    assertThat(RateCheckRules.day(new java.sql.Date(late.getTime()))).isEqualTo(SCALE);
    assertThat(RateCheckRules.day(null)).isNull();
    assertThat(RateCheckRules.day(Timestamp.from(late.toInstant()))).isEqualTo(SCALE);
  }
}
