package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import java.util.Map;

/**
 * The detail scale document state machine, as far as the generated-summary actions need it: the
 * document state ({@code DETAIL_SCALE_DOCUMENT.SCALE_RETURN_STATE_CODE}, legacy
 * {@code dac.detail.control.DocumentState}) and the version state and active flag
 * ({@code SCL_RTN_VERSION_STATE_CODE} / {@code ACTIVE_VERSION_IND}, legacy
 * {@code dac.detail.control.VersionState}). Transition names, guards and failure texts are the
 * legacy ones; an illegal transition is an {@link IllegalStateException}, as legacy threw.
 *
 * <p>The version state and active flag as loaded are remembered for the audit
 * ({@code VersionState.getOriginalValue / getOriginalActive}).
 */
final class DetailState {

  // DacConstants.ScaleReturnStatus (document)
  static final String OIP = "OIP";
  static final String OCM = "OCM";
  static final String CIP = "CIP";
  static final String CCM = "CCM";
  static final String CWR = "CWR";
  static final String CWC = "CWC";
  static final String RNP = "RNP";
  static final String RCP = "RCP";
  static final String RNC = "RNC";
  static final String RCC = "RCC";

  // DacConstants.VersionStatus (version)
  static final String ACC = "ACC";
  static final String AWP = "AWP";
  static final String CAN = "CAN";
  static final String DEL = "DEL";
  static final String DSF = "DSF";
  static final String ERR = "ERR";
  static final String FAL = "FAL";
  static final String HLD = "HLD";
  static final String INC = "INC";
  static final String LCK = "LCK";
  static final String ISS = "ISS";
  static final String PND = "PND";
  static final String PNV = "PNV";
  static final String RDY = "RDY";
  static final String SPR = "SPR";
  static final String NTI = "NTI";
  static final String INV = "INV";

  /** {@code VersionState.STRINGS}, for the legacy messages. */
  private static final Map<String, String> VERSION_NAMES = Map.ofEntries(
      Map.entry(ACC, "accepted (ACC)"), Map.entry(AWP, "awaiting approval (AWP)"),
      Map.entry(CAN, "cancelled (CAN)"), Map.entry(DEL, "discarded (DEL)"),
      Map.entry(DSF, "signature failed (DSF)"), Map.entry(ERR, "in error (ERR)"),
      Map.entry(FAL, "failed (FAL)"), Map.entry(HLD, "held (HLD)"), Map.entry(INC, "incomplete (INC)"),
      Map.entry(LCK, "locked (LCK)"), Map.entry(ISS, "issued (ISS)"),
      Map.entry(PND, "correction pending (PND)"), Map.entry(PNV, "replacement pending (PNV)"),
      Map.entry(RDY, "ready (RDY)"), Map.entry(SPR, "superceded (SPR)"),
      Map.entry(NTI, "not to be issued (NTI)"), Map.entry(INV, "invalid state (INV)"));

  private String doc;
  private String version;
  private boolean active;
  private final String loadedVersion;
  private final boolean loadedActive;

  DetailState(String doc, String version, boolean active) {
    this.doc = doc;
    this.version = version;
    this.active = active;
    this.loadedVersion = version;
    this.loadedActive = active;
  }

  String doc() {
    return doc;
  }

  String version() {
    return version;
  }

  boolean active() {
    return active;
  }

  String loadedVersion() {
    return loadedVersion;
  }

  boolean loadedActive() {
    return loadedActive;
  }

  boolean versionIs(String code) {
    return code.equals(version);
  }

  /** {@code VersionState.toString()}: e.g. "locked (LCK) active". */
  String versionText() {
    return VERSION_NAMES.getOrDefault(version, version) + " " + (active ? "active" : "inactive");
  }

  private IllegalStateException illegal(String transition) {
    return new IllegalStateException(transition + "(): Illegal transition from " + versionText());
  }

  // ── VersionState ─────────────────────────────────────────────────────────

  /** {@code VersionState.summarizationFailed} (state {@code Fail}). */
  void summarizationFailed() {
    if (versionIs(LCK) && active) version = FAL;
    else throw illegal("summarizationFailed");
  }

  /** {@code VersionState.extend}. */
  void extend() {
    if (versionIs(RDY)) version = LCK;
    else throw illegal("extend");
  }

  /** {@code VersionState.cancelAndReplaceWithNoChangesRequested}. */
  void versionCancelAndReplaceWithNoChangesRequested() {
    if (versionIs(ISS) && active) version = AWP;
    else throw illegal("cancelAndReplaceWithNoChangesRequested");
  }

  /** {@code VersionState.cancelAndReplaceWithNoChangesApproved}. */
  void versionCancelAndReplaceWithNoChangesApproved() {
    if (versionIs(AWP) && active) version = RDY;
    else throw illegal("cancelAndReplaceWithNoChangesApproved");
  }

  private void clearCancellationFailure() {
    if (versionIs(FAL) && active) version = ISS;
    else if (versionIs(PND) && !active) version = DEL;
    else throw illegal("clearCancellationFailure");
  }

  private void clearOriginalFailure() {
    if (versionIs(FAL) && active) version = RDY;
    else throw illegal("clearOriginalFailure");
  }

  /** BT 13197: back to awaiting approval. */
  private void clearReplaceWithNoChangesFailure() {
    if (versionIs(FAL) && active) version = AWP;
    else throw illegal("clearReplaceWithNoChangesFailure");
  }

  /** BT 13197: a failed version back to awaiting approval; an inactive cancelled one is left as it is. */
  private void clearReplaceWithChangesFailure() {
    if (versionIs(FAL) && active) version = AWP;
    else if (versionIs(CAN) && !active) {
      // ;//this.active = true;  BT 13197
    } else {
      throw illegal("clearReplaceWithChangesFailure");
    }
  }

  // ── DocumentState ────────────────────────────────────────────────────────

  private IllegalStateException illegalDoc(String to) {
    return new IllegalStateException("Illegal transition from " + doc + " to " + to);
  }

  private boolean cancelAllowed() {
    return OCM.equals(doc) || RNC.equals(doc) || RCC.equals(doc);
  }

  /** {@code DocumentState.cancelAndReplaceWithNoChangesRequested}. */
  void documentCancelAndReplaceWithNoChangesRequested() {
    if (cancelAllowed()) doc = CWR;
    else throw illegalDoc(CWR);
  }

  private void documentOriginalComplete() {
    boolean failed = versionIs(FAL);
    if ((OIP.equals(doc) && versionIs(ISS)) || (CIP.equals(doc) && failed) || (CWR.equals(doc) && failed)
        || (CWC.equals(doc) && failed) || (CWC.equals(doc) && versionIs(DEL))) {
      doc = OCM;
    } else {
      throw new IllegalStateException("Illegal transition from " + doc + " " + versionText() + " to " + OCM);
    }
  }

  private void documentCancelComplete() {
    if ((CIP.equals(doc) && versionIs(CAN)) || (RNC.equals(doc) && versionIs(FAL))
        || (RCP.equals(doc) && versionIs(DEL)) || (RNP.equals(doc) && versionIs(FAL))
        || (RCP.equals(doc) && versionIs(FAL)) || (CWC.equals(doc) && versionIs(DEL))) {
      doc = CCM;
    } else {
      throw illegalDoc(CCM);
    }
  }

  /**
   * {@code dac.detail.control.state.Reset.execute}: clears a summarization failure. The legacy
   * "TODO: verify that this is the correct state transition" branches are kept as they were.
   */
  void reset() {
    if (CIP.equals(doc) || CWR.equals(doc) || CWC.equals(doc)) {
      // NOTE: this also handles both 'failed' and 'correction pending' versions
      if (!versionIs(PND)) documentOriginalComplete();
      clearCancellationFailure();
    } else if (RNP.equals(doc)) {
      if (!versionIs(FAL)) documentCancelComplete();
      clearReplaceWithNoChangesFailure();
    } else if (RCP.equals(doc) || CCM.equals(doc)) {
      if (!versionIs(CAN) && !versionIs(FAL)) documentCancelComplete();
      clearReplaceWithChangesFailure();
    } else if (OIP.equals(doc)) {
      clearOriginalFailure();
    } else {
      throw new IllegalStateException("Document can not have summarization failures cleared.");
    }
  }

  @Override
  public String toString() {
    return doc + " " + versionText();
  }
}
