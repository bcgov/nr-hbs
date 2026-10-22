package ca.bc.gov.nrs.hbs.api.sampling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Sample-plan statistics, ported line for line from
 * {@code $E/util/math/Statistics.java} (the formulas behind P881 "Save &amp;
 * Calculate"). Double arithmetic, the {@code new BigDecimal(double)} rounding
 * steps and the 0.499999 / 0.9999 / 0.999999 fudge terms are kept exactly so
 * the stored STRATUM_PLAN / SAMPLE_PLAN calculations match what the legacy
 * app wrote (legacy batch B2022/B2024 and the comparison screens read them).
 */
public final class SamplingStatistics {

  private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
  private static final double STUDENT_TEE = 1.96;
  public static final String OPTIMUM = "optimum";
  public static final String TARGET = "target";

  private SamplingStatistics() {
  }

  /** {@code Statistics.calculateSampleLoadsConstraint}. */
  public static long calculateSampleLoadsConstraint(double estimatedVolume, long estimatedLoads,
      double estimatedStdDev, double precisionConstraint, long sampleFloor) {
    double temp1 = estimatedStdDev * Math.sqrt(1.0 * estimatedLoads) * STUDENT_TEE;
    double temp2 = (precisionConstraint / 100) * estimatedVolume;
    double temp3 = Math.pow(temp1 / temp2, 2);
    double sampleConst = ((temp3 * estimatedLoads) / (1 + temp3)) + 0.499999;
    long sampleLoadConstraint = new BigDecimal(sampleConst).setScale(0, ROUNDING_MODE).longValue();
    if (sampleLoadConstraint < sampleFloor) {
      sampleLoadConstraint = sampleFloor;
    } else if (sampleLoadConstraint > estimatedLoads) {
      sampleLoadConstraint = estimatedLoads;
    }
    return sampleLoadConstraint;
  }

  /** {@code Statistics.calculateFrequency} (BT-7103: 0 when either side is 0). */
  public static long calculateFrequency(long estimatedLoads, long sampleLoads) {
    if (estimatedLoads == 0 || sampleLoads == 0) {
      return 0;
    }
    return estimatedLoads / sampleLoads;
  }

  /** {@code Statistics.calculatePrecision}: percent, 2 decimals (HALF_UP on the 4-decimal fraction). */
  public static double calculatePrecision(double estimatedVolume, long estimatedLoads, double estimatedStdDev,
      long sampleLoads) {
    if (estimatedLoads <= sampleLoads) {
      return 0.0;
    }
    double temp = ((1.0 * estimatedLoads) * (estimatedLoads - sampleLoads)) / sampleLoads;
    double prec = (estimatedStdDev * Math.sqrt(temp) * STUDENT_TEE) / estimatedVolume;
    if (prec == 0.0 || Double.isNaN(prec)) {
      return 0.0;
    }
    BigDecimal prectemp = new BigDecimal(prec).setScale(4, ROUNDING_MODE);
    return prectemp.movePointRight(2).doubleValue();
  }

  /** {@code Statistics.calculateOptimumLoads}. */
  public static long calculateOptimumLoads(List<StratumPlanRow> stratums, long estimatedLoads, double estimatedStdDev,
      double precisionLimit, long sampleFloor, String estimateType) {
    double num1 = 0.0;
    long popEstTotalLds = calculatePopEstTotalLds(stratums, estimateType);
    for (StratumPlanRow plan : stratums) {
      double stratEstLds = 1.0 * getStratEstLoads(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize,
          estimateType);
      double stratLoadFrac = stratEstLds / popEstTotalLds;
      num1 += stratLoadFrac * plan.estimatedStandardDeviation;
    }
    double stratLoadFrac = (1.0 * estimatedLoads) / popEstTotalLds;
    double popOptSampleLds = calculateTempPopOptLds(stratums, popEstTotalLds, precisionLimit, estimateType);
    double stratOptSampleLds = (popOptSampleLds * ((stratLoadFrac * estimatedStdDev) / num1)) + 0.9999;
    long optimumSampleLoads = (long) stratOptSampleLds;
    if (optimumSampleLoads < sampleFloor) {
      optimumSampleLoads = sampleFloor;
    } else if (optimumSampleLoads > estimatedLoads) {
      optimumSampleLoads = estimatedLoads;
    }
    return optimumSampleLoads;
  }

  /** {@code Statistics.calculatePopEstTotalLds}. */
  public static long calculatePopEstTotalLds(List<StratumPlanRow> stratums, String estimateType) {
    long popEstTotalLds = 0;
    for (StratumPlanRow plan : stratums) {
      popEstTotalLds += getStratEstLoads(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize,
          estimateType);
    }
    return popEstTotalLds;
  }

  /** {@code Statistics.calculateTempPopOptLds}. */
  public static double calculateTempPopOptLds(List<StratumPlanRow> stratums, long popEstTotalLds, double precLimit,
      String estimateType) {
    double num1 = 0.0;
    double num2 = 0.0;
    double denom2 = 0.0;
    double popEstVol = 0.0;
    for (StratumPlanRow plan : stratums) {
      double stratEstLds = 1.0 * getStratEstLoads(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize,
          estimateType);
      double stratLoadFrac = stratEstLds / popEstTotalLds;
      double sd = plan.estimatedStandardDeviation;
      num1 += stratLoadFrac * sd;
      num2 += (sd * stratEstLds * stratEstLds) / stratLoadFrac;
      denom2 += stratEstLds * sd * sd;
      popEstVol += getStratEstVolume(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize,
          estimateType);
    }
    double denom1 = (Math.pow(precLimit / 100, 2) * Math.pow(popEstVol, 2)) / Math.pow(STUDENT_TEE, 2);
    return (num1 * num2) / (denom1 + denom2);
  }

  /** {@code Statistics.getStratEstLoads}: loads computed from volume / size for "Enter Volume and Size". */
  public static long getStratEstLoads(Double stratEstVolume, Long stratEstLoads, Double stratEstSize,
      String estimateType) {
    if (SamplePlanRow.VOLUME_SIZE_EST.equals(estimateType) && stratEstVolume != 0.0 && stratEstSize != 0.0) {
      BigDecimal temp = new BigDecimal(stratEstVolume / stratEstSize);
      return temp.setScale(0, ROUNDING_MODE).longValue();
    } else if (SamplePlanRow.VOLUME_SIZE_EST.equals(estimateType)) {
      return 0;
    }
    return stratEstLoads;
  }

  /** {@code Statistics.getStratEstSize}: size computed from volume / loads for "Enter Volume and Loads". */
  public static double getStratEstSize(Double stratEstVolume, Long stratEstLoads, Double stratEstSize,
      String estimateType) {
    if (SamplePlanRow.VOLUME_LOADS_EST.equals(estimateType) && stratEstVolume != 0.0 && stratEstLoads != 0) {
      return stratEstVolume / stratEstLoads;
    } else if (SamplePlanRow.VOLUME_LOADS_EST.equals(estimateType)) {
      return 0.0;
    }
    return stratEstSize;
  }

  /** {@code Statistics.getStratEstVolume}: volume computed from loads x size for "Enter Loads and Size". */
  public static double getStratEstVolume(Double stratEstVolume, Long stratEstLoads, Double stratEstSize,
      String estimateType) {
    if (SamplePlanRow.LOADS_SIZE_EST.equals(estimateType)) {
      return stratEstLoads * stratEstSize;
    }
    return stratEstVolume;
  }

  /** {@code Statistics.calculatePopulationVariance}. */
  public static double calculatePopulationVariance(List<StratumPlanRow> stratums, String varianceType) {
    double variance = 0.0;
    for (StratumPlanRow plan : stratums) {
      long sampleLoads = OPTIMUM.equals(varianceType) ? plan.optimumSampleCalc : plan.targetSampleCalc;
      if (sampleLoads > 0) {
        variance += ((1.0 * plan.estimatedLoads) * (plan.estimatedLoads - sampleLoads)) / sampleLoads
            * Math.pow(plan.estimatedStandardDeviation, 2);
      }
    }
    return variance;
  }

  /** {@code Statistics.calculatePopulationPrecision}: null when the variance is 0. */
  public static Double calculatePopulationPrecision(double variance, double estimatedVolume) {
    if (variance > 0.0) {
      double prec = ((((Math.sqrt(variance) * STUDENT_TEE) / estimatedVolume) * 10000) + 0.5) / 10000;
      BigDecimal prectemp = new BigDecimal(prec).setScale(4, ROUNDING_MODE);
      return prectemp.movePointRight(2).doubleValue();
    }
    return null;
  }

  /** {@code Statistics.calculatePopulationEstimatedVolume}. */
  public static double calculatePopulationEstimatedVolume(List<StratumPlanRow> stratums) {
    double volume = 0.0;
    for (StratumPlanRow plan : stratums) {
      volume += plan.estimatedVolume;
    }
    return volume;
  }

  /** {@code Statistics.calculateTargetSampleLoads} (Issue 462 fix: constrained strata). */
  public static long calculateTargetSampleLoads(List<StratumPlanRow> stratumPlans, long sampleFloor,
      StratumPlanRow stratumPlan, String estimateType, double precisionLimit) {
    double num1 = 0.0;
    double num2 = 0.0;
    double yHat = 0.0;
    double yHatPrime = 0.0;
    double denom2 = 0.0;
    double denom3 = 0.0;
    double popConstSamples;
    double popEstTotalLoads = 0.0;
    double targetSampleLoads;

    for (StratumPlanRow plan : stratumPlans) {
      if (plan.sampleCountConstraint == 0) {
        popEstTotalLoads += getStratEstLoads(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize,
            estimateType);
      }
    }
    for (StratumPlanRow plan : stratumPlans) {
      double volume = getStratEstVolume(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize,
          estimateType);
      long loads = getStratEstLoads(plan.estimatedVolume, plan.estimatedLoads, plan.estimatedLoadSize, estimateType);
      double standardDeviation = plan.estimatedStandardDeviation;
      if (plan.sampleCountConstraint == 0) {
        yHat += volume;
        num1 += (loads * standardDeviation) / popEstTotalLoads;
        num2 += standardDeviation * loads * popEstTotalLoads;
        denom2 += loads * Math.pow(standardDeviation, 2);
      } else {
        yHatPrime += volume;
        double tempN = plan.sampleCountConstraint > 0 ? plan.sampleCountConstraint : plan.optimumSampleCalc;
        double tempC = (loads * (loads - tempN)) / tempN;
        denom3 += tempC * Math.pow(standardDeviation, 2);
      }
    }
    double denom1 = (Math.pow(precisionLimit * .01, 2) * Math.pow(yHat + yHatPrime, 2)) / Math.pow(STUDENT_TEE, 2);
    if ((denom1 - denom3 + denom2) <= 0) {
      popConstSamples = 0;
    } else {
      popConstSamples = (num1 * num2) / (denom1 - denom3 + denom2);
    }
    if (stratumPlan.sampleCountConstraint == 0) {
      long loads = getStratEstLoads(stratumPlan.estimatedVolume, stratumPlan.estimatedLoads,
          stratumPlan.estimatedLoadSize, estimateType);
      double standardDeviation = stratumPlan.estimatedStandardDeviation;
      targetSampleLoads = (popConstSamples * ((loads / popEstTotalLoads * standardDeviation) / num1)) + 0.999999;
      if (targetSampleLoads < sampleFloor) {
        targetSampleLoads = sampleFloor;
      } else if (targetSampleLoads > loads) {
        targetSampleLoads = loads;
      }
    } else if (stratumPlan.sampleCountConstraint > 0) {
      targetSampleLoads = stratumPlan.sampleCountConstraint;
    } else {
      targetSampleLoads = stratumPlan.optimumSampleCalc;
    }
    return (long) Double.valueOf(targetSampleLoads).intValue();
  }

  /**
   * {@code PresentationHelper.convertToDouble(double, int)}: HALF_UP to the
   * double's printed scale, then HALF_UP to {@code scale}.
   */
  public static double round(double d, int scale) {
    return new BigDecimal(Double.toString(d)).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }
}
