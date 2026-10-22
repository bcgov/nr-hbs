package ca.bc.gov.nrs.hbs.api.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.RateInterval;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** Table-driven checks of the legacy Rating field rules ({@link RatingRules}). */
class RatingRulesTest {

  static final LocalDate TODAY = LocalDate.parse("2026-10-21");

  static List<String> messages(Problems p) {
    try {
      p.throwIfAny();
      return List.of();
    } catch (WorkflowValidationException ex) {
      return ex.problems().stream().map(WorkflowValidationException.Problem::message).toList();
    }
  }

  static DateValue d(String s) {
    if (s == null || s.isEmpty()) return new DateValue(null, false);
    Map<String, Object> body = new HashMap<>();
    body.put("d", s);
    return RatingInput.date(body, "d");
  }

  @ParameterizedTest(name = "{0} older than 7 years on {1}: {2}")
  @CsvSource({
      "2019-10-21, 2026-10-21, false",  // today less exactly 7 years is allowed (P212 "today's date 7 years ago")
      "2019-10-20, 2026-10-21, true",
      "2020-01-01, 2026-10-21, false",
      "2019-02-28, 2026-02-28, false",
      "2017-02-28, 2024-02-29, false",  // Feb 29 less 7 years = Feb 28 (Calendar.add YEAR)
      "2017-02-27, 2024-02-29, true",
  })
  void sevenYearLimit(String day, String today, boolean older) {
    assertThat(RatingRules.olderThanSevenYears(LocalDate.parse(day), LocalDate.parse(today))).isEqualTo(older);
  }

  @ParameterizedTest(name = "[{index}] {0}..{1} / {2}..{3} (min process year {4}) → {5}")
  @CsvSource(delimiter = '|', value = {
      // scaleFrom | scaleTo | processFrom | processTo | minYear | expected messages (; separated)
      "2026-01-01|2026-03-31|2026-10-21|2026-12-31|0|",
      "2026-04-01|2026-03-31|2026-10-21|2026-12-31|0|Invalid scaling date range.",
      "2019-10-20|2026-03-31|2026-10-21|2026-12-31|0|Scale date must be greater than or equal to the current date less 7 years.",
      "2026-01-01|2026-03-31|2026-10-20|2026-12-31|0|Processing date must be a current or future date.",
      "2026-01-01|2026-03-31|2027-01-01|2026-12-31|0|Invalid processing date range.",
      "|2026-03-31|2026-10-21|2026-12-31|0|Invalid Scale From date.",
      "2026-01-01|2100-01-01|2026-10-21|2026-12-31|0|Invalid Scale To date.",
      "2026-01-01|2026-03-31|1999-12-31|2026-12-31|2000|Invalid process from date.",
      "2026-01-01|2026-03-31|2026-10-21|not-a-date|0|Invalid process to date.",
  })
  void overrideRuleDates(String sf, String st, String pf, String pt, int minYear, String expected) {
    Problems p = new Problems();
    RatingRules.overrideRuleDates(p, d(sf), d(st), d(pf), d(pt), minYear, TODAY);
    assertThat(messages(p)).containsExactlyElementsOf(expected == null ? List.of() : List.of(expected.split(";")));
  }

  @ParameterizedTest(name = "[{index}] species={0} product=''{1}'' grade={2} rate={3}")
  @CsvSource(delimiter = '|', nullValues = "null", value = {
      // species | product | grade | rate | spg exists | expected
      "FI|01|U|12.50|true|",
      "FI|01|U|12.50|false|Species, product and grade combination is invalid.",
      "null|' '|null|10|false|",                    // All species / grade for logs
      "null|01|U|10|true|The species and/or grade can only be all when the product type is logs.",
      "FI|01|null|10|true|The species and/or grade can only be all when the product type is logs.",
      "FI|null|U|10|true|All fields of an override rate detail row must be filled in.",
      "FI|01|U|null|true|All fields of an override rate detail row must be filled in.",
      "FI|01|U|abc|true|Rate must be a number.",
      "FI|01|U|-0.01|true|Rate cannot be less than 0.00.",
      "FI|01|U|0|true|",                             // legacy allows a zero rate
      "FI|01|U|9999.99|true|",
      "FI|01|U|10000|true|Rate cannot be greater than $9,999.99.",
      "FI|01|U|1.234|true|Rate must have 2 or less decimal places.",
  })
  void overrideRateRow(String species, String product, String grade, String rate, boolean spg, String expected) {
    Map<String, Object> body = new HashMap<>();
    body.put("r", rate);
    Problems p = new Problems();
    RatingRules.overrideRateRow(p, species, product == null ? null : product.replace("'", ""), grade,
        RatingInput.number(body, "r"), () -> spg);
    assertThat(messages(p)).containsExactlyElementsOf(expected == null ? List.of() : List.of(expected));
  }

  @ParameterizedTest(name = "[{index}] rate={0} max={1}")
  @CsvSource(delimiter = '|', nullValues = "null", value = {
      "100.00|9999.99|$9,999.99|",
      "null|9999.99|$9,999.99|Rate is required.",
      "x1|9999.99|$9,999.99|Rate must be a number.",
      "1.001|9999.99|$9,999.99|Rate must have 2 or less decimal places.",
      "10000|9999.99|$9,999.99|Rate cannot be greater than $9,999.99.",
      "1000|999.99|999.99|Rate cannot be greater than 999.99.",
      "-1|999.99|999.99|Rate cannot be less than 0.00.",
      "0.00|999.99|999.99|",
  })
  void validatorRate(String rate, String max, String label, String expected) {
    Map<String, Object> body = new HashMap<>();
    body.put("r", rate);
    Problems p = new Problems();
    boolean ok = RatingRules.validatorRate(p, "rate", RatingInput.number(body, "r"), new BigDecimal(max), label);
    assertThat(ok).isEqualTo(expected == null);
    assertThat(messages(p)).containsExactlyElementsOf(expected == null ? List.of() : List.of(expected));
  }

  @ParameterizedTest(name = "[{index}] new {0}..{1}")
  @CsvSource(delimiter = '|', value = {
      // existing interval 2026-01-01..2026-06-30 (id 7)
      "2025-01-01|2025-12-31|",
      "2026-07-01|2026-12-31|",
      "2026-03-01|2026-12-31|Effective date for proposed entry is within an existing interval.",
      "2025-03-01|2026-01-01|Expiry date for proposed entry is within an existing interval.",
      "2025-03-01|2026-12-31|Interval for proposed entry includes an existing interval.",
      "2026-01-01|2026-06-30|Effective date for proposed entry is within an existing interval.;Expiry date for proposed entry is within an existing interval.",
  })
  void districtIntervals(String eff, String exp, String expected) {
    Problems p = new Problems();
    RatingRules.districtIntervals(p, LocalDate.parse(eff), LocalDate.parse(exp),
        List.of(new RateInterval(7, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"))), null);
    assertThat(messages(p)).containsExactlyElementsOf(expected == null ? List.of() : List.of(expected.split(";")));
  }

  @Test
  void districtIntervals_skipItself_andReportEachMessageOnce() {
    var existing = List.of(
        new RateInterval(1, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30")),
        new RateInterval(2, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30")));
    Problems self = new Problems();
    RatingRules.districtIntervals(self, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01"),
        existing.subList(0, 1), 1L);
    assertThat(messages(self)).isEmpty();

    Problems twice = new Problems();
    RatingRules.districtIntervals(twice, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-01"), existing, null);
    assertThat(messages(twice)).containsExactly(RatingMessages.DDR_EFF_IN_INTERVAL, RatingMessages.DDR_EXP_IN_INTERVAL);
  }

  @ParameterizedTest(name = "[{index}] method={0} product=''{1}'' grade={2}")
  @CsvSource(delimiter = '|', nullValues = "null", value = {
      "C|' '|2000-01-01..2099-12-31|true|",
      "C|01|null|true|Rates with Coast Appraisal Method must use Coast grade codes.",
      "I|01|null|true|Rates with Interior Appraisal Method must use Interior grade codes.",
      "I|01|null|false|",                       // P213: non-logs never looked at the grade
      "I|' '|null|false|Rates with Interior Appraisal Method must use Interior grade codes.",
      "C|' '|2026-03-01..2099-12-31|true|Rates for logs with Coast Appraisal Method must use Coast grade codes in effect on the Effective Date.",
      "I|' '|2000-01-01..2026-06-30|true|Rates for logs with Interior Appraisal Method must use Interior grade codes in effect on the Expiry Date.",
      "C|01|2026-03-01..2026-03-02|true|",       // only logs check the grade dates
  })
  void districtGradeSchedule(String method, String product, String grade, boolean checkExists, String expected) {
    Interval interval = grade == null ? null
        : new Interval(LocalDate.parse(grade.split("\\.\\.")[0]), LocalDate.parse(grade.split("\\.\\.")[1]));
    Problems p = new Problems();
    RatingRules.districtGradeSchedule(p, method, product.replace("'", ""), interval,
        LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"), checkExists);
    assertThat(messages(p)).containsExactlyElementsOf(expected == null ? List.of() : List.of(expected));
  }

  @ParameterizedTest(name = "cut block {0}: {1}")
  @CsvSource(delimiter = '|', quoteCharacter = '"', value = {"CB1|true", "A-1(2/3|true", "A-1)|false", "</'(-:>|true", "CB 1|false", "CB_1|false", "CB#|false"})
  void cutBlockCharacters(String cutBlock, boolean ok) {
    assertThat(RatingRules.cutBlockCharacters(cutBlock)).isEqualTo(ok);
  }

  @ParameterizedTest(name = "deck {0}: {1}")
  @CsvSource(delimiter = '|', nullValues = "null", value = {"null|true", "A|true", "7|true", "AB|false", "-|false"})
  void deck(String deck, boolean ok) {
    Problems p = new Problems();
    RatingRules.deck(p, deck);
    assertThat(p.isEmpty()).isEqualTo(ok);
  }

  @Test
  void productKeepsTheLogsSpace() {
    Map<String, Object> body = new HashMap<>();
    body.put("p", " ");
    assertThat(RatingInput.product(body, "p")).isEqualTo(" ");
    body.put("p", "");
    assertThat(RatingInput.product(body, "p")).isNull();
    body.put("p", " 01 ");
    assertThat(RatingInput.product(body, "p")).isEqualTo("01");
  }

  @Test
  void lockAcceptsTheMidnightDateTheQueryLayerSends() {
    Map<String, Object> body = new HashMap<>();
    body.put("updateTimestamp", "2026-10-01");
    RatingInput.checkLock(FakeRatingRepository.ts("2026-10-01"), body);
    body.put("updateTimestamp", "2026-10-01T10:15:30");
    RatingInput.checkLock(FakeRatingRepository.UPDATED, body);
    body.put("updateTimestamp", "2026-10-01T10:15:31");
    assertThatThrownBy(() -> RatingInput.checkLock(FakeRatingRepository.UPDATED, body))
        .isInstanceOf(ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException.class);
  }
}
