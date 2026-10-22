package ca.bc.gov.nrs.hbs.api.detailreturns.state;

import static ca.bc.gov.nrs.hbs.api.detailreturns.state.Status.Document.*;

import java.util.Map;

/**
 * The document half of the state machine
 * ({@code DETAIL_SCALE_DOCUMENT.SCALE_RETURN_STATE_CODE}), ported from
 * {@code $E/dac/detail/control/DocumentState.java}. Several transitions
 * depend on the state of the version it is paired with, exactly as legacy.
 * Only the transitions the online workflows use are ported; the batch-only
 * ones (originalComplete/replace…Complete after invoicing) stay in legacy B2031.
 */
public final class DocumentState {

  private static final Map<String, String> STRINGS = Map.ofEntries(
      Map.entry(ORIGINAL_IN_PROGRESS, "original in progress (OIP)"),
      Map.entry(ORIGINAL_COMPLETE, "original complete (OCM)"),
      Map.entry(CANCEL_IN_PROGRESS, "cancel in progress (CIP)"),
      Map.entry(CANCEL_COMPLETE, "cancel complete (CCM)"),
      Map.entry(CANCEL_WITH_REPLACE_NO_CHANGES, "cancel and replace with no changes in progress (CWR)"),
      Map.entry(CANCEL_WITH_REPLACE_WITH_CHANGES, "cancel and replace with changes in progress (CWC)"),
      Map.entry(REPLACE_NO_CHANGES_IN_PROGRESS, "replace with no changes in progress (RNP)"),
      Map.entry(REPLACE_WITH_CHANGES_IN_PROGRESS, "replace with changes in progress (RCP)"),
      Map.entry(REPLACE_NO_CHANGES_COMPLETE, "replace with no changes complete (RNC)"),
      Map.entry(REPLACE_WITH_CHANGES_COMPLETE, "replace with changes complete (RCC)"),
      // not in the legacy list (its constructor rejected DIS); accepted here so such a row still loads
      Map.entry(ORIGINAL_DISCARDED, "original discarded (DIS)"));

  private String value;
  private VersionState versionState;

  public DocumentState(String value, VersionState versionState) {
    if (value == null || !STRINGS.containsKey(value)) {
      throw new IllegalArgumentException("Unknown document state value " + value);
    }
    this.value = value;
    this.versionState = versionState;
  }

  public String value() {
    return value;
  }

  /** Legacy {@code setValue}: used by Discard and processReplacementVersionPending to force a state. */
  public void setValue(String value) {
    this.value = value;
  }

  public VersionState versionState() {
    return versionState;
  }

  public void setVersionState(VersionState versionState) {
    this.versionState = versionState;
  }

  private IllegalStateException illegal(String to) {
    return new IllegalStateException("Illegal transition from " + value + " to " + to);
  }

  public void originalComplete() {
    if ((isOriginalInProgress() && versionState.isInvoiced())
        || (isCancelInProgress() && versionState.isFailed())
        || (isCancelAndReplaceWithNoChangesInProgress() && versionState.isFailed())
        || (isCancelAndReplaceWithChangesInProgress() && versionState.isFailed())
        || (isCancelAndReplaceWithChangesInProgress() && versionState.isDiscarded())) {
      value = ORIGINAL_COMPLETE;
    } else {
      throw new IllegalStateException("Illegal transition from " + value + " " + versionState + " to "
          + ORIGINAL_COMPLETE);
    }
  }

  public void cancelRequested() {
    if (!isCancelAllowed()) throw illegal(CANCEL_IN_PROGRESS);
    value = CANCEL_IN_PROGRESS;
  }

  public void cancelFailed() {
    if (!isCancelInProgress()) throw illegal(ORIGINAL_COMPLETE);
    value = ORIGINAL_COMPLETE;
  }

  public void cancelComplete() {
    if ((isCancelInProgress() && versionState.isCancelled())
        || (isReplaceWithNoChangesComplete() && versionState.isFailed())
        || (isReplaceWithChangesInProgress() && versionState.isDiscarded())
        || (isReplaceWithNoChangesInProgress() && versionState.isFailed())
        || (isReplaceWithChangesInProgress() && versionState.isFailed())
        || (isCancelAndReplaceWithChangesInProgress() && versionState.isDiscarded())) {
      value = CANCEL_COMPLETE;
    } else {
      throw illegal(CANCEL_COMPLETE);
    }
  }

  public void cancelAndReplaceWithNoChangesRequested() {
    if (!isCancelAllowed()) throw illegal(CANCEL_WITH_REPLACE_NO_CHANGES);
    value = CANCEL_WITH_REPLACE_NO_CHANGES;
  }

  public void cancelAndReplaceWithNoChangesFailed() {
    if (!isCancelAndReplaceWithNoChangesInProgress()) throw illegal(ORIGINAL_COMPLETE);
    value = ORIGINAL_COMPLETE;
  }

  public void cancelAndReplaceWithChangesRequested() {
    if (!isCancelAllowed()) throw illegal(CANCEL_WITH_REPLACE_WITH_CHANGES);
    value = CANCEL_WITH_REPLACE_WITH_CHANGES;
  }

  public void cancelAndReplaceWithChangesFailed() {
    if (!isCancelAndReplaceWithChangesInProgress()) throw illegal(ORIGINAL_COMPLETE);
    value = ORIGINAL_COMPLETE;
  }

  public void replaceWithChangesRequested() {
    if (!(isCancelAndReplaceWithChangesInProgress() || isCancelComplete())) {
      throw illegal(REPLACE_WITH_CHANGES_IN_PROGRESS);
    }
    value = REPLACE_WITH_CHANGES_IN_PROGRESS;
  }

  public void replaceWithChangesFailed() {
    if (!isReplaceWithChangesInProgress()) {
      throw new IllegalStateException("Illegal transition from " + value + " to " + CANCEL_COMPLETE + " or "
          + REPLACE_WITH_CHANGES_IN_PROGRESS);
    }
    value = versionState.isAwaitingApproval() || versionState.isDiscarded()
        ? REPLACE_WITH_CHANGES_IN_PROGRESS
        : CANCEL_COMPLETE;
  }

  public void replaceWithNoChangesRequested() {
    if (!(isCancelAndReplaceWithNoChangesInProgress() || isCancelComplete())) {
      throw illegal(REPLACE_NO_CHANGES_IN_PROGRESS);
    }
    value = REPLACE_NO_CHANGES_IN_PROGRESS;
  }

  public void replaceWithNoChangesFailed() {
    if (!isReplaceWithNoChangesInProgress()) throw illegal(CANCEL_COMPLETE);
    value = CANCEL_COMPLETE;
  }

  /** RNP whose version was cancelled by Hold (BT 7867). */
  public void replaceWithChangesInProgress() {
    if (!(isReplaceWithNoChangesInProgress() && versionState.isCancelled())) {
      throw illegal(REPLACE_WITH_CHANGES_IN_PROGRESS);
    }
    value = REPLACE_WITH_CHANGES_IN_PROGRESS;
  }

  public boolean isCancelAllowed() {
    return isOriginalComplete() || isReplaceWithNoChangesComplete() || isReplaceWithChangesComplete();
  }

  public boolean isReplacementAllowed() {
    return isCancelInProgress() || isCancelAndReplaceWithNoChangesInProgress()
        || isCancelAndReplaceWithChangesInProgress() || isReplaceWithNoChangesInProgress()
        || isReplaceWithChangesInProgress();
  }

  public boolean isInvoiceComplete() {
    return isOriginalComplete() || isReplaceWithChangesComplete() || isReplaceWithNoChangesComplete();
  }

  public boolean isOriginalInProgress() {
    return ORIGINAL_IN_PROGRESS.equals(value);
  }

  public boolean isOriginalComplete() {
    return ORIGINAL_COMPLETE.equals(value);
  }

  public boolean isCancelInProgress() {
    return CANCEL_IN_PROGRESS.equals(value);
  }

  public boolean isCancelComplete() {
    return CANCEL_COMPLETE.equals(value);
  }

  public boolean isCancelAndReplaceWithNoChangesInProgress() {
    return CANCEL_WITH_REPLACE_NO_CHANGES.equals(value);
  }

  public boolean isReplaceWithNoChangesInProgress() {
    return REPLACE_NO_CHANGES_IN_PROGRESS.equals(value);
  }

  public boolean isReplaceWithNoChangesComplete() {
    return REPLACE_NO_CHANGES_COMPLETE.equals(value);
  }

  public boolean isCancelAndReplaceWithChangesInProgress() {
    return CANCEL_WITH_REPLACE_WITH_CHANGES.equals(value);
  }

  public boolean isReplaceWithChangesInProgress() {
    return REPLACE_WITH_CHANGES_IN_PROGRESS.equals(value);
  }

  public boolean isReplaceWithChangesComplete() {
    return REPLACE_WITH_CHANGES_COMPLETE.equals(value);
  }

  @Override
  public String toString() {
    return STRINGS.get(value) + " " + versionState;
  }
}
