package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.util.Date;

/**
 * What every step of one transmission's unpack shares: the table API, the
 * reads, the batch user (legacy {@code HbsBatchConfiguration.getBatchUserId})
 * and "now" (legacy {@code new Date()} on the Pacific batch host).
 */
record UnpackContext(TableApi tableApi, UnpackRepository repository, String userId, Timestamp now) {

  /** Sequences (legacy {@code UniqueIdQuery}: {@code <table>_SEQ}). */
  static final String SEQ_BATCH = "DTL_DOC_BATCH_SEQ";
  static final String SEQ_BATCH_DOCUMENT = "DTL_DOC_BATCH_DOCUMENT_SEQ";
  static final String SEQ_EDIT_ERROR = "DETAIL_HBS_EDIT_ERROR_SEQ";
  /** DTL_DOC_SEQ of every version, and (legacy quirk) the SFP_LOG_DETAIL ids. */
  static final String SEQ_DETAIL_SCALE_DOCUMENT = "DETAIL_SCALE_DOCUMENT_SEQ";
  static final String SEQ_SCL_SITE_LEDGER = "SCL_SITE_LEDGER_SEQ";
  static final String SEQ_LOG_TALLY_DETAIL = "LOG_TALLY_DETAIL_SEQ";
  static final String SEQ_LOG_TALLY_DETAIL_DEFECT = "LOG_TALLY_DETAIL_DEFECT_SEQ";
  static final String SEQ_SAMPLE_TALLY_DETAIL = "SAMPLE_TALLY_DETAIL_SEQ";
  static final String SEQ_SAMPLE_TALLY_DETAIL_DEFECT = "SAMPLE_TALLY_DETAIL_DEFECT_SEQ";
  static final String SEQ_AUDIT = "HBS_AUDIT_SEQ";

  long nextId(String sequence) {
    return tableApi.nextId(sequence);
  }

  /**
   * Arguments with every IN parameter of the proc set to null: the legacy
   * value objects bound null for every field B1012 never set.
   */
  ProcArgs nulls(String proc) {
    ProcArgs args = ProcArgs.of();
    for (Param p : tableApi.signatures().get(proc).params()) {
      if (p.isIn()) {
        args.put(p.name(), null);
      }
    }
    return args;
  }

  void call(String proc, ProcArgs args) {
    tableApi.call(proc, args);
  }

  static Timestamp ts(Date d) {
    return d == null ? null : new Timestamp(d.getTime());
  }

  static String flag(Boolean b) {
    return b == null ? null : (b ? "Y" : "N");
  }

  /** {@code StringUtil.empty}: null or "". */
  static boolean empty(String s) {
    return s == null || s.isEmpty();
  }

  static String upperOrNull(String s) {
    return empty(s) ? null : s.toUpperCase();
  }

  static String upper(String s) {
    return s == null ? null : s.toUpperCase();
  }

  /** {@code StringUtil.leftTrimZerosForIntegerString}. */
  static String leftTrimZeros(String s) {
    if (s == null || s.trim().length() != s.length()) {
      return s;
    }
    try {
      return String.valueOf(Long.parseLong(s));
    } catch (NumberFormatException e) {
      return s;
    }
  }
}
