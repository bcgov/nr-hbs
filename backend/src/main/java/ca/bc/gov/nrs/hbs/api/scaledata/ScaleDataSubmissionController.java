package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.ApiError;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.DocumentResult;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.Page;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionResult;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionSummary;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Submit scale data files and read their results: the replacement for the FTP
 * {@code inbound} folders (docs/scale-data-api.md endpoints 1–5).
 */
@RestController
@RequestMapping(ScaleDataApi.BASE)
@ConditionalOnProperty(name = "hbs.scale-data-api.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Submissions", description = "Send scale return XML files (what you put in the FTP inbound folder today) "
    + "and read each file's result, document by document.")
public class ScaleDataSubmissionController extends ScaleDataControllerSupport {

  private static final Set<String> XML_TYPES = Set.of("application/xml", "text/xml");

  private final ScaleDataIntake intake;
  private final ScaleDataReads reads;

  public ScaleDataSubmissionController(ScaleDataIntake intake, ScaleDataReads reads, ApiAccountRegistry accounts,
      ScaleDataApiProperties properties) {
    super(accounts, properties);
    this.intake = intake;
    this.reads = reads;
  }

  // ── 1. submit ──────────────────────────────────────────────────────────

  @PostMapping("/submissions")
  @PreAuthorize(HbsAuthorities.SCALE_DATA_SUBMIT)
  @Operation(operationId = "submitFile", summary = "Send one scale data XML file and get its result",
      description = """
          The body is the same HBS_Schema_V6_1b XML file you send by FTP today, as `application/xml`, or as \
          `multipart/form-data` with the file in a part named `file`. One file per request, up to the size limit.

          HBS scans the file for viruses, checks it against the schema, records it and unpacks it while you wait. \
          Every problem found comes back in `errors`, at FILE, TRANSMISSION, BATCH or DOCUMENT level.

          Sending the same file again is always safe: HBS recognises a file it already has for the client (the same \
          bytes, within 30 days) and answers with the original result, `"duplicate": true`. A file that was \
          REJECTED is processed again when you resend it.""",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_SUBMIT),
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = {
          @Content(mediaType = "application/xml", schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "multipart/form-data", schema = @Schema(implementation = FileUpload.class),
              encoding = @Encoding(name = "file", contentType = "application/xml"))}),
      responses = {
          @ApiResponse(responseCode = "201", description = "Processed in the request.",
              headers = @Header(name = "Location", description = "The statusUrl."),
              content = @Content(schema = @Schema(implementation = TransmissionResult.class))),
          @ApiResponse(responseCode = "202", description = "Received, not final yet (large file, waiting for the "
              + "detail edits, or a resend still in progress). Check statusUrl.",
              content = @Content(schema = @Schema(implementation = TransmissionResult.class))),
          @ApiResponse(responseCode = "200", description = "Already received for this client: the original result, "
              + "\"duplicate\": true. Nothing new was stored.",
              content = @Content(schema = @Schema(implementation = TransmissionResult.class))),
          @ApiResponse(responseCode = "400", description = "CLIENT_NUMBER_REQUIRED, CLIENT_NUMBER_INVALID, "
              + "IDEMPOTENCY_KEY_INVALID, FILE_EMPTY.", content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "401", description = "No token, or it is expired or not valid for this API.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "403", description = "CLIENT_NOT_PERMITTED, or INSUFFICIENT_SCOPE.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED: the key was used for another file.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "415", description = "UNSUPPORTED_MEDIA_TYPE.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "422", description = "SCHEMA_INVALID or VIRUS_DETECTED (nothing recorded), or "
              + "FILE_REJECTED (recorded as a rejected transmission; transmissionId set).",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "429", description = "Too many requests: wait for Retry-After.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "503", description = "VIRUS_SCAN_UNAVAILABLE: send the same file again after "
              + "Retry-After.", content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public ResponseEntity<TransmissionResult> submit(
      HttpServletRequest request,
      @Parameter(in = ParameterIn.HEADER, description = "The 8-digit client number the file is for. Required when "
          + "your account submits for more than one client.", example = "00012345")
      @RequestHeader(name = ScaleDataApi.HEADER_CLIENT_NUMBER, required = false) String clientNumber,
      @Parameter(in = ParameterIn.HEADER, description = "Your file name; shown to HBS staff and returned to you.")
      @RequestHeader(name = ScaleDataApi.HEADER_FILE_NAME, required = false) String fileName,
      @Parameter(in = ParameterIn.HEADER, description = "Optional. A new UUID per file, the same one on every retry. "
          + "HBS then goes by the key instead of the file's contents.")
      @RequestHeader(name = ScaleDataApi.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey)
      throws IOException {
    ApiAccount account = account();
    FileBody file = readFile(request, fileName);
    ScaleDataIntake.Outcome outcome = intake.submit(account,
        new ScaleDataIntake.Upload(file.bytes(), file.name(), clientNumber, idempotencyKey));
    return ResponseEntity.status(outcome.status())
        .location(URI.create(outcome.result().statusUrl()))
        .body(outcome.result());
  }

  // ── 2. validate (dry run) ──────────────────────────────────────────────

  @PostMapping("/submissions:validate")
  @PreAuthorize(HbsAuthorities.SCALE_DATA_SUBMIT)
  @Operation(operationId = "validateFile", summary = "Check a file without submitting it; nothing is stored",
      description = """
          Runs the virus scan, the schema check and the unpack's format edits (file, batch and document level) \
          against HBS's current reference data, and stores nothing. Use it to test a new release of your software.

          Schema errors and viruses answer 422 as for a submission. Format-edit problems answer 200 with \
          `"valid": false` and the errors listed. Checks that need the file to be stored (the seven-year date \
          limit, replacement versions, duplicates within the file) and the detail edits run only on a real \
          submission.""",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_SUBMIT),
      requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = {
          @Content(mediaType = "application/xml", schema = @Schema(type = "string", format = "binary")),
          @Content(mediaType = "multipart/form-data", schema = @Schema(implementation = FileUpload.class))}),
      responses = {
          @ApiResponse(responseCode = "200", description = "Checked. valid=false lists the format-edit problems.",
              content = @Content(schema = @Schema(implementation = ScaleDataIntake.ValidationReport.class))),
          @ApiResponse(responseCode = "422", description = "SCHEMA_INVALID or VIRUS_DETECTED.",
              content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public ScaleDataIntake.ValidationReport validate(
      HttpServletRequest request,
      @Parameter(in = ParameterIn.HEADER, description = "As for a submission.")
      @RequestHeader(name = ScaleDataApi.HEADER_CLIENT_NUMBER, required = false) String clientNumber,
      @RequestHeader(name = ScaleDataApi.HEADER_FILE_NAME, required = false) String fileName) throws IOException {
    ApiAccount account = account();
    FileBody file = readFile(request, fileName);
    return intake.validate(account, new ScaleDataIntake.Upload(file.bytes(), file.name(), clientNumber, null));
  }

  // ── 3. one transmission ────────────────────────────────────────────────

  @GetMapping("/submissions/{transmissionId}")
  @PreAuthorize(HbsAuthorities.SCALE_DATA_READ)
  @Operation(operationId = "getSubmission", summary = "A file's current result, errors included",
      description = "The same body a submission answers with. Check it after a 202 until the status is PROCESSED "
          + "or REJECTED (every 30 seconds, then every 5 minutes). Later re-edits of the documents show here too.",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_READ),
      responses = {
          @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TransmissionResult.class))),
          @ApiResponse(responseCode = "404", description = "No such transmission for this account's clients.",
              content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public TransmissionResult get(@PathVariable String transmissionId) {
    return reads.result(id(transmissionId, "The transmission"), account())
        .orElseThrow(() -> ScaleDataApiException.notFound("The transmission"));
  }

  // ── 4. documents ───────────────────────────────────────────────────────

  @GetMapping("/submissions/{transmissionId}/documents")
  @PreAuthorize(HbsAuthorities.SCALE_DATA_READ)
  @Operation(operationId = "listSubmissionDocuments", summary = "A file's documents and their errors, page by page",
      description = "In file order. Use status=ERROR to page through the errors when a result has "
          + "errorsTruncated=true.",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_READ),
      responses = {
          @ApiResponse(responseCode = "200"),
          @ApiResponse(responseCode = "404", description = "No such transmission for this account's clients.",
              content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public Page<DocumentResult> documents(
      @PathVariable String transmissionId,
      @Parameter(description = "Only documents with this status.", schema = @Schema(allowableValues = {
          "ACCEPTED", "READY", "ERROR", "SIGNATURE_FAILED", "REJECTED", "TEST_PASSED", "SUMMARIZED", "INVOICED",
          "HELD", "AWAITING_APPROVAL", "CORRECTION_PENDING", "SUPERSEDED", "CANCELLED", "DISCARDED", "NOT_BILLED",
          "OTHER"}))
      @RequestParam(required = false) String status,
      @Parameter(description = "The next value from the previous page.") @RequestParam(required = false) String cursor,
      @Parameter(description = "Page size (default 50).") @RequestParam(required = false) Integer limit) {
    ScaleDataReads.Transmission t = reads.transmission(id(transmissionId, "The transmission"), account())
        .orElseThrow(() -> ScaleDataApiException.notFound("The transmission"));
    String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
    if (filter != null && !DOCUMENT_STATUSES.contains(filter)) {
      throw ScaleDataApiException.badRequest("Unknown document status '" + status + "'.");
    }
    return reads.documents(t, filter, cursor, limit(limit));
  }

  private static final Set<String> DOCUMENT_STATUSES = Set.of("ACCEPTED", "READY", "ERROR",
      "SIGNATURE_FAILED", "REJECTED", "TEST_PASSED", "SUMMARIZED", "INVOICED", "HELD", "AWAITING_APPROVAL",
      "CORRECTION_PENDING", "SUPERSEDED", "CANCELLED", "DISCARDED", "NOT_BILLED", "OTHER");

  // ── 5. changed files ───────────────────────────────────────────────────

  @GetMapping("/submissions")
  @PreAuthorize(HbsAuthorities.SCALE_DATA_READ)
  @Operation(operationId = "listSubmissions", summary = "Your files whose results changed since a time",
      description = """
          Newest change first, in pages. Call it once an hour or once a day with the time of your last check to \
          pick up documents that became READY later (when related data arrived) without checking each file. \
          Covers files received for your clients through any channel (API, web upload, FTP) in the last 400 days.""",
      security = @SecurityRequirement(name = ScaleDataApi.SECURITY_SCHEME, scopes = ScaleDataApi.SCOPE_READ),
      responses = {
          @ApiResponse(responseCode = "200"),
          @ApiResponse(responseCode = "400", description = "A parameter is not valid.",
              content = @Content(schema = @Schema(implementation = ApiError.class))),
          @ApiResponse(responseCode = "403", description = "CLIENT_NOT_PERMITTED.",
              content = @Content(schema = @Schema(implementation = ApiError.class)))})
  public Page<TransmissionSummary> list(
      @Parameter(description = "ISO-8601 date-time with offset; default 24 hours ago.",
          example = "2026-10-29T06:00:00-07:00")
      @RequestParam(required = false) String updatedSince,
      @Parameter(description = "One of your clients; default all of them.") @RequestParam(required = false)
      String clientNumber,
      @Parameter(schema = @Schema(allowableValues = {"QUEUED", "PROCESSING", "PROCESSED", "REJECTED"}))
      @RequestParam(required = false) String status,
      @Parameter(description = "The next value from the previous page.") @RequestParam(required = false) String cursor,
      @Parameter(description = "Page size (default 50).") @RequestParam(required = false) Integer limit) {
    ApiAccount account = account();
    OffsetDateTime since = timestamp("updatedSince", updatedSince, OffsetDateTime.now().minusDays(1));
    String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
    if (filter != null && !ScaleDataReads.STATUS_FILTER.containsKey(filter)) {
      throw ScaleDataApiException.badRequest("status must be QUEUED, PROCESSING, PROCESSED or REJECTED.");
    }
    return reads.list(account.clientsFor(clientNumber), since, filter, cursor, limit(limit));
  }

  // ── request body ───────────────────────────────────────────────────────

  /** The multipart form, for the OpenAPI document. */
  @Schema(name = "FileUpload")
  record FileUpload(@Schema(type = "string", format = "binary", description = "The XML file.") MultipartFile file) {
  }

  private record FileBody(byte[] bytes, String name) {
  }

  /** The file from an XML body or a multipart {@code file} part, counting bytes against the limit. */
  private FileBody readFile(HttpServletRequest request, String headerName) throws IOException {
    if (request instanceof MultipartHttpServletRequest multipart) {
      MultipartFile part = multipart.getFile("file");
      if (part == null || part.isEmpty()) {
        throw ScaleDataApiException.emptyFile();
      }
      if (part.getSize() > properties.maxBytes()) {
        throw ScaleDataApiException.fileTooLarge(properties.maxBytes());
      }
      return new FileBody(part.getBytes(), headerName != null ? headerName : part.getOriginalFilename());
    }
    MediaType type;
    try {
      type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
    } catch (IllegalArgumentException ex) {
      type = null;
    }
    if (type == null || !XML_TYPES.contains(type.getType() + "/" + type.getSubtype())) {
      throw ScaleDataApiException.unsupportedMediaType();
    }
    try (InputStream in = request.getInputStream()) {
      byte[] bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, properties.maxBytes() + 1));
      if (bytes.length > properties.maxBytes()) {
        throw ScaleDataApiException.fileTooLarge(properties.maxBytes());
      }
      return new FileBody(bytes, headerName);
    }
  }
}
