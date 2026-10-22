package ca.bc.gov.nrs.hbs.api.summaryreturns.generated;

import ca.bc.gov.nrs.hbs.api.summaryreturns.GeneratedSummaryDetails;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * The detail-document side of the generated-summary actions (see {@link GeneratedSummaryDetails}).
 *
 * <p>Eligibility and the preview amounts are read with {@link CorrectableDetails}, for summary
 * version 1 as every legacy caller passed it ({@code isEligibleForRateCorrection(dcn, new Long(1))},
 * {@code findCorrectable*(dcn, DacConstants.INITIAL_VERSION)}).
 */
@Component
public class LegacyGeneratedSummaryDetails implements GeneratedSummaryDetails {

  /** {@code DacConstants.INITIAL_VERSION}. */
  static final long INITIAL_VERSION = 1;

  private final CorrectableDetails correctable;
  private final DetailDocumentReset reset;
  private final RateCorrectionExtension extension;

  public LegacyGeneratedSummaryDetails(CorrectableDetails correctable, DetailDocumentReset reset,
      RateCorrectionExtension extension) {
    this.correctable = correctable;
    this.reset = reset;
    this.extension = extension;
  }

  @Override
  public boolean isEligibleForRateCorrection(SummaryReturn generated) {
    return correctable.any(generated.type(), generated.dcn(), INITIAL_VERSION);
  }

  @Override
  public void resetDetailDocuments(SummaryReturn generated, String user) {
    reset.reset(generated, user);
  }

  @Override
  public void extendRateCorrection(SummaryReturn awaitingApproval, String user) {
    extension.extend(awaitingApproval, user);
  }

  @Override
  public Amounts correctableAmounts(SummaryReturn generated) {
    // FetchConfirmationDataTag: the displayed return's version (current on request, previous invoiced on approve)
    BigDecimal[] a = correctable.amounts(generated.type(), generated.dcn(), generated.versionNo());
    return new Amounts(a[0], a[1]);
  }
}
