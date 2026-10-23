package ca.bc.gov.nrs.hbs.api.service.v1.report;

import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;

/**
 * A screen-specific check run by {@code HbsReportController} before a report
 * is generated — the legacy report actions validated or narrowed their prompts
 * before submitting the report (e.g. {@code P450Action} FOI gate, {@code P403Action}
 * rates). Every {@code @Component} implementing it is applied in turn.
 */
public interface ReportRequestGuard {

  /**
   * Checks (throws {@code IllegalArgumentException} / {@code WorkflowValidationException}
   * for a 400) and may return a request with prompts replaced; returns
   * {@code request} unchanged for reports it does not handle.
   */
  HbsReportRequestDto apply(String reportId, HbsReportRequestDto request);
}
