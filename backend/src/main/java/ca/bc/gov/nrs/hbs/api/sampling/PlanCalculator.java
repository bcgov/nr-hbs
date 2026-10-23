package ca.bc.gov.nrs.hbs.api.sampling;

import static ca.bc.gov.nrs.hbs.api.sampling.SamplingMessages.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * P881 "Save &amp; Calculate": the per-stratum estimate validation
 * ({@code SamplePlanValidator.validateStratumPlan}), the calculation
 * ({@code P891Action.doCalculations}), the result-size check
 * ({@code SamplePlanValidator.validateFieldLengths}) and the population
 * precision ({@code P891Action.updatePlan}). Pure functions over the rows.
 */
final class PlanCalculator {

  private PlanCalculator() {
  }

  /** Plan-level inputs of the calculation. */
  record PlanInputs(String estimateType, String constraintType, double precisionLimit, long sampleFloor,
      String ratioTypeCode) {}

  /** {@code SamplePlanValidator.validateStratumPlan(stratPlan, …)} — estimate fields of one row. */
  static void validateEstimates(StratumPlanRow sp, int row, PlanInputs in, Problems e) {
    String est = in.estimateType();
    String con = in.constraintType();
    long floor = in.sampleFloor();
    boolean sampleRatio = SamplePlanRow.SAMPLE_RATIO_TYPE.equals(in.ratioTypeCode());

    if (sp.estimatedVolume == null) {
      e.add("estimatedVolume_" + row, numberDecimals("Estimated Volume", 3));
    } else if (sp.estimatedVolume > 9999999.999) {
      e.add("estimatedVolume_" + row, EST_VOLUME_INVALID);
    } else if (!decimalsOk(sp.estimatedVolume, 3)) {
      e.add("estimatedVolume_" + row, numberDecimals("Estimated Volume", 3));
    } else if (sp.estimatedVolume < 10.00 && !SamplePlanRow.LOADS_SIZE_EST.equals(est)) {
      e.add("estimatedVolume_" + row, lessThan("Estimated Volume", "10 M3"));
    }

    if (sp.estimatedLoads == null || sp.estimatedLoads < 0) {
      e.add("estimatedLoads_" + row, EST_LOADS_INVALID);
    } else if (sampleRatio && sp.estimatedLoads < floor && !SamplePlanRow.VOLUME_SIZE_EST.equals(est)) {
      e.add("estimatedLoads_" + row, lessThan("Estimated Loads", "Sample Floor"));
    }

    if (sp.estimatedLoadSize == null) {
      e.add("estimatedSize_" + row, numberDecimals("Estimated Size", 3));
    } else if (!decimalsOk(sp.estimatedLoadSize, 3)) {
      e.add("estimatedSize_" + row, numberDecimals("Estimated Size", 3));
    } else if (sp.estimatedLoadSize < 1.0 && !SamplePlanRow.VOLUME_LOADS_EST.equals(est)) {
      e.add("estimatedSize_" + row, lessThan("Estimated Size", "1.000 M3"));
    }

    if (sampleRatio) {
      Double sd = sp.estimatedStandardDeviation;
      if (sd == null) {
        e.add("estimatedDeviation_" + row, numberDecimals("Estimated Standard Deviation", 2));
      } else if (sd < 0.10) {
        e.add("estimatedDeviation_" + row, lessThan("Estimated Standard deviation", "0.10 M3"));
      } else if (!decimalsOk(sd, 2)) {
        e.add("estimatedDeviation_" + row, numberDecimals("Estimated Standard Deviation", 2));
      } else if (!(sd > 0.0)) {
        e.add("estimatedDeviation_" + row, EST_STDDEV_INVALID);
      } else if (!SamplePlanRow.VOLUME_LOADS_EST.equals(est) && sp.estimatedLoadSize != null
          && sd > sp.estimatedLoadSize) {
        e.add("estimatedDeviation_" + row, EST_STDDEV_GT_SIZE);
      }

      Long sc = sp.sampleCountConstraint;
      if (sc == null) {
        e.add("sampleConstraint_" + row, SAMPLE_CONSTRAINT_INVALID);
      } else if (sc < 0 && SamplePlanRow.SAMPLES_CONSTRAINT.equals(con)) {
        e.add("sampleConstraint_" + row, lessThan("Constrained Samples", "0"));
      } else if (sc > 0 && SamplePlanRow.SAMPLES_CONSTRAINT.equals(con) && sc < floor) {
        e.add("sampleConstraint_" + row, lessThan("Constrained Samples", "Sample Floor"));
      } else if (sp.estimatedLoads != null && sc > sp.estimatedLoads) {
        e.add("sampleConstraint_" + row, greaterThan("Constrained Samples", "Estimated Loads"));
      }

      Double pc = sp.precisionConstraint;
      if (pc == null) {
        e.add("precisionConstraint_" + row, numberDecimals("Constrained Precision", 2));
      } else if (pc < 0.0 && SamplePlanRow.PRECISION_CONSTRAINT.equals(con)) {
        e.add("precisionConstraint_" + row, lessThan("Constrained Precision", "0"));
      } else if (pc > 99.99) {
        e.add("precisionConstraint_" + row, greaterThan("Constrained Precision", "99.99%"));
      } else if (!decimalsOk(pc, 2)) {
        e.add("precisionConstraint_" + row, numberDecimals("Constraint Precision", 2));
      }
    }

    if (sp.effectiveDate == null) {
      e.add("effectiveDate_", EFFECT_DATE_NULL);
    }
  }

  /** {@code P891Action.doCalculations} for one row (rows are updated in place, in order). */
  static void doCalculations(StratumPlanRow sp, List<StratumPlanRow> all, PlanInputs in, Problems e,
      boolean validate, int row) {
    String est = in.estimateType();
    long floor = in.sampleFloor();
    double calcVolume = SamplingStatistics.getStratEstVolume(sp.estimatedVolume, sp.estimatedLoads,
        sp.estimatedLoadSize, est);
    long calcLoads = SamplingStatistics.getStratEstLoads(sp.estimatedVolume, sp.estimatedLoads,
        sp.estimatedLoadSize, est);
    double calcSize = SamplingStatistics.getStratEstSize(sp.estimatedVolume, sp.estimatedLoads,
        sp.estimatedLoadSize, est);
    if (validate) {
      if (SamplePlanRow.LOADS_SIZE_EST.equals(est)) {
        if (calcVolume >= 10000000) {
          e.add("estimatedVolume_" + row, CALC_VOLUME_TOO_BIG);
        } else if (calcVolume < 10) {
          e.add("estimatedVolume_" + row, CALC_VOLUME_TOO_SMALL);
        }
        sp.estimatedVolume = SamplingStatistics.round(calcVolume, 3);
      }
      if (SamplePlanRow.VOLUME_SIZE_EST.equals(est)) {
        if (calcLoads < floor) {
          e.add("estimatedLoads_" + row, CALC_LOADS_LT_FLOOR);
        }
        if (sp.sampleCountConstraint > calcLoads) {
          e.add("estimatedLoads_" + row, CALC_LOADS_LT_CONSTRAINT);
        }
        sp.estimatedLoads = calcLoads;
      }
      if (SamplePlanRow.VOLUME_LOADS_EST.equals(est)) {
        if (calcSize < 1) {
          e.add("estimatedSize_" + row, CALC_SIZE_TOO_SMALL);
        }
        if (sp.estimatedStandardDeviation > calcSize) {
          e.add("estimatedSize_" + row, CALC_SIZE_LT_STDDEV);
        }
        sp.estimatedLoadSize = SamplingStatistics.round(calcSize, 3);
      }
    } else {
      sp.estimatedVolume = SamplingStatistics.round(calcVolume, 3);
      sp.estimatedLoads = calcLoads;
      sp.estimatedLoadSize = SamplingStatistics.round(calcSize, 3);
    }

    sp.optimumSampleCalc = SamplingStatistics.calculateOptimumLoads(all, calcLoads, sp.estimatedStandardDeviation,
        in.precisionLimit(), floor, est);
    sp.optimumPrecisionCalc = SamplingStatistics.calculatePrecision(sp.estimatedVolume, sp.estimatedLoads,
        sp.estimatedStandardDeviation, sp.optimumSampleCalc);
    sp.optimumFrequencyCalc = SamplingStatistics.calculateFrequency(sp.estimatedLoads, sp.optimumSampleCalc);

    if (SamplePlanRow.SAMPLES_CONSTRAINT.equals(in.constraintType())) {
      if (sp.sampleCountConstraint == null || sp.sampleCountConstraint == 0) {
        sp.precisionConstraint = 0.0;
      } else {
        sp.precisionConstraint = SamplingStatistics.calculatePrecision(sp.estimatedVolume, sp.estimatedLoads,
            sp.estimatedStandardDeviation, sp.sampleCountConstraint);
      }
    } else {
      if (sp.precisionConstraint == null || sp.precisionConstraint == 0.0) {
        sp.sampleCountConstraint = 0L;
      } else {
        sp.sampleCountConstraint = SamplingStatistics.calculateSampleLoadsConstraint(sp.estimatedVolume,
            sp.estimatedLoads, sp.estimatedStandardDeviation, sp.precisionConstraint, floor);
      }
    }
    sp.targetSampleCalc = SamplingStatistics.calculateTargetSampleLoads(all, floor, sp, est, in.precisionLimit());
    sp.targetPrecisionCalc = SamplingStatistics.calculatePrecision(sp.estimatedVolume, sp.estimatedLoads,
        sp.estimatedStandardDeviation, sp.targetSampleCalc);
    sp.targetFrequencyCalc = SamplingStatistics.calculateFrequency(sp.estimatedLoads, sp.targetSampleCalc);
  }

  /** {@code SamplePlanValidator.validateFieldLengths}: the results must fit their columns. */
  static void validateFieldLengths(StratumPlanRow sp, int row, Problems e) {
    if (len(sp.estimatedVolume) > 12) e.add("estimatedVolume_" + row, LEN_VOLUME);
    if (len(sp.estimatedLoads) > 6) e.add("estimatedLoads_" + row, LEN_LOADS);
    if (len(sp.estimatedLoadSize) > 4) e.add("estimatedSize_" + row, LEN_SIZE);
    if (len(sp.optimumSampleCalc) > 4) e.add("optimumSample_" + row, LEN_OPT_SAMPLE);
    if (len(sp.optimumPrecisionCalc) > 3) e.add("optimumPrecision_" + row, LEN_OPT_PRECISION);
    if (len(sp.optimumFrequencyCalc) > 4) e.add("optimumFrequency_" + row, LEN_OPT_FREQUENCY);
    if (len(sp.targetSampleCalc) > 4) e.add("targetSample_" + row, LEN_TARGET_SAMPLE);
    if (len(sp.targetPrecisionCalc) > 3) e.add("targetPrecision_" + row, LEN_TARGET_PRECISION);
    if (len(sp.targetFrequencyCalc) > 4) e.add("targetFrequency_" + row, LEN_TARGET_FREQUENCY);
    if (len(sp.sampleCountConstraint) > 6) e.add("sampleConstraint_" + row, LEN_SAMPLE_CONSTRAINT);
    if (len(sp.precisionConstraint) > 3) e.add("precisionConstraint_" + row, LEN_PRECISION_CONSTRAINT);
  }

  /**
   * {@code P891Action.updatePlan} (validateFlag = true): calculate every row,
   * check the result sizes; the caller stores the rows only when no problem was added.
   */
  static void calculateAll(List<StratumPlanRow> strata, PlanInputs in, Problems e) {
    int row = 0;
    for (StratumPlanRow sp : strata) {
      doCalculations(sp, strata, in, e, true, row);
      validateFieldLengths(sp, row, e);
      row++;
    }
  }

  /** Population optimum / target precision for SAMPLE_PLAN (null when the variance is 0). */
  static Double[] populationPrecisions(List<StratumPlanRow> strata) {
    double popVolume = SamplingStatistics.calculatePopulationEstimatedVolume(strata);
    Double optimum = SamplingStatistics.calculatePopulationPrecision(
        SamplingStatistics.calculatePopulationVariance(strata, SamplingStatistics.OPTIMUM), popVolume);
    Double target = SamplingStatistics.calculatePopulationPrecision(
        SamplingStatistics.calculatePopulationVariance(strata, SamplingStatistics.TARGET), popVolume);
    return new Double[] {optimum, target};
  }

  /** {@code ("" + value.longValue()).length()}. */
  private static int len(Number n) {
    return n == null ? 0 : Long.toString(n.longValue()).length();
  }

  /** {@code HBSValidatorHelper.isValidPrecision(value.toString(), n)}. */
  static boolean decimalsOk(double value, int places) {
    String s = Double.toString(value);
    if (s.contains("E")) {
      s = new BigDecimal(s).toPlainString();
    }
    int dot = s.indexOf('.');
    if (dot < 0) return true;
    String frac = s.substring(dot + 1).replaceAll("0+$", "");
    return frac.length() <= places;
  }
}
