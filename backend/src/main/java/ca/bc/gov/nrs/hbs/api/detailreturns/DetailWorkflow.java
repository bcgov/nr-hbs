package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailVersion;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.DocumentState;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.StateManager;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.Status;
import ca.bc.gov.nrs.hbs.api.detailreturns.state.VersionState;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailAuditTrail;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.DetailValidator;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.DetailValidators;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The detail-return workflows, ported from the legacy EJB tier:
 * {@code $E/dac/detail/control/workflow/*} (Add, Save, Release, Hold, Discard,
 * Cancel, ReplaceWithChanges, ReplaceWithNoChanges, Approve, Reject,
 * WorkflowDocumentFactory) and the orchestration in {@code WorkflowManagerBean}
 * (release of a held version → processHeld, reject → replacement pending
 * version, clearSignatureFailure, process).
 *
 * <p>Each method works on documents loaded in the caller's transaction and
 * writes through {@link DetailStore}; the caller (a {@code WorkflowCommand})
 * runs the whole thing in one transaction, so none of the intermediate states
 * the legacy app autocommitted between proc calls is ever visible to the
 * legacy batch (docs/coexistence.md).
 *
 * <p>{@link WorkflowException} carries the legacy {@code WorkflowException} /
 * {@code StateException} messages; {@link IllegalStateException}s raised by
 * {@link VersionState}/{@link DocumentState} keep their legacy text too.
 */
@Component
public class DetailWorkflow {

  /** {@code control.workflow.WorkflowException}. */
  public static class WorkflowException extends IllegalStateException {
    public WorkflowException(String message) {
      super(message);
    }
  }

  private final DetailStore store;
  private final DetailValidators validators;
  private final Supplier<LocalDate> today;

  @org.springframework.beans.factory.annotation.Autowired
  public DetailWorkflow(DetailStore store, DetailValidators validators) {
    this(store, validators, DetailValidator::today);
  }

  DetailWorkflow(DetailStore store, DetailValidators validators, Supplier<LocalDate> today) {
    this.store = store;
    this.validators = validators;
    this.today = today;
  }

  public DetailStore store() {
    return store;
  }

  private DetailValidator validator(DetailDocument doc) {
    return validators.of(doc, store, today.get());
  }

  /** {@code AbstractWorkflowCommand.updateDocument}: version (and lines), then the document. */
  private void update(DetailDocument doc, String user, String actionType) {
    store.store(doc, user, actionType);
  }

  // ── Add / Save ────────────────────────────────────────────────────

  /**
   * {@code workflow.Add}: run the edits, then create the document (OIP) and
   * its first version (INC, active, original). Legacy
   * {@code createAndStoreDetailScaleDocumentAndVersion} does not store the edit
   * errors found here — they appear after the first Save or Release.
   */
  public void add(DetailDocument doc, String user) {
    doc.errors().clear();
    validator(doc).copyInputFieldsAndValidate();
    // removeErrors on a version that is not stored yet: nothing to remove
    store.createDocumentAndVersion(doc, user);
  }

  /**
   * {@code workflow.Save}: edits (incl. deck id and event date), tally total,
   * then the version, its lines and its errors. The document row is not written.
   */
  public void save(DetailDocument doc, String user) {
    doc.errors().clear();
    DetailValidator validator = validator(doc);
    validator.copyInputFieldsAndValidate();
    validator.fieldDeckIdValidate();
    validator.validateEventOrScaleDate(); // BT 10747
    totalNetVolume(doc);
    store.removeErrors(doc);
    store.storeVersion(doc, user, DetailAuditTrail.UPDATE, true);
    store.saveErrors(doc);
  }

  /** {@code LogTallyCommon.calculateTotalNetVolume} into NET_VOLUME. */
  static void totalNetVolume(DetailDocument doc) {
    if (doc.isTally()) {
      doc.version().row().set("net_volume", LogVolume.decimal(LogVolume.totalNetVolume(doc.version().logs())));
    }
  }

  // ── Release (WorkflowManagerBean.release / clearSignatureFailure) ─

  /**
   * {@code WorkflowManagerBean.release}: refuses a DSF version, runs
   * {@link #releaseCommand}, then for a version that was held hands any
   * replacement pending version on ({@code processHeld}).
   */
  public void release(DetailDocument doc, String user) {
    if (doc.version().state().isSignatureFailed()) {
      throw new WorkflowException("Cannot release a document version that has a state of 'digital signature "
          + "failure'.  Use clearSignatureFailure() instead.");
    }
    boolean held = doc.version().state().isHeld();
    releaseCommand(doc, user);
    if (held) {
      processReplacementVersionPending(doc, user);
    }
  }

  /** {@code WorkflowManagerBean.clearSignatureFailure}: release a DSF version. */
  public void clearSignatureFailure(DetailDocument doc, String user) {
    if (!doc.version().state().isSignatureFailed()) {
      throw new WorkflowException("Cannot clear a signature failure for a document version that does not have a "
          + "state of 'digital signature failure'.  Use release() instead.");
    }
    releaseCommand(doc, user);
  }

  /** {@code workflow.Release.execute}: the branch order is the legacy one (the RCP/PND branch is unreachable). */
  void releaseCommand(DetailDocument doc, String user) {
    DocumentState s = doc.state();
    VersionState v = doc.version().state();
    if (s.isCancelAndReplaceWithChangesInProgress() && v.isIncomplete()) {
      StateManager.release(doc);
      update(doc, user, DetailAuditTrail.RELEASE);
    } else if (s.isCancelAndReplaceWithChangesInProgress() && v.isReleaseAllowed()) {
      if (!v.isReady()) {
        validateAndChangeState(doc, user);
      }
    } else if (s.isOriginalInProgress() && v.isReleaseAllowed()) {
      String initial = v.value();
      validateAndChangeState(doc, user);
      if (!initial.equals(doc.version().state().value())) {
        update(doc, user, DetailAuditTrail.RELEASE);
      }
    } else if (s.isReplaceWithChangesInProgress() && v.isReleaseAllowed()) {
      validateAndChangeState(doc, user);
    } else if (s.isReplaceWithChangesInProgress() && v.isCorrection()) {
      String initial = v.value();
      validateAndChangeState(doc, user);
      if (!initial.equals(doc.version().state().value())) {
        doc.version().row().set("dtl_doc_seq", store.api().nextId("LOG_TALLY_SEQ")); // LogTallyHelper.getId
        update(doc, user, DetailAuditTrail.RELEASE);
      }
    } else if (s.isReplaceWithNoChangesInProgress() && v.isReleaseAllowed()) {
      validateAndChangeState(doc, user);
    } else if (s.isCancelAndReplaceWithNoChangesInProgress() && v.isReleaseAllowed()) {
      if (!v.isReady()) {
        validateAndChangeState(doc, user);
      }
    } else if (s.isCancelInProgress() && v.isReleaseAllowed()) {
      if (!v.isReady()) {
        validateAndChangeState(doc, user);
      }
    } else if (v.isNotToBeBilled() && doc.type() == ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType.WEIGH_SLIP) {
      if (!v.isReady()) {
        validateAndChangeState(doc, user);
      }
    } else if (v.isLocked()) {
      validateAndSetTrue(doc, user);
    } else {
      throw new WorkflowException("Cannot release document with state " + s);
    }
  }

  /**
   * {@code Release.validateAndChangeState}: re-run the edits (not on an ACC
   * version), total the tally, then RDY (or NTI when not billable, or AWP under
   * RCP) when clean, ERR otherwise; store the version and its errors.
   */
  private void validateAndChangeState(DetailDocument doc, String user) {
    DetailValidator validator = validator(doc);
    if (!doc.version().state().isAccepted()) {
      doc.errors().clear();
      validator.copyInputFieldsAndValidate();
      validator.fieldDeckIdValidate(); // 10013
      validator.validateEventOrScaleDate(); // BT 10747
    }
    totalNetVolume(doc);
    if (doc.errors().isEmpty()) {
      StateManager.valid(doc);
      if (!validator.isBillableScaleReturn()) {
        StateManager.nonBillable(doc);
      }
    } else {
      StateManager.invalid(doc);
    }
    store.removeErrors(doc);
    store.storeVersion(doc, user, DetailAuditTrail.RELEASE, true);
    store.saveErrors(doc);
  }

  /** {@code Release.validateAndSetTrue}: a locked check scale marks its original replaced (NTI). */
  private void validateAndSetTrue(DetailDocument doc, String user) {
    if (DetailValidator.CHECK_SCALE.equals(doc.version().row().str("scale_event_type_code"))) {
      setOriginalToNonBillable(doc, user);
    }
  }

  /** {@code Release.setOriginalToNonBillable}. */
  private void setOriginalToNonBillable(DetailDocument doc, String user) {
    Row v = doc.version().row();
    String originalDdn = v.str("original_ddn");
    DetailDocument original;
    if (originalDdn != null && !originalDdn.isEmpty()) { // StringUtil.empty: no trim
      original = store.findActive(originalDdn)
          .orElseThrow(() -> new WorkflowException("Previously found original not found."));
    } else {
      throw new WorkflowException("Previously found original not found."); // by licence + return: B1021 territory
    }
    if (!original.version().state().isNotToBeBilled()) {
      original.version().state().setToNonBillable();
    }
    original.version().row().setYes("check_replaces_original_ind", true);
    store.storeVersion(original, user, DetailAuditTrail.UPDATE, true);
  }

  // ── Hold ──────────────────────────────────────────────────────────

  /**
   * {@code workflow.Hold}: the current version is superseded (or, under RNP
   * with ERR/RDY/NTI, cancelled — BT 7867) and a copy becomes a new HLD version,
   * which is re-edited and stored with its errors.
   *
   * @return the new held version
   */
  public DetailDocument hold(DetailDocument doc, String user) {
    VersionState v = doc.version().state();
    if (doc.state().isReplaceWithNoChangesInProgress() && (v.isError() || v.isReady() || v.isNotToBeBilled())) {
      StateManager.cancel(doc);
    } else {
      StateManager.supercede(doc);
    }
    store.removeErrors(doc); // BT 9717
    update(doc, user, DetailAuditTrail.HOLD);
    DetailDocument held = newVersion(doc, user, Status.Version.HELD);
    DetailValidator validator = validator(held);
    validator.copyInputFieldsAndValidate();
    validator.validateEventOrScaleDate();
    store.removeErrors(held);
    store.storeVersion(held, user, DetailAuditTrail.HOLD, true);
    store.saveErrors(held);
    return held;
  }

  // ── Discard ───────────────────────────────────────────────────────

  /**
   * {@code workflow.Discard}: discard the version; under OIP a pending
   * replacement (PNV) is processed and released; under CWC the issued version
   * is re-activated; under RCP the pending replacement is released, or the
   * latest cancelled version re-activated.
   */
  public void discard(DetailDocument doc, String user) {
    String initial = doc.state().value();
    String ddn = doc.ddn();
    DetailDocument pending = replacementPending(ddn);
    StateManager.discard(doc);
    store.removeErrors(doc); // BT 9717
    if (pending != null) {
      doc.state().setValue(initial);
    }
    DetailDocument discarded = doc;
    if (Status.Document.ORIGINAL_IN_PROGRESS.equals(initial)) {
      if (pending != null) {
        discarded.version().state().inactivateDiscarded();
        update(discarded, user, DetailAuditTrail.DELETE);
        StateManager.process(pending);
        releaseCommand(pending, user);
      }
    } else if (Status.Document.CANCEL_WITH_REPLACE_WITH_CHANGES.equals(initial)) {
      update(discarded, user, DetailAuditTrail.DELETE);
      DetailDocument invoiced = store.findSingleByStatus(ddn, Status.Version.INVOICED)
          .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
      StateManager.reject(invoiced);
      update(invoiced, user, DetailAuditTrail.DELETE);
      discarded = reload(discarded);
    } else if (Status.Document.REPLACE_WITH_CHANGES_IN_PROGRESS.equals(initial)) {
      update(doc, user, DetailAuditTrail.DELETE);
      if (pending != null) {
        releaseCommand(pending, user);
      } else {
        DetailDocument cancelled = store.findLatestByStatus(ddn, Status.Version.CANCELLED_AND_INVOICED)
            .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
        StateManager.reject(cancelled);
        update(cancelled, user, DetailAuditTrail.DELETE);
      }
      discarded = reload(discarded);
    }
    update(discarded, user, DetailAuditTrail.DELETE);
  }

  // ── Cancel / Replace ──────────────────────────────────────────────

  /** {@code workflow.Cancel}: ISS → AWP, document → CIP. */
  public void cancel(DetailDocument doc, String user) {
    StateManager.cancel(doc);
    update(doc, user, DetailAuditTrail.CANCEL);
  }

  /**
   * {@code workflow.ReplaceWithChanges} (Cancel and Replace with Changes /
   * Replace with Changes): the issued (or cancelled) version is put in play
   * and a copy becomes a new INC version for editing.
   *
   * @return the new incomplete version
   */
  public DetailDocument replaceWithChanges(DetailDocument doc, String user) {
    StateManager.replaceWithChanges(doc);
    update(doc, user, DetailAuditTrail.REQUEST);
    DetailDocument replacement = newVersion(doc, user, Status.Version.INCOMPLETE);
    update(replacement, user, DetailAuditTrail.REQUEST);
    return replacement;
  }

  /** {@code workflow.ReplaceWithNoChanges}: ISS/CAN → AWP, document → CWR / RNP. */
  public void replaceWithNoChanges(DetailDocument doc, String user) {
    stampDocument(doc, user);
    StateManager.replaceWithNoChanges(doc);
    update(doc, user, DetailAuditTrail.REQUEST);
  }

  // ── Approve / Reject ──────────────────────────────────────────────

  /**
   * {@code workflow.Approve}: approve the AWP version; under CWC the issued
   * version becomes RDY too; under RCP/CWR/RNP the approved version is released.
   */
  public void approve(DetailDocument doc, String user) {
    String initial = doc.state().value();
    stampDocument(doc, user);
    StateManager.approve(doc);
    if (Status.Document.CANCEL_WITH_REPLACE_WITH_CHANGES.equals(initial)) {
      DetailDocument invoiced = store.findSingleByStatus(doc.ddn(), Status.Version.INVOICED)
          .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
      StateManager.approve(invoiced);
      update(invoiced, user, DetailAuditTrail.APPROVE);
    } else if (Status.Document.REPLACE_WITH_CHANGES_IN_PROGRESS.equals(initial)
        || Status.Document.CANCEL_WITH_REPLACE_NO_CHANGES.equals(initial)
        || Status.Document.REPLACE_NO_CHANGES_IN_PROGRESS.equals(initial)) {
      releaseCommand(doc, user);
    }
    update(doc, user, DetailAuditTrail.APPROVE);
  }

  /**
   * {@code WorkflowManagerBean.reject} → {@code workflow.Reject}: reject the AWP
   * version; under CWC re-activate the issued version; under RCP re-activate the
   * latest superseded (else cancelled) version; then process a replacement
   * pending version, if any.
   */
  public void reject(DetailDocument doc, String user) {
    String initial = doc.state().value();
    String ddn = doc.ddn();
    StateManager.reject(doc);
    update(doc, user, DetailAuditTrail.REJECT);
    if (Status.Document.CANCEL_WITH_REPLACE_WITH_CHANGES.equals(initial)) {
      DetailDocument invoiced = store.findSingleByStatus(ddn, Status.Version.INVOICED)
          .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
      StateManager.reject(invoiced);
      update(invoiced, user, DetailAuditTrail.REJECT);
    } else if (Status.Document.REPLACE_WITH_CHANGES_IN_PROGRESS.equals(initial)) {
      if (replacementPending(ddn) != null) {
        // legacy Reject left detailDocument null and updateDocument(null) failed (NPE → "Unable to perform
        // workflow operation."); the first update had already autocommitted. Here the whole reject rolls back.
        throw new WorkflowException("Unable to perform workflow operation.");
      }
      DetailDocument previous = store.findLatestByStatus(ddn, Status.Version.SUPERCEDED)
          .or(() -> store.findLatestByStatus(ddn, Status.Version.CANCELLED_AND_INVOICED))
          .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
      StateManager.reject(previous);
      update(previous, user, DetailAuditTrail.REJECT);
    }
    DetailDocument pending = replacementPending(ddn);
    if (pending != null) {
      process(pending, user);
    }
  }

  // ── WorkflowManagerBean helpers ───────────────────────────────────

  /**
   * {@code WorkflowManagerBean.processReplacementVersionPending(invoiceVersion, user)}
   * (via {@code processHeld}): only when a PNV exists, retire the released
   * version (RCP: supersede the active version; OIP: supersede it; RNP: lock,
   * cancel and deactivate it and move the document to RCP; otherwise put it
   * back in play) and process the pending version.
   */
  void processReplacementVersionPending(DetailDocument released, String user) {
    DetailDocument pending = replacementPending(released.ddn());
    if (pending == null) {
      return;
    }
    if (released.state().isReplaceWithChangesInProgress()) {
      DetailDocument active = store.findActive(released.ddn())
          .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
      active.version().state().supercede();
      store.removeErrors(active); // BT9717
      update(active, user, DetailAuditTrail.UPDATE);
    } else if (released.state().isOriginalInProgress()) {
      released.version().state().supercede();
      store.removeErrors(released); // BT9717
      update(released, user, DetailAuditTrail.UPDATE);
    } else if (released.state().isReplaceWithNoChangesInProgress()) {
      released.state().setValue(Status.Document.REPLACE_WITH_CHANGES_IN_PROGRESS);
      released.version().state().extend();
      released.version().state().cancelComplete();
      released.version().state().inactivateCancel();
      update(released, user, DetailAuditTrail.UPDATE);
      pending.state().setValue(released.state().value());
      pending.markPersisted(); // syncState
    } else {
      released.version().state().cancelAndReplaceWithChangesRequested();
      update(released, user, DetailAuditTrail.UPDATE);
    }
    process(pending, user);
  }

  /**
   * The second half of {@code WorkflowManagerBean.reset} — a detail document of a
   * failed generated summary was just reset (by the summary-return discard,
   * {@code summaryreturns.generated.DetailDocumentReset}) — ported from
   * {@code processReplacementVersionPending(ddn, detailDoc, user)}: when the
   * document has a replacement-pending (PNV) version, the reset version is
   * superseded if the document is OIP or RWC and the version is active and
   * RDY or AWP; then the pending version is processed ({@link #process}: AWP,
   * or the full release). No PNV: nothing happens (legacy
   * {@code DetailScaleDocumentException} was "normal").
   *
   * @param ddn the reset document
   * @param version the version that was reset
   */
  public void processReplacementVersionPendingAfterReset(String ddn, long version, String user) {
    DetailDocument pending = replacementPending(ddn);
    if (pending == null) {
      return;
    }
    DetailDocument reset = store.find(ddn, version)
        .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
    if (reset.state().isReplaceWithChangesInProgress() || reset.state().isOriginalInProgress()) {
      VersionState v = reset.version().state();
      if (v.isActive() && (v.isReady() || v.isAwaitingApproval())) {
        v.supercede();
        store.removeErrors(reset); // BT 9717
        update(reset, user, DetailAuditTrail.UPDATE);
      }
    }
    process(pending, user);
  }

  /**
   * {@code WorkflowManagerBean.process}: a pending (PNV) version is put in play;
   * when it goes to AWP the previous issued version is deactivated, otherwise
   * it is released.
   */
  void process(DetailDocument pending, String user) {
    StateManager.process(pending);
    if (pending.version().state().isAwaitingApproval()) {
      update(pending, user, DetailAuditTrail.UPDATE);
      DetailDocument invoiced = store.findPreviousInvoiced(pending.ddn(), pending.version().number())
          .orElseThrow(() -> new WorkflowException("Could not find previous invoiced version for "
              + pending.ddn() + "," + pending.version().number() + "."));
      invoiced.version().state().setActive(false);
      update(invoiced, user, DetailAuditTrail.UPDATE);
    } else {
      release(pending, user);
    }
  }

  /** {@code findDocumentAndReplacementPendingVersion}, none → null; several → "More than one PNV for ddn". */
  private DetailDocument replacementPending(String ddn) {
    try {
      return store.findSingleByStatus(ddn, Status.Version.REPLACEMENT_VERSION_PENDING).orElse(null);
    } catch (IllegalStateException ex) {
      throw new WorkflowException("More than one PNV for ddn " + ddn);
    }
  }

  private DetailDocument reload(DetailDocument doc) {
    return store.find(doc.ddn(), doc.version().number())
        .orElseThrow(() -> new WorkflowException("Unable to perform workflow operation."));
  }

  /** The document's update user/time, as Approve / ReplaceWithNoChanges set them. */
  private void stampDocument(DetailDocument doc, String user) {
    doc.row().set("update_userid", user);
    doc.row().set("update_timestamp", store.audit().now());
  }

  /**
   * {@code WorkflowDocumentFactory.newVersion}: a copy of the document's
   * version as the next version number, in the given state, active, not the
   * original, with a new DTL_DOC_SEQ (BT 5298) and fresh log/defect ids; stored
   * with {@code updateDetailScaleDocumentAndInsertVersion}.
   */
  DetailDocument newVersion(DetailDocument doc, String user, String versionStatus) {
    long dv = store.nextVersion(doc.type(), doc.ddn());
    Timestamp now = store.audit().now();
    VersionState state = new VersionState(versionStatus, true);
    Row row = doc.version().row().copy()
        .set("version", dv)
        .setYes("original_version_ind", false)
        .set("dtl_doc_seq", store.api().nextId("DETAIL_SCALE_DOCUMENT_SEQ"))
        .set("entry_userid", user).set("entry_timestamp", now)
        .set("update_userid", user).set("update_timestamp", now);
    DetailVersion version = new DetailVersion(row, state, false);
    for (LogLine line : doc.version().logs()) {
      LogLine copy = line.copyForNewVersion();
      copy.row().set("version", dv);
      version.logs().add(copy);
    }
    Row docRow = doc.row().copy().set("update_userid", user).set("update_timestamp", now);
    DetailDocument copy = new DetailDocument(doc.type(), docRow, new DocumentState(doc.state().value(), state),
        version, true);
    copy.syncSavedState(doc.savedState());
    store.insertVersion(copy, user);
    return copy;
  }

  public boolean isBillableMark(String mark) {
    return validators.isBillableMark(mark);
  }

  /** Optional helper for callers that load by key. */
  public Optional<DetailDocument> find(String ddn, long version) {
    return store.find(ddn, version);
  }
}
