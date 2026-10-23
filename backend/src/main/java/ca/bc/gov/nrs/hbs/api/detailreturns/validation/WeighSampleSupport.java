package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.PopulationRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.ReferenceData.StratumRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Optional;

/**
 * Small legacy helpers shared by {@link WeighSlipValidator} and
 * {@link SampleLogTallyValidator}: the {@code findStratum} / {@code findPopulation}
 * rules of {@code DetailScaleDocumentValidator}, {@code StringUtil.isAlphaNumeric},
 * {@code StatisticsCalculator.calculateRatio} and date comparisons on
 * {@code java.util.Date} values.
 */
final class WeighSampleSupport {

  /** {@code StatisticsCalculator.VOLUME_SCALE} / {@code RATIO_SCALE} / {@code ROUNDING_MODE}. */
  static final int VOLUME_SCALE = 3;
  static final int RATIO_SCALE = 5;
  static final BigDecimal TEN = BigDecimal.TEN;

  private WeighSampleSupport() {
  }

  /**
   * {@code DetailScaleDocumentValidator.findStratum}: empty (StratumNotFoundException)
   * when the population or stratum is null or only whitespace
   * ({@code StringUtil.empty(s, false)} — this one trims, BT 11263) or the year
   * is null, otherwise {@code SrtsManager.findStratumByPrimaryKey}. The legacy
   * static one-entry cache is not ported (it only saved a lookup).
   */
  static Optional<StratumRef> findStratum(ReferenceData ref, String population, String stratum, Long year) {
    if (DetailValidator.blank(population) || DetailValidator.blank(stratum) || year == null) {
      return Optional.empty();
    }
    return ref.stratum(population, stratum, year);
  }

  /**
   * {@code DetailScaleDocumentValidator.findPopulation}: legacy turned
   * PopulationNotFoundException into a runtime {@code HbsDetailedEjbException}
   * ("getPopulation"), aborting the edits; kept.
   */
  static PopulationRef findPopulation(ReferenceData ref, String population, Long year) {
    return ref.population(population, year).orElseThrow(() ->
        new IllegalStateException("getPopulation: population not found " + population + "/" + year));
  }

  /** {@code StringUtil.isAlphaNumeric}: non-empty (no trim) and every character a letter or digit. */
  static boolean isAlphaNumeric(String s) {
    if (s == null || s.isEmpty()) return false;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isLetterOrDigit(s.charAt(i))) return false;
    }
    return true;
  }

  /**
   * {@code StatisticsCalculator.calculateRatio(volume, weight)}: m³ per tonne
   * (weight in kg moved three places left), volume rounded to 3 places,
   * ratio to 5 places (HALF_UP); a zero weight gives 0.
   *
   * @return false when the ratio is 10 or more (legacy {@code InvalidRatioException})
   */
  static boolean isValidRatio(BigDecimal volume, BigDecimal weightKg) {
    BigDecimal weight = weightKg.movePointLeft(3);
    if (BigDecimal.ZERO.compareTo(weight) == 0) {
      return true;
    }
    BigDecimal ratio = volume.setScale(VOLUME_SCALE, RoundingMode.HALF_UP).divide(weight, RATIO_SCALE,
        RoundingMode.HALF_UP);
    return ratio.compareTo(TEN) < 0;
  }

  /** {@code Date.before} on millisecond values (Timestamp-safe). */
  static boolean before(Date a, Date b) {
    return a.getTime() < b.getTime();
  }

  /** {@code Date.after} on millisecond values (Timestamp-safe). */
  static boolean after(Date a, Date b) {
    return a.getTime() > b.getTime();
  }

  /** {@code Date.equals} on millisecond values (Timestamp-safe). */
  static boolean same(Date a, Date b) {
    return a.getTime() == b.getTime();
  }
}
