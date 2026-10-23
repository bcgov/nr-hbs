package ca.bc.gov.nrs.hbs.api.detailreturns.store;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.EditError;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.Status;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import ca.bc.gov.nrs.hbs.api.workflow.RowLock;
import ca.bc.gov.nrs.hbs.api.workflow.StaleUpdateException;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * Loads and writes detail scale documents — the persistence half of the legacy
 * {@code DetailManagerBean} (finders, {@code createAndStoreDetailScaleDocumentAndVersion},
 * {@code updateDetailScaleDocumentAndChildren}, {@code updateDetailScaleDocumentVersion},
 * {@code updateDetailScaleDocumentAndInsertVersion}, {@code removeErrors},
 * {@code saveDetailScaleDocumentErrors}) and of the entity beans / DAOs under it.
 *
 * <p>Every write goes through {@link TableApi}. Every update re-reads the row
 * first and refuses a concurrent change:
 * <ul>
 *   <li>the document: its stored state must still be the state read
 *       (legacy {@code DetailScaleDocumentEntityBean.setScaleReturnStateCode});</li>
 *   <li>the version: its UPDATE_TIMESTAMP, state and active flag must still be
 *       the ones read (legacy {@code WeighSlipEntityBean} / {@code SpecialForestProductTallyEntityBean}
 *       state checks, extended to every type with {@link RowLock}).</li>
 * </ul>
 * Both throw {@link StaleUpdateException} with the legacy wording
 * ({@code error.unable.tosave.doc}).
 */
@Component
public class DetailStore {

  /** ApplicationResources {@code error.unable.tosave.doc}. */
  public static final String STALE_MESSAGE =
      "Your changes were not saved because the scale return has been updated by another process.";

  private final DetailRows rows;
  private final TableApi api;
  private final Audit audit;
  private final DetailAuditTrail auditTrail;

  public DetailStore(DetailRows rows, TableApi api, Audit audit) {
    this.rows = rows;
    this.api = api;
    this.audit = audit;
    this.auditTrail = new DetailAuditTrail(api);
  }

  public TableApi api() {
    return api;
  }

  public Audit audit() {
    return audit;
  }

  public DetailRows rows() {
    return rows;
  }

  // ── finders ───────────────────────────────────────────────────────

  /** {@code findDetailScaleDocumentByPrimaryKey} != null — the Add duplicate-DDN check. */
  public boolean exists(String ddn) {
    return rows.documentRow(ddn).isPresent();
  }

  public Optional<DocType> typeOf(String ddn) {
    return rows.documentRow(ddn).map(r -> DocType.of(r.str("detail_scale_doc_type_code")));
  }

  /** {@code findDetailScaleDocumentAndVersionByPrimaryKey}. */
  public Optional<DetailDocument> find(String ddn, long version) {
    return findVersion(ddn, r -> Objects.equals(r.lng("version"), version)).stream().findFirst();
  }

  /** {@code findDetailScaleDocumentAndActiveVersionByPrimaryKey}; legacy failed on several active versions. */
  public Optional<DetailDocument> findActive(String ddn) {
    List<DetailDocument> found = findVersion(ddn, r -> "Y".equals(r.str("active_version_ind")));
    if (found.size() > 1) {
      throw new IllegalStateException("Multiple active versions found for document " + ddn);
    }
    return found.stream().findFirst();
  }

  /**
   * {@code findDocumentAndReplacementPendingVersion} / {@code ...InvoicedVersion} /
   * {@code ...CorrectionPendingVersion}: the one version in that status; legacy
   * threw a DetailScaleDocumentException ("Multiple … versions found") on more than one.
   */
  public Optional<DetailDocument> findSingleByStatus(String ddn, String status) {
    List<DetailDocument> found = findVersion(ddn, r -> status.equals(r.str("scl_rtn_version_state_code")));
    if (found.size() > 1) {
      throw new IllegalStateException("Multiple " + status + " versions found for document " + ddn);
    }
    return found.stream().findFirst();
  }

  /** {@code findDocumentAndLatestCancelledVersion} / {@code ...LatestSupersededVersion}: the highest version. */
  public Optional<DetailDocument> findLatestByStatus(String ddn, String status) {
    List<DetailDocument> found = findVersion(ddn, r -> status.equals(r.str("scl_rtn_version_state_code")));
    return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
  }

  /**
   * {@code findScaleDocumentWithPreviousInvoicedVersion}: the highest version
   * below {@code version} that is ISS or CAN ({@code DetailManagerQuery.selectPreviousInvoicedVersion}).
   */
  public Optional<DetailDocument> findPreviousInvoiced(String ddn, long version) {
    List<DetailDocument> found = findVersion(ddn, r -> {
      String s = r.str("scl_rtn_version_state_code");
      Long v = r.lng("version");
      return v != null && v < version
          && (Status.Version.INVOICED.equals(s) || Status.Version.CANCELLED_AND_INVOICED.equals(s));
    });
    return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
  }

  /** {@code getNextVersionId}: MAX(version) + 1. */
  public long nextVersion(DocType type, String ddn) {
    return rows.versionRows(type, ddn).stream().map(r -> r.lng("version")).filter(Objects::nonNull)
        .mapToLong(Long::longValue).max().orElse(0L) + 1;
  }

  /** Every version of the document (version order), each attached to its own document object. */
  public List<DetailDocument> versions(String ddn) {
    return findVersion(ddn, r -> true);
  }

  private List<DetailDocument> findVersion(String ddn, Predicate<Row> which) {
    Optional<Row> docRow = rows.documentRow(ddn);
    if (docRow.isEmpty()) {
      return List.of();
    }
    DocType type = DocType.of(docRow.get().str("detail_scale_doc_type_code"));
    List<DetailDocument> out = new ArrayList<>();
    for (Row v : rows.versionRows(type, ddn)) {
      if (which.test(v)) {
        out.add(assemble(type, docRow.get().copy(), v));
      }
    }
    return out;
  }

  private DetailDocument assemble(DocType type, Row docRow, Row versionRow) {
    VersionState vs = new VersionState(versionRow.str("scl_rtn_version_state_code"),
        "Y".equals(versionRow.str("active_version_ind")));
    DetailVersion version = new DetailVersion(versionRow, vs, true);
    if (type.isTally()) {
      for (Row line : rows.lineRows(type, versionRow.str("detail_document_number"), versionRow.lng("version"))) {
        LogLine log = new LogLine(line);
        log.defects().addAll(rows.defectRows(type, line.lng("logdtl_id")));
        version.logs().add(log);
      }
    }
    DocumentState ds = new DocumentState(docRow.str("scale_return_state_code"), vs);
    return new DetailDocument(type, docRow, ds, version, true);
  }

  // ── writes ────────────────────────────────────────────────────────

  /**
   * {@code createAndStoreDetailScaleDocumentAndVersion}: the document row
   * (HBS_CREATE_DTL_SCL_DOCUMENT) then the first version (original) and its lines.
   */
  public void createDocumentAndVersion(DetailDocument doc, String user) {
    Row d = doc.row();
    d.set("scale_return_state_code", doc.state().value());
    d.set("detail_scale_doc_type_code", doc.type().code());
    api.call("HBS_CREATE_DTL_SCL_DOCUMENT", withNulls("HBS_CREATE_DTL_SCL_DOCUMENT", d));
    doc.markPersisted();
    doc.version().row().setYes("original_version_ind", true);
    createVersion(doc, user, DetailAuditTrail.INSERT);
  }

  /** {@code createAndStoreDetailScaleDocumentVersionInt}: the version row and its log lines. */
  public void createVersion(DetailDocument doc, String user, String actionType) {
    DetailVersion v = doc.version();
    Timestamp now = audit.now();
    auditTrail.record(doc.type(), v, doc.ddn(), user, now, actionType, true);
    copyState(v);
    String proc = doc.type().createProc();
    api.call(proc, versionArgs(proc, doc.type().createAliases(), v.row()));
    if (doc.isTally()) {
      for (LogLine line : v.logs()) {
        createLine(doc, line);
      }
    }
    v.markPersisted();
  }

  /**
   * {@code updateDetailScaleDocumentAndInsertVersion}: stores the document, hands
   * the original-version flag to the new version when a later original exists,
   * then creates the version.
   */
  public void insertVersion(DetailDocument doc, String user) {
    storeDocument(doc);
    long dv = doc.version().number();
    for (DetailDocument other : versions(doc.ddn())) {
      DetailVersion ov = other.version();
      if (ov.number() > dv && Boolean.TRUE.equals(ov.row().yes("original_version_ind"))) {
        doc.version().row().setYes("original_version_ind", true);
        ov.row().setYes("original_version_ind", false);
        storeVersion(other, user, DetailAuditTrail.UPDATE, false);
        break;
      }
    }
    createVersion(doc, user, DetailAuditTrail.INSERT);
  }

  /**
   * {@code updateDetailScaleDocumentAndChildren}: the version (and its lines),
   * then the document.
   */
  public void store(DetailDocument doc, String user, String actionType) {
    storeVersion(doc, user, actionType, true);
    storeDocument(doc);
  }

  /** {@code updateDetailScaleDocumentVersion}: audit, then HBS_STORE_&lt;version&gt; and the line changes. */
  public void storeVersion(DetailDocument doc, String user, String actionType, boolean children) {
    DetailVersion v = doc.version();
    if (!v.persisted()) {
      throw new IllegalStateException("Version " + doc.ddn() + "," + v.number() + " was never created");
    }
    checkVersionUnchanged(doc);
    Timestamp now = audit.now();
    auditTrail.record(doc.type(), v, doc.ddn(), user, now, actionType, false);
    v.row().set("update_userid", user);
    v.row().set("update_timestamp", now);
    copyState(v);
    String proc = doc.type().storeProc();
    api.call(proc, versionArgs(proc, doc.type().storeAliases(), v.row()));
    if (children && doc.isTally()) {
      syncLines(doc, user, now);
    }
    v.markPersisted();
  }

  /** {@code updateDetailScaleDocument}: HBS_STORE_DTL_SCL_DOCUMENT after the stale-state check. */
  public void storeDocument(DetailDocument doc) {
    Row current = rows.documentRow(doc.ddn())
        .orElseThrow(() -> new StaleUpdateException(STALE_MESSAGE));
    if (doc.savedState() != null && !doc.savedState().equals(current.str("scale_return_state_code"))) {
      throw new StaleUpdateException(STALE_MESSAGE);
    }
    doc.row().set("scale_return_state_code", doc.state().value());
    api.call("HBS_STORE_DTL_SCL_DOCUMENT", withNulls("HBS_STORE_DTL_SCL_DOCUMENT", doc.row()));
    doc.markPersisted();
  }

  /** {@code removeErrors}: every version error and every line error of the version. */
  public void removeErrors(DetailDocument doc) {
    DocType type = doc.type();
    DetailVersion v = doc.version();
    if (!v.persisted()) {
      return;
    }
    if (type.isTally()) {
      for (Row line : rows.lineRows(type, doc.ddn(), v.number())) {
        for (Long id : rows.lineErrorIds(type, line.lng("logdtl_id"))) {
          api.call(type.details().errorRemove(), ProcArgs.of().put("i_error_id", id));
        }
      }
    }
    for (Long id : rows.versionErrorIds(type, doc.ddn(), v.number())) {
      api.call(type.errorRemove(), ProcArgs.of().put("i_error_id", id));
    }
  }

  /** {@code saveDetailScaleDocumentErrors}: one row per edit error found by the validator. */
  public void saveErrors(DetailDocument doc) {
    DocType type = doc.type();
    Timestamp now = audit.now();
    for (EditError e : doc.errors()) {
      String proc = e.isLineError() ? type.details().errorCreate() : type.errorCreate();
      api.call(proc, ProcArgs.of()
          .put("i_error_id", api.nextId(DocType.ERROR_SEQUENCE))
          .put("i_hbs_edit_err_category_code", e.category())
          .put("i_hbs_edit_err_message_code", e.code())
          .put("i_logdtl_id", e.isLineError() ? e.line().id() : null)
          .put("i_detail_document_number", doc.ddn())
          .put("i_version", doc.version().number())
          .put("i_error_timestamp", now));
    }
  }

  // ── helpers ───────────────────────────────────────────────────────

  private void copyState(DetailVersion v) {
    v.row().set("scl_rtn_version_state_code", v.state().value());
    v.row().setYes("active_version_ind", v.state().active());
  }

  private ProcArgs versionArgs(String proc, Map<String, String> aliases, Row row) {
    ProcArgs args = api.argsFromRow(proc, row.asMap());
    for (Param p : api.signatures().get(proc).params()) {
      if (!p.isIn() || args.has(p.name())) continue;
      String column = aliases.get(p.name());
      args.put(p.name(), column == null ? null : row.get(column));
    }
    return args;
  }

  private void checkVersionUnchanged(DetailDocument doc) {
    DetailVersion v = doc.version();
    Row current = rows.versionRows(doc.type(), doc.ddn()).stream()
        .filter(r -> Objects.equals(r.lng("version"), v.number()))
        .findFirst()
        .orElseThrow(() -> new StaleUpdateException(STALE_MESSAGE));
    Row loaded = v.loaded();
    if (!Objects.equals(current.str("scl_rtn_version_state_code"), loaded.str("scl_rtn_version_state_code"))
        || !Objects.equals(current.str("active_version_ind"), loaded.str("active_version_ind"))) {
      throw new StaleUpdateException(STALE_MESSAGE);
    }
    try {
      RowLock.check(current.get("update_timestamp"), loaded.get("update_timestamp"));
    } catch (StaleUpdateException ex) {
      throw new StaleUpdateException(STALE_MESSAGE);
    }
  }

  private void createLine(DetailDocument doc, LogLine line) {
    DocType.Details d = doc.type().details();
    Row r = line.row();
    r.set("logdtl_id", api.nextId(d.sequence()));
    r.set("detail_document_number", doc.ddn());
    r.set("version", doc.version().number());
    api.call(d.create(), withNulls(d.create(), r));
    if (d.defects() != null) {
      for (Row defect : line.defects()) {
        defect.set("log_defect_id", api.nextId(d.defects().sequence()));
        defect.set("logdtl_id", r.get("logdtl_id"));
        api.call(d.defects().create(), withNulls(d.defects().create(), defect));
      }
    }
  }

  /**
   * {@code LogTallyDAO.updateLogs} / {@code DatabaseRecordSetSynchronizer}: delete
   * the stored lines that are gone (with their defects and errors), store the
   * changed ones, insert the new ones; the same for each line's defects
   * ({@code LogTallyDetailDAO.syncDefects}).
   */
  private void syncLines(DetailDocument doc, String user, Timestamp now) {
    DocType type = doc.type();
    DocType.Details d = type.details();
    Map<Long, Row> stored = new HashMap<>();
    for (Row r : rows.lineRows(type, doc.ddn(), doc.version().number())) {
      stored.put(r.lng("logdtl_id"), r);
    }
    Set<Long> kept = new HashSet<>();
    for (LogLine line : doc.version().logs()) {
      if (line.id() != null && stored.containsKey(line.id())) {
        kept.add(line.id());
      }
    }
    for (Map.Entry<Long, Row> e : stored.entrySet()) {
      if (!kept.contains(e.getKey())) {
        removeLine(type, e.getKey());
      }
    }
    for (LogLine line : doc.version().logs()) {
      if (line.id() == null || !stored.containsKey(line.id())) {
        line.row().set("entry_userid", Optional.ofNullable(line.row().str("entry_userid")).orElse(user));
        line.row().set("entry_timestamp", Optional.ofNullable(line.row().get("entry_timestamp")).orElse(now));
        line.row().set("update_userid", user);
        line.row().set("update_timestamp", now);
        createLine(doc, line);
        continue;
      }
      if (changed(stored.get(line.id()), line.row())) {
        line.row().set("update_userid", user);
        line.row().set("update_timestamp", now);
        api.call(d.store(), withNulls(d.store(), line.row()));
      }
      if (d.defects() != null) {
        syncDefects(type, line, user, now);
      }
    }
  }

  private void syncDefects(DocType type, LogLine line, String user, Timestamp now) {
    DocType.Defects df = type.details().defects();
    Map<Long, Row> stored = new HashMap<>();
    for (Row r : rows.defectRows(type, line.id())) {
      stored.put(r.lng("log_defect_id"), r);
    }
    Set<Long> kept = new HashSet<>();
    for (Row defect : line.defects()) {
      Long id = defect.lng("log_defect_id");
      if (id != null && stored.containsKey(id)) kept.add(id);
    }
    for (Long id : stored.keySet()) {
      if (!kept.contains(id)) {
        api.call(df.remove(), ProcArgs.of().put("i_log_defect_id", id));
      }
    }
    for (Row defect : line.defects()) {
      Long id = defect.lng("log_defect_id");
      defect.set("logdtl_id", line.id());
      if (id == null || !stored.containsKey(id)) {
        defect.set("log_defect_id", api.nextId(df.sequence()));
        defect.set("entry_userid", Optional.ofNullable(defect.str("entry_userid")).orElse(user));
        defect.set("entry_timestamp", Optional.ofNullable(defect.get("entry_timestamp")).orElse(now));
        defect.set("update_userid", user);
        defect.set("update_timestamp", now);
        api.call(df.create(), withNulls(df.create(), defect));
      } else if (changed(stored.get(id), defect)) {
        defect.set("update_userid", user);
        defect.set("update_timestamp", now);
        api.call(df.store(), withNulls(df.store(), defect));
      }
    }
  }

  /** {@code LogTallyDetailDAO.delete}: defects, then the line's errors (BT6229), then the line. */
  private void removeLine(DocType type, long logdtlId) {
    DocType.Details d = type.details();
    if (d.defects() != null) {
      for (Row defect : rows.defectRows(type, logdtlId)) {
        api.call(d.defects().remove(), ProcArgs.of().put("i_log_defect_id", defect.lng("log_defect_id")));
      }
    }
    for (Long id : rows.lineErrorIds(type, logdtlId)) {
      api.call(d.errorRemove(), ProcArgs.of().put("i_error_id", id));
    }
    api.call(d.remove(), ProcArgs.of().put("i_logdtl_id", logdtlId));
  }

  /** Content comparison ({@code LogTallyDetailContentComparator}): every column but the audit ones. */
  private static boolean changed(Row stored, Row current) {
    for (Map.Entry<String, Object> e : current.asMap().entrySet()) {
      String c = e.getKey();
      if (c.startsWith("entry_") || c.startsWith("update_")) continue;
      if (!Row.same(stored.get(c), e.getValue())) return true;
    }
    return false;
  }

  private ProcArgs withNulls(String proc, Row row) {
    ProcArgs args = api.argsFromRow(proc, row.asMap());
    for (Param p : api.signatures().get(proc).params()) {
      if (p.isIn() && !args.has(p.name())) {
        args.put(p.name(), null);
      }
    }
    return args;
  }
}
