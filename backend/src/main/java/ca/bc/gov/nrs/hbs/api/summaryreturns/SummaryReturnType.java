package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.util.Arrays;
import java.util.Locale;

/**
 * The three summary scale return types ({@code HBS_SCALE_RETURN.HBS_RETURN_TYPE_CODE})
 * and the tables / table-API procs each one is stored in. Legacy:
 * {@code DacConstants.HBS_RETURN_TYPE_CODE_*} and the entity beans'
 * {@code *Query.createByPK/updateByPK/deleteByPK} calls.
 */
public enum SummaryReturnType {

  PIECE("P", "PIECE_SCALE_SUMMARY", "PIECE_SCALE_SUMMARY",
      "PIECE_SCL_SEGREGATION", "PIECE_SCL_SEG",
      "PIECE_SCL_NOTATION", "PIECE_SCL_NOTATION",
      "PIECE_SCL_VERSION_ERROR", "PIECE_SCL_VER_ERR",
      "PIECE_SCL_SEG_ERROR", "PIECE_SCL_SEG_ERROR"),

  WEIGHT("W", "WEIGHT_SCALE_SUMMARY", "WEIGHT_SCL_SUMMARY",
      null, null,
      "WEIGHT_SCALE_NOTATION", "WEIGHT_SCL_NOTATION",
      "WEIGHT_SCL_VERSION_ERROR", "WEIGHT_SCL_VER_ERR",
      null, null),

  SAMPLE("S", "SAMPLE_SCALE_SUMMARY", "SAMPLE_SCL_SUMMARY",
      "SAMPLE_SEGREGATION", "SAMPLE_SEG",
      "SAMPLE_SCL_NOTATION", "SAMPLE_SCL_NOTATION",
      "SAMPLE_SCL_VERSION_ERROR", "SAMPLE_SCL_VER_ERR",
      "SAMPLE_SCL_SEG_ERROR", "SAM_SCL_SEG_ERROR");

  /** Weight daily load summaries ({@code WeightScaleDailyLoadSummaryQuery}). */
  public static final String DAILY_LOAD_TABLE = "WEIGHT_SCL_DAILY_LOAD_SUMMARY";
  public static final String DAILY_LOAD_PROC = "WEI_SCL_DLY_LD_SUM";
  public static final String LOAD_ERROR_TABLE = "WEIGHT_SCL_LOAD_SMRY_ERROR";
  public static final String LOAD_ERROR_PROC = "WEI_SCL_LD_SR_ERR";

  private final String code;
  private final String versionTable;
  private final String versionProc;
  private final String segregationTable;
  private final String segregationProc;
  private final String notationTable;
  private final String notationProc;
  private final String versionErrorTable;
  private final String versionErrorProc;
  private final String segregationErrorTable;
  private final String segregationErrorProc;

  SummaryReturnType(String code, String versionTable, String versionProc,
      String segregationTable, String segregationProc,
      String notationTable, String notationProc,
      String versionErrorTable, String versionErrorProc,
      String segregationErrorTable, String segregationErrorProc) {
    this.code = code;
    this.versionTable = versionTable;
    this.versionProc = versionProc;
    this.segregationTable = segregationTable;
    this.segregationProc = segregationProc;
    this.notationTable = notationTable;
    this.notationProc = notationProc;
    this.versionErrorTable = versionErrorTable;
    this.versionErrorProc = versionErrorProc;
    this.segregationErrorTable = segregationErrorTable;
    this.segregationErrorProc = segregationErrorProc;
  }

  public static SummaryReturnType fromCode(String code) {
    String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(values()).filter(t -> t.code.equals(c)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown summary return type '" + code + "'"));
  }

  public String code() {
    return code;
  }

  public String versionTable() {
    return versionTable;
  }

  public String createVersion() {
    return "HBS_CREATE_" + versionProc;
  }

  public String storeVersion() {
    return "HBS_STORE_" + versionProc;
  }

  public String removeVersion() {
    return "HBS_REMOVE_" + versionProc;
  }

  public boolean hasSegregations() {
    return segregationTable != null;
  }

  public String segregationTable() {
    return segregationTable;
  }

  public String createSegregation() {
    return "HBS_CREATE_" + segregationProc;
  }

  public String storeSegregation() {
    return "HBS_STORE_" + segregationProc;
  }

  public String removeSegregation() {
    return "HBS_REMOVE_" + segregationProc;
  }

  public String notationTable() {
    return notationTable;
  }

  public String createNotation() {
    return "HBS_CREATE_" + notationProc;
  }

  public String storeNotation() {
    return "HBS_STORE_" + notationProc;
  }

  public String removeNotation() {
    return "HBS_REMOVE_" + notationProc;
  }

  public String versionErrorTable() {
    return versionErrorTable;
  }

  public String createVersionError() {
    return "HBS_CREATE_" + versionErrorProc;
  }

  public String removeVersionError() {
    return "HBS_REMOVE_" + versionErrorProc;
  }

  public String segregationErrorTable() {
    return segregationErrorTable;
  }

  public String createSegregationError() {
    return "HBS_CREATE_" + segregationErrorProc;
  }

  public String removeSegregationError() {
    return "HBS_REMOVE_" + segregationErrorProc;
  }

  /** Piece and weight returns are invoiced; sample returns are compiled into ratio statements. */
  public boolean isBillable() {
    return this != SAMPLE;
  }
}
