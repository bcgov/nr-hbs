package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.rating.RatingRules.HeaderDates;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;

/**
 * Add an override rule — two legacy screens, one bean each:
 * <ul>
 *   <li>{@code rating.overrideRule.createDateRule}: P203 "Activate Rule"
 *       ({@code $V/plu/P203Action.hbsPerform/validateForm},
 *       {@code RuleHelper.createOverrideDateRule}) — the date rule is saved
 *       directly as Active, so the overlap check applies.</li>
 *   <li>{@code rating.overrideRule.createRateRule}: P204 first "Save and Add
 *       More Rate Details" ({@code $V/plu/P204Action.hbsPerform} ACTION_SAVE
 *       with no rule id yet, {@code RuleHelper.createOverrideRateRule}) — saved
 *       as Pending; P204's {@code validateOverrideRule} runs the overlap check
 *       on Save as well.</li>
 * </ul>
 * Both are reached from P200, which required an existing timber mark.
 */
abstract class OverrideRuleCreateCommand extends RatingCommand {

  OverrideRuleCreateCommand(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
    super(repo, tableApi, audit, clock);
  }

  @Override
  public Capability capability() {
    return Capability.RATE_ADMIN;
  }

  abstract boolean dateRule();

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    LocalDate today = today();
    Problems p = new Problems();
    String timberMark = RatingInput.upper(body, "timberMark");
    timberMark(p, timberMark);

    DateValue scaleFrom = RatingInput.date(body, "scaleEffectiveDate");
    DateValue scaleTo = RatingInput.date(body, "scaleExpiryDate");
    DateValue processFrom = RatingInput.date(body, "processingEffectiveDate");
    DateValue processTo = RatingInput.date(body, "processingExpiryDate");
    // P203 also rejects process years before 2000; P204 only checks the 4-digit / ≤ 2099 year.
    HeaderDates dates = RatingRules.overrideRuleDates(p, scaleFrom, scaleTo, processFrom, processTo,
        dateRule() ? 2000 : 0, today);

    LocalDate overrideRateDate = null;
    if (dateRule()) {
      DateValue rateDate = RatingInput.date(body, "overrideRateDate");
      if (rateDate.date() == null || !RatingInput.validYear(rateDate.date(), 0)) {
        p.add("overrideRateDate", RatingMessages.OVERRIDE_RATE_DATE_INVALID);
      } else if (RatingRules.olderThanSevenYears(rateDate.date(), today)) {
        p.add("overrideRateDate", RatingMessages.OVERRIDE_RATE_DATE_PAST);
      } else {
        overrideRateDate = rateDate.date();
      }
    }

    String deck = RatingInput.upper(body, "fieldScaleDeckId");
    RatingRules.deck(p, deck);
    String comment = RatingInput.text(body, "ovrrdrtrlComment");
    if (comment != null && comment.length() > 240) {
      p.add("ovrrdrtrlComment", "Reason cannot be greater than 240 characters."); // errors.maxlength, column size
    }

    if (dates.scaleOk() && dates.processOk() && timberMark != null
        && repo.countOverlappingActiveRules(timberMark, deck, processFrom.date(), processTo.date(),
            scaleFrom.date(), scaleTo.date()) > 0) {
      p.add("timberMark", deck == null ? RatingMessages.RULE_OVERLAP : RatingMessages.RULE_OVERLAP_DECK);
    }
    p.throwIfAny();

    long id = tableApi.nextId("OVERRIDE_RATING_RULE_SEQ");
    Timestamp now = audit.now();
    String user = audit.user();
    tableApi.call("HBS_CREATE_OVERRIDE_RULE", ProcArgs.of()
        .put("i_override_rating_rule_id", id)
        .put("i_ovrrdrtrl_type", dateRule() ? TYPE_DATE_RULE : TYPE_RATE_RULE)
        .put("i_override_rule_category_code", CATEGORY_PRIMARY) // Issue 489
        .put("i_timber_mark", timberMark)
        .put("i_processing_effective_date", processFrom.date())
        .put("i_processing_expiry_date", processTo.date())
        .put("i_scale_effective_date", scaleFrom.date())
        .put("i_scale_expiry_date", scaleTo.date())
        .put("i_ovrrdrtrl_comment", comment)
        .put("i_override_rate_date", overrideRateDate)
        .put("i_override_rule_status_code", dateRule() ? ACTIVE : PENDING)
        .put("i_entry_userid", user)
        .put("i_entry_timestamp", now)
        .put("i_update_userid", user)
        .put("i_update_timestamp", now)
        .put("i_field_scale_deck_id", deck));
    return Map.of("overrideRatingRuleId", id);
  }

  /** P203 Add Override Date Rule. */
  @org.springframework.stereotype.Component
  static class DateRule extends OverrideRuleCreateCommand {
    DateRule(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    boolean dateRule() {
      return true;
    }

    @Override
    public String id() {
      return "rating.overrideRule.createDateRule";
    }

    @Override
    public String legacy() {
      return "P203 Activate Rule (P203Action, RuleHelper.createOverrideDateRule)";
    }
  }

  /** P204 Add Override Rate Rule (first save, Pending). */
  @org.springframework.stereotype.Component
  static class RateRule extends OverrideRuleCreateCommand {
    RateRule(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
      super(repo, tableApi, audit, clock);
    }

    @Override
    boolean dateRule() {
      return false;
    }

    @Override
    public String id() {
      return "rating.overrideRule.createRateRule";
    }

    @Override
    public String legacy() {
      return "P204 Save and Add More Rate Details (P204Action, RuleHelper.createOverrideRateRule)";
    }
  }
}
