package ca.bc.gov.nrs.hbs.api.scaledata;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One problem with a file, at the level it was found (docs/scale-data-api.md
 * "Validation errors in the response"). Fields that don't apply are left out.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "One problem with a file. Fields that don't apply to the level are left out.")
public record ApiErrorItem(
    @Schema(description = "Where the problem was found.", allowableValues = {"FILE", "TRANSMISSION", "BATCH", "DOCUMENT"})
    String level,
    @Schema(description = "HBS edit code (the code staff see on the transmission screens). Quote it to the help desk.",
        example = "FBA04")
    String code,
    @Schema(description = "What is wrong.")
    String message,
    @Schema(description = "Batch document type: W weigh slip, P piece scale, S sample scale, F special forest product, "
        + "A arrival ledger, D departure ledger.", example = "W")
    String batch,
    @Schema(description = "The batch id from your file (BATCH level).")
    String batchNumber,
    @Schema(description = "Detail document number (DOCUMENT level).")
    String documentNumber,
    @Schema(description = "Document version (DOCUMENT level).")
    Integer version,
    @Schema(description = "Line in the file (FILE level).")
    Integer line,
    @Schema(description = "Column in the file (FILE level).")
    Integer column) {

  public static ApiErrorItem file(Integer line, Integer column, String message) {
    return new ApiErrorItem("FILE", null, message, null, null, null, null, line, column);
  }

  public static ApiErrorItem transmission(String code, String message) {
    return new ApiErrorItem("TRANSMISSION", code, message, null, null, null, null, null, null);
  }

  public static ApiErrorItem batch(String code, String message, String batch, String batchNumber) {
    return new ApiErrorItem("BATCH", code, message, batch, batchNumber, null, null, null, null);
  }

  public static ApiErrorItem document(String code, String message, String batch, String documentNumber,
      Integer version) {
    return new ApiErrorItem("DOCUMENT", code, message, batch, null, documentNumber, version, null, null);
  }
}
