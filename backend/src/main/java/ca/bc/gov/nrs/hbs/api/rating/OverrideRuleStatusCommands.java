package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.HeaderDates;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Status changes and header edits of an existing override rule — each a
 * full-row {@code HBS_STORE_OVERRIDE_RULE} after re-reading the rule,
 * checking its {@code UPDATE_TIMESTAMP} and the status the legacy page
 * offered the action for.
 */
final class OverrideRuleStatusCommands {

  private static final String STORE = "HBS_STORE_OVERRIDE_RULE";

  private OverrideRuleStatusCommands() {
  }

  /**
   * P204/P207 "Activate Rule" ({@code P204Action/P207Action.hbsPerform}, the
   * non-Save branch, then {@code RuleHelper.updateOverrideRateRule} with
   * status ACT): only a Pending rule (P201 links P207 only for Pending rules);
   * the header dates are re-validated, the scaling and processing intervals
   * must not both overlap an Active rule of the mark (and deck), and the rule
   * needs at least one override rate.
   */
  @Component
  static class Activate extends RatingCommand {
    Activate(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.overrideRule.activate";
    }

    @Override
    public String legacy() {
      return "P204/P207 Activate Rule (validateOverrideRule, RateManagerBean.findOverLapCountByCriteria)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "overrideRatingRuleId");
      Map<String, Object> rule = repo.overrideRule(id);
      RatingInput.checkLock(col(rule, "update_timestamp"), body);
      requireStatus(rule, PENDING, RatingMessages.ONLY_PENDING_ACTIVATE);

      Problems p = new Problems();
      LocalDate scaleFrom = day(rule, "scale_effective_date");
      LocalDate scaleTo = day(rule, "scale_expiry_date");
      LocalDate processFrom = day(rule, "processing_effective_date");
      LocalDate processTo = day(rule, "processing_expiry_date");
      HeaderDates dates = RatingRules.overrideRuleDates(p, present(scaleFrom), present(scaleTo),
          present(processFrom), present(processTo), 0, today());
      String deck = str(rule, "field_scale_deck_id");
      if (deck != null && deck.isEmpty()) deck = null;
      if (dates.scaleOk() && dates.processOk()
          && repo.countOverlappingActiveRules(str(rule, "timber_mark"), deck, processFrom, processTo,
              scaleFrom, scaleTo) > 0) {
        p.add("timberMark", deck == null ? RatingMessages.RULE_OVERLAP : RatingMessages.RULE_OVERLAP_DECK);
      }
      if (repo.countOverrideRates(id) == 0) {
        p.add("overrideRate", RatingMessages.RATE_REQUIRED);
      }
      p.throwIfAny();

      tableApi.call(STORE, storeArgs(STORE, rule).put("i_override_rule_status_code", ACTIVE));
      return Map.of("overrideRatingRuleId", id);
    }
  }

  /**
   * P202/P206 "De-activate Rule" ({@code P202Action/P206Action},
   * {@code RateManagerBean.disableOverrideRule} → {@code OverrideRateRuleFactory.disableEntity}:
   * status INA, update user and time). P202.jsp offers it for Active rules only.
   */
  @Component
  static class Deactivate extends RatingCommand {
    Deactivate(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.overrideRule.deactivate";
    }

    @Override
    public String legacy() {
      return "P202/P206 De-activate Rule (RateManagerBean.disableOverrideRule)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "overrideRatingRuleId");
      Map<String, Object> rule = repo.overrideRule(id);
      RatingInput.checkLock(col(rule, "update_timestamp"), body);
      requireStatus(rule, ACTIVE, RatingMessages.ONLY_ACTIVE_DEACTIVATE);
      tableApi.call(STORE, storeArgs(STORE, rule).put("i_override_rule_status_code", INACTIVE));
      return Map.of("overrideRatingRuleId", id);
    }
  }

  /**
   * P207 "Save and Add More Rate Details" on an existing Pending rate rule
   * ({@code P207Action.hbsPerform} ACTION_SAVE): re-validates and stores the
   * editable header — scale and process dates and the reason — keeping it
   * Pending (category PR from the hidden {@code prodType}, type OVRRDRULE).
   * The timber mark and deck are not editable on P207. No overlap check on
   * Save ({@code validateOverrideRule} skips it for ACTION_SAVE).
   */
  @Component
  static class Update extends RatingCommand {
    Update(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    public String id() {
      return "rating.overrideRule.update";
    }

    @Override
    public String legacy() {
      return "P207 Save (P207Action.validateOverrideRule, RuleHelper.updateOverrideRateRule)";
    }

    @Override
    public Capability capability() {
      return Capability.RATE_ADMIN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      long id = RatingInput.id(body, "overrideRatingRuleId");
      Map<String, Object> rule = repo.overrideRule(id);
      RatingInput.checkLock(col(rule, "update_timestamp"), body);
      requireStatus(rule, PENDING, RatingMessages.ONLY_PENDING_EDIT);

      Problems p = new Problems();
      DateValue scaleFrom = RatingInput.date(body, "scaleEffectiveDate");
      DateValue scaleTo = RatingInput.date(body, "scaleExpiryDate");
      DateValue processFrom = RatingInput.date(body, "processingEffectiveDate");
      DateValue processTo = RatingInput.date(body, "processingExpiryDate");
      RatingRules.overrideRuleDates(p, scaleFrom, scaleTo, processFrom, processTo, 0, today());
      String comment = RatingInput.text(body, "ovrrdrtrlComment");
      if (comment != null && comment.length() > 240) {
        p.add("ovrrdrtrlComment", "Reason cannot be greater than 240 characters.");
      }
      p.throwIfAny();

      tableApi.call(STORE, storeArgs(STORE, rule)
          .put("i_override_rule_category_code", CATEGORY_PRIMARY)
          .put("i_processing_effective_date", processFrom.date())
          .put("i_processing_expiry_date", processTo.date())
          .put("i_scale_effective_date", scaleFrom.date())
          .put("i_scale_expiry_date", scaleTo.date())
          .put("i_override_rule_status_code", PENDING)
          .put("i_ovrrdrtrl_type", TYPE_RATE_RULE)
          .put("i_ovrrdrtrl_comment", comment));
      return Map.of("overrideRatingRuleId", id);
    }
  }

  private static DateValue present(LocalDate d) {
    return new DateValue(d, true);
  }
}
