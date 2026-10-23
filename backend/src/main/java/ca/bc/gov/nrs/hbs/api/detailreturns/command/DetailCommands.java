package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Button;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailPayload;
import ca.bc.gov.nrs.hbs.api.detailreturns.EventChange;
import ca.bc.gov.nrs.hbs.api.detailreturns.KeyGenerator;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.Status;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * The detail-return commands ({@code POST /api/v1/hbs/commands/detailReturns.*}),
 * one per legacy button of the detail pages (Buttons.jsp, P550/P600/P650/P700/P750
 * Add, the P554/P604/P654/P754 confirmations, the event-type change pages).
 * Each loads the version the SPA showed (with its {@code updateTimestamp}),
 * applies the legacy button rule ({@link ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority})
 * and runs the ported workflow in the command's single transaction.
 */
public final class DetailCommands {

  private DetailCommands() {
  }

  /** Base: the support bean and the one-document pattern. */
  abstract static class Base implements WorkflowCommand {
    protected final DetailCommandSupport s;

    Base(DetailCommandSupport s) {
      this.s = s;
    }

    @Override
    public Capability capability() {
      return Capability.DETAIL_RETURN_EDIT;
    }

    protected DetailDocument loadFor(Button button, Map<String, Object> body) {
      DetailDocument doc = s.load(body);
      s.authority().require(button, doc, s.caller());
      return doc;
    }
  }

  /** P550/P600/P650/P700/P750 Add ({@code LogTallyActionHandler} ADD, {@code P600Action.addDetailDocument}, …). */
  @Component
  public static class Add extends Base {
    public Add(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.add";
    }

    @Override
    public String legacy() {
      return "P550/P600/P650/P700/P750 Add → WorkflowManagerBean.add";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> body) {
      DocType type = DocType.of(DetailCommandSupport.required(body, "docType"));
      String user = s.user();
      Timestamp now = s.store().audit().now();
      DetailDocument doc = newDocument(type, user, now);
      s.checkForm(doc, true, body);
      DetailPayload.applyFields(doc, (Map<String, Object>) body.get("fields"));
      DetailPayload.applyLogs(doc, (List<Map<String, Object>>) body.get("logs"));
      DetailPayload.defaultCheckboxes(doc);
      String ddn = ddn(doc);
      if (s.store().exists(ddn)) {
        // LogTallyActionHandler: error.duplicate.ddn
        throw new WorkflowValidationException("ddn",
            "The detail scale document you are trying to insert already exists.");
      }
      doc.row().set("detail_document_number", ddn);
      doc.version().row().set("detail_document_number", ddn);
      s.fence(doc);
      s.run(() -> s.workflow().add(doc, user));
      return s.result(doc);
    }

    /**
     * {@code WorkflowDocumentFactory.newOnline*} / {@code P700Action.composeARL/DPL}:
     * OIP document, version 1 INC active original, valid signature, a new
     * DTL_DOC_SEQ, SFP volume method G.
     */
    DetailDocument newDocument(DocType type, String user, Timestamp now) {
      Row v = DetailPayload.blankVersion(type)
          .set("version", 1L)
          .setYes("valid_signature_ind", true)
          .setYes("original_version_ind", true)
          .set("dtl_doc_seq", s.store().api().nextId(type.docSeqSequence()))
          .set("entry_userid", user).set("entry_timestamp", now)
          .set("update_userid", user).set("update_timestamp", now);
      if (type == DocType.SFP_TALLY) {
        v.set("volume_calc_method_type", "G"); // LogTallyCommon.VolumeCalcTypeCode.GROSS
      }
      VersionState vs = VersionState.initialOnline();
      Row d = new Row().set("scale_return_state_code", Status.Document.ORIGINAL_IN_PROGRESS)
          .set("detail_scale_doc_type_code", type.code())
          .set("entry_userid", user).set("entry_timestamp", now)
          .set("update_userid", user).set("update_timestamp", now);
      return new DetailDocument(type, d, new DocumentState(Status.Document.ORIGINAL_IN_PROGRESS, vs),
          new DetailVersion(v, vs, false), false);
    }

    /** {@code KeyGenerator.getDetailDocumentNumber} / ledger prefix + SCL_SITE_LEDGER_SEQ. */
    String ddn(DetailDocument doc) {
      Row v = doc.version().row();
      try {
        return switch (doc.type()) {
          case LOG_TALLY, SAMPLE_LOG_TALLY, SFP_TALLY -> KeyGenerator.tally(v.str("primary_license_number"),
              v.lng("return_number"), v.date("scale_date").toLocalDateTime());
          case WEIGH_SLIP -> KeyGenerator.weighSlip(v.str("scale_site_id_nmbr"), v.str("weigh_slip_number"),
              v.date("scale_date").toLocalDateTime());
          case ARRIVAL_LEDGER -> KeyGenerator.ledger(true, s.store().api().nextId("SCL_SITE_LEDGER_SEQ"));
          case DEPARTURE_LEDGER -> KeyGenerator.ledger(false, s.store().api().nextId("SCL_SITE_LEDGER_SEQ"));
        };
      } catch (NullPointerException | IllegalArgumentException ex) {
        String detail = ex instanceof IllegalArgumentException ? ex.getMessage()
            : "Scale date, scale site / weigh slip number or primary scaler and return number are required";
        throw new WorkflowValidationException("ddn", detail);
      }
    }
  }

  /** P553/P603/P653/P703/P753 Save, incl. log details P558/P658/P758 and defects P559/P659. */
  @Component
  public static class Save extends Base {
    public Save(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.save";
    }

    @Override
    public String legacy() {
      return "P553/P603/P653/P703/P753/P558/P559/P658/P659/P758 Save → WorkflowManagerBean.save";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.SAVE, body);
      if (!s.authority().isEditable(doc, s.caller())) {
        // DetailScaleDocumentHelper.revertIfNotEditable → viewNotAuth
        throw new AccessDeniedException("hbs.no_access_right: you are not authorized to update this scale return");
      }
      s.applyEdits(doc, body);
      s.run(() -> s.workflow().save(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Release (P553/P603/P653/P703 Release, P554 "Release of RCP/CWC" confirmation). */
  @Component
  public static class Release extends Base {
    public Release(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.release";
    }

    @Override
    public String legacy() {
      return "Release → WorkflowManagerBean.release";
    }

    protected Button button() {
      return Button.RELEASE;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(button(), body);
      if (s.applyEdits(doc, body) && !s.authority().isEditable(doc, s.caller())) {
        throw new AccessDeniedException("hbs.no_access_right: you are not authorized to update this scale return");
      }
      s.run(() -> s.workflow().release(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Re-Edit (BT-5790): {@code LogTallyActionHandler} REEDIT → {@code helper.release}. */
  @Component
  public static class ReEdit extends Release {
    public ReEdit(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.reEdit";
    }

    @Override
    public String legacy() {
      return "Re-Edit → WorkflowManagerBean.release";
    }

    @Override
    protected Button button() {
      return Button.REEDIT;
    }
  }

  /** Hold → a new HLD version. */
  @Component
  public static class Hold extends Base {
    public Hold(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.hold";
    }

    @Override
    public String legacy() {
      return "Hold → WorkflowManagerBean.hold";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.HOLD, body);
      DetailDocument[] held = new DetailDocument[1];
      s.run(() -> held[0] = s.workflow().hold(doc, s.user()));
      return s.result(held[0]);
    }
  }

  /** Discard (P554 confirmation). */
  @Component
  public static class Discard extends Base {
    public Discard(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.discard";
    }

    @Override
    public String legacy() {
      return "P554 Discard → WorkflowManagerBean.discard";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.DISCARD, body);
      s.run(() -> s.workflow().discard(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Cancel (P554 confirmation): ISS → AWP, document CIP. */
  @Component
  public static class Cancel extends Base {
    public Cancel(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.cancel";
    }

    @Override
    public String legacy() {
      return "P554 Cancel → WorkflowManagerBean.cancel";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.CANCEL, body);
      s.run(() -> s.workflow().cancel(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Cancel and Replace with Changes / Replace with Changes → a new INC version. */
  @Component
  public static class ReplaceWithChanges extends Base {
    private final String id;
    private final Button button;

    @org.springframework.beans.factory.annotation.Autowired
    public ReplaceWithChanges(DetailCommandSupport s) {
      this(s, "detailReturns.cancelReplaceChanges", Button.CANCEL_REPLACE);
    }

    ReplaceWithChanges(DetailCommandSupport s, String id, Button button) {
      super(s);
      this.id = id;
      this.button = button;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String legacy() {
      return "P554 " + (button == Button.REPLACE ? "Replace" : "Cancel and Replace") + " with Changes"
          + " → WorkflowManagerBean.replaceWithChanges";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(button, body);
      DetailDocument[] next = new DetailDocument[1];
      s.run(() -> next[0] = s.workflow().replaceWithChanges(doc, s.user()));
      return s.result(next[0]);
    }
  }

  /** Replace with Changes (on a cancelled version). */
  @Component
  public static class ReplaceChanges extends ReplaceWithChanges {
    public ReplaceChanges(DetailCommandSupport s) {
      super(s, "detailReturns.replaceChanges", Button.REPLACE);
    }
  }

  /** Replace without Changes (on a cancelled version); "Cancel and Replace without Changes" was removed (#830). */
  @Component
  public static class ReplaceNoChanges extends Base {
    public ReplaceNoChanges(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.replaceNoChanges";
    }

    @Override
    public String legacy() {
      return "P554 Replace without Changes → WorkflowManagerBean.replaceWithNoChanges";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.REPLACE, body);
      s.run(() -> s.workflow().replaceWithNoChanges(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Approve Change (HBS_INV_CORR_APP with district authority). */
  @Component
  public static class Approve extends Base {
    public Approve(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.approve";
    }

    @Override
    public String legacy() {
      return "P554 Approve Change → WorkflowManagerBean.approve";
    }

    @Override
    public Capability capability() {
      return Capability.DETAIL_RETURN_APPROVE;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.APPROVE_CHANGE, body);
      s.run(() -> s.workflow().approve(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Reject Change. */
  @Component
  public static class Reject extends Base {
    public Reject(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.reject";
    }

    @Override
    public String legacy() {
      return "P554 Reject Change → WorkflowManagerBean.reject";
    }

    @Override
    public Capability capability() {
      return Capability.DETAIL_RETURN_APPROVE;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.APPROVE_CHANGE, body);
      s.run(() -> s.workflow().reject(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Clear Digital Signature Failure (HBS_SCALE_ADMIN with site authority). */
  @Component
  public static class ClearSignatureFailure extends Base {
    public ClearSignatureFailure(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.clearSignatureFailure";
    }

    @Override
    public String legacy() {
      return "P554/P560 Clear Digital Signature Failure → WorkflowManagerBean.clearSignatureFailure";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.CLEAR_DIGITAL_SIGNATURE_FAILURE, body);
      s.run(() -> s.workflow().clearSignatureFailure(doc, s.user()));
      return s.result(doc);
    }
  }

  /** Change Event Type (P562/P554A, P612/P604A, P662/P654A): clear the fields, then Save. */
  @Component
  public static class ChangeEvent extends Base {
    public ChangeEvent(DetailCommandSupport s) {
      super(s);
    }

    @Override
    public String id() {
      return "detailReturns.changeEvent";
    }

    @Override
    public String legacy() {
      return "P554A/P604A/P654A Change Event Type → WorkflowManagerBean.save";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      DetailDocument doc = loadFor(Button.RELEASE, body); // shown inside the Release button block of Buttons.jsp
      String event = DetailCommandSupport.required(body, "scaleEventTypeCode").toUpperCase();
      EventChange.apply(doc, event);
      DetailPayload.defaultCheckboxes(doc);
      s.run(() -> s.workflow().save(doc, s.user()));
      return s.result(doc);
    }
  }
}
