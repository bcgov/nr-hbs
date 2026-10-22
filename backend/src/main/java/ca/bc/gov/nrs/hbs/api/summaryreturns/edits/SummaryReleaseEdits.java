package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.ReleaseEdits;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The summary-return release edits: {@code SummaryScaleReturnValidator.getValidator} dispatching to
 * {@link PieceEdits} ({@code PieceScaleValidator}), {@link WeightEdits} ({@code WeightScaleValidator})
 * and {@link SampleEdits} ({@code SampleScaleValidator}). Reads go through {@link Lookups} and
 * {@link Rates}; nothing is written. Where the legacy validator failed with a runtime exception
 * (a "previously validated" mark, scale site or stratum missing, a null it dereferenced) a
 * {@link ReleaseEditException} is thrown so the release rolls back, as it did in legacy.
 */
@Component
public class SummaryReleaseEdits implements ReleaseEdits {

  private final Lookups lookups;
  private final Rates rates;
  private final Clock clock;

  @Autowired
  public SummaryReleaseEdits(Lookups lookups, Rates rates) {
    this(lookups, rates, Clock.systemDefaultZone());
  }

  public SummaryReleaseEdits(Lookups lookups, Rates rates, Clock clock) {
    this.lookups = lookups;
    this.rates = rates;
    this.clock = clock;
  }

  private ReturnEdits validator(SummaryReturn ret) {
    return switch (ret.type()) {
      case PIECE -> new PieceEdits(ret, lookups, rates, clock);
      case WEIGHT -> new WeightEdits(ret, lookups, rates, clock);
      case SAMPLE -> new SampleEdits(ret, lookups, clock);
    };
  }

  @Override
  public List<EditError> applyEditsAndValidate(SummaryReturn ret) {
    return List.copyOf(validator(ret).applyEditsAndValidate());
  }

  @Override
  public List<EditError> applyEditsForClient(SummaryReturn ret) {
    return List.copyOf(validator(ret).applyEditsForClient());
  }

  /**
   * BT15212 ({@code Release.execute}): generates the segregations and notation 50 of an area-based
   * piece return, then {@code PieceScaleValidator.validateGeneratedSegregations}. Errors on a
   * generated segregation carry a null segregation id (the rows are not saved yet). Any other
   * return is left unchanged.
   *
   * <p>Legacy note: {@code Release.execute} discarded the result of
   * {@code validateGeneratedSegregations} (the errors were never saved); the contract here returns
   * them and the caller decides.
   */
  @Override
  public List<EditError> generateAreaBasedSegregations(SummaryReturn ret, String user) {
    if (ret.type() != SummaryReturnType.PIECE
        || !SummaryReturnRules.PSSMRY_AREA_TYPE.equals(ret.version().str("pssmry_type"))) {
      return List.of();
    }
    new AreaBasedSegregations(lookups, clock).generate(ret, user);
    return validateGeneratedSegregations(ret);
  }

  /**
   * {@code PieceScaleValidator.validateGeneratedSegregations} alone: the segregation edits of a piece
   * return, e.g. again after the writer gave the generated segregations their ids.
   */
  public List<EditError> validateGeneratedSegregations(SummaryReturn ret) {
    return List.copyOf(new PieceEdits(ret, lookups, rates, clock).validateGeneratedSegregations());
  }
}
