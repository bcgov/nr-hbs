package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.store.DetailStore;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * {@code DetailScaleDocumentValidator.getValidator}: the validator for a
 * document's type, wired with its lookups.
 */
@Component
public class DetailValidators {

  private final ReferenceData ref;
  private final TallyLookups tally;
  private final WeighSampleLookups weighSample;
  private final LedgerLookups ledger;

  public DetailValidators(ReferenceData ref, TallyLookups tally, WeighSampleLookups weighSample,
                          LedgerLookups ledger) {
    this.ref = ref;
    this.tally = tally;
    this.weighSample = weighSample;
    this.ledger = ledger;
  }

  /** {@code DetailScaleDocumentValidator.isBillableMark} (BT5884): not cash sale, cruise or area based. */
  public boolean isBillableMark(String mark) {
    return ref.timberMark(mark).map(m -> !m.isCashSale() && !m.isCruiseBased() && !m.isAreaBased()).orElse(false);
  }

  public DetailValidator of(DetailDocument document, DetailStore store) {
    return of(document, store, DetailValidator.today());
  }

  public DetailValidator of(DetailDocument document, DetailStore store, LocalDate today) {
    ValidationContext ctx = new ValidationContext(ref, store, today);
    return switch (document.type()) {
      case LOG_TALLY -> new LogTallyValidator(document, ctx, tally);
      case WEIGH_SLIP -> new WeighSlipValidator(document, ctx, weighSample);
      case SAMPLE_LOG_TALLY -> new SampleLogTallyValidator(document, ctx, tally, weighSample);
      case SFP_TALLY -> new SfpTallyValidator(document, ctx, tally);
      case ARRIVAL_LEDGER -> new ArrivalLedgerValidator(document, ctx, ledger);
      case DEPARTURE_LEDGER -> new DepartureLedgerValidator(document, ctx, ledger);
    };
  }
}
