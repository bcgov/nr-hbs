package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Lookups only the log / sample / SFP tally edits ({@link LogTallyValidator},
 * {@link SampleLogTallyValidator} log lines, {@link SfpTallyValidator}) need:
 * species-product-grade cross references, coastal / interior grade schedules,
 * field deck ids and check-scale originals. Production: {@link JdbcTallyLookups}.
 */
public interface TallyLookups {

  /**
   * A SPECIES_PRODUCT_GRADE_XREF row ({@code infrastructure.codetable.SpeciesProductGrade}),
   * dates as stored (callers apply {@link ScaledLogXref#startDay}/{@link ScaledLogXref#endDay}).
   */
  record SpeciesProductGradeRef(String scaleProductCode, String scaleGradeCode, String scaleSpeciesCode,
                                Date effectiveDate, Date expiryDate) {
  }

  /** A COAST_SCALE_GRADE_CODE / INTERIOR_SCALE_GRADE_CODE row (dates as stored). */
  record ScaleGradeRef(String scaleGradeCode, Date effectiveDate, Date expiryDate) {
  }

  /**
   * {@code CodeReader.findSpeciesProductGradeByPrimaryKey(product, grade, species)}
   * ({@code SpeciesProductGradeQuery.selectByPK}); empty = SpeciesProductGradeNotFoundException.
   */
  Optional<SpeciesProductGradeRef> speciesProductGrade(String scaleProductCode, String scaleGradeCode,
                                                       String scaleSpeciesCode);

  /**
   * {@code CodeReader.findCostalorInteriorScaleGradeByPrimaryKey(grade, "C"|"I")}
   * ({@code ScaleGradeDAO.getScaleGradeInfo}): the grade in the coastal or
   * interior grade table; empty when not on file.
   */
  Optional<ScaleGradeRef> scheduleGrade(String scaleGradeCode, String gradeSchedule);

  /**
   * {@code SummaryPieceScaleReturnVersionEntityHome.findFieldDeckId(deckId, mark)}
   * ({@code SummaryPieceScaleReturnVersionQuery.selectFieldDeckId}, BT 8253): the
   * number of active field-scale estimate summaries for that deck and mark.
   */
  int fieldDeckCount(String fieldScaleDeckId, String timberMark);

  /**
   * {@code DetailManagerBean.findActiveDocumentByScalerAndReturnNumber}: the
   * DDNs of the active versions of {@code type} by that primary scaler and
   * return number ({@code LogTallyQuery} / {@code SampleLogTallyQuery} /
   * {@code SpecialForestProductTallyQuery.selectByScalerAndReturnNumber}). Legacy
   * threw "not found" on none and "multiple" on more than one; the caller maps
   * those to CHKS01 / CHKS02.
   *
   * @throws IllegalArgumentException for a type other than P, S or F (as legacy)
   */
  List<String> activeOriginalsByScalerAndReturnNumber(DocType type, String scalerLicense, Long returnNumber,
                                                      Date checkScaleDate);
}
