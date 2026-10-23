package ca.bc.gov.nrs.hbs.api.rating;

import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.spgKey;
import static ca.bc.gov.nrs.hbs.api.rating.OverrideRuleCommandsTest.body;
import static ca.bc.gov.nrs.hbs.api.rating.OverrideRuleCommandsTest.problems;
import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P230/P231 stumpage rate lookup: validation and the HBS_RATES call sequence per return type. */
class StumpageRateServiceTest {

  static final LocalDate TODAY = LocalDate.parse("2026-10-21");

  FakeRatingRepository repo;
  RecordingTableApi api;
  StumpageRateService service;
  String schedule = "C";

  @BeforeEach
  void setUp() {
    repo = new FakeRatingRepository();
    api = new RecordingTableApi();
    RatingClock clock = new RatingClock(Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    service = new StumpageRateService(repo, api, clock,
        (org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate) null) {
      @Override
      String gradeScheduleType(String timberMark, LocalDate day) {
        return schedule;
      }

      @Override
      Map<String, Object> appraisal(String timberMark, LocalDate day) {
        return Map.of("appraisalMethod", "C");
      }
    };
    repo.spg.put(spgKey("FI", " ", "U"), new Interval(LocalDate.parse("2000-01-01"), LocalDate.parse("2099-12-31")));
    repo.grades.put("C|U", new Interval(LocalDate.parse("2000-01-01"), LocalDate.parse("2099-12-31")));
  }

  Map<String, Object> lookup(Object... kv) {
    Map<String, Object> q = new HashMap<>(body("timberMark", "abc123", "species", "FI", "product", " ",
        "grade", "U", "scaleDate", "2026-05-01", "returnType", "Normal"));
    q.putAll(body(kv));
    return service.lookup(StumpageRateService.Request.from(q));
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> rate(Map<String, Object> result) {
    return (Map<String, Object>) result.get("rate");
  }

  @Test
  void normal_getOverrideAndAppraisal_thenRetrieveNormalRate() {
    api.answer("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", c -> {
      Map<String, Object> out = new HashMap<>();
      out.put("o_rate_rule_found", "Y");
      out.put("o_rate_rule_id", new BigDecimal(77));
      out.put("o_rate_rule_type", "OVRRDRULE");
      out.put("o_worksheet_found", "Y");
      out.put("o_stumpage_found", "N");
      out.put("o_appraisal_method", "C");
      out.put("o_reserve_stumpage", new BigDecimal("1.10"));
      out.put("o_rate_rule_overlap", "N");
      return out;
    });
    api.answer("HBS_RATES.RETRIEVE_NORMAL_RATE", c -> Map.of("o_rate_found", "Y",
        "o_reserve_stumpage", new BigDecimal("10.00"), "o_bonus_bid", new BigDecimal("2.50"),
        "o_dev_levy", new BigDecimal("0.25"), "o_silv_levy", new BigDecimal("0.00"),
        "o_rate_source", "Override Rate", "o_override_rate_id", new BigDecimal(901)));

    Map<String, Object> rate = rate(lookup());

    assertThat(api.names()).containsExactly("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", "HBS_RATES.RETRIEVE_NORMAL_RATE");
    var normal = api.calls.get(1);
    assertThat(normal.arg("i_rate_rule_found")).isEqualTo("Y");
    assertThat(normal.arg("i_rate_rule_id")).isEqualTo(new BigDecimal(77));
    assertThat(normal.arg("i_worksheet_found")).isEqualTo("Y");
    assertThat(normal.arg("i_stumpage_found")).isEqualTo("N");
    assertThat(normal.arg("i_fixed_rates_found")).isEqualTo("N");
    assertThat(normal.arg("i_avoidable")).isEqualTo("Y");
    assertThat(normal.arg("i_product")).isEqualTo(" ");
    assertThat(normal.arg("i_scale_date")).isEqualTo(Timestamp.valueOf("2026-05-01 00:00:00"));
    assertThat(rate).containsEntry("totalRate", new BigDecimal("12.75"))
        .containsEntry("rateSource", "Override Rate")
        .containsEntry("overrideRuleId", new BigDecimal(77))
        .containsEntry("overrideRateId", new BigDecimal(901))
        .containsEntry("overrideRuleType", "Rate");
  }

  @Test
  void normal_overlappingRulesOrNoRate_isNotFound() {
    api.answer("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", c -> Map.of("o_rate_rule_overlap", "Y"));
    assertThat(rate(lookup())).isNull();
    assertThat(api.names()).containsExactly("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL");

    api.calls.clear();
    api.answer("HBS_RATES.GET_OVERRIDE_AND_APPRAISAL", c -> Map.of("o_rate_rule_found", "N"));
    api.answer("HBS_RATES.RETRIEVE_NORMAL_RATE", c -> Map.of("o_rate_found", "N", "o_failure_reason", "NOWKSHT"));
    assertThat(rate(lookup())).isNull();
  }

  @Test
  void privateMark_isAZeroRateWithoutCallingTheRatingPackage() {
    repo.billedFileTypes.put("ABC123", "B13");
    Map<String, Object> rate = rate(lookup());
    assertThat(api.calls).isEmpty();
    assertThat(rate).containsEntry("rateSource", "Private Mark").containsEntry("totalRate", new BigDecimal("0.00"));
  }

  @Test
  void beachcombAndWaste() {
    api.answer("HBS_RATES.RETRIEVE_BEACHCOMB_RATE", c -> Map.of("o_rate_found", "Y",
        "o_reserve_stumpage", new BigDecimal("3.00"), "o_rate_source", "Appraisal", "o_override_rule_id", new BigDecimal(5)));
    Map<String, Object> rate = rate(lookup("timberMark", "BCH001", "returnType", "Beachcomb",
        "beachcombTimberMark", "BCOMB1"));
    assertThat(api.calls.get(0).arg("i_beachcomb_seg_timber_mark")).isEqualTo("BCOMB1");
    assertThat(rate).containsEntry("totalRate", new BigDecimal("3.00")).containsEntry("overrideRuleId", new BigDecimal(5));

    api.calls.clear();
    repo.cutBlocks.put("ABC123", List.of("CB1"));
    api.answer("HBS_RATES.RETRIEVE_WASTE_RATE", c -> Map.of("o_rate_found", "Y",
        "o_reserve_stumpage", new BigDecimal("4.00"), "o_rate_source", "Waste Rate", "o_rate_id", new BigDecimal(8)));
    rate = rate(lookup("returnType", "Waste", "cutBlock", "cb1"));
    assertThat(api.names()).containsExactly("HBS_RATES.RETRIEVE_WASTE_RATE");
    assertThat(api.calls.get(0).arg("i_cut_block")).isEqualTo("CB1");
    assertThat(rate).containsEntry("wasteRateId", new BigDecimal(8));
  }

  @Test
  void validation_likeP230() {
    assertThat(problems(() -> lookup("scaleDate", "2026-10-22")))
        .containsExactly("Scale Date must be on or before the current date.");
    assertThat(problems(() -> lookup("timberMark", "ZZZ999"))).containsExactly(RatingMessages.TIMBER_MARK_REF);
    assertThat(problems(() -> lookup("timberMark", "BCH001"))).containsExactly(RatingMessages.RETURN_TYPE_BEACHCOMB);
    assertThat(problems(() -> lookup("cutBlock", "CB1", "beachcombTimberMark", "X")))
        .containsExactly(RatingMessages.CUT_BLOCK_BLANK, RatingMessages.BEACHCOMB_MARK_BLANK);
    assertThat(problems(() -> lookup("returnType", "Beachcomb")))
        .containsExactly(RatingMessages.NOT_BEACHCOMB_MARK, RatingMessages.BEACHCOMB_MARK_REQUIRED);
    assertThat(problems(() -> lookup("timberMark", "BCH001", "returnType", "Beachcomb", "beachcombTimberMark", "NOPE")))
        .containsExactly(RatingMessages.BEACHCOMB_MARK_INVALID);
    assertThat(problems(() -> lookup("returnType", "Waste")))
        .containsExactly(RatingMessages.WASTE_CUT_BLOCK_REQUIRED);
    assertThat(problems(() -> lookup("returnType", "Waste", "cutBlock", "CB9")))
        .containsExactly(RatingMessages.CUT_BLOCK_INVALID_FOR_MARK);
    assertThat(problems(() -> lookup("species", "", "grade", "")))
        .containsExactly(RatingMessages.SPECIES_REQUIRED, RatingMessages.GRADE_REQUIRED);
    assertThat(problems(() -> lookup("species", "HE"))).containsExactly(RatingMessages.SPG_INVALID);
    assertThat(problems(() -> lookup("scaleDate", "1999-12-31")))
        .containsExactly(RatingMessages.SPG_NOT_ON_SCALE_DATE);
    schedule = "I";
    assertThat(problems(() -> lookup())).containsExactly(RatingMessages.GRADE_INTERIOR);
    assertThat(api.calls).isEmpty();
  }
}
