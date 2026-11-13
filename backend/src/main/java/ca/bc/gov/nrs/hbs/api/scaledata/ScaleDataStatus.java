package ca.bc.gov.nrs.hbs.api.scaledata;

/**
 * Plain names for the database codes (docs/scale-data-api.md "Status and error
 * handling").
 */
public final class ScaleDataStatus {

  private ScaleDataStatus() {
  }

  public static final String QUEUED = "QUEUED";
  public static final String PROCESSING = "PROCESSING";
  public static final String PROCESSED = "PROCESSED";
  public static final String REJECTED = "REJECTED";

  /**
   * Transmission step ({@code HBS_XML_TRANS_STEP_CODE}) and status
   * ({@code HBS_PROCESSING_STATUS_CODE}) → API status. RCD/CSA waits for the
   * unpack (QUEUED); the unpack and the detail edits (B1021) are PROCESSING
   * until HBE/CSA; any failed step (CUS) is REJECTED.
   */
  public static String transmission(String step, String status) {
    if ("CUS".equals(status)) {
      return REJECTED;
    }
    if ("RCD".equals(step)) {
      return QUEUED;
    }
    if ("HBE".equals(step) && "CSA".equals(status)) {
      return PROCESSED;
    }
    return PROCESSING;
  }

  /**
   * Version state ({@code SCL_RTN_VERSION_STATE_CODE}, legacy {@code VersionState})
   * → API document status.
   */
  public static String version(String state) {
    if (state == null) {
      return "OTHER";
    }
    return switch (state) {
      case "ACC" -> "ACCEPTED";
      case "RDY" -> "READY";
      case "ERR" -> "ERROR";
      case "DSF" -> "SIGNATURE_FAILED";
      case "LCK" -> "SUMMARIZED";
      case "ISS" -> "INVOICED";
      case "HLD" -> "HELD";
      case "AWP" -> "AWAITING_APPROVAL";
      case "PND", "PNV" -> "CORRECTION_PENDING";
      case "SPR" -> "SUPERSEDED";
      case "CAN" -> "CANCELLED";
      case "DEL" -> "DISCARDED";
      case "NTI" -> "NOT_BILLED";
      default -> "OTHER";
    };
  }
}
