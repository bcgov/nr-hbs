package ca.bc.gov.nrs.hbs.api.detailreturns.state;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;

/**
 * The legacy {@code StateManagerBean} and its state commands
 * ({@code $E/dac/detail/control/state/*.java}): in-memory transitions of one
 * document + version pair, no persistence. A {@link StateException} carries the
 * legacy message of the command that refused.
 */
public final class StateManager {

  private StateManager() {
  }

  /** {@code control.state.StateException}. */
  public static class StateException extends IllegalStateException {
    public StateException(String message) {
      super(message);
    }
  }

  private static VersionState v(DetailDocument d) {
    return d.version().state();
  }

  /** {@code state.Release}: only CWC/INC and RCP/INC|HLD go to AWP here. */
  public static void release(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isCancelAndReplaceWithChangesInProgress() && v(d).isIncomplete()) {
      v(d).cancelAndReplaceWithChangesRequested();
    } else if (s.isReplaceWithChangesInProgress() && (v(d).isIncomplete() || v(d).isHeld())) {
      v(d).replaceWithChangesRequested();
    } else {
      throw new StateException("Cannot release document with state " + s);
    }
  }

  /** {@code state.Hold}. */
  public static void hold(DetailDocument d) {
    if (!v(d).isActive()) {
      throw new StateException("Document version is not active.");
    }
    v(d).hold();
  }

  /** {@code state.Supercede}. */
  public static void supercede(DetailDocument d) {
    v(d).supercede();
  }

  /** {@code state.Discard}. */
  public static void discard(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isOriginalInProgress()) {
      v(d).discardOriginalInProgress();
    } else if (s.isReplaceWithChangesInProgress()) {
      v(d).discard();
      s.cancelComplete();
    } else if (s.isCancelAndReplaceWithChangesInProgress()) {
      v(d).discard();
      s.originalComplete();
    } else {
      throw new StateException("Document cannot be discarded.");
    }
  }

  /** {@code state.Cancel} (BT 7867: RNP with ERR/RDY/NTI is cancelled and becomes RCP). */
  public static void cancel(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isReplaceWithNoChangesInProgress() && (v(d).isError() || v(d).isReady() || v(d).isNotToBeBilled())) {
      v(d).replaceWithNoChangesHeld();
      s.replaceWithChangesInProgress();
    } else {
      v(d).cancelRequested();
      s.cancelRequested();
    }
  }

  /** {@code state.ReplaceWithChanges}. */
  public static void replaceWithChanges(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isCancelComplete()) {
      v(d).replaceWithChangesRequested();
      s.replaceWithChangesRequested();
    } else if (s.isInvoiceComplete()) {
      v(d).cancelAndReplaceWithChangesRequested();
      s.cancelAndReplaceWithChangesRequested();
    } else {
      throw new StateException("Attempt to perform replace with changes on a document that is not in an "
          + "'complete' state.");
    }
  }

  /** {@code state.ReplaceWithNoChanges}. */
  public static void replaceWithNoChanges(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isCancelComplete()) {
      v(d).replaceWithNoChangesRequested();
      s.replaceWithNoChangesRequested();
    } else if (s.isInvoiceComplete()) {
      v(d).cancelAndReplaceWithNoChangesRequested();
      s.cancelAndReplaceWithNoChangesRequested();
    } else {
      throw new StateException("Attempt to perform replace with no changes on a document that is not in an "
          + "'complete' state");
    }
  }

  /** {@code state.Approve}. */
  public static void approve(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isCancelInProgress()) {
      v(d).cancelApproved();
    } else if (s.isCancelAndReplaceWithNoChangesInProgress()) {
      v(d).cancelAndReplaceWithNoChangesApproved();
    } else if (s.isReplaceWithNoChangesInProgress()) {
      v(d).replaceWithNoChangesApproved();
    } else if (s.isReplaceWithChangesInProgress()) {
      v(d).replaceWithChangesApproved();
    } else if (s.isCancelAndReplaceWithChangesInProgress()) {
      v(d).cancelAndReplaceWithChangesApproved();
    } else {
      throw new StateException("Cannot approve a document with state " + s);
    }
  }

  /** {@code state.Reject}. */
  public static void reject(DetailDocument d) {
    DocumentState s = d.state();
    if (s.isCancelInProgress()) {
      v(d).cancelRejected();
      s.cancelFailed();
    } else if (s.isCancelAndReplaceWithNoChangesInProgress()) {
      v(d).cancelAndReplaceWithNoChangesRejected();
      s.cancelAndReplaceWithNoChangesFailed();
    } else if (s.isReplaceWithNoChangesInProgress()) {
      v(d).replaceWithNoChangesRejected();
      s.replaceWithNoChangesFailed();
    } else if (s.isCancelAndReplaceWithChangesInProgress()) {
      v(d).cancelAndReplaceWithChangesRejected();
      s.cancelAndReplaceWithChangesFailed();
    } else if (s.isReplaceWithChangesInProgress()) {
      v(d).replaceWithChangesRejected();
      s.replaceWithChangesFailed();
    } else if (s.isOriginalComplete()) {
      v(d).cancelAndReplaceWithChangesRejected();
    } else if (s.isCancelComplete()) {
      v(d).replaceWithChangesRejected();
    } else {
      throw new StateException("Document version is not awaiting approval or is not active.");
    }
  }

  /** {@code state.Valid}. */
  public static void valid(DetailDocument d) {
    if (d.state().isReplaceWithChangesInProgress()
        && (v(d).isError() || v(d).isIncomplete() || v(d).isHeld())) {
      v(d).replaceWithChangesRequested();
    } else {
      v(d).valid();
    }
  }

  /** {@code state.Invalid}. */
  public static void invalid(DetailDocument d) {
    v(d).invalid();
  }

  /** {@code state.NonBillable}. */
  public static void nonBillable(DetailDocument d) {
    v(d).setToNonBillable();
  }

  /** {@code state.Process}: a pending replacement (PNV) or accepted version is put in play. */
  public static void process(DetailDocument d) {
    DocumentState s = d.state();
    if (!(v(d).isAccepted() || v(d).isReplacment() || v(d).isInactive())) {
      throw new StateException("Document version must be either accepted or a replacement version pending and "
          + "must be inactive.");
    }
    if (s.isOriginalComplete() || s.isReplaceWithNoChangesComplete() || s.isReplaceWithChangesComplete()) {
      v(d).cancelAndReplaceWithChangesRequested();
      s.cancelAndReplaceWithChangesRequested();
    } else if (s.isCancelComplete()) {
      v(d).replaceWithChangesRequested();
      s.replaceWithChangesRequested();
    } else if (s.isReplaceWithChangesInProgress()) {
      v(d).replaceWithChangesRequested();
    } else if (s.isOriginalInProgress()) {
      v(d).replaceOriginalInProgress();
    } else {
      v(d).defer();
    }
  }
}
