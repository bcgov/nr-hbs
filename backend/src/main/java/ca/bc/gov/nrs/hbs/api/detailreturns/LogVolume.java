package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Log volume arithmetic, ported from {@code $E/util/math/LogUtil} and the tally
 * totals ({@code LogTally/SampleLogTally/SpecialForestProductTally.calculateTotalNetVolume}).
 * The legacy code mixed {@code float} and {@code BigDecimal}: every float went
 * into {@code new BigDecimal(float)} (its exact binary value), which this port
 * reproduces with {@code new BigDecimal((double) f)} so results match to the
 * last digit.
 */
public final class LogVolume {

  /** {@code LogUtil.PI}: {@code new BigDecimal(3.141592F).setScale(6, HALF_UP)}. */
  static final BigDecimal PI = new BigDecimal((double) 3.141592F).setScale(6, RoundingMode.HALF_UP);
  private static final BigDecimal TWO = new BigDecimal(2);

  private LogVolume() {
  }

  /**
   * {@code LogUtil.netVolume}: smalian volume of the log (top and butt radii in
   * cm, length in m) less its defect volume, rounded to 3 decimals, as a float.
   */
  public static float netVolume(float length, int top, int butt, float totalDefectVolume) {
    BigDecimal net = volume(length, top, butt)
        .subtract(new BigDecimal((double) totalDefectVolume))
        .setScale(3, RoundingMode.HALF_UP);
    return net.floatValue();
  }

  /** {@code LogUtil.volume}. */
  static BigDecimal volume(float length, int top, int butt) {
    BigDecimal total = area(top).add(area(butt));
    BigDecimal average = total.divide(TWO, 6, RoundingMode.HALF_UP).setScale(5, RoundingMode.HALF_UP);
    return average.multiply(new BigDecimal((double) length)).setScale(3, RoundingMode.HALF_UP);
  }

  /** {@code LogUtil.area}: r² × π rounded to 1 decimal, then / 10 000 (cm² → m²). */
  private static BigDecimal area(int radius) {
    BigDecimal r = new BigDecimal(radius);
    return r.multiply(r).multiply(PI).setScale(1, RoundingMode.HALF_UP).movePointLeft(4);
  }

  /** {@code LogDetail.getTotalDefectVolume}: float sum of the defect volumes. */
  public static float totalDefectVolume(List<Row> defects) {
    float total = 0.0f;
    for (Row d : defects) {
      BigDecimal v = d.num("volume");
      total += v == null ? 0.0f : v.floatValue();
    }
    return total;
  }

  /**
   * {@code DetailScaleDocumentHelper.calculateNetVolume(ILogDimension)} for one
   * log line: null dimensions count as 0 ({@code LogDetail.get*Measurement}).
   */
  public static float netVolume(LogLine line) {
    Row r = line.row();
    BigDecimal length = r.num("length");
    BigDecimal top = r.num("top");
    BigDecimal butt = r.num("butt");
    return netVolume(length == null ? 0.0f : length.floatValue(), top == null ? 0 : top.intValue(),
        butt == null ? 0 : butt.intValue(), totalDefectVolume(line.defects()));
  }

  /**
   * {@code calculateTotalNetVolume}: sum of the lines' NET_VOLUME (each through
   * {@code new BigDecimal(float)}), returned as a float.
   */
  public static float totalNetVolume(List<LogLine> lines) {
    BigDecimal total = new BigDecimal("0.00");
    for (LogLine l : lines) {
      BigDecimal v = l.row().num("net_volume");
      total = total.add(new BigDecimal((double) (v == null ? 0.0f : v.floatValue())));
    }
    return total.floatValue();
  }

  /** A float as the decimal the database receives (its shortest decimal form). */
  public static BigDecimal decimal(float f) {
    return new BigDecimal(Float.toString(f));
  }
}
