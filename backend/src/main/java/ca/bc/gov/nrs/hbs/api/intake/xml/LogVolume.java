package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Net volume checks of the format edits (FTAL09): {@code util.math.LogUtil}
 * (Smalian log volume) and {@code VolumeUtil.netVolume(IStackDimensions)}
 * (SFP stacks), reproduced step by step — including the {@code new
 * BigDecimal(float)} conversions — so the same logs pass and fail.
 */
public final class LogVolume {

  /** {@code LogUtil.VOLUME_TOLERENCE}. */
  public static final float VOLUME_TOLERANCE = 0.0021F;

  /** {@code LogUtil.PI}: {@code new BigDecimal(3.141592F).setScale(6, HALF_UP)}. */
  static final BigDecimal PI = new BigDecimal(3.141592F).setScale(6, RoundingMode.HALF_UP);
  private static final BigDecimal TWO = new BigDecimal(2);

  private LogVolume() {
  }

  /** {@code LogUtil.netVolume}: gross volume less the defect volume, to 3 decimals. */
  public static float netVolume(int top, int butt, float length, float totalDefectVolume) {
    return volume(top, butt, length)
        .subtract(new BigDecimal(totalDefectVolume))
        .setScale(3, RoundingMode.HALF_UP)
        .floatValue();
  }

  static BigDecimal volume(int top, int butt, float length) {
    BigDecimal totalArea = area(top).add(area(butt));
    BigDecimal averageArea = totalArea.divide(TWO, 6, RoundingMode.HALF_UP).setScale(5, RoundingMode.HALF_UP);
    return averageArea.multiply(new BigDecimal(length)).setScale(3, RoundingMode.HALF_UP);
  }

  private static BigDecimal area(int radius) {
    BigDecimal r = new BigDecimal(radius);
    return r.multiply(r).multiply(PI).setScale(1, RoundingMode.HALF_UP).movePointLeft(4);
  }

  /** {@code VolumeUtil.netVolume(IStackDimensions)}: L x W x H (cm) to m3, times the conversion factor. */
  public static float stackVolume(long length, long width, long height, double conversion) {
    long volume = length * width * height;
    BigDecimal factor = new BigDecimal(conversion);
    BigDecimal gross = new BigDecimal(volume / 1000000.0).setScale(3, RoundingMode.HALF_UP);
    return gross.multiply(factor).setScale(3, RoundingMode.HALF_UP).floatValue();
  }
}
