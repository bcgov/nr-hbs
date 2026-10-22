package ca.bc.gov.nrs.hbs.api.controller.v1;

import ca.bc.gov.nrs.hbs.api.exception.ReportGenerationException;
import ca.bc.gov.nrs.hbs.api.exception.ReportNotFoundException;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.service.v1.report.HbsReportDefinition;
import ca.bc.gov.nrs.hbs.api.service.v1.report.HbsReportResult;
import ca.bc.gov.nrs.hbs.api.service.v1.report.HbsReportService;
import ca.bc.gov.nrs.hbs.api.service.v1.report.ReportLogWriter;
import ca.bc.gov.nrs.hbs.api.service.v1.report.ReportRequestGuard;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirrors nr-fsp-new's FspReportController: POST a request body, get the
 * rendered PDF/CSV back as an attachment. Adds a GET catalogue so the SPA's
 * Reports page can list every legacy HBS report the caller may run.
 *
 * <p>Audience follows the legacy app: industry users could run the return,
 * invoice-copy and billing-history reports for their own client (the procs
 * filter by the forced {@code *_USERCLILOC} prompt); every other report was
 * ministry-only.</p>
 */
@RestController
@RequestMapping("/api/v1/hbs/reports")
@Tag(name = "Reports", description = "Legacy HBS JasperReports (formerly Crystal / JCRS)")
public class HbsReportController {

  /** Reports industry (CLI/SPC) users may run — scoped to their client by the proc. */
  // HBS2R027 (batch list) is deliberately excluded: its proc takes no client prompt, so it
  // can't be fenced to the caller. HBS2R032/033 (one transmission / batch) are fenced by
  // submissions.SubmissionReportGuard (the transmission or batch must be the caller's client's).
  static final Set<String> INDUSTRY_REPORTS = Set.of(
      "HBS2R051", "HBS2R052", "HBS2R101", "HBS2R102",
      "HBS2R151", "HBS2R152", "HBS2R551", "HBS2R552", "HBS2R552DOCD", "HBS2R601", "HBS2R602",
      "HBS2R602DOCD", "HBS2R651", "HBS2R652", "HBS2R652DOCD", "HBS3R416", "HBS3R417",
      "HBS3R431", "HBS3R441", "HBS3R755",
      // P451/P456 Print / Print Client Register: legacy industry users could run them when the
      // FOI check passed (P450Action.canShowIndustry); queries.QueriesReportGuard forces the
      // caller's client and applies that check.
      "HBS4R452", "HBS4R462", "HBS4R472", "HBS4R477", "HBS3R451", "HBS3R461", "HBS3R471", "HBS3R476",
      "HBS2R032", "HBS2R033");

  private final HbsReportService reportService;
  private final List<ReportRequestGuard> guards;
  private final ReportLogWriter reportLog;

  public HbsReportController(HbsReportService reportService, List<ReportRequestGuard> guards,
      ReportLogWriter reportLog) {
    this.reportService = reportService;
    this.guards = guards == null ? List.of() : guards;
    this.reportLog = reportLog;
  }

  @GetMapping
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "List the reports the caller may run")
  public List<HbsReportDefinition> listReports() {
    boolean ministry = RequestUtil.isMinistryUser();
    return reportService.listReports().stream()
        .filter(r -> ministry || INDUSTRY_REPORTS.contains(r.id().toUpperCase()))
        .toList();
  }

  @PostMapping("/{reportId}")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Run a report and download the PDF/CSV")
  public ResponseEntity<byte[]> generateReport(
      @PathVariable("reportId") String reportId,
      @RequestBody HbsReportRequestDto request) {
    if (!RequestUtil.isMinistryUser() && !INDUSTRY_REPORTS.contains(reportId.toUpperCase())) {
      throw new AccessDeniedException("hbs.no_access_right");
    }
    HbsReportRequestDto checked = request;
    for (ReportRequestGuard guard : guards) {
      checked = guard.apply(reportId, checked);
    }
    HbsReportResult result = reportService.generateReport(reportId, checked);
    // PresentationHelper.logReport: the HBS_REPORT_LOG audit row of each report run.
    reportLog.record(reportService.definition(reportId).id(), checked.format());
    ContentDisposition disposition = ContentDisposition.attachment()
        .filename(result.filename(), StandardCharsets.UTF_8)
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(result.mediaType())
        .body(result.content());
  }

  @ExceptionHandler(ReportNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(ReportNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage()));
  }

  @ExceptionHandler(ReportGenerationException.class)
  public ResponseEntity<ProblemDetail> handleReportFailure(ReportGenerationException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage()));
  }
}
