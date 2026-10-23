package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.AWAITING_APPROVAL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_WITH_REPLACE_NO_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCEL_WITH_REPLACE_WITH_CHANGES;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CORRECTION_PENDING;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.DISCARDED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.HELD;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.NOT_TO_BE_BILLED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.ORIGINAL_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.READY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_NO_CHANGES_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_WITH_CHANGES_COMPLETE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_WITH_CHANGES_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_CANCEL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_DELETE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_HOLD;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_INSERT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_RELEASE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_REPLACE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_REQUEST;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The summary-return state workflows: port of {@code $E/dac/summary/SummaryScaleReturnStateHelper}
 * (hold, discard, discardGeneratedFailed, cancel, replace with / without changes, cancel and replace
 * with / without changes, request / reject rate correction, createNewVersion) and
 * {@code $E/dac/summary/control/workflow/Release} (release, approve rate correction). Each method
 * changes the in-memory return through {@link ScaleReturnState} and saves it with
 * {@link SummaryReturnWriter} exactly where the legacy code called {@code updateSummaryScaleReturn},
 * {@code updateScaleReturnVersion} and {@code createAndStoreSummaryScaleReturnVersion}.
 *
 * <p>Runs inside the calling command's transaction, so the intermediate states the legacy code
 * committed one call at a time (and re-read with {@code verifyScaleReturnIsInState}) are never
 * visible to the legacy batch. The precondition checks of the screens live in the commands.
 */
@Service
@Slf4j
public class SummaryReturnWorkflows {

  /** The legacy entry user of the B0013 waste returns (BT11514 "wasteInd"). */
  public static final String WASTE_BATCH_USER = "HBSB013";

  /** Copies of a version start without these columns ({@code Summary*ScaleReturnVersion.newVersion}, BT 6258). */
  private static final Set<String> NOT_COPIED = Set.of("client_number_paid_by", "client_locn_code_paid_by",
      "client_number_copied_to", "client_locn_code_copied_to", "input_timber_mark", "input_scaler_license_num",
      "input_population", "input_stratum", "input_sampling_year", "pending_txn_comment");
  private static final Set<String> SEGREGATION_NOT_COPIED = Set.of("input_species", "input_product", "input_grade",
      "input_other_desc");

  private final SummaryReturnRepository repo;
  private final SummaryReturnWriter writer;
  private final Audit audit;
  private final ObjectProvider<ReleaseEdits> edits;
  private final ObjectProvider<GeneratedSummaryDetails> details;

  public SummaryReturnWorkflows(SummaryReturnRepository repo, SummaryReturnWriter writer, Audit audit,
      ObjectProvider<ReleaseEdits> edits, ObjectProvider<GeneratedSummaryDetails> details) {
    this.repo = repo;
    this.writer = writer;
    this.audit = audit;
    this.edits = edits;
    this.details = details;
  }

  private ReleaseEdits edits() {
    return edits.getObject();
  }

  private GeneratedSummaryDetails details() {
    return details.getObject();
  }

  private static WorkflowValidationException notAvailable(String action, SummaryReturn ret) {
    return new WorkflowValidationException(null, action + " is not available for a summary return in state "
        + ret.docState() + "/" + ret.versionState() + (ret.active() ? "" : " (inactive version)") + ".");
  }

  private static ScaleReturnState transition(String action, SummaryReturn ret,
      java.util.function.UnaryOperator<ScaleReturnState> op) {
    try {
      return op.apply(ret.state());
    } catch (IllegalStateException ex) {
      throw notAvailable(action, ret);
    }
  }

  // ── New versions ─────────────────────────────────────────────────────────

  /**
   * {@code SummaryScaleReturnStateHelper.createNewVersion}: version MAX+1, a copy of the given version
   * ({@code newVersion}: Bill To / Copy To and input fields not copied), active, children copied
   * with new segregation and notation ids.
   */
  public SummaryReturn createNewVersion(SummaryReturn from, String user) {
    long next = repo.maxVersion(from.type(), from.dcn()) + 1;
    var now = audit.now();
    Row v = from.version().copy();
    NOT_COPIED.forEach(c -> {
      if (v.has(c)) v.set(c, null);
    });
    v.set("version", next).set("active_version_ind", "Y")
        .set("entry_userid", user).set("entry_timestamp", now).set("update_userid", user).set("update_timestamp", now);
    List<Row> segs = new ArrayList<>();
    for (Row s : from.segregations()) {
      Row c = s.copy();
      SEGREGATION_NOT_COPIED.forEach(col -> {
        if (c.has(col)) c.set(col, null);
      });
      c.set("segregation_id", null).set("version", next)
          .set("entry_userid", user).set("entry_timestamp", now).set("update_userid", user).set("update_timestamp", now);
      segs.add(c);
    }
    List<Row> notations = new ArrayList<>();
    for (Row n : from.notations()) {
      Row c = n.copy().set("ntn_id", null).set("version", next);
      if (from.type() != SummaryReturnType.WEIGHT) {
        // weight notations keep their audit columns (WeightScaleNotation copy)
        c.set("entry_userid", user).set("entry_timestamp", now).set("update_userid", user).set("update_timestamp", now);
      }
      notations.add(c);
    }
    List<Row> loads = new ArrayList<>();
    for (Row d : from.dailyLoads()) {
      loads.add(d.copy().set("version", next)
          .set("entry_userid", user).set("entry_timestamp", now).set("update_userid", user).set("update_timestamp", now));
    }
    return new SummaryReturn(from.type(), from.header(), v, segs, notations, loads);
  }

  // ── Hold ─────────────────────────────────────────────────────────────────

  /**
   * {@code SummaryScaleReturnStateHelper.hold} (BT16641): the ready version becomes SPR and inactive,
   * a new held version is created; area-based returns lose their generated segregations, log count
   * and notation (BT15212).
   */
  public SummaryReturn hold(SummaryReturn ret, String user) {
    if (!ret.state().holdAllowed()) throw notAvailable("Hold", ret);
    ret.setState(transition("Hold", ret, ScaleReturnState::hold));
    writer.saveVersion(ret, ACTION_HOLD, user, null);

    SummaryReturn held = createNewVersion(ret, user);
    held.setState(new ScaleReturnState(ret.docState(), HELD, true));
    writer.storeHeader(held.header());
    writer.createVersion(held, ACTION_INSERT, user);

    if (held.areaBased()) {
      held.segregations().clear();
      held.version().set("log_count", 0);
      held.notations().stream()
          .filter(n -> Long.valueOf(SummaryReturnRules.AREA_BASED_HOLD_NOTATION_NUMBER).equals(n.lng("notation_number")))
          .findFirst()
          .or(() -> held.notations().stream().max(Comparator.comparing((Row n) -> n.lng("notation_number"))))
          .ifPresent(held.notations()::remove);
      writer.saveReturnAndChildren(held, user, true);
    }
    return held;
  }

  // ── Release (Release.execute) ────────────────────────────────────────────

  public record ReleaseOutcome(SummaryReturn active, List<EditError> errors, List<String> messages) {}

  /**
   * {@code Release.execute()} as the screens ran it (no batch name): generated returns get the client
   * edits (and, for an approved rate correction, the detail extension first); submitted returns the
   * full release edits, then RDY / NTI (BT17364) or ERR with the errors saved; area-based returns get
   * their segregations generated (BT15212); a cancel-and-replace-with-changes release also releases the
   * previous invoiced version so the cancel is extended first.
   */
  public ReleaseOutcome release(SummaryReturn ret, String user) {
    List<String> messages = new ArrayList<>();
    List<EditError> errors;
    if (ret.generated()) {
      if (CANCEL_WITH_REPLACE_NO_CHANGES.equals(ret.docState()) && AWAITING_APPROVAL.equals(ret.versionState())) {
        details().extendRateCorrection(ret, user);
        ret = repo.load(ret.dcn(), ret.versionNo()); // refreshScaleReturn
      }
      errors = edits().applyEditsForClient(ret);
      final SummaryReturn r = ret;
      ret.setState(errors.isEmpty() ? transition("Release", r, s -> s.release(true))
          : transition("Release", r, ScaleReturnState::failRelease));
    } else {
      errors = edits().applyEditsAndValidate(ret);
      final SummaryReturn r = ret;
      if (errors.isEmpty()) {
        ret.setState(ret.areaBasedWithZeroAreaCut()
            ? transition("Release", r, s -> s.releaseAreaBased(false))
            : transition("Release", r, s -> s.release(false)));
        if (ret.areaBased()) {
          List<EditError> generatedErrors = edits().generateAreaBasedSegregations(ret, user);
          writer.saveReturnAndChildren(ret, user, true);
          if (!generatedErrors.isEmpty()) {
            // Legacy defect kept: validateGeneratedSegregations() results are never added to the return.
            log.info("Area-based return {}: generated segregation edits not saved (legacy): {}", ret, generatedErrors);
          }
        }
      } else {
        ret.setState(transition("Release", r, ScaleReturnState::failRelease));
      }
      if (isCorrectionPending(ret)) {
        releaseCancelWithReplaceWithChangesPreviousVersion(ret, user);
      }
    }
    if (!ret.version().flag("visibility_ind")) {
      ret.version().set("visibility_ind", "Y");
    }
    writer.saveVersion(ret, ACTION_RELEASE, user, errors);

    // BT7327 (P100Action.updateYTDCyclicBillingInd): a released weight Final Bill marks the stratum/mark billed.
    if (ret.type() == SummaryReturnType.WEIGHT && !ret.generated()
        && SummaryReturnRules.CATEGORY_FINAL_BILL.equals(ret.version().str("scl_rtn_category_code"))
        && READY.equals(ret.versionState())) {
      markFinalBill(ret, user).ifPresent(messages::add);
    }

    SummaryReturn active = ret.active() ? ret : repo.loadActive(ret.dcn());
    return new ReleaseOutcome(active, errors, messages);
  }

  /** {@code Release.isCorrectionPending}: the version is PND, or an RCP return's version is CAN. */
  static boolean isCorrectionPending(SummaryReturn ret) {
    if (CORRECTION_PENDING.equals(ret.versionState())) return true;
    return REPLACE_WITH_CHANGES_IN_PROGRESS.equals(ret.docState()) && ret.state().isCancelled();
  }

  /**
   * {@code Release.releaseCancelWithReplaceWithChangesPreviousVersion}: the previous invoiced version
   * is released (CWC/ISS → RDY, so B2031 extends the cancel) and becomes the active version.
   */
  private void releaseCancelWithReplaceWithChangesPreviousVersion(SummaryReturn ret, String user) {
    SummaryReturn previous = repo.loadPreviousInvoiced(ret)
        .orElseThrow(() -> new IllegalStateException("No previous invoiced version for " + ret));
    previous.header().set("scale_return_state_code", ret.docState());
    previous.setState(new ScaleReturnState(ret.docState(), previous.versionState(), true).release(false));
    ret.version().set("active_version_ind", "N");
    writer.saveVersion(previous, ACTION_RELEASE, user, null);
  }

  /** Finds the YTD cyclic billing row of the stratum/mark and sets FINAL_BILL_IND = 'Y'. */
  private Optional<String> markFinalBill(SummaryReturn ret, String user) {
    Row v = ret.version();
    Optional<Row> ytd = repo.cyclicBilling(v.str("population_number"), v.str("stratum_number"),
        v.lng("sampling_year"), v.str("timber_mark"));
    if (ytd.isEmpty()) {
      return Optional.of(SummaryReturnMessages.PSYM_REQUIRED);
    }
    Row y = ytd.get();
    writer.api().call("HBS_STORE_YTD_CYCLIC_BILLING", ProcArgs.of()
        .put("i_population_number", y.get("population_number"))
        .put("i_stratum_number", y.get("stratum_number"))
        .put("i_sampling_year", y.get("sampling_year"))
        .put("i_timber_mark", y.get("timber_mark"))
        .put("i_ytd_weight", y.get("ytd_weight"))
        .put("i_final_bill_ind", "Y")
        .put("i_entry_userid", y.get("entry_userid"))
        .put("i_entry_timestamp", y.get("entry_timestamp"))
        .put("i_update_userid", user)
        .put("i_update_timestamp", audit.now()));
    return Optional.empty();
  }

  // ── Discard ──────────────────────────────────────────────────────────────

  /**
   * {@code SummaryScaleReturnHelper.discard} (view) → {@code SummaryScaleReturnStateHelper.discard} /
   * {@code discardGeneratedFailed}. Returns the version that is active afterwards.
   */
  public SummaryReturn discard(SummaryReturn ret, String user) {
    if (ret.generated()) {
      return discardGeneratedFailed(ret, user);
    }
    String doc0 = ret.docState();
    String ver0 = ret.versionState();
    ScaleReturnState next = transition("Discard", ret, s -> s.discard(false));

    if (ORIGINAL_IN_PROGRESS.equals(doc0) || CANCEL_WITH_REPLACE_NO_CHANGES.equals(doc0)
        || REPLACE_NO_CHANGES_IN_PROGRESS.equals(doc0) || CANCEL_IN_PROGRESS.equals(doc0)) {
      ret.setState(next);
      if (ORIGINAL_IN_PROGRESS.equals(doc0)) {
        ret.version().set("visibility_ind", "N");
      }
      writer.storeHeader(ret.header());
      writer.saveVersion(ret, ACTION_DELETE, user, List.of());
      return ret;
    }
    if (CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc0) || REPLACE_WITH_CHANGES_COMPLETE.equals(doc0)
        || REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc0)) {
      ret.setState(next);
      SummaryReturn current;
      SummaryReturn previous;
      boolean areaBasedNti = ret.areaBased() && NOT_TO_BE_BILLED.equals(ver0);
      if (CANCEL_WITH_REPLACE_WITH_CHANGES.equals(doc0) && (READY.equals(ver0) || areaBasedNti)) {
        // The released previous version stays; the pending correction is discarded.
        previous = ret;
        current = repo.loadNextPending(ret)
            .orElseThrow(() -> new IllegalStateException("No pending version for " + ret));
        current.header().set("scale_return_state_code", previous.docState());
        current.version().set("scl_rtn_version_state_code", DISCARDED);
      } else {
        current = ret;
        previous = repo.loadPreviousInvoiced(ret)
            .orElseThrow(() -> new IllegalStateException("No previous invoiced version for " + ret));
        previous.header().set("scale_return_state_code", current.docState());
      }
      current.version().set("visibility_ind", "N").set("active_version_ind", "N");
      previous.version().set("visibility_ind", "Y").set("active_version_ind", "Y");
      writer.storeHeader(current.header());
      writer.saveVersion(current, ACTION_DELETE, user, List.of());
      writer.saveVersion(previous, ACTION_DELETE, user, List.of());
      return previous;
    }
    return ret;
  }

  /**
   * {@code SummaryScaleReturnStateHelper.discardGeneratedFailed}: the detail documents are reset, then a
   * rejected rate correction (CWR with more than one version) or DIS/DEL. Legacy keeps the discarded
   * version active ({@code setActiveVersionFlag(TRUE)}).
   */
  public SummaryReturn discardGeneratedFailed(SummaryReturn ret, String user) {
    if (!ret.state().discardAllowed()) throw notAvailable("Discard", ret);
    details().resetDetailDocuments(ret, user);
    if (CANCEL_WITH_REPLACE_NO_CHANGES.equals(ret.docState()) && repo.versionCount(ret.type(), ret.dcn()) > 1) {
      return rejectRateCorrection(ret, user);
    }
    ret.setState(transition("Discard", ret, s -> s.discard(true)));
    ret.version().set("visibility_ind", "N").set("active_version_ind", "Y");
    writer.storeHeader(ret.header());
    writer.saveVersion(ret, ACTION_DELETE, user, List.of());
    return ret;
  }

  // ── Invoiced: cancel / replace ───────────────────────────────────────────

  /** {@code SummaryScaleReturnStateHelper.cancel} (BT17364: area-based with Area Cut 0 → CIP/NTI). */
  public SummaryReturn cancel(SummaryReturn ret, String user) {
    ret.setState(ret.areaBasedWithZeroAreaCut()
        ? transition("Cancel", ret, ScaleReturnState::cancelAreaBased)
        : transition("Cancel", ret, ScaleReturnState::cancel));
    writer.storeHeader(ret.header());
    writer.saveVersion(ret, ACTION_CANCEL, user, null);
    return ret;
  }

  /** {@code SummaryScaleReturnStateHelper.replaceWithoutChanges}: RNP/RDY (NTI for area cut 0). */
  public SummaryReturn replaceWithoutChanges(SummaryReturn ret, String user) {
    ret.setState(ret.areaBasedWithZeroAreaCut()
        ? transition("Replace Without Changes", ret, ScaleReturnState::replaceWithoutChangesAreaBased)
        : transition("Replace Without Changes", ret, ScaleReturnState::replaceWithoutChanges));
    writer.storeHeader(ret.header());
    writer.saveVersion(ret, ACTION_CANCEL, user, null);
    return ret;
  }

  /** {@code SummaryScaleReturnStateHelper.cancelAndReplaceWithoutChanges}: CWR/RDY (NTI for area cut 0). */
  public SummaryReturn cancelAndReplaceWithoutChanges(SummaryReturn ret, String user) {
    ret.setState(ret.areaBasedWithZeroAreaCut()
        ? transition("Cancel & Replace Without Changes", ret, ScaleReturnState::cancelAndReplaceWithoutChangesAreaBased)
        : transition("Cancel & Replace Without Changes", ret, ScaleReturnState::cancelAndReplaceWithoutChanges));
    writer.storeHeader(ret.header());
    writer.saveVersion(ret, ACTION_REPLACE, user, null);
    return ret;
  }

  /** {@code SummaryScaleReturnStateHelper.cancelAndReplaceWithChanges}: a new CWC/INC version. */
  public SummaryReturn cancelAndReplaceWithChanges(SummaryReturn ret, String user) {
    if (!ret.state().cancelAllowed()) throw notAvailable("Cancel & Replace With Changes", ret);
    return newCorrectionVersion(ret, user, ACTION_REPLACE, ScaleReturnState::cancelAndReplaceWithChanges, false);
  }

  /** {@code SummaryScaleReturnStateHelper.replaceWithChanges}: a new RCP/INC version. */
  public SummaryReturn replaceWithChanges(SummaryReturn ret, String user) {
    if (!ret.state().replaceWithChangesAllowed()) throw notAvailable("Replace With Changes", ret);
    return newCorrectionVersion(ret, user, ACTION_REPLACE, ScaleReturnState::replaceWithChanges, false);
  }

  /**
   * {@code SummaryScaleReturnStateHelper.requestRateCorrection}: generated piece / weight returns with
   * correctable details get a new CWR/AWP version without segregations / daily loads.
   */
  public SummaryReturn requestRateCorrection(SummaryReturn ret, String user) {
    if (!ret.state().requestRateChangeAllowed()) throw notAvailable("Request Rate Correction", ret);
    if (!ret.generated()) {
      throw new WorkflowValidationException(null, "Scale return " + ret.dcn() + " is not a generated summary!");
    }
    if (ret.type() == SummaryReturnType.SAMPLE) {
      throw new WorkflowValidationException(null, "scale return must be piece or weight");
    }
    if (!details().isEligibleForRateCorrection(ret)) {
      throw new WorkflowValidationException(null, SummaryReturnMessages.RATE_CORRECTION_CONFIRM_NO_DETAILS);
    }
    return newCorrectionVersion(ret, user, ACTION_REQUEST, ScaleReturnState::requestRateChange, true);
  }

  private SummaryReturn newCorrectionVersion(SummaryReturn ret, String user, String oldVersionAction,
      java.util.function.UnaryOperator<ScaleReturnState> op, boolean clearForSummarizer) {
    SummaryReturn next = createNewVersion(ret, user);
    ret.version().set("active_version_ind", "N");
    writer.saveVersion(ret, oldVersionAction, user, null);
    if (clearForSummarizer) {
      // the summarizer does not need segregations or daily loads
      next.segregations().clear();
      next.dailyLoads().clear();
      if (next.type() == SummaryReturnType.PIECE) {
        next.version().set("log_count", 0); // SummaryPieceScaleReturnVersion.clearSegregations
      }
    }
    String action = oldVersionAction.equals(ACTION_REQUEST) ? "Request Rate Correction" : "Replace";
    next.setState(transition(action, next, op));
    writer.storeHeader(next.header());
    writer.createVersion(next, ACTION_REPLACE, user);
    return next;
  }

  /**
   * {@code SummaryScaleReturnStateHelper.rejectRateCorrection}: the AWP version is discarded (OCM/DEL,
   * hidden, inactive) and the previous invoiced version is active and visible again.
   */
  public SummaryReturn rejectRateCorrection(SummaryReturn ret, String user) {
    if (!ret.state().discardAllowed()) throw notAvailable("Reject Rate Correction", ret);
    ret.setState(transition("Reject Rate Correction", ret, s -> s.discard(ret.generated())));
    ret.version().set("visibility_ind", "N").set("active_version_ind", "N");
    writer.storeHeader(ret.header());
    writer.saveVersion(ret, ACTION_DELETE, user, List.of());
    SummaryReturn previous = repo.loadPreviousInvoiced(ret)
        .orElseThrow(() -> new IllegalStateException("No previous invoiced version for " + ret));
    previous.header().set("scale_return_state_code", ret.docState());
    previous.version().set("visibility_ind", "Y").set("active_version_ind", "Y");
    writer.saveVersion(previous, ACTION_DELETE, user, List.of());
    return previous;
  }

  /** BT11514: returns created by the B0013 waste batch offer Cancel only (no replace). */
  public static boolean isWasteBatchReturn(SummaryReturn ret) {
    return WASTE_BATCH_USER.equals(ret.version().str("entry_userid"));
  }
}
