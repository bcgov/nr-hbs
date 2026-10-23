package ca.bc.gov.nrs.hbs.api.summaryreturns;

import java.util.List;

/**
 * The release edits of a summary return: {@code $E/dac/summary/control/SummaryScaleReturnValidator}
 * and its subclasses {@code PieceScaleValidator}, {@code WeightScaleValidator},
 * {@code SampleScaleValidator} (via {@code SummaryBillableScaleReturnValidator}).
 *
 * <p>Implementations read reference data with SQL only; they never write. They
 * may change the return in memory exactly where the legacy validators did
 * ({@code validateAndMove*} moves input fields into the business columns), and
 * the calling workflow saves those changes in the same transaction.
 */
public interface ReleaseEdits {

  /**
   * {@code SummaryScaleReturnValidator.applyEditsAndValidate()}:
   * {@code validateAndMoveInputFields()}, {@code validateEventDate()}, {@code validate()}.
   *
   * @return the edit errors (empty when the return may be released)
   */
  List<EditError> applyEditsAndValidate(SummaryReturn ret);

  /** {@code SummaryScaleReturnValidator.applyEditsForClient()} (generated returns released from the UI, BT-9202). */
  List<EditError> applyEditsForClient(SummaryReturn ret);

  /**
   * BT15212, {@code SummaryScaleReturnManagerBean.generateAreaBasedSegregationsAndNotation} followed by
   * {@code PieceScaleValidator.validateGeneratedSegregations()}: replaces the segregations of an
   * area-based piece return with the cruise-based species/grade ratios × area cut, sets the log
   * count, adds or updates notation 98, and returns the errors of the generated segregations.
   * New rows carry a null {@code segregation_id} / {@code ntn_id}; the writer assigns ids.
   */
  List<EditError> generateAreaBasedSegregations(SummaryReturn ret, String user);
}
