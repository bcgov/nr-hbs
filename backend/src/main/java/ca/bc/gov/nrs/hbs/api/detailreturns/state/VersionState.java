package ca.bc.gov.nrs.hbs.api.detailreturns.state;

import static ca.bc.gov.nrs.hbs.api.detailreturns.state.Status.Version.*;

import java.util.Map;

/**
 * The version half of the detail-document state machine: a
 * {@code SCL_RTN_VERSION_STATE_CODE} plus the active flag. Ported line for
 * line from {@code $E/dac/detail/control/VersionState.java}; every transition
 * keeps the legacy guard and its {@link IllegalStateException} message
 * ("hold(): Illegal transition from held (HLD) active"), so a workflow that
 * reaches an illegal transition fails the same way the legacy EJB did.
 *
 * <p>The original value/active pair is kept so the version write can audit the
 * change (legacy {@code DetailScaleDocumentVersion.getStateChangeEdits}).
 */
public final class VersionState {

  private static final Map<String, String> STRINGS = Map.ofEntries(
      Map.entry(ACCEPTED, "accepted (ACC)"),
      Map.entry(AWAITING_APPROVAL, "awaiting approval (AWP)"),
      Map.entry(CANCELLED_AND_INVOICED, "cancelled (CAN)"),
      Map.entry(DISCARDED, "discarded (DEL)"),
      Map.entry(SIGNATURE_FAILED, "signature failed (DSF)"),
      Map.entry(ERROR, "in error (ERR)"),
      Map.entry(FAILED, "failed (FAL)"),
      Map.entry(HELD, "held (HLD)"),
      Map.entry(INCOMPLETE, "incomplete (INC)"),
      Map.entry(LOCKED, "locked (LCK)"),
      Map.entry(INVOICED, "issued (ISS)"),
      Map.entry(CORRECTION_PENDING, "correction pending (PND)"),
      Map.entry(REPLACEMENT_VERSION_PENDING, "replacement pending (PNV)"),
      Map.entry(READY, "ready (RDY)"),
      Map.entry(SUPERCEDED, "superceded (SPR)"),
      Map.entry(NOT_TO_BE_BILLED, "not to be issued (NTI)"),
      Map.entry(INVALID_STATE, "invalid state (INV)"));

  private String value;
  private boolean active;
  private String originalValue;
  private boolean originalActive;

  public VersionState(String value, boolean active) {
    if (value == null || !STRINGS.containsKey(value)) {
      throw new IllegalArgumentException("Unknown version state value " + value);
    }
    this.value = value;
    this.active = active;
    this.originalValue = value;
    this.originalActive = active;
  }

  /** {@code VersionState.getInitialOnlineState}: a version keyed in the UI. */
  public static VersionState initialOnline() {
    return new VersionState(INCOMPLETE, true);
  }

  public void resetOriginalValues() {
    originalValue = value;
    originalActive = active;
  }

  public String value() {
    return value;
  }

  public boolean active() {
    return active;
  }

  public String originalValue() {
    return originalValue;
  }

  public boolean originalActive() {
    return originalActive;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isInactive() {
    return !active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  private IllegalStateException illegal(String method) {
    return new IllegalStateException(method + "(): Illegal transition from " + this);
  }

  public void valid() {
    if (!isReleaseAllowed()) throw illegal("valid");
    value = READY;
    active = true;
  }

  public void invalid() {
    if (!isReleaseAllowed()) throw illegal("invalid");
    value = ERROR;
    active = true;
  }

  public void signatureFailed() {
    if (!isSignatureFailureAllowed()) throw illegal("signatureFailed");
    value = SIGNATURE_FAILED;
    active = true;
  }

  public void hold() {
    if (!isHoldAllowed()) throw illegal("hold");
    value = HELD;
    active = true;
  }

  public void supercede() {
    if (!isSupercedeAllowed()) throw illegal("supercede");
    value = SUPERCEDED;
    active = false;
  }

  public void discard() {
    if (isDiscarded()) {
      active = false;
    } else if (isDiscardAllowed()) {
      value = DISCARDED;
      active = false;
    } else {
      throw illegal("discard");
    }
  }

  /** Legacy keeps the discarded original active (the document has nothing else). */
  public void discardOriginalInProgress() {
    if (!isDiscardAllowed()) throw illegal("discard");
    value = DISCARDED;
    active = true;
  }

  public void cancelRequested() {
    if (!(isCancelAllowed() && isActive())) throw illegal("cancelRequested");
    value = AWAITING_APPROVAL;
  }

  public void cancelApproved() {
    if (!(isAwaitingApproval() && isActive())) throw illegal("cancelApproved");
    value = READY;
  }

  public void cancelRejected() {
    // legacy message names cancelApproved() here (copy/paste in VersionState.cancelRejected)
    if (!(isAwaitingApproval() && isActive())) throw illegal("cancelApproved");
    value = INVOICED;
  }

  public void cancelComplete() {
    if (!(isLocked() && isActive())) throw illegal("cancelComplete");
    value = CANCELLED_AND_INVOICED;
  }

  public void inactivateCancel() {
    if (!(isCancelled() && isActive())) throw illegal("cancelComplete");
    active = false;
  }

  public void inactivateDiscarded() {
    if (!isDiscarded()) throw illegal("inactivateDiscarded");
    active = false;
  }

  public void cancelAndReplaceWithNoChangesRequested() {
    if (!(isInvoiced() && isActive())) throw illegal("cancelAndReplaceWithNoChangesRequested");
    value = AWAITING_APPROVAL;
  }

  public void cancelAndReplaceWithNoChangesApproved() {
    if (!(isAwaitingApproval() && isActive())) throw illegal("cancelAndReplaceWithNoChangesApproved");
    value = READY;
  }

  public void cancelAndReplaceWithNoChangesRejected() {
    if (!(isAwaitingApproval() && isActive())) throw illegal("cancelAndReplaceWithNoChangesRejected");
    value = INVOICED;
  }

  public void cancelAndReplaceWithChangesRequested() {
    if ((isInvoiced() || isCancelled()) && isActive()) { // BT 6663
      active = false;
    } else if (isCancelAndReplaceWithChangedAllowed() && isInactive()) {
      value = AWAITING_APPROVAL;
      active = true;
    } else if (isIncomplete() && isActive()) {
      value = AWAITING_APPROVAL;
    } else {
      throw illegal("cancelAndReplaceWithChangesRequested");
    }
  }

  public void cancelAndReplaceWithChangesApproved() {
    if (isAwaitingApproval() && isActive()) {
      value = CORRECTION_PENDING;
      active = false;
    } else if (isInvoiced() && isInactive()) {
      value = READY;
      active = true;
    } else {
      throw illegal("cancelAndReplaceWithChangesApproved");
    }
  }

  public void cancelAndReplaceWithChangesRejected() {
    if (isAwaitingApproval() && isActive()) {
      value = DISCARDED;
      active = false;
    } else if (isInvoiced() && isInactive()) {
      active = true;
    } else {
      throw illegal("cancelAndReplaceWithChangesRejected");
    }
  }

  public void replaceWithNoChangesRequested() {
    if (!(isReplaceWithNoChangedAllowed() && isActive())) throw illegal("replaceWithNoChangesRequested");
    value = AWAITING_APPROVAL;
  }

  public void replaceWithNoChangesApproved() {
    if (!(isAwaitingApproval() && isActive())) throw illegal("replaceWithNoChangesApproved");
    value = READY;
  }

  public void replaceWithNoChangesRejected() {
    if (!(isAwaitingApproval() && isActive())) throw illegal("replaceWithNoChangesRejected");
    value = CANCELLED_AND_INVOICED;
  }

  /** RNP with ERR/RDY/NTI held: the version is cancelled instead (BT 7867). */
  public void replaceWithNoChangesHeld() {
    if (!(isReady() || isError() || isNotToBeBilled())) throw illegal("replaceWithNoChangesHeld");
    value = CANCELLED_AND_INVOICED;
    active = false;
  }

  public void extend() {
    if (!isReady()) throw illegal("extend");
    value = LOCKED;
  }

  public void replaceWithChangesRequested() {
    if (isCancelled() && isActive()) {
      value = CANCELLED_AND_INVOICED;
      active = false;
    } else if (isReplaceWithChangedAllowed()) {
      value = AWAITING_APPROVAL;
      active = true;
    } else if ((isHeld() || isError()) && isActive()) {
      value = AWAITING_APPROVAL;
    } else {
      throw illegal("replaceWithChangesRequested");
    }
  }

  public void defer() {
    if (!(isAccepted() && isInactive())) throw illegal("defer");
    value = REPLACEMENT_VERSION_PENDING;
  }

  public void replaceWithChangesApproved() {
    if (!(isAwaitingApproval() && isActive())) throw illegal("replaceWithChangesApproved");
    value = CORRECTION_PENDING;
  }

  public void replaceWithChangesRejected() {
    if (isAwaitingApproval() && isActive()) {
      value = DISCARDED;
      active = false;
    } else if (isCancelled() && isInactive()) {
      active = true;
    } else if (isSuperceded()) {
      value = AWAITING_APPROVAL;
      active = true;
    } else {
      throw illegal("replaceWithChangesRejected");
    }
  }

  public void replaceOriginalInProgress() {
    if (!isReplacment()) throw illegal("replaceOriginalInProgress");
    value = READY;
    active = true;
  }

  public void setToNonBillable() {
    if (isSetToNonBillableAllowed() && isActive()) {
      value = NOT_TO_BE_BILLED;
    } else if (isAccepted() && isInactive()) {
      value = NOT_TO_BE_BILLED;
      active = true;
    } else {
      throw illegal("setToNonBillable");
    }
  }

  // ── guards (VersionState.is*Allowed) ─────────────────────────────

  public boolean isHoldAllowed() {
    return isReady() || isError() || isFailed();
  }

  public boolean isSignatureFailureAllowed() {
    return isAccepted() || isSignatureFailed();
  }

  public boolean isSupercedeAllowed() {
    return isAccepted() || isReady() || isError() || isHeld() || isSignatureFailed() || isReplacment()
        || isAwaitingApproval() || isNotToBeBilled() || isIncomplete();
  }

  /** TATA BT-5790: PNV may be released too. */
  public boolean isReleaseAllowed() {
    return isIncomplete() || isAccepted() || isError() || isReady() || isSignatureFailed() || isHeld()
        || isCorrection() || isNotToBeBilled() || isReplacment();
  }

  public boolean isDiscardAllowed() {
    return isReady() || isError() || isHeld() || isFailed() || isSignatureFailed() || isAwaitingApproval()
        || isAccepted() || isIncomplete();
  }

  /** BT 7258. */
  public boolean isSetToNonBillableAllowed() {
    return isAccepted() || isReady() || isError() || isHeld() || isSignatureFailed() || isAwaitingApproval();
  }

  public boolean isCancelAllowed() {
    return isInvoiced();
  }

  public boolean isReplaceWithChangedAllowed() {
    return isAccepted() || isReplacment() || isIncomplete();
  }

  public boolean isCancelAndReplaceWithChangedAllowed() {
    return isAccepted() || isReplacment();
  }

  public boolean isReplaceWithNoChangedAllowed() {
    return isCancelled() || isLocked();
  }

  public boolean isIncomplete() {
    return INCOMPLETE.equals(value);
  }

  public boolean isAccepted() {
    return ACCEPTED.equals(value);
  }

  public boolean isReady() {
    return READY.equals(value);
  }

  public boolean isLocked() {
    return LOCKED.equals(value);
  }

  public boolean isFailed() {
    return FAILED.equals(value);
  }

  public boolean isSuperceded() {
    return SUPERCEDED.equals(value);
  }

  /** (sic) legacy spelling. */
  public boolean isReplacment() {
    return REPLACEMENT_VERSION_PENDING.equals(value);
  }

  public boolean isCorrection() {
    return CORRECTION_PENDING.equals(value);
  }

  public boolean isInvoiced() {
    return INVOICED.equals(value);
  }

  public boolean isCancelled() {
    return CANCELLED_AND_INVOICED.equals(value);
  }

  public boolean isAwaitingApproval() {
    return AWAITING_APPROVAL.equals(value);
  }

  public boolean isError() {
    return ERROR.equals(value);
  }

  public boolean isSignatureFailed() {
    return SIGNATURE_FAILED.equals(value);
  }

  public boolean isHeld() {
    return HELD.equals(value);
  }

  public boolean isDiscarded() {
    return DISCARDED.equals(value);
  }

  public boolean isNotToBeBilled() {
    return NOT_TO_BE_BILLED.equals(value);
  }

  @Override
  public String toString() {
    return STRINGS.get(value) + " " + (active ? "active" : "inactive");
  }
}
