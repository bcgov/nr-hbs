package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.util.List;
import java.util.Optional;

/**
 * Lookups only the weigh slip and sample tally edits ({@link WeighSlipValidator},
 * {@link SampleLogTallyValidator}) need: the sampled weigh slip / sample tally /
 * red-tag cross checks and the field deck checks. One method per legacy
 * finder; production: {@link JdbcWeighSampleLookups}. Strata
 * ({@code findStratum}) and populations ({@code findPopulation}) come from
 * {@link ReferenceData}.
 */
public interface WeighSampleLookups {

  /** The version states the BT 8637/10414/14566 cross checks consider (BT 11361, {@code DacConstants.VERSION_STATUS_MM_ERRORS}). */
  List<String> MM_STATES = List.of("ISS", "RDY", "NTI", "ERR", "HLD", "LCK", "DSF", "AWP");

  /**
   * {@code DetailManager.findSampleLogTallies} (BT 10414,
   * {@code DetailManagerQuery.selectSampleLogTallies}): 1 when more than one
   * active sample tally samples this weigh slip and either none or more than
   * one of them matches the slip's event/mark/PSY/weight (MM05), else 0.
   */
  long sampleTallyConflict(String scaleSite, String origRedtagScaleSite, String weighSlipNumber, String eventType,
                           String timberMark, String population, String stratum, Long samplingYear,
                           Long sampleWeight);

  /**
   * {@code DetailManager.findLogTallies} (BT 8637,
   * {@code DetailManagerQuery.selectLogTallies}): active log tallies of a red
   * tag on this weigh slip (MM06 when more than one).
   */
  long redTagLogTallyCount(String scaleSite, String origRedtagScaleSite, String weighSlipNumber, String eventType);

  /**
   * {@code SummaryPieceScaleReturnVersionEntityHome.findFieldDeckId} (BT 8253,
   * {@code SummaryPieceScaleReturnVersionQuery.selectFieldDeckId}): active
   * field-scale estimate summaries for this deck and mark (FSX02 when none).
   */
  long fieldScaleEstimateCount(String fieldScaleDeckId, String timberMark);

  /**
   * {@code DetailManager.findWeighSlips(scaleSite, originatingScaleSite,
   * weighSlipNumber, eventType, checkReplaceFlag)} (BT 8637/14566,
   * {@code DetailManagerQuery.selectWeighSlips}): count of active weigh slips
   * a sample tally samples.
   */
  long sampledWeighSlipCount(String scaleSite, String originatingScaleSite, String weighSlipNumber,
                             String eventType, boolean checkReplaceFlag);

  /**
   * {@code DetailManager.findWeighSlips(weighSlipNumber, scaleSite)} (BT 14566):
   * the active weigh slip with this number at this site (WEIGH_SLIP row).
   */
  Optional<Row> sampledWeighSlip(String weighSlipNumber, String scaleSite);

  /**
   * {@code WeighSlipEntityHome.findByCriteria(..., scaleSite, pop, strat, year,
   * ..., weighSlipNumber, ...)} as {@code SampleLogTallyValidator.validateStratumDate}
   * (BT 6519) calls it: every version of the weigh slips matching the given
   * (non-null) criteria; rows carry {@code scale_date} and {@code scale_event_type_code}.
   */
  List<Row> weighSlipsByCriteria(String scaleSite, String populationNumber, String stratumNumber,
                                 Long samplingYear, String weighSlipNumber);

  /**
   * {@code DetailManager.getFieldScaleDeckId} (BT 10013,
   * {@code DetailManagerQuery.selectFieldScaleDeckId}): the field scale deck of
   * the one active weigh slip sampled (null when none or several).
   */
  String sampledWeighSlipFieldScaleDeckId(String scaleSite, String weighSlipNumber, String eventType);
}
