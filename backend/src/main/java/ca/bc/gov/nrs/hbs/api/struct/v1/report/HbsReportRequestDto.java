package ca.bc.gov.nrs.hbs.api.struct.v1.report;

import java.util.Map;

/**
 * Generic report-request payload. Every HBS report is a legacy JCRS unit
 * whose prompts are the stored-procedure parameter names (e.g.
 * {@code PSR_TIMBERMARK}, {@code WSINVOICE_LIST01}); the SPA sends them
 * verbatim in {@code parameters}. Values are strings because that's what the
 * legacy prompts were — the procs parse dates as 'yyyy-mm-dd'.
 *
 * <p>Security-sensitive prompts (user type, user client/location, job number,
 * select count) are NOT taken from the caller — {@code HbsReportService}
 * overwrites them from the JWT.</p>
 */
public record HbsReportRequestDto(
    Map<String, String> parameters,
    /** Output format. Defaults to PDF when null. */
    HbsReportFormat format
) {}
