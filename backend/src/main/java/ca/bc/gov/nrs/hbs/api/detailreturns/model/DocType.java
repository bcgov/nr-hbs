package ca.bc.gov.nrs.hbs.api.detailreturns.model;

import java.util.Arrays;
import java.util.Map;

/**
 * The six detail document types ({@code DETAIL_SCALE_DOC_TYPE_CODE}) and the
 * tables / table-API procs / sequences behind each, as the legacy
 * {@code DetailManagerBean}, the {@code *DAO}/{@code *EntityBean} classes and
 * {@code DetailHbsEditErrorQuery} used them.
 *
 * <p>Where a proc parameter is not named after its column (the generated
 * LOG_TALLY / SAMPLE_LOG_TALLY / SFP_TALLY procs abbreviate a few), the
 * {@code createAliases}/{@code storeAliases} map the parameter to the column.
 */
public enum DocType {

  /** Piece scale log tally (P550/P552/P553). */
  LOG_TALLY("P", "LOG_TALLY", "HBS_CREATE_LOG_TALLY", "HBS_STORE_LOG_TALLY",
      // KeyGenerator / WorkflowDocumentFactory.newOnlineLogTally: LogTallyHelper.getId
      "LOG_TALLY_SEQ",
      "LOG_TALLY_ERROR", "HBS_CREATE_LOG_TALLY_ERROR", "HBS_REMOVE_LOG_TALLY_ERROR",
      new Details("LOG_TALLY_DETAIL", "LOG_TALLY_DETAIL_SEQ", "HBS_CREATE_LOG_TALLY_DETAIL",
          "HBS_STORE_LOG_TALLY_DETAIL", "HBS_REMOVE_LOG_TALLY_DETAIL",
          "LOG_TALLY_DETAIL_ERROR", "HBS_CREATE_LOG_TALLY_DTL_ERROR", "HBS_REMOVE_LOG_TALLY_DTL_ERROR",
          new Defects("LOG_TALLY_DETAIL_DEFECT", "LOG_TALLY_DETAIL_DEFECT_SEQ", "HBS_CREATE_LOG_TALLY_DTL_DEF",
              "HBS_STORE_LOG_TALLY_DTL_DEF", "HBS_REMOVE_LOG_TALLY_DTL_DEF")),
      Map.of("i_orig_chk_scl_quant_lic_tp_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_orig_chk_scl_ddn", "original_ddn",
          "i_orig_chk_scl_ddv", "original_ddv",
          "i_orig_redtag_scl_site_id_nmbr", "orig_redtag_scale_site_id_nmbr",
          "i_input_orig_chk_scl_ddn", "input_original_ddn",
          "i_input_orig_chk_scl_ddv", "input_original_ddv",
          "i_input_orig_chk_scl_scl_lic", "input_orig_chk_scl_scaler_lic"),
      Map.of("i_orig_chk_scl_qnt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_orig_chk_scl_ddn", "original_ddn",
          "i_orig_chk_scl_ddv", "original_ddv",
          "i_orig_redtag_scale_site_id_nm", "orig_redtag_scale_site_id_nmbr",
          "i_input_orig_chk_scl_ddn", "input_original_ddn",
          "i_input_orig_chk_scl_ddv", "input_original_ddv",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic"),
      "log_tally_comment"),

  /** Weight scale weigh slip (P600/P602/P603). */
  WEIGH_SLIP("W", "WEIGH_SLIP", "HBS_CREATE_WEIGH_SLIP", "HBS_STORE_WEIGH_SLIP", "WEIGH_SLIP_SEQ",
      "WEIGH_SLIP_ERROR", "HBS_CREATE_WEIGH_SLIP_ERROR", "HBS_REMOVE_WEIGH_SLIP_ERROR",
      null, Map.of(), Map.of(), "weigh_slip_comment"),

  /** Sample scale log tally (P650/P652/P653). */
  SAMPLE_LOG_TALLY("S", "SAMPLE_LOG_TALLY", "HBS_CREATE_SAMPLE_LOG_TALLY", "HBS_STORE_SAMPLE_LOG_TALLY",
      "SAMPLE_LOG_TALLY_SEQ",
      "SAMPLE_LOG_TALLY_ERROR", "HBS_CREATE_SMPL_LOG_TALLY_ERR", "HBS_REMOVE_SMPL_LOG_TALLY_ERR",
      new Details("SAMPLE_TALLY_DETAIL", "SAMPLE_TALLY_DETAIL_SEQ", "HBS_CREATE_SAMPLE_TALLY_DETAIL",
          "HBS_STORE_SAMPLE_TALLY_DETAIL", "HBS_REMOVE_SAMPLE_TALLY_DETAIL",
          "SAMPLE_TALLY_DETAIL_ERROR", "HBS_CREATE_SMPL_TALLY_DTL_ERR", "HBS_REMOVE_SMPL_TALLY_DTL_ERR",
          new Defects("SAMPLE_TALLY_DETAIL_DEFECT", "SAMPLE_TALLY_DETAIL_DEFECT_SEQ",
              "HBS_CREATE_SMPL_TALLY_DTL_DEF", "HBS_STORE_SMPL_TALLY_DTL_DEF", "HBS_REMOVE_SMPL_TALLY_DTL_DEF")),
      Map.of("i_orig_chk_scl_qt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_orig_chk_scl_ddn", "original_ddn",
          "i_orig_chk_scl_ddv", "original_ddv",
          "i_orig_redtag_scl_site_id_nmbr", "orig_redtag_scale_site_id_nmbr",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic",
          "i_input_orig_chk_scl_ddn", "input_original_ddn",
          "i_input_orig_chk_scl_ddv", "input_original_ddv"),
      Map.of("i_orig_chk_scl_qt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_orig_chk_scl_ddn", "original_ddn",
          "i_orig_chk_scl_ddv", "original_ddv",
          "i_orig_redtag_scl_site_id_nmbr", "orig_redtag_scale_site_id_nmbr",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic",
          "i_input_orig_chk_scl_ddn", "input_original_ddn",
          "i_input_orig_chk_scl_ddv", "input_original_ddv"),
      "sample_log_tally_comment"),

  /** Special forest product tally (P750/P752/P753; DEV/TEST-only online entry in legacy). */
  SFP_TALLY("F", "SPECIAL_FOREST_PRODUCT_TALLY", "HBS_CREATE_SFP_TALLY", "HBS_STORE_SFP_TALLY",
      // WorkflowDocumentFactory.newOnlineSpecialtyForestLogTallyDocument uses LogTallyHelper.getId
      "LOG_TALLY_SEQ",
      "SFP_TALLY_ERROR", "HBS_CREATE_SFP_TALLY_ERROR", "HBS_REMOVE_SFP_TALLY_ERROR",
      new Details("SFP_LOG_DETAIL", "SFP_LOG_DETAIL_SEQ", "HBS_CREATE_SFP_LOG_DETAIL",
          "HBS_STORE_SFP_LOG_DETAIL", "HBS_REMOVE_SFP_LOG_DETAIL",
          "SFP_LOG_TALLY_DETAIL_ERROR", "HBS_CREATE_SFP_TALLY_DTL_ERR", "HBS_REMOVE_SFP_TALLY_DTL_ERR",
          null),
      Map.of("i_orig_chk_scl_ql_code", "orig_chk_scl_quant_lic_type_cd",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic"),
      Map.of("i_orig_chk_scl_ql_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic"),
      "sfp_tally_comment"),

  /** Scale site arrival ledger entry (P700/P702, DDN ARL + SCL_SITE_LEDGER_SEQ). */
  ARRIVAL_LEDGER("A", "SCL_SITE_ARRIVAL_LEDGER", "HBS_CREATE_SCL_SITE_ARR_LEDGER", "HBS_STORE_SCL_SITE_ARR_LEDGER",
      // P700Action.composeARL: dtlDocSeq = DetailScaleDocumentHelper.getId (DETAIL_SCALE_DOCUMENT)
      "DETAIL_SCALE_DOCUMENT_SEQ",
      "SCALE_SITE_ARVL_LDGR_ERROR", "HBS_CREATE_SCL_SITE_ARV_LG_ERR", "HBS_REMOVE_SCL_SITE_ARV_LG_ERR",
      null, Map.of(), Map.of(), "arrival_ledger_comment"),

  /** Scale site departure ledger entry (P700/P702, DDN DEP + SCL_SITE_LEDGER_SEQ). */
  DEPARTURE_LEDGER("D", "SCL_SITE_DEPARTURE_LEDGER", "HBS_CREATE_DEPARTURE_LEDGER", "HBS_STORE_DEPARTURE_LEDGER",
      "DETAIL_SCALE_DOCUMENT_SEQ",
      "SCALE_SITE_DEP_LDGR_ERROR", "HBS_CREATE_SCL_SITE_DEP_LD_ERR", "HBS_REMOVE_SCL_SITE_DEP_LD_ERR",
      null, Map.of(), Map.of(), null);

  /** Log / piece lines of a tally and the procs that write them. */
  public record Details(String table, String sequence, String create, String store, String remove,
                        String errorTable, String errorCreate, String errorRemove, Defects defects) {
  }

  /** Defects of a log line (log and sample tallies). */
  public record Defects(String table, String sequence, String create, String store, String remove) {
  }

  /** {@code DETAIL_HBS_EDIT_ERROR} sequence: every edit-error row id (DetailHbsEditErrorHelper.getId). */
  public static final String ERROR_SEQUENCE = "DETAIL_HBS_EDIT_ERROR_SEQ";

  private final String code;
  private final String table;
  private final String createProc;
  private final String storeProc;
  private final String docSeqSequence;
  private final String errorTable;
  private final String errorCreate;
  private final String errorRemove;
  private final Details details;
  private final Map<String, String> createAliases;
  private final Map<String, String> storeAliases;
  private final String commentColumn;

  DocType(String code, String table, String createProc, String storeProc, String docSeqSequence,
          String errorTable, String errorCreate, String errorRemove, Details details,
          Map<String, String> createAliases, Map<String, String> storeAliases, String commentColumn) {
    this.code = code;
    this.table = table;
    this.createProc = createProc;
    this.storeProc = storeProc;
    this.docSeqSequence = docSeqSequence;
    this.errorTable = errorTable;
    this.errorCreate = errorCreate;
    this.errorRemove = errorRemove;
    this.details = details;
    this.createAliases = createAliases;
    this.storeAliases = storeAliases;
    this.commentColumn = commentColumn;
  }

  public static DocType of(String code) {
    return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown detail document type code " + code));
  }

  public String code() {
    return code;
  }

  public String table() {
    return table;
  }

  public String createProc() {
    return createProc;
  }

  public String storeProc() {
    return storeProc;
  }

  /** Sequence of DTL_DOC_SEQ for a version keyed in the UI. */
  public String docSeqSequence() {
    return docSeqSequence;
  }

  public String errorTable() {
    return errorTable;
  }

  public String errorCreate() {
    return errorCreate;
  }

  public String errorRemove() {
    return errorRemove;
  }

  /** Null for weigh slips and ledgers. */
  public Details details() {
    return details;
  }

  public boolean isTally() {
    return details != null;
  }

  public boolean isLedger() {
    return this == ARRIVAL_LEDGER || this == DEPARTURE_LEDGER;
  }

  public Map<String, String> createAliases() {
    return createAliases;
  }

  public Map<String, String> storeAliases() {
    return storeAliases;
  }

  public String commentColumn() {
    return commentColumn;
  }
}
