package ca.bc.gov.nrs.hbs.api.service.v1.report;

import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportFormat;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The legacy report audit trail: every report run is written to
 * HBS_REPORT_LOG, as {@code PresentationHelper.logReport} →
 * {@code ReportLogManager.createReportLog} did after each report request
 * (user, report code, submitted date, status {@code SUB}, format, the caller's
 * client / location, the report name and "Job Number: n").
 *
 * <p>Best effort, as in legacy: the report has already been produced, so a
 * failed log write (e.g. a report code missing from HBS_REPORT_CODE — legacy
 * used the report id, {@code Constants.REPORT_CODE_TO_NAME_TABLE}) is logged and
 * does not fail the download. The write autocommits (no surrounding
 * transaction), like every legacy proc call.
 */
@Component
public class ReportLogWriter {

  private static final Logger LOG = LoggerFactory.getLogger(ReportLogWriter.class);
  /** {@code Constants.REPORT_TYPE_STATUS_SUBMITTED}. */
  static final String SUBMITTED = "SUB";

  private final TableApi api;
  private final Audit audit;

  public ReportLogWriter(TableApi api, Audit audit) {
    this.api = api;
    this.audit = audit;
  }

  public void record(String reportId, HbsReportFormat format) {
    try {
      String client = RequestUtil.isMinistryUser() ? null : RequestUtil.getCurrentClientNumber();
      long jobNumber = System.currentTimeMillis();
      api.call("HBS_CREATE_REPORT_LOG", ProcArgs.of()
          .put("i_request_id", api.nextId("HBS_REPORT_LOG_SEQ"))
          .put("i_user_id", audit.user())
          .put("i_hbs_report_code", reportId)
          .put("i_request_date", audit.now())
          .put("i_completed_date", audit.now())
          .put("i_hbs_report_status_code", SUBMITTED)
          .put("i_mime_type_code", HbsReportFormat.fromNullable(format).name())
          .put("i_client_number", client == null || client.isBlank() ? null : client)
          .put("i_client_locn_code", null)
          .put("i_request_url", reportId)
          .put("i_report_url", "Job Number: " + jobNumber));
    } catch (RuntimeException ex) {
      LOG.warn("Report {} ran but its HBS_REPORT_LOG entry could not be written: {}", reportId, ex.getMessage());
    }
  }
}
