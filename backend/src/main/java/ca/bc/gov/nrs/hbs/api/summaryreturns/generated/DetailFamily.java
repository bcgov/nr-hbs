package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import java.util.Map;

/**
 * The four billable detail-document families behind a generated summary, with the tables and
 * table-API procs the legacy entity beans used ({@code LogTallyQuery}, {@code WeighSlipQuery},
 * {@code SampleLogTallyQuery}, {@code SpecialForestProductTallyQuery} and their txn / txn
 * segregation queries). {@link #code} is {@code DETAIL_SCALE_DOCUMENT.DETAIL_SCALE_DOC_TYPE_CODE}
 * ({@code DetailScaleDocument.LOG_TALLY / WEIGH_SLIP / SAMPLE_LOG_TALLY / SFP_LOG_TALLY}).
 *
 * <p>Table names here are constants, never input. {@code *Renames} map proc parameters whose name
 * is not {@code i_<column>} to their column (read from the procs' UPDATE / INSERT statements in
 * nr-mof-db).
 */
enum DetailFamily {

  LOG_TALLY("P", "LOG_TALLY", "LOG_TALLY_TXN", "LOG_TALLY_TXN_SEG", "LOG_TALLY_DETAIL",
      "HBS_STORE_LOG_TALLY", Map.of(
          "i_orig_chk_scl_qnt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_orig_chk_scl_ddn", "original_ddn",
          "i_orig_chk_scl_ddv", "original_ddv",
          "i_orig_redtag_scale_site_id_nm", "orig_redtag_scale_site_id_nmbr",
          "i_input_orig_chk_scl_ddn", "input_original_ddn",
          "i_input_orig_chk_scl_ddv", "input_original_ddv",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic"),
      "LOG_TALLY_TXN", "LOG_TALLY_TXN_SEG",
      Map.of("i_reserve_stmpg_amt_component", "reserve_stmpg_amount_component")),

  SFP_TALLY("F", "SPECIAL_FOREST_PRODUCT_TALLY", "SFP_TALLY_TXN", "SFP_TALLY_TXN_SEG", "SFP_LOG_DETAIL",
      "HBS_STORE_SFP_TALLY", Map.of(
          "i_orig_chk_scl_ql_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic"),
      "SFP_TALLY_TXN", "SFP_TALLY_TXN_SEG", Map.of()),

  WEIGH_SLIP("W", "WEIGH_SLIP", "WEIGH_SLIP_TXN", "WEIGH_SLIP_TXN_SEG", null,
      "HBS_STORE_WEIGH_SLIP", Map.of(),
      "WEIGH_SLIP_TXN", "WEIGH_SLIP_TXN_SEG",
      Map.of("i_reserve_stmpg_amt_component", "reserve_stmpg_amount_component")),

  SAMPLE_LOG_TALLY("S", "SAMPLE_LOG_TALLY", "SAMPLE_TALLY_TXN", "SAMPLE_TALLY_TXN_SEG", "SAMPLE_TALLY_DETAIL",
      "HBS_STORE_SAMPLE_LOG_TALLY", Map.of(
          "i_orig_chk_scl_qt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd",
          "i_orig_chk_scl_ddn", "original_ddn",
          "i_orig_chk_scl_ddv", "original_ddv",
          "i_orig_redtag_scl_site_id_nmbr", "orig_redtag_scale_site_id_nmbr",
          "i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic",
          "i_input_orig_chk_scl_ddn", "input_original_ddn",
          "i_input_orig_chk_scl_ddv", "input_original_ddv"),
      "SAMPLE_TALLY_TXN", "SAMPLE_TALLY_TXN_SEG", Map.of());

  final String code;
  final String versionTable;
  final String txnTable;
  final String txnSegTable;
  /** The log / piece rows of a version ({@code LogTally.getLogs()}); null for weigh slips. */
  final String detailTable;
  final String storeVersionProc;
  final Map<String, String> versionRenames;
  private final String txnProc;
  private final String txnSegProc;
  final Map<String, String> txnSegRenames;

  DetailFamily(String code, String versionTable, String txnTable, String txnSegTable, String detailTable,
      String storeVersionProc, Map<String, String> versionRenames, String txnProc, String txnSegProc,
      Map<String, String> txnSegRenames) {
    this.code = code;
    this.versionTable = versionTable;
    this.txnTable = txnTable;
    this.txnSegTable = txnSegTable;
    this.detailTable = detailTable;
    this.storeVersionProc = storeVersionProc;
    this.versionRenames = versionRenames;
    this.txnProc = txnProc;
    this.txnSegProc = txnSegProc;
    this.txnSegRenames = txnSegRenames;
  }

  String storeTxnProc() {
    return "HBS_STORE_" + txnProc;
  }

  String createTxnProc() {
    return "HBS_CREATE_" + txnProc;
  }

  String createTxnSegProc() {
    return "HBS_CREATE_" + txnSegProc;
  }

  /** {@code UniqueIdQuery.getNextKey(TABLE_*_TXN)}: {@code <TABLE>_SEQ}. */
  String txnSequence() {
    return txnTable + "_SEQ";
  }

  String txnSegSequence() {
    return txnSegTable + "_SEQ";
  }
}
