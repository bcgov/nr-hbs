package ca.bc.gov.nrs.hbs.api.rating;

import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.row;
import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.spgKey;
import static ca.bc.gov.nrs.hbs.api.rating.FakeRatingRepository.ts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.rating.RatingRules.Interval;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Override rules and rates (P202–P208): proc sequences, validations, status gating, locks. */
class OverrideRuleCommandsTest {

  static final LocalDate TODAY = LocalDate.parse("2026-10-21");

  FakeRatingRepository repo;
  RecordingTableApi api;
  Audit audit;
  RatingClock clock;

  @BeforeEach
  void setUp() {
    repo = new FakeRatingRepository();
    api = new RecordingTableApi();
    audit = new Audit("TESTER");
    clock = new RatingClock(Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
  }

  static Map<String, Object> body(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  static List<String> problems(Runnable r) {
    try {
      r.run();
    } catch (WorkflowValidationException ex) {
      return ex.problems().stream().map(WorkflowValidationException.Problem::message).toList();
    }
    throw new AssertionError("expected a validation failure");
  }

  Map<String, Object> ruleBody(Object... extra) {
    Map<String, Object> b = body(
        "timberMark", "abc123",
        "scaleEffectiveDate", "2026-01-01",
        "scaleExpiryDate", "2026-06-30",
        "processingEffectiveDate", "2026-10-21",
        "processingExpiryDate", "2026-12-31",
        "ovrrdrtrlComment", "Court order");
    b.putAll(body(extra));
    return b;
  }

  WorkflowCommand dateRule() {
    return new OverrideRuleCreateCommand.DateRule(repo, api, audit, clock);
  }

  WorkflowCommand rateRule() {
    return new OverrideRuleCreateCommand.RateRule(repo, api, audit, clock);
  }

  Map<String, Object> pendingRule(long id) {
    Map<String, Object> r = row(
        "override_rating_rule_id", BigDecimal.valueOf(id),
        "ovrrdrtrl_type", "OVRRDRULE",
        "override_rule_category_code", "PR",
        "timber_mark", "ABC123",
        "processing_effective_date", ts("2026-11-01"),
        "processing_expiry_date", ts("2026-12-31"),
        "scale_effective_date", ts("2026-01-01"),
        "scale_expiry_date", ts("2026-06-30"),
        "ovrrdrtrl_comment", "why",
        "override_rate_date", null,
        "override_rule_status_code", "PND",
        "entry_userid", "IDIR\\ORIG",
        "entry_timestamp", ts("2026-09-01"),
        "update_userid", "IDIR\\ORIG",
        "update_timestamp", FakeRatingRepository.UPDATED,
        "field_scale_deck_id", null);
    repo.rules.put(id, r);
    return r;
  }

  // ── P203 date rule ──

  @Test
  void createDateRule_createsAnActiveDateRule() {
    Map<String, Object> out = dateRule().execute(ruleBody("overrideRateDate", "2026-03-15", "fieldScaleDeckId", "a"));

    assertThat(api.names()).containsExactly("HBS_CREATE_OVERRIDE_RULE");
    var call = api.calls.get(0);
    assertThat(call.arg("i_override_rating_rule_id")).isEqualTo(new BigDecimal(1001));
    assertThat(call.arg("i_ovrrdrtrl_type")).isEqualTo("RTDTRL");
    assertThat(call.arg("i_override_rule_category_code")).isEqualTo("PR");
    assertThat(call.arg("i_override_rule_status_code")).isEqualTo("ACT");
    assertThat(call.arg("i_timber_mark")).isEqualTo("ABC123");
    assertThat(call.arg("i_field_scale_deck_id")).isEqualTo("A");
    assertThat(call.arg("i_override_rate_date")).isEqualTo(ts("2026-03-15"));
    assertThat(call.arg("i_entry_userid")).isEqualTo("TESTER");
    assertThat(out).containsEntry("overrideRatingRuleId", 1001L);
    assertThat(repo.lastOverlapArgs).containsExactly("ABC123", "A", TODAY, LocalDate.parse("2026-12-31"),
        LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"));
  }

  @Test
  void createDateRule_validatesLikeP203() {
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2019-10-20"))))
        .containsExactly(RatingMessages.OVERRIDE_RATE_DATE_PAST);
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", ""))))
        .containsExactly(RatingMessages.OVERRIDE_RATE_DATE_INVALID);
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15",
        "processingEffectiveDate", "1999-01-01"))))
        .containsExactly(RatingMessages.PROCESS_FROM_INVALID);
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15", "timberMark", "ZZZ999"))))
        .containsExactly(RatingMessages.TIMBER_MARK_INVALID);
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15", "timberMark", ""))))
        .containsExactly(RatingMessages.TIMBER_MARK_REQUIRED);
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15", "fieldScaleDeckId", "#"))))
        .containsExactly(RatingMessages.DECK_INVALID);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void createDateRule_overlapWithAnActiveRule_isRefused_withTheDeckWording() {
    repo.overlappingRules = 1;
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15"))))
        .containsExactly(RatingMessages.RULE_OVERLAP);
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15", "fieldScaleDeckId", "B"))))
        .containsExactly(RatingMessages.RULE_OVERLAP_DECK);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void createDateRule_skipsTheOverlapCheckWhenDatesAreInvalid() {
    repo.overlappingRules = 1;
    assertThat(problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15",
        "scaleEffectiveDate", "2026-07-01"))))
        .containsExactly(RatingMessages.SCALE_RANGE_INVALID, RatingMessages.RULE_OVERLAP);
    repo.lastOverlapArgs = null;
    problems(() -> dateRule().execute(ruleBody("overrideRateDate", "2026-03-15", "scaleEffectiveDate", "bad")));
    assertThat(repo.lastOverlapArgs).isNull();
  }

  // ── P204 rate rule ──

  @Test
  void createRateRule_createsAPendingRateRule_afterTheOverlapCheck() {
    Map<String, Object> out = rateRule().execute(ruleBody());
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_CREATE_OVERRIDE_RULE");
    assertThat(call.arg("i_ovrrdrtrl_type")).isEqualTo("OVRRDRULE");
    assertThat(call.arg("i_override_rule_status_code")).isEqualTo("PND");
    assertThat(call.arg("i_override_rate_date")).isNull();
    assertThat(out).containsKey("overrideRatingRuleId");

    repo.overlappingRules = 2;
    assertThat(problems(() -> rateRule().execute(ruleBody()))).containsExactly(RatingMessages.RULE_OVERLAP);
  }

  // ── Activate / De-activate / Update ──

  @Test
  void activate_storesTheWholeRowAsActive() {
    pendingRule(5);
    activate().execute(body("overrideRatingRuleId", "5", "updateTimestamp", "2026-10-01T10:15:30"));

    assertThat(api.names()).containsExactly("HBS_STORE_OVERRIDE_RULE");
    var call = api.calls.get(0);
    assertThat(call.arg("i_override_rule_status_code")).isEqualTo("ACT");
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\ORIG");
    assertThat(call.arg("i_entry_timestamp")).isEqualTo(ts("2026-09-01"));
    assertThat(call.arg("i_update_userid")).isEqualTo("TESTER");
    assertThat(call.arg("i_ovrrdrtrl_comment")).isEqualTo("why");
    assertThat(call.arg("i_scale_effective_date")).isEqualTo(ts("2026-01-01"));
  }

  @Test
  void activate_refusesOverlapNoRatesPastProcessDateAndNonPending() {
    pendingRule(5);
    repo.overlappingRules = 1;
    repo.overrideRateCount = 0;
    assertThat(problems(() -> activate().execute(body("overrideRatingRuleId", "5"))))
        .containsExactly(RatingMessages.RULE_OVERLAP, RatingMessages.RATE_REQUIRED);

    repo.overlappingRules = 0;
    repo.overrideRateCount = 3;
    pendingRule(6).put("PROCESSING_EFFECTIVE_DATE", ts("2026-10-01"));
    assertThat(problems(() -> activate().execute(body("overrideRatingRuleId", "6"))))
        .containsExactly(RatingMessages.PROCESS_FROM_PAST);

    pendingRule(7).put("OVERRIDE_RULE_STATUS_CODE", "ACT");
    assertThat(problems(() -> activate().execute(body("overrideRatingRuleId", "7"))))
        .containsExactly(RatingMessages.ONLY_PENDING_ACTIVATE);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void activate_checksTheLock() {
    pendingRule(5);
    assertThatThrownBy(() -> activate().execute(body("overrideRatingRuleId", "5",
        "updateTimestamp", "2026-10-01T10:15:31"))).isInstanceOf(StaleUpdateException.class);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void deactivate_onlyActiveRules_toInactive() {
    pendingRule(5).put("OVERRIDE_RULE_STATUS_CODE", "ACT");
    deactivate().execute(body("overrideRatingRuleId", 5));
    assertThat(api.calls.get(0).arg("i_override_rule_status_code")).isEqualTo("INA");

    pendingRule(6);
    assertThat(problems(() -> deactivate().execute(body("overrideRatingRuleId", 6))))
        .containsExactly(RatingMessages.ONLY_ACTIVE_DEACTIVATE);
  }

  @Test
  void update_storesTheEditedHeaderOfAPendingRule_withoutTheOverlapCheck() {
    pendingRule(5);
    repo.overlappingRules = 9;
    new OverrideRuleStatusCommands.Update(repo, api, audit, clock).execute(body(
        "overrideRatingRuleId", "5",
        "scaleEffectiveDate", "2026-02-01", "scaleExpiryDate", "2026-07-31",
        "processingEffectiveDate", "2026-11-15", "processingExpiryDate", "2027-01-31",
        "ovrrdrtrlComment", "new reason",
        "updateTimestamp", "2026-10-01T10:15:30"));
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_STORE_OVERRIDE_RULE");
    assertThat(call.arg("i_scale_effective_date")).isEqualTo(ts("2026-02-01"));
    assertThat(call.arg("i_processing_expiry_date")).isEqualTo(ts("2027-01-31"));
    assertThat(call.arg("i_ovrrdrtrl_comment")).isEqualTo("new reason");
    assertThat(call.arg("i_override_rule_status_code")).isEqualTo("PND");
    assertThat(call.arg("i_timber_mark")).isEqualTo("ABC123");
    assertThat(repo.lastOverlapArgs).isNull();
  }

  @Test
  void update_refusesAnActiveRuleAndBadDates() {
    pendingRule(5).put("OVERRIDE_RULE_STATUS_CODE", "ACT");
    assertThat(problems(() -> new OverrideRuleStatusCommands.Update(repo, api, audit, clock)
        .execute(body("overrideRatingRuleId", "5")))).containsExactly(RatingMessages.ONLY_PENDING_EDIT);
    pendingRule(6);
    assertThat(problems(() -> new OverrideRuleStatusCommands.Update(repo, api, audit, clock).execute(body(
        "overrideRatingRuleId", "6", "scaleEffectiveDate", "2026-02-01", "scaleExpiryDate", "2026-01-31",
        "processingEffectiveDate", "2026-11-15", "processingExpiryDate", "2027-01-31"))))
        .containsExactly(RatingMessages.SCALE_RANGE_INVALID);
  }

  // ── Override rates ──

  @Test
  void createRate_allSpeciesAndGradeForLogs() {
    pendingRule(5);
    Map<String, Object> out = new OverrideRateCommands.Create(repo, api, audit, clock).execute(body(
        "overrideRatingRuleId", "5", "scaleSpeciesCode", "", "scaleProductCode", " ", "scaleGradeCode", "ALL",
        "overrideRate", "12.50"));
    var call = api.calls.get(0);
    assertThat(call.name()).isEqualTo("HBS_CREATE_OVERRIDE_RATE");
    assertThat(call.arg("i_override_rating_rule_id")).isEqualTo(new BigDecimal(5));
    assertThat(call.arg("i_scale_species_code")).isNull();
    assertThat(call.arg("i_scale_product_code")).isEqualTo(" ");
    assertThat(call.arg("i_scale_grade_code")).isNull();
    assertThat(call.arg("i_override_rate")).isEqualTo(new BigDecimal("12.50"));
    assertThat(out).containsEntry("overrideRateId", 1001L);
  }

  @Test
  void createRate_validatesTheRowAndThePendingRule() {
    pendingRule(5);
    var create = new OverrideRateCommands.Create(repo, api, audit, clock);
    assertThat(problems(() -> create.execute(body("overrideRatingRuleId", "5", "scaleSpeciesCode", "FI",
        "scaleProductCode", "01", "scaleGradeCode", "U", "overrideRate", "5"))))
        .containsExactly(RatingMessages.SPG_INVALID);
    repo.spg.put(spgKey("FI", "01", "U"), new Interval(LocalDate.MIN, LocalDate.MAX));
    create.execute(body("overrideRatingRuleId", "5", "scaleSpeciesCode", "FI",
        "scaleProductCode", "01", "scaleGradeCode", "U", "overrideRate", "5"));
    assertThat(api.names()).containsExactly("HBS_CREATE_OVERRIDE_RATE");

    pendingRule(6).put("OVERRIDE_RULE_STATUS_CODE", "ACT");
    assertThat(problems(() -> create.execute(body("overrideRatingRuleId", "6", "scaleSpeciesCode", "FI",
        "scaleProductCode", "01", "scaleGradeCode", "U", "overrideRate", "5"))))
        .containsExactly(RatingMessages.ONLY_PENDING_EDIT);
  }

  @Test
  void updateAndRemoveRate() {
    pendingRule(5);
    repo.rates.put(40L, row("override_rate_id", BigDecimal.valueOf(40), "override_rating_rule_id", BigDecimal.valueOf(5),
        "scale_species_code", "FI", "scale_product_code", "01", "scale_grade_code", "U",
        "override_rate", new BigDecimal("1.00"), "entry_userid", "IDIR\\ORIG", "entry_timestamp", ts("2026-09-01"),
        "update_userid", "IDIR\\ORIG", "update_timestamp", FakeRatingRepository.UPDATED));
    new OverrideRateCommands.Update(repo, api, audit, clock).execute(body("overrideRateId", "40",
        "scaleSpeciesCode", "ALL", "scaleProductCode", " ", "scaleGradeCode", "", "overrideRate", "3.25",
        "updateTimestamp", "2026-10-01T10:15:30"));
    new OverrideRateCommands.Remove(repo, api, audit, clock).execute(body("overrideRateId", "40"));

    assertThat(api.names()).containsExactly("HBS_STORE_OVERRIDE_RATE", "HBS_REMOVE_OVERRIDE_RATE");
    var store = api.calls.get(0);
    assertThat(store.arg("i_override_rate")).isEqualTo(new BigDecimal("3.25"));
    assertThat(store.arg("i_scale_species_code")).isNull();
    assertThat(store.arg("i_entry_userid")).isEqualTo("IDIR\\ORIG");
    assertThat(store.arg("i_update_userid")).isEqualTo("TESTER");
    assertThat(api.calls.get(1).arg("i_override_rate_id")).isEqualTo(new BigDecimal(40));

    assertThatThrownBy(() -> new OverrideRateCommands.Update(repo, api, audit, clock).execute(body(
        "overrideRateId", "40", "scaleSpeciesCode", "", "scaleProductCode", " ", "scaleGradeCode", "",
        "overrideRate", "3", "updateTimestamp", "2026-01-01T00:00:01"))).isInstanceOf(StaleUpdateException.class);
  }

  WorkflowCommand activate() {
    return new OverrideRuleStatusCommands.Activate(repo, api, audit, clock);
  }

  WorkflowCommand deactivate() {
    return new OverrideRuleStatusCommands.Deactivate(repo, api, audit, clock);
  }

  @Test
  void capabilitiesAndIds() {
    assertThat(List.of(dateRule(), rateRule(), activate(), deactivate()))
        .extracting(WorkflowCommand::id)
        .containsExactly("rating.overrideRule.createDateRule", "rating.overrideRule.createRateRule",
            "rating.overrideRule.activate", "rating.overrideRule.deactivate");
    assertThat(dateRule().capability()).isEqualTo(ca.bc.gov.nrs.hbs.api.query.Capability.RATE_ADMIN);
  }
}
