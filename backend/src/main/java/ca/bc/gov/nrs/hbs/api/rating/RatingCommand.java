package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.time.LocalDate;
import java.util.Map;

/**
 * Shared plumbing of the Rating workflow commands: the reads, the table API,
 * the audit stamp and "today". Each subclass is one legacy Struts action
 * branch; {@code CommandService} runs it in one transaction after checking
 * its capability.
 */
abstract class RatingCommand implements WorkflowCommand {

  /** OVERRIDE_RATING_RULE.OVERRIDE_RULE_STATUS_CODE / OVRRDRTRL_TYPE / category ({@code plu.OverrideRateRule}). */
  static final String ACTIVE = "ACT";
  static final String PENDING = "PND";
  static final String INACTIVE = "INA";
  static final String TYPE_RATE_RULE = "OVRRDRULE";
  static final String TYPE_DATE_RULE = "RTDTRL";
  static final String CATEGORY_PRIMARY = "PR";

  protected final RatingRepository repo;
  protected final TableApi tableApi;
  protected final Audit audit;
  protected final RatingClock clock;

  RatingCommand(RatingRepository repo, TableApi tableApi, Audit audit, RatingClock clock) {
    this.repo = repo;
    this.tableApi = tableApi;
    this.audit = audit;
    this.clock = clock;
  }

  protected LocalDate today() {
    return clock.today();
  }

  /** A STORE proc's arguments from the current row, with the UPDATE_ audit columns stamped. */
  protected ProcArgs storeArgs(String proc, Map<String, Object> row) {
    return tableApi.argsFromRow(proc, row)
        .put("i_update_userid", audit.user())
        .put("i_update_timestamp", audit.now());
  }

  /**
   * P200 (which gated P203/P204): the timber mark is required and must exist
   * ({@code RuleHelper.isValidTimberMark}).
   */
  protected void timberMark(Problems p, String timberMark) {
    if (timberMark == null) {
      p.add("timberMark", RatingMessages.TIMBER_MARK_REQUIRED);
    } else if (!repo.timberMarkExists(timberMark)) {
      p.add("timberMark", RatingMessages.TIMBER_MARK_INVALID);
    }
  }

  /** The rule's status must be the one the legacy page offered the action for. */
  protected static void requireStatus(Map<String, Object> rule, String status, String message) {
    if (!status.equals(str(rule, "override_rule_status_code"))) {
      throw new WorkflowValidationException("overrideRuleStatusCode", message);
    }
  }

  /** A column of a {@code SELECT *} row, whatever the label case. */
  static Object col(Map<String, Object> row, String column) {
    Object v = row.get(column);
    if (v == null) v = row.get(column.toUpperCase());
    if (v == null) v = row.get(column.toLowerCase());
    return v;
  }

  static String str(Map<String, Object> row, String column) {
    Object v = col(row, column);
    return v == null ? null : v.toString().trim();
  }

  static LocalDate day(Map<String, Object> row, String column) {
    Object v = col(row, column);
    if (v == null) return null;
    if (v instanceof java.sql.Timestamp t) return t.toLocalDateTime().toLocalDate();
    if (v instanceof java.sql.Date d) return d.toLocalDate();
    if (v instanceof java.util.Date d) return new java.sql.Timestamp(d.getTime()).toLocalDateTime().toLocalDate();
    if (v instanceof java.time.LocalDateTime t) return t.toLocalDate();
    if (v instanceof LocalDate d) return d;
    return LocalDate.parse(v.toString().substring(0, 10));
  }
}
