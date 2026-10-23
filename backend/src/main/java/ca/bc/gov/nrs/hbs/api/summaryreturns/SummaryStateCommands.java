package ca.bc.gov.nrs.hbs.api.summaryreturns;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The P052/P102/P152 (and P053/P103/P153) workflow buttons as commands. Each loads the active
 * version ({@link SummaryReturnCommand#loadForAction}), checks that the legacy page would have offered
 * the button to this caller ({@link SummaryButtons}), and runs the {@link SummaryReturnWorkflows}
 * transition in one transaction. Legacy: {@code P050Action / P100Action / P150Action} "Yes" branch →
 * {@code SummaryScaleReturnHelper} (view) → {@code SummaryScaleReturnStateHelper} /
 * {@code SummaryWorkflowManager.release}.
 *
 * <p>Body: {@code documentControlNumber}, {@code version}, {@code updateTimestamp}.
 * Response: the resulting active version ({@code documentControlNumber}, {@code version},
 * {@code returnStateCode}, {@code status}); release also returns {@code editErrors} and {@code messages}.
 */
public final class SummaryStateCommands {

  private SummaryStateCommands() {
  }

  /** A button-backed transition. */
  public abstract static class StateCommand extends SummaryReturnCommand {

    protected final SummaryReturnWorkflows workflows;
    protected final SummaryButtons buttons;
    private final String button;
    private final String label;
    private final String legacy;
    private final Capability capability;
    private final BiFunction<SummaryReturn, String, SummaryReturn> transition;

    protected StateCommand(Deps deps, String button, String label, String legacy, Capability capability,
        BiFunction<SummaryReturn, String, SummaryReturn> transition) {
      super(deps.repo, deps.writer, deps.access, deps.audit);
      this.workflows = deps.workflows;
      this.buttons = deps.buttons;
      this.button = button;
      this.label = label;
      this.legacy = legacy;
      this.capability = capability;
      this.transition = transition;
    }

    @Override
    public String id() {
      return "summaryReturns." + button;
    }

    @Override
    public String legacy() {
      return legacy;
    }

    @Override
    public Capability capability() {
      return capability;
    }

    /** Loads, checks the button rule, and returns the return to act on. */
    protected SummaryReturn prepare(Map<String, Object> body, SummaryCaller caller) {
      SummaryReturn ret = loadForAction(body, caller);
      if (!buttons.available(caller, ret).contains(button)) {
        throw refuse(label + " is not available for this summary scale return (state "
            + ret.docState() + "/" + ret.versionState() + ").");
      }
      return ret;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      SummaryCaller caller = caller();
      SummaryReturn ret = prepare(body, caller);
      return result(transition.apply(ret, audit.user()));
    }
  }

  /** The beans every state command needs. */
  @Component
  public static class Deps {
    final SummaryReturnRepository repo;
    final SummaryReturnWriter writer;
    final SummaryReturnAccess access;
    final Audit audit;
    final SummaryReturnWorkflows workflows;
    final SummaryButtons buttons;
    final ObjectProvider<GeneratedSummaryDetails> details;

    public Deps(SummaryReturnRepository repo, SummaryReturnWriter writer, SummaryReturnAccess access, Audit audit,
        SummaryReturnWorkflows workflows, SummaryButtons buttons, ObjectProvider<GeneratedSummaryDetails> details) {
      this.repo = repo;
      this.writer = writer;
      this.access = access;
      this.audit = audit;
      this.workflows = workflows;
      this.buttons = buttons;
      this.details = details;
    }
  }

  @Component
  public static class Hold extends StateCommand {
    public Hold(Deps d) {
      super(d, SummaryButtons.HOLD, "Hold", "P052/P102/P152 Hold — SummaryScaleReturnStateHelper.hold",
          Capability.SUMMARY_RETURN_EDIT, d.workflows::hold);
    }
  }

  @Component
  public static class Discard extends StateCommand {
    public Discard(Deps d) {
      super(d, SummaryButtons.DISCARD, "Discard",
          "P052/P053 Discard — SummaryScaleReturnStateHelper.discard / discardGeneratedFailed",
          Capability.SUMMARY_RETURN_EDIT, d.workflows::discard);
    }
  }

  @Component
  public static class Cancel extends StateCommand {
    public Cancel(Deps d) {
      super(d, SummaryButtons.CANCEL, "Cancel", "P052 Cancel — SummaryScaleReturnStateHelper.cancel",
          Capability.SUMMARY_RETURN_CORRECT, d.workflows::cancel);
    }
  }

  @Component
  public static class CancelReplaceWithChanges extends StateCommand {
    public CancelReplaceWithChanges(Deps d) {
      super(d, SummaryButtons.CANCEL_REPLACE_WITH_CHANGES, "Cancel & Replace With Changes",
          "P052 Cancel & Replace With Changes — SummaryScaleReturnStateHelper.cancelAndReplaceWithChanges",
          Capability.SUMMARY_RETURN_CORRECT, d.workflows::cancelAndReplaceWithChanges);
    }
  }

  @Component
  public static class CancelReplaceWithoutChanges extends StateCommand {
    public CancelReplaceWithoutChanges(Deps d) {
      super(d, SummaryButtons.CANCEL_REPLACE_WITHOUT_CHANGES, "Cancel & Replace Without Changes",
          "P052 Cancel & Replace Without Changes — SummaryScaleReturnStateHelper.cancelAndReplaceWithoutChanges",
          Capability.SUMMARY_RETURN_CORRECT, d.workflows::cancelAndReplaceWithoutChanges);
    }
  }

  @Component
  public static class ReplaceWithChanges extends StateCommand {
    public ReplaceWithChanges(Deps d) {
      super(d, SummaryButtons.REPLACE_WITH_CHANGES, "Replace With Changes",
          "P052 Replace With Changes — SummaryScaleReturnStateHelper.replaceWithChanges",
          Capability.SUMMARY_RETURN_CORRECT, d.workflows::replaceWithChanges);
    }
  }

  @Component
  public static class ReplaceWithoutChanges extends StateCommand {
    public ReplaceWithoutChanges(Deps d) {
      super(d, SummaryButtons.REPLACE_WITHOUT_CHANGES, "Replace Without Changes",
          "P052 Replace Without Changes — SummaryScaleReturnStateHelper.replaceWithoutChanges",
          Capability.SUMMARY_RETURN_CORRECT, d.workflows::replaceWithoutChanges);
    }
  }

  @Component
  public static class RequestRateCorrection extends StateCommand {
    public RequestRateCorrection(Deps d) {
      super(d, SummaryButtons.REQUEST_RATE_CORRECTION, "Request Rate Correction",
          "P052/P102 Request Rate Correction — SummaryScaleReturnStateHelper.requestRateCorrection",
          Capability.SUMMARY_RETURN_ADMIN, d.workflows::requestRateCorrection);
    }
  }

  @Component
  public static class RejectRateCorrection extends StateCommand {
    public RejectRateCorrection(Deps d) {
      super(d, SummaryButtons.REJECT_RATE_CORRECTION, "Reject Rate Correction",
          "P052/P102 Reject Rate Correction — SummaryScaleReturnStateHelper.rejectRateCorrection",
          Capability.SUMMARY_RATE_CORRECTION_APPROVE, d.workflows::rejectRateCorrection);
    }
  }

  /**
   * Release (P052 Release, P053 Release incl. the CWC/RCP confirmation) and Approve Rate Correction,
   * both {@code SummaryWorkflowManager.release} → {@code Release.execute}.
   */
  public abstract static class ReleaseCommand extends StateCommand {
    private final ObjectProvider<GeneratedSummaryDetails> details;
    private final boolean approval;

    protected ReleaseCommand(Deps d, String button, String label, String legacy, Capability capability,
        boolean approval) {
      super(d, button, label, legacy, capability, (r, u) -> r);
      this.details = d.details;
      this.approval = approval;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> body) {
      SummaryCaller caller = caller();
      SummaryReturn ret = prepare(body, caller);
      if (approval && !details.getObject().isEligibleForRateCorrection(ret)) {
        // P100Action "Yes" on Approve: isEligibleForRateCorrection(dcn, 1)
        throw refuse(SummaryReturnMessages.RATE_CORRECTION_CONFIRM_NO_DETAILS);
      }
      SummaryReturnWorkflows.ReleaseOutcome outcome = workflows.release(ret, audit.user());
      Map<String, Object> out = result(outcome.active());
      out.put("editErrors", outcome.errors().stream().map(e -> e.category() + " " + e.message()).toList());
      out.put("messages", outcome.messages());
      return out;
    }
  }

  @Component
  public static class Release extends ReleaseCommand {
    public Release(Deps d) {
      super(d, SummaryButtons.RELEASE, "Release", "P052/P053 Release — SummaryWorkflowManager.release (Release.execute)",
          Capability.SUMMARY_RETURN_EDIT, false);
    }
  }

  @Component
  public static class ApproveRateCorrection extends ReleaseCommand {
    public ApproveRateCorrection(Deps d) {
      super(d, SummaryButtons.APPROVE_RATE_CORRECTION, "Approve Rate Correction",
          "P052/P102 Approve Rate Correction — Release.execute with extendRateCorrectionDetails",
          Capability.SUMMARY_RATE_CORRECTION_APPROVE, true);
    }
  }

  /** Every command id this file registers. */
  public static final List<String> IDS = List.of("summaryReturns.hold", "summaryReturns.release",
      "summaryReturns.discard", "summaryReturns.cancel", "summaryReturns.cancelReplaceWithChanges",
      "summaryReturns.cancelReplaceWithoutChanges", "summaryReturns.replaceWithChanges",
      "summaryReturns.replaceWithoutChanges", "summaryReturns.requestRateCorrection",
      "summaryReturns.approveRateCorrection", "summaryReturns.rejectRateCorrection");
}
