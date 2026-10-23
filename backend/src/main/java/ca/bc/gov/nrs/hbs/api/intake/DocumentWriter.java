package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.intake.VersionComposer.Child;
import ca.bc.gov.nrs.hbs.api.intake.VersionComposer.ComposedVersion;
import ca.bc.gov.nrs.hbs.api.intake.xml.FormatEdit;
import ca.bc.gov.nrs.hbs.api.intake.xml.FormatEditException;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a composed version and its scale document, as
 * {@code DetailManagerBean.createAndStoreDetailScaleDocumentAndVersion} (new
 * document) and {@code updateDetailScaleDocumentAndInsertVersion} (another
 * version of an existing document) did, through the same procs:
 *
 * <ul>
 *   <li>document: HBS_CREATE_DTL_SCL_DOCUMENT (state OIP) or HBS_STORE_DTL_SCL_DOCUMENT;</li>
 *   <li>an existing later original version loses its original flag (audit UPD + its store proc);</li>
 *   <li>HBS_CREATE_AUDIT ('DSR', 'INS'), the version create proc, its children,
 *       and for log / sample tallies the store the entity bean issued after the
 *       create (NET_VOLUME 0, update columns null).</li>
 * </ul>
 */
final class DocumentWriter {

  static final String ORIGINAL_IN_PROGRESS = "OIP";
  static final String AUDIT_OBJECT_DETAIL_SCALE = "DSR";

  /** Store-proc parameters whose names differ from the table columns (param → column). */
  private static final Map<String, String> STORE_ALIASES = Map.ofEntries(
      Map.entry("i_orig_chk_scl_qnt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd"),
      Map.entry("i_orig_chk_scl_qt_lic_type_cd", "orig_chk_scl_quant_lic_type_cd"),
      Map.entry("i_orig_chk_scl_ql_type_cd", "orig_chk_scl_quant_lic_type_cd"),
      Map.entry("i_orig_chk_scl_ddn", "original_ddn"),
      Map.entry("i_orig_chk_scl_ddv", "original_ddv"),
      Map.entry("i_orig_redtag_scale_site_id_nm", "orig_redtag_scale_site_id_nmbr"),
      Map.entry("i_orig_redtag_scl_site_id_nmbr", "orig_redtag_scale_site_id_nmbr"),
      Map.entry("i_input_orig_chk_scl_ddn", "input_original_ddn"),
      Map.entry("i_input_orig_chk_scl_ddv", "input_original_ddv"),
      Map.entry("i_input_orig_chk_scl_sclr_lic", "input_orig_chk_scl_scaler_lic"));

  /** HBS_CREATE_LOG_TALLY parameter names → HBS_STORE_LOG_TALLY names (the two procs disagree). */
  private static final Map<String, String> LOG_TALLY_CREATE_TO_STORE = Map.of(
      "i_orig_chk_scl_quant_lic_tp_cd", "i_orig_chk_scl_qnt_lic_type_cd",
      "i_orig_redtag_scl_site_id_nmbr", "i_orig_redtag_scale_site_id_nm",
      "i_input_orig_chk_scl_scl_lic", "i_input_orig_chk_scl_sclr_lic");

  private final UnpackContext ctx;

  DocumentWriter(UnpackContext ctx) {
    this.ctx = ctx;
  }

  /** B1012.processDocument after composing: create or update the document, then insert the version. */
  void writeDocumentVersion(ComposedVersion v, long batchDocumentId) throws FormatEditException {
    v.args().put("i_batdoc_id", batchDocumentId);
    Map<String, Object> document = ctx.repository().detailScaleDocument(v.ddn());
    if (document != null) {
      v.args().put("i_original_version_ind", "N");
      restrictScaleDate(v);
      updateDocumentAndInsertVersion(document, v);
    } else {
      v.args().put("i_original_version_ind", "Y");
      restrictScaleDate(v);
      createDocumentAndVersion(v);
    }
  }

  /** B1012.processEntries: a ledger entry always creates a new document (version 1, already original). */
  void writeLedgerVersion(ComposedVersion v) throws FormatEditException {
    restrictScaleDate(v);
    createDocumentAndVersion(v);
  }

  private void createDocumentAndVersion(ComposedVersion v) {
    ProcArgs doc = ProcArgs.of()
        .put("i_detail_document_number", v.ddn())
        .put("i_scale_return_state_code", ORIGINAL_IN_PROGRESS)
        .put("i_detail_scale_doc_type_code", v.docTypeCode())
        .put("i_entry_userid", ctx.userId())
        .put("i_entry_timestamp", ctx.now())
        .put("i_update_userid", ctx.userId())
        .put("i_update_timestamp", ctx.now());
    ctx.call("HBS_CREATE_DTL_SCL_DOCUMENT", doc);
    v.args().put("i_original_version_ind", "Y"); // createAndStoreDetailScaleDocumentAndVersionInt
    insertVersion(v);
  }

  private void updateDocumentAndInsertVersion(Map<String, Object> document, ComposedVersion v) {
    ProcArgs doc = ctx.tableApi().argsFromRow("HBS_STORE_DTL_SCL_DOCUMENT", document)
        .put("i_update_userid", ctx.userId())
        .put("i_update_timestamp", ctx.now());
    ctx.call("HBS_STORE_DTL_SCL_DOCUMENT", doc);

    String storedType = (String) document.get(column(document, "detail_scale_doc_type_code"));
    for (Map<String, Object> existing : ctx.repository().versions(storedType, v.ddn())) {
      long version = ((Number) existing.get(column(existing, "version"))).longValue();
      if (version > v.version() && "Y".equals(existing.get(column(existing, "original_version_ind")))) {
        v.args().put("i_original_version_ind", "Y");
        unflagOriginal(storedType, v.ddn(), version);
        break;
      }
    }
    insertVersion(v);
  }

  /**
   * {@code updateDetailScaleDocumentVersionNotChildrenInt}: audit UPD, then the
   * version's store proc re-sending its row with ORIGINAL_VERSION_IND 'N'.
   */
  private void unflagOriginal(String docTypeCode, String ddn, long version) {
    Map<String, Object> row = ctx.repository().versionRow(docTypeCode, ddn, version);
    long auditId = audit(ddn, version, "UPD");
    String store = switch (docTypeCode) {
      case "W" -> "HBS_STORE_WEIGH_SLIP";
      case "P" -> "HBS_STORE_LOG_TALLY";
      case "S" -> "HBS_STORE_SAMPLE_LOG_TALLY";
      case "F" -> "HBS_STORE_SFP_TALLY";
      default -> throw new IllegalStateException("No later version can exist for a ledger " + ddn);
    };
    if ("W".equals(docTypeCode)) {
      // Loading a WeighSlip recorded a weigh-slip-number edit from null (WeighSlip constructor),
      // so the legacy audit got one detail row; the store wrote the number with leading zeros trimmed.
      Object number = row.get(column(row, "weigh_slip_number"));
      if (number != null) {
        String trimmed = UnpackContext.leftTrimZeros(number.toString());
        ctx.call("HBS_CREATE_SCL_RTN_AUDIT", ProcArgs.of().put("i_audit_id", auditId)
            .put("i_field_name", "Weight_Slip_Number").put("i_action_type_code", "UPD")
            .put("i_hbs_object_type_code", AUDIT_OBJECT_DETAIL_SCALE).put("i_old_value", null)
            .put("i_new_value", trimmed));
        row.put(column(row, "weigh_slip_number"), trimmed);
      }
    }
    ProcArgs args = fromRow(store, row);
    args.put("i_original_version_ind", "N");
    args.put("i_update_userid", ctx.userId());
    args.put("i_update_timestamp", ctx.now());
    ctx.call(store, args);
  }

  /** {@code createAndStoreDetailScaleDocumentVersionInt}: audit INS, version, children, follow-up store. */
  private void insertVersion(ComposedVersion v) {
    audit(v.ddn(), v.version(), "INS");
    ctx.call(v.createProc(), v.args());
    for (Child c : v.children()) {
      ctx.call(c.proc(), c.args());
    }
    if (v.storeProc() != null) {
      ProcArgs store = ctx.nulls(v.storeProc());
      v.args().asMap().forEach((name, value) -> {
        String target = LOG_TALLY_CREATE_TO_STORE.getOrDefault(name, name);
        if (store.has(target)) {
          store.put(target, value);
        }
      });
      store.put("i_net_volume", 0L); // getTotalNetVolume(): never calculated by the unpack
      store.put("i_update_userid", null);
      store.put("i_update_timestamp", null);
      ctx.call(v.storeProc(), store);
    }
  }

  /** {@code DetailManagerBean.createAudit}: HBS_AUDIT header (no detail rows for a new batch version). */
  private long audit(String ddn, long version, String action) {
    long id = ctx.nextId(UnpackContext.SEQ_AUDIT);
    String object = ddn + "," + version;
    ctx.call("HBS_CREATE_AUDIT", ProcArgs.of()
        .put("i_audit_id", id)
        .put("i_user_id", ctx.userId())
        .put("i_user_id_record_actions_for", ctx.userId())
        .put("i_audit_date", ctx.now())
        .put("i_hbs_object_type_code", AUDIT_OBJECT_DETAIL_SCALE)
        .put("i_object_old_id", object)
        .put("i_object_new_id", object)
        .put("i_action_type_code", action));
    return id;
  }

  /** Arguments of a version store proc from a {@code SELECT *} row, including the renamed columns. */
  private ProcArgs fromRow(String proc, Map<String, Object> row) {
    ProcArgs args = ctx.tableApi().argsFromRow(proc, row);
    for (Param p : ctx.tableApi().signatures().get(proc).params()) {
      if (p.isIn() && !args.has(p.name())) {
        String col = STORE_ALIASES.get(p.name());
        args.put(p.name(), col == null ? null : row.get(column(row, col)));
      }
    }
    return args;
  }

  private static String column(Map<String, Object> row, String name) {
    for (String k : row.keySet()) {
      if (k.equalsIgnoreCase(name)) {
        return k;
      }
    }
    return name.toUpperCase(Locale.ROOT);
  }

  /**
   * {@code ValidationHelper.restrictScaleDate} (BT6020) with {@code
   * DacValidator.compareForYearLimit}: dates before 1 January seven years ago
   * are refused (OLDDD1 / OLDAD1 / OLDSD1).
   */
  static void restrictScaleDate(ComposedVersion v) throws FormatEditException {
    if ("D".equals(v.docTypeCode())) {
      if (beforeYearLimit(v.departureDate())) {
        throw new FormatEditException(FormatEdit.YearLimit.DEPARTURE_DATE);
      }
    } else if ("A".equals(v.docTypeCode())) {
      if (beforeYearLimit(v.arrivalDate())) {
        throw new FormatEditException(FormatEdit.YearLimit.ARRIVAL_DATE);
      }
      if (beforeYearLimit(v.scaleDate())) {
        throw new FormatEditException(FormatEdit.YearLimit.SCALE_DATE);
      }
    } else if (beforeYearLimit(v.scaleDate())) {
      throw new FormatEditException(FormatEdit.YearLimit.SCALE_DATE);
    }
  }

  static boolean beforeYearLimit(Date date) {
    if (date == null) {
      return false;
    }
    GregorianCalendar limit = new GregorianCalendar(LocalDate.now(ZoneId.systemDefault()).getYear() - 7,
        Calendar.JANUARY, 1);
    GregorianCalendar day = new GregorianCalendar();
    day.setTime(date);
    day.set(Calendar.HOUR_OF_DAY, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MILLISECOND, 0);
    return limit.getTime().after(day.getTime());
  }
}
