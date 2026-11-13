package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.queries.StatementTransmissionFiles;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.ApiError;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.Page;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.StatementItem;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Statement collection: the replacement for the FTP {@code outbound} folders
 * (docs/scale-data-api.md endpoints 6 and 7). The files are rebuilt from Oracle
 * exactly as the e-mailed links serve them ({@link StatementTransmissionFiles}),
 * and stay available for seven years instead of 31 days.
 */
@RestController
@RequestMapping(ScaleDataApi.BASE + "/statements")
@ConditionalOnProperty(name = "hbs.scale-data-api.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Statements", description = "Invoices and statements issued to your clients (what the FTP outbound "
    + "folder held), as PDF and XML.")
public class ScaleDataStatementController extends ScaleDataControllerSupport {

  private final ScaleDataReads reads;
  private final StatementTransmissionFiles files;

  public ScaleDataStatementController(ScaleDataReads reads, StatementTransmissionFiles files,
      ApiAccountRegistry accounts, ScaleDataApiProperties properties) {
    super(accounts, properties);
    this.reads = reads;
    this.files = files;
  }

  @GetMapping
  @PreAuthorize(HbsAuthorities.SCALE_DATA_STATEMENTS)
  @Operation(operationId = "listStatements", summary = "Statements issued to your clients since a time",
      description = """
          Newest first, in pages. Save the issuedAt of the newest item you processed and pass it as `since` next \
          time; while a response has `next`, call again with it. Download each item from its pdfUrl and xmlUrl \
          with the same token.""",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_STATEMENTS),
      responses = {
          @ApiResponse(responseCode = "200"),
          @ApiResponse(responseCode = "400", description = "A parameter is not valid.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "403", description = "CLIENT_NOT_PERMITTED, or INSUFFICIENT_SCOPE.",
              content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public Page<StatementItem> list(
      @Parameter(description = "ISO-8601 date-time with offset; default 31 days ago (what the FTP folder kept).",
          example = "2026-10-01T00:00:00-07:00")
      @RequestParam(required = false) String since,
      @Parameter(description = "One of your clients; default all of them.") @RequestParam(required = false)
      String clientNumber,
      @Parameter(description = "The next value from the previous page.") @RequestParam(required = false) String cursor,
      @Parameter(description = "Page size (default 50).") @RequestParam(required = false) Integer limit) {
    ApiAccount account = account();
    OffsetDateTime from = timestamp("since", since, OffsetDateTime.now().minusDays(31));
    return reads.statements(account.clientsFor(clientNumber), from, cursor, limit(limit));
  }

  @GetMapping("/{transmissionId}.{format}")
  @PreAuthorize(HbsAuthorities.SCALE_DATA_STATEMENTS)
  @Operation(operationId = "getStatement", summary = "Download a statement transmission as PDF or XML",
      description = "The XML follows HBS_Transmission_Schema_V1_0h, as on FTP.",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_STATEMENTS),
      responses = {
          @ApiResponse(responseCode = "200", content = {
              @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")),
              @Content(mediaType = "text/xml", schema = @Schema(type = "string", format = "binary"))}),
          @ApiResponse(responseCode = "404", description = "No such statement for this account's clients.",
              content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public ResponseEntity<byte[]> download(
      @PathVariable String transmissionId,
      @Parameter(schema = @Schema(allowableValues = {"pdf", "xml"})) @PathVariable String format) {
    ApiAccount account = account();
    boolean pdf = "pdf".equalsIgnoreCase(format);
    if (!pdf && !"xml".equalsIgnoreCase(format)) {
      throw ScaleDataApiException.notFound("The statement");
    }
    StatementTransmissionFiles.Found found = files.find(id(transmissionId, "The statement"))
        .filter(f -> account.mayUse(f.clientNumber()))
        .filter(f -> f.has(pdf))
        // The seven-year rule the list applies (statements older than that are not served).
        .filter(f -> f.created().isAfter(java.time.LocalDateTime.now(ca.bc.gov.nrs.hbs.api.workflow.Audit.ZONE)
            .minusYears(7)))
        .orElseThrow(() -> ScaleDataApiException.notFound("The statement"));
    return files.download(found, pdf);
  }
}
