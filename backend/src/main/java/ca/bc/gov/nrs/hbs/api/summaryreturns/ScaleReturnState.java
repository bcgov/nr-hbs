package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.util.HashMap;
import java.util.Map;

/**
 * The summary-return state machine: document state
 * ({@code HBS_SCALE_RETURN.SCALE_RETURN_STATE_CODE}) × version state
 * ({@code *_SCALE_SUMMARY.SCL_RTN_VERSION_STATE_CODE}) × active flag.
 *
 * <p>Port of {@code $E/dac/summary/control/ScaleReturnStateFactory} and the state
 * classes it returns ({@code ScaleReturnHeldState}, {@code …ReadyState},
 * {@code …InvoicedState}, {@code …AwaitingApprovalState}, {@code …LockedState},
 * {@code …ReleasableState}, {@code …DiscardedState}, {@code …InactiveState},
 * {@code …InvalidState}). Every transition returns the next state or throws
 * {@link IllegalStateException} where the legacy state class did ("… operation is
 * not available."). Transitions only legacy batch performs (extend, invoice,
 * reconcile) are not ported.
 */
public record ScaleReturnState(String doc, String version, boolean active) {

  // ── SCALE_RETURN_STATE_CODE (DacConstants.ScaleReturnStatus) ─────────────
  public static final String ORIGINAL_IN_PROGRESS = "OIP";
  public static final String ORIGINAL_COMPLETE = "OCM";
  public static final String ORIGINAL_DISCARDED = "DIS";
  public static final String CANCEL_IN_PROGRESS = "CIP";
  public static final String CANCEL_COMPLETE = "CCM";
  public static final String CANCEL_WITH_REPLACE_NO_CHANGES = "CWR";
  public static final String REPLACE_NO_CHANGES_IN_PROGRESS = "RNP";
  public static final String REPLACE_NO_CHANGES_COMPLETE = "RNC";
  public static final String CANCEL_WITH_REPLACE_WITH_CHANGES = "CWC";
  public static final String REPLACE_WITH_CHANGES_IN_PROGRESS = "RCP";
  public static final String REPLACE_WITH_CHANGES_COMPLETE = "RCC";

  // ── SCL_RTN_VERSION_STATE_CODE (DacConstants.VersionStatus) ──────────────
  public static final String ACCEPTED = "ACC";
  public static final String AWAITING_APPROVAL = "AWP";
  public static final String CANCELLED_AND_INVOICED = "CAN";
  public static final String DISCARDED = "DEL";
  public static final String ERROR = "ERR";
  public static final String FAILED = "FAL";
  public static final String HELD = "HLD";
  public static final String INCOMPLETE = "INC";
  public static final String READY = "RDY";
  public static final String LOCKED = "LCK";
  public static final String INVOICED = "ISS";
  public static final String CORRECTION_PENDING = "PND";
  public static final String SUPERCEDED = "SPR";
  public static final String NOT_TO_BE_BILLED = "NTI";
  public static final String INVALID_STATE = "INV";

  /** The state class a combination maps to. */
  public enum Kind { HELD, INVOICED, LOCKED, READY, RELEASABLE, AWAITING_APPROVAL, DISCARDED, INACTIVE, INVALID }

  private static final Map<String, Kind> TABLE = new HashMap<>();

  static {
    // ScaleReturnStateFactory static initializer, entry for entry.
    put(CANCEL_IN_PROGRESS, INCOMPLETE, Kind.HELD);
    put(ORIGINAL_IN_PROGRESS, INCOMPLETE, Kind.HELD);
    put(ORIGINAL_IN_PROGRESS, HELD, Kind.HELD);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, HELD, Kind.HELD);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, INCOMPLETE, Kind.HELD);
    put(CANCEL_WITH_REPLACE_NO_CHANGES, INCOMPLETE, Kind.HELD);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, INCOMPLETE, Kind.HELD);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, INCOMPLETE, Kind.HELD);

    put(ORIGINAL_COMPLETE, INVOICED, Kind.INVOICED);
    put(REPLACE_NO_CHANGES_COMPLETE, INVOICED, Kind.INVOICED);
    put(REPLACE_WITH_CHANGES_COMPLETE, INVOICED, Kind.INVOICED);
    put(CANCEL_COMPLETE, CANCELLED_AND_INVOICED, Kind.INVOICED);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, CANCELLED_AND_INVOICED, Kind.INVOICED);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, CANCELLED_AND_INVOICED, Kind.INVOICED);

    put(ORIGINAL_IN_PROGRESS, LOCKED, Kind.LOCKED);
    put(CANCEL_IN_PROGRESS, LOCKED, Kind.LOCKED);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, LOCKED, Kind.LOCKED);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, LOCKED, Kind.LOCKED);
    put(CANCEL_WITH_REPLACE_NO_CHANGES, LOCKED, Kind.LOCKED);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, LOCKED, Kind.LOCKED);

    put(ORIGINAL_IN_PROGRESS, READY, Kind.READY);
    put(ORIGINAL_IN_PROGRESS, ERROR, Kind.READY);
    put(CANCEL_IN_PROGRESS, READY, Kind.READY);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, READY, Kind.READY);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, ERROR, Kind.READY);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, READY, Kind.READY);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, ERROR, Kind.READY);
    put(CANCEL_WITH_REPLACE_NO_CHANGES, READY, Kind.READY);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, READY, Kind.READY);
    put(ORIGINAL_IN_PROGRESS, NOT_TO_BE_BILLED, Kind.READY);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, NOT_TO_BE_BILLED, Kind.READY);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, NOT_TO_BE_BILLED, Kind.READY);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, NOT_TO_BE_BILLED, Kind.READY);
    put(ORIGINAL_IN_PROGRESS, SUPERCEDED, Kind.READY);
    put(REPLACE_NO_CHANGES_IN_PROGRESS, SUPERCEDED, Kind.READY);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, SUPERCEDED, Kind.READY);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, SUPERCEDED, Kind.READY);

    put(ORIGINAL_IN_PROGRESS, ACCEPTED, Kind.RELEASABLE);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, INVOICED, Kind.RELEASABLE);
    put(REPLACE_WITH_CHANGES_IN_PROGRESS, CORRECTION_PENDING, Kind.RELEASABLE);
    put(CANCEL_WITH_REPLACE_WITH_CHANGES, CORRECTION_PENDING, Kind.RELEASABLE);

    put(CANCEL_WITH_REPLACE_NO_CHANGES, AWAITING_APPROVAL, Kind.AWAITING_APPROVAL);
    put(ORIGINAL_DISCARDED, DISCARDED, Kind.DISCARDED);
  }

  private static void put(String doc, String version, Kind kind) {
    TABLE.put(doc + version, kind);
  }

  /** {@code ScaleReturnStateFactory.getState}: unknown combinations are invalid, or inactive when not active. */
  public Kind kind() {
    Kind k = TABLE.get(doc + version);
    if (k != null) return k;
    return active ? Kind.INVALID : Kind.INACTIVE;
  }

  private boolean releasable() {
    Kind k = kind();
    return k == Kind.HELD || k == Kind.READY || k == Kind.RELEASABLE || k == Kind.AWAITING_APPROVAL;
  }

  private ScaleReturnState with(String newDoc, String newVersion) {
    return new ScaleReturnState(newDoc, newVersion, active);
  }

  private IllegalStateException unavailable(String operation) {
    // SummaryScaleReturnState.getStateMessage
    return new IllegalStateException(operation + " operation is not available.  Current state: document state=["
        + doc + "]version state=[" + version + "]active=[" + active + "]");
  }

  // ── Releasable (Held, Ready, Releasable, AwaitingApproval) ───────────────

  /** {@code ScaleReturnReleasableState.correctionPending}. */
  private boolean correctionPending() {
    return CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc) && INCOMPLETE.equals(version);
  }

  /** {@code ScaleReturnReleasableState.release(generated)}. */
  public ScaleReturnState release(boolean generated) {
    if (!releasable()) throw unavailable("release()");
    return with(doc, !generated && correctionPending() ? CORRECTION_PENDING : READY);
  }

  /** {@code ScaleReturnReleasableState.releaseAreaBased(generated)} (BT17364: area cut 0 → NTI). */
  public ScaleReturnState releaseAreaBased(boolean generated) {
    if (!releasable()) throw unavailable("releaseAreaBased()");
    if (!generated && correctionPending()) return with(doc, CORRECTION_PENDING);
    boolean toNti = INCOMPLETE.equals(version) || HELD.equals(version) || ERROR.equals(version)
        || CORRECTION_PENDING.equals(version) || NOT_TO_BE_BILLED.equals(version);
    return with(doc, toNti ? NOT_TO_BE_BILLED : READY);
  }

  /** {@code ScaleReturnReleasableState.failRelease} (and LockedState's, used by batch). */
  public ScaleReturnState failRelease() {
    if (kind() == Kind.LOCKED) return with(doc, ERROR);
    if (!releasable()) throw unavailable("failRelease()");
    return with(doc, correctionPending() ? CORRECTION_PENDING : ERROR);
  }

  // ── Hold (ReadyState) ────────────────────────────────────────────────────

  /** {@code ScaleReturnReadyState.holdAllowed}. */
  public boolean holdAllowed() {
    return kind() == Kind.READY
        && (ORIGINAL_IN_PROGRESS.equals(doc) || REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc));
  }

  /** {@code ScaleReturnReadyState.hold}: the held version is superseded and inactive. */
  public ScaleReturnState hold() {
    if (kind() != Kind.READY) throw unavailable("hold()");
    return new ScaleReturnState(doc, SUPERCEDED, false);
  }

  // ── Discard (Held, Ready, AwaitingApproval) ──────────────────────────────

  public boolean discardAllowed() {
    return switch (kind()) {
      case HELD, AWAITING_APPROVAL -> true;
      case READY -> ORIGINAL_IN_PROGRESS.equals(doc) || REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc);
      default -> false;
    };
  }

  /** {@code discard(generated)} of the Held / Ready / AwaitingApproval state classes. */
  public ScaleReturnState discard(boolean generated) {
    return switch (kind()) {
      case HELD -> generated ? discardGeneratedFailed() : heldDiscard();
      case READY -> generated ? with(ORIGINAL_DISCARDED, DISCARDED) : readyDiscard();
      case AWAITING_APPROVAL -> CANCEL_WITH_REPLACE_NO_CHANGES.equals(doc)
          ? with(ORIGINAL_COMPLETE, DISCARDED) : this;
      default -> throw unavailable("discard()");
    };
  }

  /** {@code ScaleReturnHeldState.discardGeneratedFailed}. */
  public ScaleReturnState discardGeneratedFailed() {
    if (kind() != Kind.HELD) throw unavailable("discardGeneratedFailed()");
    return with(ORIGINAL_DISCARDED, DISCARDED);
  }

  private ScaleReturnState heldDiscard() {
    return switch (doc) {
      case ORIGINAL_IN_PROGRESS -> with(ORIGINAL_DISCARDED, DISCARDED);
      case CANCEL_WITH_REPLACE_WITH_CHANGES -> with(ORIGINAL_COMPLETE, DISCARDED);
      case CANCEL_WITH_REPLACE_NO_CHANGES, CANCEL_IN_PROGRESS -> with(ORIGINAL_COMPLETE, INVOICED);
      case REPLACE_WITH_CHANGES_IN_PROGRESS, REPLACE_WITH_CHANGES_COMPLETE -> with(CANCEL_COMPLETE, DISCARDED);
      case REPLACE_NO_CHANGES_IN_PROGRESS -> with(CANCEL_COMPLETE, CANCELLED_AND_INVOICED);
      default -> this; // legacy left the state unchanged
    };
  }

  private ScaleReturnState readyDiscard() {
    return switch (doc) {
      case ORIGINAL_IN_PROGRESS -> with(ORIGINAL_DISCARDED, DISCARDED);
      case CANCEL_WITH_REPLACE_WITH_CHANGES -> READY.equals(version)
          ? with(ORIGINAL_COMPLETE, INVOICED) : with(ORIGINAL_COMPLETE, DISCARDED);
      case CANCEL_WITH_REPLACE_NO_CHANGES, CANCEL_IN_PROGRESS -> with(ORIGINAL_COMPLETE, INVOICED);
      case REPLACE_WITH_CHANGES_IN_PROGRESS, REPLACE_WITH_CHANGES_COMPLETE -> with(CANCEL_COMPLETE, DISCARDED);
      case REPLACE_NO_CHANGES_IN_PROGRESS -> with(CANCEL_COMPLETE, CANCELLED_AND_INVOICED);
      default -> throw new IllegalStateException(doc + " " + version + " is not a valid ReadyState");
    };
  }

  // ── Invoiced ─────────────────────────────────────────────────────────────

  private void requireInvoiced(String operation) {
    if (kind() != Kind.INVOICED) throw unavailable(operation);
  }

  public boolean cancelAllowed() {
    return kind() == Kind.INVOICED;
  }

  public ScaleReturnState cancel() {
    requireInvoiced("cancel()");
    return with(CANCEL_IN_PROGRESS, READY);
  }

  public ScaleReturnState cancelAreaBased() {
    requireInvoiced("cancel()");
    return with(CANCEL_IN_PROGRESS, NOT_TO_BE_BILLED);
  }

  public boolean replaceWithoutChangesAllowed() {
    return kind() == Kind.INVOICED;
  }

  public ScaleReturnState replaceWithoutChanges() {
    requireInvoiced("replaceWithoutChanges()");
    return with(REPLACE_NO_CHANGES_IN_PROGRESS, READY);
  }

  public ScaleReturnState replaceWithoutChangesAreaBased() {
    requireInvoiced("replaceWithoutChanges()");
    return with(REPLACE_NO_CHANGES_IN_PROGRESS, NOT_TO_BE_BILLED);
  }

  /** {@code ScaleReturnInvoicedState.replaceWithChangesAllowed}. */
  public boolean replaceWithChangesAllowed() {
    return kind() == Kind.INVOICED
        && ((ORIGINAL_COMPLETE.equals(doc) && INVOICED.equals(version))
        || (CANCEL_COMPLETE.equals(doc) && CANCELLED_AND_INVOICED.equals(version)));
  }

  public ScaleReturnState replaceWithChanges() {
    requireInvoiced("replaceWithChanges()");
    return with(REPLACE_WITH_CHANGES_IN_PROGRESS, INCOMPLETE);
  }

  public ScaleReturnState cancelAndReplaceWithChanges() {
    requireInvoiced("cancelAndReplaceWithChanges()");
    return with(CANCEL_WITH_REPLACE_WITH_CHANGES, INCOMPLETE);
  }

  public ScaleReturnState cancelAndReplaceWithoutChanges() {
    requireInvoiced("cancelAndReplaceWithoutChanges()");
    return with(CANCEL_WITH_REPLACE_NO_CHANGES, READY);
  }

  public ScaleReturnState cancelAndReplaceWithoutChangesAreaBased() {
    requireInvoiced("cancelAndReplaceWithoutChanges()");
    return with(CANCEL_WITH_REPLACE_NO_CHANGES, NOT_TO_BE_BILLED);
  }

  public boolean requestRateChangeAllowed() {
    return kind() == Kind.INVOICED;
  }

  /** {@code ScaleReturnInvoicedState.requestRateChange}. */
  public ScaleReturnState requestRateChange() {
    if (kind() != Kind.INVOICED) throw new IllegalStateException("release() operation is not available");
    return with(CANCEL_WITH_REPLACE_NO_CHANGES, AWAITING_APPROVAL);
  }

  /** {@code SummaryScaleReturnState.isCancelled}. */
  public boolean isCancelled() {
    return CANCELLED_AND_INVOICED.equals(version);
  }

  @Override
  public String toString() {
    return "State '" + doc + "':'" + version + "'" + (active ? "" : " (inactive)");
  }
}
