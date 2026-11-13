package ca.bc.gov.nrs.hbs.api.scaledata;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response bodies of the scale data API (docs/scale-data-api-client-guide.md).
 * Times are ISO-8601 with the Pacific offset, as HBS records them.
 */
public final class ScaleDataModels {

  private ScaleDataModels() {
  }

  /** A transmission's result: {@code POST /submissions} and {@code GET /submissions/{id}}. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "TransmissionResult", description = "A submitted file and its processing result.")
  public record TransmissionResult(
      @Schema(description = "HBS transmission id. Keep it; quote it to the help desk.", example = "812345")
      long transmissionId,
      @Schema(description = "Client the file was submitted for.", example = "00012345")
      String clientNumber,
      @Schema(description = "File name as received.", example = "SITE42_20261029_0600.xml")
      String fileName,
      OffsetDateTime receivedAt,
      @Schema(description = "Last time this file's result changed (including later re-edits of its documents).")
      OffsetDateTime updatedAt,
      @Schema(description = "QUEUED and PROCESSING are not final: check statusUrl again. PROCESSED and REJECTED are final.",
          allowableValues = {"QUEUED", "PROCESSING", "PROCESSED", "REJECTED"})
      String status,
      @Schema(description = "Processing step code, for the help desk (RCD received, UNP unpacking, HBE detail edits).",
          example = "HBE")
      String step,
      @Schema(description = "Step status code, for the help desk (PRC running, CSA succeeded, CUS failed).", example = "CSA")
      String stepStatus,
      @Schema(description = "True when the file's own test indicator is set; test files are checked, never billed.")
      boolean testFile,
      @Schema(description = "True when this file was already received for this client: nothing new was stored.")
      boolean duplicate,
      int batchCount,
      int batchesRejected,
      int documentCount,
      DocumentCounts documents,
      @Schema(description = "Every problem found, at FILE, TRANSMISSION, BATCH and DOCUMENT level. Empty when all was accepted.")
      List<ApiErrorItem> errors,
      @Schema(description = "True when there were more document errors than returned here; page through documentsUrl?status=ERROR.")
      boolean errorsTruncated,
      String statusUrl,
      String documentsUrl) {

    TransmissionResult asDuplicate() {
      return new TransmissionResult(transmissionId, clientNumber, fileName, receivedAt, updatedAt, status, step,
          stepStatus, testFile, true, batchCount, batchesRejected, documentCount, documents, errors, errorsTruncated,
          statusUrl, documentsUrl);
    }

    boolean isFinal() {
      return "PROCESSED".equals(status) || "REJECTED".equals(status);
    }
  }

  /** One file in {@code GET /submissions}: its status without the per-document detail. */
  @Schema(name = "TransmissionSummary")
  public record TransmissionSummary(
      long transmissionId,
      String clientNumber,
      String fileName,
      OffsetDateTime receivedAt,
      @Schema(description = "Last time this file's result changed; list calls compare updatedSince with it.")
      OffsetDateTime updatedAt,
      @Schema(allowableValues = {"QUEUED", "PROCESSING", "PROCESSED", "REJECTED"}) String status,
      String step,
      String stepStatus,
      boolean testFile,
      String statusUrl,
      String documentsUrl) {
  }

  /** Documents of a transmission by status. */
  @Schema(name = "DocumentCounts")
  public record DocumentCounts(
      @Schema(description = "Accepted at unpack, not yet through the detail edits (legacy state ACC).") int accepted,
      int ready,
      int error,
      int signatureFailed,
      @Schema(description = "Refused by a format edit at unpack; nothing was stored for it.") int rejected,
      @Schema(description = "Test-file documents that passed the format edits (not stored).") int testPassed,
      @Schema(description = "Any later state: summarized, invoiced, held, superseded and so on.") int other) {
  }

  /** One document version of a transmission: {@code GET /submissions/{id}/documents}. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "DocumentResult")
  public record DocumentResult(
      @Schema(description = "Batch document type (W, P, S, F, A, D).", example = "W") String batch,
      @Schema(description = "Batch id from your file.") String batchNumber,
      String documentNumber,
      Integer version,
      @Schema(allowableValues = {"ACCEPTED", "READY", "ERROR", "SIGNATURE_FAILED", "REJECTED", "TEST_PASSED",
          "SUMMARIZED", "INVOICED", "HELD", "AWAITING_APPROVAL", "CORRECTION_PENDING", "SUPERSEDED", "CANCELLED",
          "DISCARDED", "NOT_BILLED", "OTHER"})
      String status,
      @Schema(description = "The database version state code, for the help desk.", example = "RDY") String stateCode,
      OffsetDateTime updatedAt,
      List<ApiErrorItem> errors) {
  }

  /** A page of items; call again with {@code next} while it is present. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Page<T>(
      List<T> items,
      @Schema(description = "Cursor for the next page; absent on the last page.") String next) {
  }

  /** An issued statement transmission: {@code GET /statements}. */
  @Schema(name = "StatementItem")
  public record StatementItem(
      long transmissionId,
      String clientNumber,
      @Schema(description = "Client location the statements were addressed to.") String clientLocationCode,
      OffsetDateTime issuedAt,
      @Schema(description = "PSI/PSV piece-scale invoice/volume statement, WSI/WSV weight-scale, CSS compiled sample, "
          + "RS ratio.") List<String> documentTypes,
      String pdfUrl,
      String xmlUrl) {
  }

  /** The error body every endpoint uses. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Schema(name = "ApiError", description = "Error body used by every endpoint. Quote correlationId to the help desk.")
  public record ApiError(
      @Schema(example = "SCHEMA_INVALID") String code,
      String message,
      List<ApiErrorItem> errors,
      @Schema(description = "Set on FILE_REJECTED.") Long transmissionId,
      @Schema(description = "Set on FILE_TOO_LARGE.") Long maxBytes,
      String correlationId) {
  }
}
