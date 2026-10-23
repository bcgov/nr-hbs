package ca.bc.gov.nrs.hbs.api.sampling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Table-driven checks of the ported {@code Statistics} formulas, the P881
 * calculation and the P897 strata date scaling. Expected values come from an
 * independent re-computation of the legacy formulas (same double arithmetic and
 * {@code new BigDecimal(double)} rounding).
 */
class SamplingStatisticsTest {

  @ParameterizedTest
  @CsvSource({
      // volume, loads, stddev, samples, expected precision %
      "1000.0, 100, 2.0, 10, 11.76",
      "50000.0, 2000, 3.0, 300, 1.25",
      "30000.0, 1000, 4.0, 200, 1.65",
      "1000.0, 100, 2.0, 100, 0.0",     // loads <= samples
      "1000.0, 100, 0.0, 10, 0.0",      // zero precision
  })
  void precision(double volume, long loads, double sd, long samples, double expected) {
    assertThat(SamplingStatistics.calculatePrecision(volume, loads, sd, samples)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
      // volume, loads, stddev, precision constraint, floor, expected samples
      "1000.0, 100, 2.0, 5.0, 2, 39",
      "1000.0, 100, 2.0, 50.0, 10, 10",  // below the floor
      "1000.0, 100, 20.0, 0.5, 2, 100",  // capped at the loads
  })
  void sampleLoadsConstraint(double volume, long loads, double sd, double pc, long floor, long expected) {
    assertThat(SamplingStatistics.calculateSampleLoadsConstraint(volume, loads, sd, pc, floor)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"2000, 37, 54", "2000, 0, 0", "0, 5, 0"})
  void frequency(long loads, long samples, long expected) {
    assertThat(SamplingStatistics.calculateFrequency(loads, samples)).isEqualTo(expected);
  }

  @Test
  void estimateMethodsComputeTheMissingValue() {
    assertThat(SamplingStatistics.getStratEstLoads(1000.0, 0L, 7.0, "VS")).isEqualTo(143);
    assertThat(SamplingStatistics.getStratEstLoads(0.0, 9L, 7.0, "VS")).isZero();
    assertThat(SamplingStatistics.getStratEstLoads(1000.0, 9L, 7.0, "VL")).isEqualTo(9);
    assertThat(SamplingStatistics.getStratEstSize(1000.0, 40L, 7.0, "VL")).isEqualTo(25.0);
    assertThat(SamplingStatistics.getStratEstVolume(1000.0, 40L, 7.5, "LS")).isEqualTo(300.0);
    assertThat(SamplingStatistics.round(26.6665, 3)).isEqualTo(26.667);
  }

  private static StratumPlanRow row(String number, double volume, long loads, double size, double sd, long sc,
      double pc) {
    StratumPlanRow r = new StratumPlanRow();
    r.stratumNumber = number;
    r.estimatedVolume = volume;
    r.estimatedLoads = loads;
    r.estimatedLoadSize = size;
    r.estimatedStandardDeviation = sd;
    r.sampleCountConstraint = sc;
    r.precisionConstraint = pc;
    r.optimumSampleCalc = 0L;
    r.effectiveDate = LocalDate.of(2026, 1, 1).atStartOfDay();
    return r;
  }

  private static long[] results(StratumPlanRow r) {
    return new long[] {r.optimumSampleCalc, Math.round(r.optimumPrecisionCalc * 100), r.optimumFrequencyCalc,
        r.sampleCountConstraint, Math.round(r.precisionConstraint * 100), r.targetSampleCalc,
        Math.round(r.targetPrecisionCalc * 100), r.targetFrequencyCalc};
  }

  @Test
  void saveAndCalculateVolumeAndLoadsConstrainPrecision() {
    List<StratumPlanRow> strata = List.of(row("01", 50000, 2000, 0, 3.0, 0, 0), row("02", 30000, 1000, 0, 4.0, 0, 0));
    Problems e = new Problems();
    PlanCalculator.calculateAll(strata, new PlanCalculator.PlanInputs("VL", "P", 1.0, 10, "S"), e);
    assertThat(e.isEmpty()).isTrue();
    assertThat(strata.get(0).estimatedLoadSize).isEqualTo(25.0);
    assertThat(results(strata.get(0))).containsExactly(300, 125, 6, 0, 0, 300, 125, 6);
    assertThat(results(strata.get(1))).containsExactly(200, 165, 5, 0, 0, 200, 165, 5);
    assertThat(PlanCalculator.populationPrecisions(strata)).containsExactly(1.0, 1.0);
  }

  @Test
  void laterStrataSeeTheConstraintComputedForEarlierOnes() {
    List<StratumPlanRow> strata = List.of(row("01", 50000, 2000, 0, 3.0, 0, 2.0), row("02", 30000, 1000, 0, 4.0, 0, 0));
    Problems e = new Problems();
    PlanCalculator.calculateAll(strata, new PlanCalculator.PlanInputs("VL", "P", 1.0, 10, "S"), e);
    assertThat(results(strata.get(0))).containsExactly(300, 125, 6, 130, 200, 130, 199, 15);
    assertThat(results(strata.get(1))).containsExactly(200, 165, 5, 0, 0, 10, 822, 100);
    assertThat(PlanCalculator.populationPrecisions(strata)).containsExactly(1.0, 3.33);
  }

  @Test
  void loadsAndSizeConstrainSamples() {
    List<StratumPlanRow> strata = List.of(row("01", 0, 400, 25.0, 3.0, 50, 0), row("02", 0, 300, 20.0, 2.5, 0, 0));
    Problems e = new Problems();
    PlanCalculator.calculateAll(strata, new PlanCalculator.PlanInputs("LS", "S", 2.0, 10, "S"), e);
    assertThat(strata.get(0).estimatedVolume).isEqualTo(10000.0);
    assertThat(strata.get(1).estimatedVolume).isEqualTo(6000.0);
    assertThat(results(strata.get(0))).containsExactly(73, 249, 5, 50, 311, 50, 311, 8);
    assertThat(results(strata.get(1))).containsExactly(46, 332, 6, 0, 0, 169, 125, 1);
    assertThat(PlanCalculator.populationPrecisions(strata)).containsExactly(2.0, 2.0);
  }

  @Test
  void computedValuesAreValidated() {
    List<StratumPlanRow> strata = List.of(row("01", 5, 100, 0, 3.0, 0, 0));
    Problems e = new Problems();
    PlanCalculator.calculateAll(strata, new PlanCalculator.PlanInputs("VL", "P", 1.0, 10, "S"), e);
    assertThat(e.list()).extracting(p -> p.message())
        .contains(SamplingMessages.CALC_SIZE_TOO_SMALL, SamplingMessages.CALC_SIZE_LT_STDDEV);
  }

  @ParameterizedTest
  @CsvSource({
      // field to break, expected message
      "volumeNull, Estimated Volume must be a number and have 3 or less decimal places.",
      "volumeSmall, Estimated Volume cannot be less than 10 M3.",
      "volumeBig, 'Estimated Volume cannot be greater than 9,999,999.999. '",
      "loadsFloor, Estimated Loads cannot be less than Sample Floor.",
      "sdSmall, Estimated Standard deviation cannot be less than 0.10 M3.",
      "scOverLoads, Constrained Samples cannot be greater than Estimated Loads.",
      "pcBig, Constrained Precision cannot be greater than 99.99%.",
  })
  void estimateRules(String broken, String message) {
    StratumPlanRow r = row("01", 50000, 2000, 25, 3.0, 0, 0);
    switch (broken) {
      case "volumeNull" -> r.estimatedVolume = null;
      case "volumeSmall" -> r.estimatedVolume = 9.5;
      case "volumeBig" -> r.estimatedVolume = 10000000.0;
      case "loadsFloor" -> r.estimatedLoads = 5L;
      case "sdSmall" -> r.estimatedStandardDeviation = 0.05;
      case "scOverLoads" -> r.sampleCountConstraint = 2001L;
      default -> r.precisionConstraint = 100.0;
    }
    Problems e = new Problems();
    PlanCalculator.validateEstimates(r, 0, new PlanCalculator.PlanInputs("VL", "P", 1.0, 10, "S"), e);
    assertThat(e.list()).extracting(p -> p.message()).containsExactly(message);
  }

  @Test
  void defaultRatioPlansSkipTheSamplingRules() {
    StratumPlanRow r = row("01", 50000, 1, 25, 0.0, 0, 0);
    Problems e = new Problems();
    PlanCalculator.validateEstimates(r, 0, new PlanCalculator.PlanInputs("VL", "P", 0.0, 0, "D"), e);
    assertThat(e.isEmpty()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
      // new plan, old plan, old stratum → new stratum (P897 CalculateNewStrataDate)
      "2026-04-01, 2027-03-31, 2025-04-01, 2026-03-31, 2025-07-01, 2025-12-31, 2026-07-01, 2026-12-31",
      "2026-04-01, 2026-09-30, 2025-04-01, 2026-03-31, 2025-07-01, 2025-12-31, 2026-05-01, 2026-08-31",
      "2026-01-01, 2026-12-31, 2025-01-01, 2025-12-31, 2025-01-01, 2025-12-31, 2026-01-01, 2026-12-31",
  })
  void strataDatesScaleIntoTheNewPlan(LocalDate newEff, LocalDate newExp, LocalDate oldEff, LocalDate oldExp,
      LocalDate strataEff, LocalDate strataExp, LocalDate expectEff, LocalDate expectExp) {
    LocalDate eff = StrataDates.scaledDate(newEff, newExp, oldEff, oldExp, strataEff, strataExp, true);
    assertThat(eff).isEqualTo(expectEff);
    assertThat(StrataDates.scaledDate(newEff, newExp, oldEff, oldExp, eff, strataExp, false)).isEqualTo(expectExp);
  }
}
