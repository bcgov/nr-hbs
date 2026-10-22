package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.math.BigDecimal;

/**
 * The detail-document side of the generated-summary actions: a generated summary
 * return (B1031 summarized detail returns into it) is corrected or discarded together
 * with the log tallies / weigh slips / SFP tallies / sample tallies behind it.
 *
 * <p>Implemented in {@code summaryreturns.generated}: the summarization-side detail
 * transitions (lock / fail / reset, rate-correction extension, txn status) that the
 * detail-return package does not model, plus — where legacy ran the detail workflow
 * itself (a replacement-pending version after a reset) — the real
 * {@code detailreturns.DetailWorkflow}.
 */
public interface GeneratedSummaryDetails {

  /**
   * {@code DetailManagerBean.isEligibleForRateCorrection(dcn, version)}: the generated return has
   * detail documents that can be rate-corrected ({@code findCorrectable*}).
   */
  boolean isEligibleForRateCorrection(SummaryReturn generated);

  /**
   * {@code SummaryScaleReturnStateHelper.discardGeneratedFailed}: the detail documents of the
   * generated version ({@code find*DocumentsByGeneratedSummary}) must all be LCK
   * ({@code assertDetailsInLockedState}) and are reset ({@code WorkflowManager.reset}: latest txn
   * FAL, state fail → reset, correction-pending / cancelled companions reset, then a
   * replacement-pending version is processed by the detail-return workflow).
   *
   * @throws ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException when there are no detail
   *         documents or one is not locked (the legacy exception texts)
   */
  void resetDetailDocuments(SummaryReturn generated, String user);

  /**
   * {@code Release.extendRateCorrectionDetails} → {@code SummarizationManager.extendRateCorrection}:
   * for a generated CWR/AWP version being approved, each correctable detail document
   * ({@code findCorrectable*(dcn, 1)}) is moved through cancel-and-replace-without-changes and gets a
   * cancelling txn against {@code awaitingApproval}; the detail documents (LCK) and txns are saved,
   * and the summary version's children are rebuilt and saved
   * ({@code updateGeneratedSummaryScaleReturnAndChildren}). The caller then reloads the version
   * (legacy {@code refreshScaleReturn}) and runs the release.
   */
  void extendRateCorrection(SummaryReturn awaitingApproval, String user);

  /** Prorated volume and value of the correctable details' latest txn segregations (P054 preview). */
  Amounts correctableAmounts(SummaryReturn generated);

  record Amounts(BigDecimal volume, BigDecimal value) {}
}
