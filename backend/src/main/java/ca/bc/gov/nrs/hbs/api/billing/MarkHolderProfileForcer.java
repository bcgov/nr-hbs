package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The write step of P812 / P814 "Yes" ({@code ClientHelper.updateHbsMarkHolderProfile}
 * → {@code ClientProfileManagerBean.updateMarkHolderProfile}): the profile as
 * loaded, with FORCED_SUMMARIZATION_DATE = the force date (00:00, as
 * P811Form built it) and the update audit columns. HBS_STORE_HBS_MARK_PROFILE
 * rewrites every column, so the other values come from the row read in the
 * same transaction, never from the browser.
 */
@Component
public class MarkHolderProfileForcer {

  static final String PROC = "HBS_STORE_HBS_MARK_PROFILE";

  private final TableApi tableApi;

  public MarkHolderProfileForcer(TableApi tableApi) {
    this.tableApi = tableApi;
  }

  public void force(Map<String, Object> profileRow, LocalDate forceDate, String user, Timestamp now) {
    ProcArgs args = tableApi.argsFromRow(PROC, profileRow)
        .put("i_forced_summarization_date", forceDate.atStartOfDay())
        .put("i_update_userid", user)
        .put("i_update_timestamp", now);
    tableApi.call(PROC, args);
  }
}
