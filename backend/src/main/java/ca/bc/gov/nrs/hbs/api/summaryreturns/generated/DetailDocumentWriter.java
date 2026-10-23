package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnWriter;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.ProcSignatures.Param;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The detail-document writes of the generated-summary actions, through the legacy table-API procs:
 * {@code DetailManagerBean.updateDetailScaleDocumentAndChildren} (audit, version row, document row),
 * {@code updateDetailTransaction} and {@code createAndStore{LogTally,WeighSlip,SFPTally}Txn}.
 */
@Component
public class DetailDocumentWriter {

  /** {@code Constants.HBS_OBJECT_TYPE_DETAIL_SCALE}. */
  static final String OBJECT_DETAIL_SCALE = "DSR";
  /** {@code Constants.ACTION_TYPE_UPDATE}: the action of every audit detail row the version records. */
  static final String ACTION_UPDATE = "UPD";
  /** {@code DacConstants.ScaleReturnAudit.VERSION_STATUS_FIELD_NAME / VERSION_ACTIVE_FIELD_NAME}. */
  static final String FIELD_VERSION_STATUS = "Version_Status";
  static final String FIELD_VERSION_ACTIVE = "Version_Active";

  private final TableApi api;
  private final Audit audit;

  public DetailDocumentWriter(TableApi api, Audit audit) {
    this.api = api;
    this.audit = audit;
  }

  /** Arguments for a proc from a whole row; {@code renames} maps parameters not named {@code i_<column>}. */
  ProcArgs args(String proc, Row row, Map<String, String> renames) {
    ProcArgs a = api.argsFromRow(proc, row.asMap());
    for (Param p : api.signatures().get(proc).params()) {
      if (p.isIn() && !a.has(p.name()) && renames.containsKey(p.name())) {
        a.put(p.name(), row.get(renames.get(p.name())));
      }
    }
    return a;
  }

  /**
   * {@code updateDetailScaleDocumentAndChildren(document, user, actionType)} →
   * {@code updateDetailScaleDocumentVersionIntInt}: an {@code HBS_AUDIT} row (object DSR
   * "ddn,version", {@code actionType}) with a detail row for a changed version state / active flag
   * (BT11741), the version stamped (BT 4654) and stored, then the document row stored as it is
   * (its own audit columns are not touched). The version's children are not rewritten: these
   * actions change nothing in them.
   */
  void update(DetailDocument d, String user, String actionType) {
    DetailState s = d.state;
    List<String[]> edits = new ArrayList<>();
    if (!s.loadedVersion().equals(s.version())) {
      edits.add(new String[] {FIELD_VERSION_STATUS, s.loadedVersion(), s.version()});
    }
    if (s.loadedActive() != s.active()) {
      edits.add(new String[] {FIELD_VERSION_ACTIVE, s.loadedActive() ? "Y" : "N", s.active() ? "Y" : "N"});
    }
    writeAudit(user, d.ddn(), d.versionNo(), actionType, edits);

    d.version.set("scl_rtn_version_state_code", s.version()).set("active_version_ind", s.active() ? "Y" : "N")
        .set("update_userid", user).set("update_timestamp", audit.now());
    api.call(d.family.storeVersionProc, args(d.family.storeVersionProc, d.version, d.family.versionRenames));

    d.document.set("scale_return_state_code", s.doc());
    api.call("HBS_STORE_DTL_SCL_DOCUMENT", args("HBS_STORE_DTL_SCL_DOCUMENT", d.document, Map.of()));
  }

  /** {@code updateDetailTransaction}: the txn row as it is (legacy did not stamp it). */
  void storeTxn(DetailFamily f, Row txn) {
    api.call(f.storeTxnProc(), args(f.storeTxnProc(), txn, Map.of()));
  }

  /** {@code createAndStore*Txn}: the txn, then each of its segregations. */
  void createTxn(DetailFamily f, Row txn, List<Row> segregations) {
    api.call(f.createTxnProc(), args(f.createTxnProc(), txn, Map.of()));
    for (Row seg : segregations) {
      api.call(f.createTxnSegProc(), args(f.createTxnSegProc(), seg, f.txnSegRenames));
    }
  }

  long nextId(String sequence) {
    return api.nextId(sequence);
  }

  /**
   * {@code DetailManagerBean.createAudit} → {@code AuditManager2Impl.createAudit}: object
   * "ddn,version" of type DSR, one {@code HBS_SCALE_RETURN_AUDIT} row per edit.
   */
  private void writeAudit(String user, String ddn, long version, String actionType, List<String[]> edits) {
    long auditId = api.nextId(SummaryReturnWriter.AUDIT_SEQ);
    String objectId = ddn + "," + version;
    api.call("HBS_CREATE_AUDIT", ProcArgs.of()
        .put("i_audit_id", auditId)
        .put("i_user_id", user)
        .put("i_user_id_record_actions_for", user)
        .put("i_audit_date", audit.now())
        .put("i_hbs_object_type_code", OBJECT_DETAIL_SCALE)
        .put("i_object_old_id", objectId)
        .put("i_object_new_id", objectId)
        .put("i_action_type_code", actionType));
    for (String[] e : edits) {
      api.call("HBS_CREATE_SCL_RTN_AUDIT", ProcArgs.of()
          .put("i_audit_id", auditId)
          .put("i_field_name", e[0])
          .put("i_action_type_code", ACTION_UPDATE)
          .put("i_hbs_object_type_code", OBJECT_DETAIL_SCALE)
          .put("i_old_value", e[1])
          .put("i_new_value", e[2]));
    }
  }
}
