package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.NumberValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Override rate detail rows of a Pending rate rule — the rate grid of P204 /
 * P207 ({@code P204Action/P207Action.hbsPerform}: the new row is created,
 * each listed row is updated, a row with every field cleared is deleted;
 * each row through {@code validateRate}). The grid is only shown for a
 * Pending rule, so all three require one.
 *
 * <p>Body: {@code scaleSpeciesCode} / {@code scaleGradeCode} blank (or "ALL")
 * = All, stored null; {@code scaleProductCode} " " = Logs.
 */
final class OverrideRateCommands {

  private OverrideRateCommands() {
  }

  /** The rate row's fields, validated as {@code validateRate}. */
  private record RateRow(String species, String product, String grade, NumberValue rate) {
  }

  private static RateRow validRow(RatingRepository repo, Map<String, Object> body) {
    RateRow row = new RateRow(
        RatingInput.allOrCode(body, "scaleSpeciesCode"),
        RatingInput.product(body, "scaleProductCode"),
        RatingInput.allOrCode(body, "scaleGradeCode"),
        RatingInput.number(body, "overrideRate"));
    Problems p = new Problems();
    RatingRules.overrideRateRow(p, row.species(), row.product(), row.grade(), row.rate(),
        () -> repo.speciesProductGrade(row.species(), row.product(), row.grade()).isPresent());
    p.throwIfAny();
    return row;
  }

  /** P204/P207 new rate row ({@code RuleHelper.createOverrideRate}). */
  @Component
  static class Create extends RatingCommand {
    Create(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.overrideRate.create";
    }

    @Override
    public String legacy() {
      return "P204/P207 rate row (validateRate, RuleHelper.createOverrideRate)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long ruleId = RatingInput.id(body, "overrideRatingRuleId");
      Map<String, Object> rule = repo.overrideRule(ruleId);
      requireStatus(rule, PENDING, RatingMessages.ONLY_PENDING_EDIT);
      RateRow row = validRow(repo, body);

      long id = tableApi.nextId("OVERRIDE_RATE_SEQ");
      Timestamp now = audit.now();
      String user = audit.user();
      tableApi.call("HBS_CREATE_OVERRIDE_RATE", ProcArgs.of()
          .put("i_override_rate_id", id)
          .put("i_override_rating_rule_id", ruleId)
          .put("i_scale_species_code", row.species())
          .put("i_scale_product_code", row.product())
          .put("i_scale_grade_code", row.grade())
          .put("i_override_rate", row.rate().value())
          .put("i_entry_userid", user)
          .put("i_entry_timestamp", now)
          .put("i_update_userid", user)
          .put("i_update_timestamp", now));
      return Map.of("overrideRateId", id, "overrideRatingRuleId", ruleId);
    }
  }

  /** P204/P207 listed rate row changed ({@code RuleHelper.updateOverrideRate}). */
  @Component
  static class Update extends RatingCommand {
    Update(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.overrideRate.update";
    }

    @Override
    public String legacy() {
      return "P204/P207 rate row (validateRate, RuleHelper.updateOverrideRate)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "overrideRateId");
      Map<String, Object> rate = repo.overrideRate(id);
      RatingInput.checkLock(col(rate, "update_timestamp"), body);
      long ruleId = ((Number) col(rate, "override_rating_rule_id")).longValue();
      requireStatus(repo.overrideRule(ruleId), PENDING, RatingMessages.ONLY_PENDING_EDIT);
      RateRow row = validRow(repo, body);

      String proc = "HBS_STORE_OVERRIDE_RATE";
      tableApi.call(proc, storeArgs(proc, rate)
          .put("i_scale_species_code", row.species())
          .put("i_scale_product_code", row.product())
          .put("i_scale_grade_code", row.grade())
          .put("i_override_rate", row.rate().value()));
      return Map.of("overrideRateId", id, "overrideRatingRuleId", ruleId);
    }
  }

  /** P204/P207 rate row with every field cleared ({@code RuleHelper.deleteRate}). */
  @Component
  static class Remove extends RatingCommand {
    Remove(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.overrideRate.remove";
    }

    @Override
    public String legacy() {
      return "P204/P207 cleared rate row (RuleHelper.deleteRate)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "overrideRateId");
      Map<String, Object> rate = repo.overrideRate(id);
      RatingInput.checkLock(col(rate, "update_timestamp"), body);
      long ruleId = ((Number) col(rate, "override_rating_rule_id")).longValue();
      requireStatus(repo.overrideRule(ruleId), PENDING, RatingMessages.ONLY_PENDING_EDIT);
      tableApi.call("HBS_REMOVE_OVERRIDE_RATE", ProcArgs.of().put("i_override_rate_id", id));
      return Map.of("overrideRateId", id, "overrideRatingRuleId", ruleId);
    }
  }
}
