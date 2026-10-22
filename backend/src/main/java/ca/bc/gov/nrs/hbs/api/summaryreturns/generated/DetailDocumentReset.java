package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailWorkflow;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The detail side of {@code SummaryScaleReturnStateHelper.discardGeneratedFailed}: the detail
 * documents of the generated summary version are found
 * ({@code DetailManagerBean.find{LogTally,WeighSlip}DocumentsByGeneratedSummary},
 * {@code findSampleLogTallyDocumentByGeneratedSummary}), must all be locked
 * ({@code assertDetailsInLockedState}) and are reset one by one
 * ({@code resetDetailDocuments} → {@code WorkflowManagerBean.reset} → {@code workflow.Reset.execute}):
 *
 * <ol>
 *   <li>the version's latest txn goes to FAL ({@code failDocument} → {@code updateDetailTransaction});</li>
 *   <li>the version LCK → FAL ({@code StateManagerBean.failed} / {@code state.Fail}), then the failure is
 *       cleared ({@code StateManagerBean.reset} / {@code state.Reset}: CIP/CWR/CWC → OCM with the version
 *       ISS, OIP → RDY, RNP / RCP / CCM → AWP);</li>
 *   <li>the document is saved with audit action REQ ({@code AbstractWorkflowCommand.updateDocument});</li>
 *   <li>a cancel-and-replace-with-changes document also resets its correction-pending (PND) version, a
 *       replace-with-changes document its latest cancelled (CAN) version, each saved the same way.</li>
 * </ol>
 *
 * <p>After each reset, legacy {@code WorkflowManagerBean.reset} processed a replacement-pending (PNV)
 * version of the document ({@code processReplacementVersionPending(ddn, document, user)} → {@code process}:
 * the reset version is superseded and the PNV version goes to AWP or through the full detail release).
 * That step runs on the detail-return workflow itself
 * ({@link DetailWorkflow#processReplacementVersionPendingAfterReset}), document by document right after
 * its reset, as legacy did.
 *
 * <p>Generated piece summaries are looked up through their log tallies only, as legacy did: a generated
 * SFP summary has none and is refused with the legacy "Detail documents missing" message (legacy
 * {@code updateDetailTransaction} did not support SFP tallies either).
 */
@Component
public class DetailDocumentReset {

  /** {@code Constants.ACTION_TYPE_REQUEST}: the action type of every {@code AbstractWorkflowCommand}. */
  static final String ACTION_REQUEST = "REQ";

  /** The detail-return workflow step that follows each reset (tests record it). */
  @FunctionalInterface
  interface PendingReplacement {
    void afterReset(String ddn, long version, String user);
  }

  private final DetailDocumentRepository repo;
  private final DetailDocumentWriter writer;
  private final PendingReplacement pending;

  @Autowired
  public DetailDocumentReset(DetailDocumentRepository repo, DetailDocumentWriter writer, DetailWorkflow workflow) {
    this(repo, writer, workflow::processReplacementVersionPendingAfterReset);
  }

  DetailDocumentReset(DetailDocumentRepository repo, DetailDocumentWriter writer, PendingReplacement pending) {
    this.repo = repo;
    this.writer = writer;
    this.pending = pending;
  }

  /** The family {@code discardGeneratedFailed} looked the details up in, by summary return type. */
  static DetailFamily family(SummaryReturn generated) {
    return switch (generated.type()) {
      case PIECE -> DetailFamily.LOG_TALLY;
      case WEIGHT -> DetailFamily.WEIGH_SLIP;
      case SAMPLE -> DetailFamily.SAMPLE_LOG_TALLY;
    };
  }

  public void reset(SummaryReturn generated, String user) {
    DetailFamily f = family(generated);
    List<DetailDocument> docs = repo.keysByGeneratedSummary(f, generated.dcn(), generated.versionNo()).stream()
        .map(k -> repo.load(f, k)).toList();
    if (docs.isEmpty()) {
      throw new WorkflowValidationException(null,
          "Detail documents missing for generated summary " + generated.dcn());
    }
    assertDetailsInLockedState(docs);
    for (DetailDocument d : docs) {
      reset(d, user);
      // WorkflowManagerBean.reset: processReplacementVersionPending(ddn, document, user)
      try {
        pending.afterReset(d.ddn(), d.versionNo(), user);
      } catch (IllegalStateException ex) { // DetailWorkflow.WorkflowException / illegal state transitions
        throw new WorkflowValidationException(null, ex.getMessage());
      }
    }
  }

  /** {@code SummaryScaleReturnStateHelper.assertDetailsInLockedState}. */
  static void assertDetailsInLockedState(List<DetailDocument> docs) {
    for (DetailDocument d : docs) {
      if (!d.state.versionIs(DetailState.LCK)) {
        throw new WorkflowValidationException(null, "Detail scale document " + d.ddn()
            + " version status should be locked not " + d.state.versionText());
      }
    }
  }

  /** {@code workflow.Reset.execute}. */
  void reset(DetailDocument d, String user) {
    String initialState = d.state.doc();

    // failDocument: the latest txn fails, then the version
    d.latestTxn().ifPresent(txn -> {
      txn.set("hbs_txn_status_code", "FAL"); // TransactionCodes.Status.FAILED
      writer.storeTxn(d.family, txn);
    });
    transition(d, d.state::summarizationFailed);
    transition(d, d.state::reset);
    writer.update(d, user, ACTION_REQUEST);

    if (DetailState.CWC.equals(initialState)) {
      // findDocumentAndCorrectionPendingVersion: re-read, so the document row carries the state just saved
      DetailDocument correction = loadSingle(d, DetailState.PND, false);
      transition(correction, correction.state::reset);
      writer.update(correction, user, ACTION_REQUEST);
    } else if (DetailState.RCP.equals(initialState)) {
      DetailDocument previous = loadSingle(d, DetailState.CAN, true);
      transition(previous, previous.state::reset);
      writer.update(previous, user, ACTION_REQUEST);
    }
  }

  /**
   * {@code findDocumentAndCorrectionPendingVersion} (exactly one PND version) /
   * {@code findDocumentAndLatestCancelledVersion} (the highest CAN version).
   */
  private DetailDocument loadSingle(DetailDocument d, String state, boolean highest) {
    List<Row> versions = repo.versionsInState(d.family, d.ddn(), state);
    if (versions.isEmpty() || (!highest && versions.size() > 1)) {
      throw new WorkflowValidationException(null, versions.isEmpty()
          ? "Detail scale document " + d.ddn() + " not found"
          : "Multiple correction pending versions found");
    }
    Row v = versions.get(versions.size() - 1);
    return repo.load(d.family, new DetailDocumentRepository.Key(d.ddn(), v.lng("version")));
  }

  /** A legacy state transition; its IllegalStateException / StateException text becomes the validation message. */
  static void transition(DetailDocument d, Runnable op) {
    try {
      op.run();
    } catch (IllegalStateException ex) {
      throw new WorkflowValidationException(null, ex.getMessage() + "; document=[" + d + "]");
    }
  }
}
