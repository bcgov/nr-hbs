package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.OBJECT_SUMMARY_SCALE;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Every summary-return write, through the legacy table-API procs ({@link TableApi}).
 * Ports the persistence half of {@code SummaryScaleReturnManagerBean}:
 * {@code createAndStoreSummaryScaleReturn}, {@code createAndStoreSummaryScaleReturnVersion[ForHold]},
 * {@code updateScaleReturnVersion}, {@code updateSummaryScaleReturn},
 * {@code updateSummaryScaleReturnAndChildrenWithAudit}, the child synchronisers
 * ({@code DatabaseRecordSetSynchronizer}), {@code saveScaleReturnErrors},
 * {@code removeScaleReturnErrors} and the audit trail ({@code AuditManager2Impl.createAudit}).
 */
@Component
public class SummaryReturnWriter {

  /** Sequences the legacy {@code UniqueIdQuery.getNextKey(table)} used ({@code <TABLE>_SEQ}). */
  public static final String DCN_SEQ = "HBS_SCALE_RETURN_SEQ";
  public static final String SEGREGATION_SEQ = "PIECE_SCL_SEGREGATION_SEQ";
  public static final String NOTATION_SEQ = "PIECE_SCL_NOTATION_SEQ";
  public static final String EDIT_ERROR_SEQ = "HBS_EDIT_ERROR_SEQ";
  public static final String AUDIT_SEQ = "HBS_AUDIT_SEQ";

  /**
   * Proc parameters whose name is not {@code i_<column>}: the generated procs
   * call the Y/N columns "flag"/"indicator" and the document state "status".
   */
  private static final Map<String, String> PARAM_COLUMNS = Map.ofEntries(
      Map.entry("i_active_version_flag", "active_version_ind"),
      Map.entry("i_queued_for_print_indicator", "queued_for_print_ind"),
      Map.entry("i_visibility_indicator", "visibility_ind"),
      Map.entry("i_check_scale_indicator", "check_scale_ind"),
      Map.entry("i_nmv_flag", "nmv_ind"),
      Map.entry("i_sb4_flag", "sb4_ind"),
      Map.entry("i_avoidable_flag", "avoidable_ind"),
      Map.entry("i_scale_return_status_code", "scale_return_state_code"),
      Map.entry("i_batch_id", "edi_batch_id"));

  private final TableApi api;
  private final SummaryReturnRepository repo;
  private final Audit audit;

  public SummaryReturnWriter(TableApi api, SummaryReturnRepository repo, Audit audit) {
    this.api = api;
    this.repo = repo;
    this.audit = audit;
  }

  public TableApi api() {
    return api;
  }

  public long nextId(String sequence) {
    return api.nextId(sequence);
  }

  /** Arguments for a create/store proc from a whole row (every parameter must map to a column). */
  ProcArgs args(String proc, Row row) {
    ProcArgs a = api.argsFromRow(proc, row.asMap());
    for (Param p : api.signatures().get(proc).params()) {
      if (p.isIn() && !a.has(p.name())) {
        String column = PARAM_COLUMNS.get(p.name());
        a.put(p.name(), column == null ? null : row.get(column));
      }
    }
    return a;
  }

  // ── HBS_SCALE_RETURN ─────────────────────────────────────────────────────

  public void createHeader(Row header) {
    api.call("HBS_CREATE_HBS_SCALE_RETURN", args("HBS_CREATE_HBS_SCALE_RETURN", header));
  }

  /** {@code updateSummaryScaleReturn}: the document row as it is. */
  public void storeHeader(Row header) {
    api.call("HBS_STORE_HBS_SCALE_RETURN", args("HBS_STORE_HBS_SCALE_RETURN", header));
  }

  // ── Versions ─────────────────────────────────────────────────────────────

  /**
   * {@code createAndStoreSummaryScaleReturnVersion(version, returnType)}: the version row and
   * its children, all new. Children rows without ids get them here.
   */
  public void createVersionAndChildren(SummaryReturn ret) {
    SummaryReturnType type = ret.type();
    api.call(type.createVersion(), args(type.createVersion(), ret.version()));
    for (Row seg : ret.segregations()) {
      prepareChild(ret, seg, "segregation_id", SEGREGATION_SEQ);
      api.call(type.createSegregation(), args(type.createSegregation(), seg));
    }
    for (Row load : ret.dailyLoads()) {
      prepareChild(ret, load, null, null);
      api.call("HBS_CREATE_" + SummaryReturnType.DAILY_LOAD_PROC, args("HBS_CREATE_" + SummaryReturnType.DAILY_LOAD_PROC, load));
    }
    for (Row ntn : ret.notations()) {
      prepareChild(ret, ntn, "ntn_id", NOTATION_SEQ);
      api.call(type.createNotation(), args(type.createNotation(), ntn));
    }
  }

  /**
   * {@code updateSummary{Piece,Weight,Sample}ScaleReturnVersion}: stores the version row and
   * synchronises its segregations (piece, sample) or daily loads (weight) with the database.
   * Removed segregations lose their edit errors first. Returns the child audit details.
   */
  public List<AuditTrail.Detail> storeVersion(SummaryReturn ret) {
    SummaryReturnType type = ret.type();
    api.call(type.storeVersion(), args(type.storeVersion(), ret.version()));
    List<AuditTrail.Detail> details = new ArrayList<>();
    if (type.hasSegregations()) {
      List<Row> db = repo.segregations(type, ret.dcn(), ret.versionNo());
      sync(ret, db, ret.segregations(), r -> r.get("segregation_id"), SEGREGATION_SEQ, "segregation_id",
          removed -> {
            removeSegregationErrors(type, List.of(removed.lng("segregation_id")));
            api.call(type.removeSegregation(), ProcArgs.of().put("i_segregation_id", removed.get("segregation_id")));
            details.add(AuditTrail.segregationDeleted(removed));
          },
          (before, after) -> {
            api.call(type.storeSegregation(), args(type.storeSegregation(), after));
            details.addAll(AuditTrail.segregationEdits(type, before, after));
          },
          added -> {
            api.call(type.createSegregation(), args(type.createSegregation(), added));
            details.add(AuditTrail.segregationAdded(added));
          });
    }
    if (type == SummaryReturnType.WEIGHT) {
      List<Row> db = repo.dailyLoads(type, ret.dcn(), ret.versionNo());
      String create = "HBS_CREATE_" + SummaryReturnType.DAILY_LOAD_PROC;
      String store = "HBS_STORE_" + SummaryReturnType.DAILY_LOAD_PROC;
      sync(ret, db, ret.dailyLoads(), r -> r.lng("day"), null, null,
          removed -> {
            api.call("HBS_REMOVE_" + SummaryReturnType.DAILY_LOAD_PROC, ProcArgs.of()
                .put("i_document_control_number", ret.dcn()).put("i_version", ret.versionNo())
                .put("i_day", removed.get("day")));
            details.add(AuditTrail.dailyLoadDeleted(removed));
          },
          (before, after) -> {
            api.call(store, args(store, after));
            details.addAll(AuditTrail.dailyLoadEdits(before, after));
          },
          added -> {
            api.call(create, args(create, added));
            details.add(AuditTrail.dailyLoadAdded(added));
          });
    }
    return details;
  }

  /** {@code updateSummaryScaleNotations}: synchronises the version's notations. */
  public List<AuditTrail.Detail> storeNotations(SummaryReturn ret) {
    SummaryReturnType type = ret.type();
    List<AuditTrail.Detail> details = new ArrayList<>();
    List<Row> db = repo.notations(type, ret.dcn(), ret.versionNo());
    sync(ret, db, ret.notations(), r -> r.get("ntn_id"), NOTATION_SEQ, "ntn_id",
        removed -> {
          api.call(type.removeNotation(), ProcArgs.of().put("i_ntn_id", removed.get("ntn_id")));
          details.add(AuditTrail.notationDeleted(removed));
        },
        (before, after) -> {
          api.call(type.storeNotation(), args(type.storeNotation(), after));
          details.addAll(AuditTrail.notationEdits(before, after));
        },
        added -> {
          api.call(type.createNotation(), args(type.createNotation(), added));
          details.add(AuditTrail.notationAdded(added));
        });
    return details;
  }

  private interface Change {
    void apply(Row before, Row after);
  }

  /**
   * {@code DatabaseRecordSetSynchronizer.groupRecordsByDatabaseAction}: rows in the database but
   * not in memory are deleted, rows in both whose content differs are updated, new rows inserted.
   */
  private void sync(SummaryReturn ret, List<Row> database, List<Row> memory, Function<Row, Object> key,
      String sequence, String idColumn, java.util.function.Consumer<Row> delete, Change update,
      java.util.function.Consumer<Row> insert) {
    Map<String, Row> dbByKey = new HashMap<>();
    database.forEach(r -> dbByKey.put(String.valueOf(key.apply(r)), r));
    Set<String> kept = new java.util.HashSet<>();
    for (Row r : memory) {
      Object k = key.apply(r);
      if (k != null && dbByKey.containsKey(String.valueOf(k))) kept.add(String.valueOf(k));
    }
    for (Row r : database) {
      if (!kept.contains(String.valueOf(key.apply(r)))) delete.accept(r);
    }
    for (Row r : memory) {
      Object k = key.apply(r);
      Row before = k == null ? null : dbByKey.get(String.valueOf(k));
      if (before == null) {
        prepareChild(ret, r, idColumn, sequence);
        insert.accept(r);
      } else if (contentChanged(before, r)) {
        r.set("document_control_number", ret.dcn()).set("version", ret.versionNo());
        r.set("update_userid", audit.user()).set("update_timestamp", audit.now());
        update.apply(before, r);
      }
    }
  }

  private static final Set<String> AUDIT_COLUMNS = Set.of("entry_userid", "entry_timestamp", "update_userid", "update_timestamp");

  private static boolean contentChanged(Row before, Row after) {
    for (var e : after.asMap().entrySet()) {
      if (AUDIT_COLUMNS.contains(e.getKey())) continue;
      Object a = before.get(e.getKey());
      Object b = e.getValue();
      if (!Row.sameValue(a instanceof String s && s.isEmpty() ? null : a, b instanceof String s && s.isEmpty() ? null : b)) {
        return true;
      }
    }
    return false;
  }

  /** New child row: keys of the version, an id from the sequence when it has none, audit columns. */
  private void prepareChild(SummaryReturn ret, Row child, String idColumn, String sequence) {
    child.set("document_control_number", ret.dcn()).set("version", ret.versionNo());
    if (idColumn != null && child.get(idColumn) == null) child.set(idColumn, api.nextId(sequence));
    Timestamp now = audit.now();
    if (child.get("entry_userid") == null) child.set("entry_userid", audit.user());
    if (child.get("entry_timestamp") == null) child.set("entry_timestamp", now);
    if (child.get("update_userid") == null) child.set("update_userid", audit.user());
    if (child.get("update_timestamp") == null) child.set("update_timestamp", now);
  }

  // ── Composite saves (the manager-bean methods the workflows call) ─────────

  /**
   * {@code createAndStoreSummaryScaleReturn}: audit "Scale_Return" INS, the document, the
   * version and its children.
   */
  public void createReturn(SummaryReturn ret, String user) {
    writeAudit(user, ret.dcn(), ret.versionNo(), SummaryReturnRules.ACTION_INSERT,
        List.of(new AuditTrail.Detail("Scale_Return", SummaryReturnRules.ACTION_INSERT, OBJECT_SUMMARY_SCALE, null, null)));
    createHeader(ret.header());
    createVersionAndChildren(ret);
    ret.markSaved();
  }

  /**
   * {@code createAndStoreSummaryScaleReturnVersion(version, type, user)} (action RRD) and
   * {@code …ForHold} (action INS): audit "Scale_Return", then the new version and its children.
   */
  public void createVersion(SummaryReturn ret, String actionType, String user) {
    writeAudit(user, ret.dcn(), ret.versionNo(), actionType,
        List.of(new AuditTrail.Detail("Scale_Return", actionType, OBJECT_SUMMARY_SCALE, null, null)));
    ret.version().set("entry_timestamp", audit.now()); // BT 5532
    createVersionAndChildren(ret);
    ret.markSaved();
  }

  /**
   * {@code updateScaleReturnVersion(scaleReturn, actionType, user, updateErrors, OMIT_TXNS)}:
   * stamps the version, removes the old edit errors (when {@code errors} is given), audits the
   * field edits under {@code actionType}, stores the version and its segregations / daily loads,
   * and saves {@code errors}.
   */
  public void saveVersion(SummaryReturn ret, String actionType, String user, List<EditError> errors) {
    ret.version().set("update_userid", user).set("update_timestamp", audit.now());
    if (errors != null) {
      removeErrors(ret);
    }
    List<AuditTrail.Detail> details = new ArrayList<>(ret.versionEdits());
    details.addAll(storeVersion(ret));
    writeAudit(user, ret.dcn(), ret.versionNo(), actionType, details);
    if (errors != null) {
      saveErrors(ret, errors);
    }
    ret.markSaved();
  }

  /**
   * {@code updateSummaryScaleReturnAndChildrenWithAudit(scaleReturn, user, doAudits)}: audit UPD,
   * the document (stamped, BT-4479), the version with its segregations / daily loads, and the
   * notations.
   */
  public void saveReturnAndChildren(SummaryReturn ret, String user, boolean doAudits) {
    ret.header().set("update_userid", user).set("update_timestamp", audit.now());
    ret.version().set("update_userid", user).set("update_timestamp", audit.now()); // BT5167 / BT11743
    List<AuditTrail.Detail> details = new ArrayList<>(ret.versionEdits());
    storeHeader(ret.header());
    details.addAll(storeVersion(ret));
    details.addAll(storeNotations(ret));
    if (doAudits) {
      writeAudit(user, ret.dcn(), ret.versionNo(), SummaryReturnRules.ACTION_UPDATE, details);
    }
    ret.markSaved();
  }

  // ── Edit errors ──────────────────────────────────────────────────────────

  /**
   * {@code removeScaleReturnErrors}: every version error of the document (all versions, as the
   * legacy {@code getVersionErrors(type, dcn, null)} found them) and the errors of the current
   * version's segregations. Weight load-summary errors are left, as legacy left them.
   */
  public void removeErrors(SummaryReturn ret) {
    SummaryReturnType type = ret.type();
    for (Long id : repo.versionErrorIds(type, ret.dcn())) {
      api.call(type.removeVersionError(), ProcArgs.of().put("i_error_id", id));
    }
    if (type.hasSegregations()) {
      List<Long> segIds = ret.segregations().stream().map(r -> r.lng("segregation_id")).filter(Objects::nonNull).toList();
      removeSegregationErrors(type, segIds);
    }
  }

  private void removeSegregationErrors(SummaryReturnType type, List<Long> segregationIds) {
    for (Long id : repo.segregationErrorIds(type, segregationIds)) {
      api.call(type.removeSegregationError(), ProcArgs.of().put("i_error_id", id));
    }
  }

  /** {@code saveScaleReturnErrors}: one row per error in the version / segregation / load-summary error table. */
  public void saveErrors(SummaryReturn ret, List<EditError> errors) {
    SummaryReturnType type = ret.type();
    for (EditError e : errors) {
      String proc = switch (e.target()) {
        case VERSION -> type.createVersionError();
        case SEGREGATION -> type.createSegregationError();
        case DAILY_LOAD -> "HBS_CREATE_" + SummaryReturnType.LOAD_ERROR_PROC;
      };
      boolean onVersion = e.target() != EditError.Target.SEGREGATION;
      api.call(proc, ProcArgs.of()
          .put("i_error_id", api.nextId(EDIT_ERROR_SEQ))
          .put("i_nack_id", null)
          .put("i_edi_doc_id", null)
          .put("i_document_control_number", onVersion ? ret.dcn() : null)
          .put("i_version", onVersion ? ret.versionNo() : null)
          .put("i_segregation_id", e.segregationId())
          .put("i_error_timestamp", audit.now())
          .put("i_day", e.day())
          .put("i_hbs_edit_err_category_code", e.category())
          .put("i_hbs_edit_err_message_code", e.message()));
    }
  }

  // ── Audit trail ──────────────────────────────────────────────────────────

  /**
   * {@code AuditManager2Impl.createAudit}: an {@code HBS_AUDIT} row for object "dcn,version"
   * (type SSR) and one {@code HBS_SCALE_RETURN_AUDIT} row per detail.
   */
  public void writeAudit(String user, long dcn, long version, String actionType, List<AuditTrail.Detail> details) {
    long auditId = api.nextId(AUDIT_SEQ);
    String objectId = dcn + "," + version;
    api.call("HBS_CREATE_AUDIT", ProcArgs.of()
        .put("i_audit_id", auditId)
        .put("i_user_id", user)
        .put("i_user_id_record_actions_for", user)
        .put("i_audit_date", audit.now())
        .put("i_hbs_object_type_code", OBJECT_SUMMARY_SCALE)
        .put("i_object_old_id", objectId)
        .put("i_object_new_id", objectId)
        .put("i_action_type_code", actionType));
    for (AuditTrail.Detail d : details) {
      api.call("HBS_CREATE_SCL_RTN_AUDIT", ProcArgs.of()
          .put("i_audit_id", auditId)
          .put("i_field_name", d.fieldName())
          .put("i_action_type_code", d.actionType())
          .put("i_hbs_object_type_code", d.objectType())
          .put("i_old_value", d.oldValue())
          .put("i_new_value", d.newValue()));
    }
  }
}
