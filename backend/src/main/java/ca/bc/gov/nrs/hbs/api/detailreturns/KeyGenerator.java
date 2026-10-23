package ca.bc.gov.nrs.hbs.api.detailreturns;

import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Detail document number (DDN) generation, ported from
 * {@code $V/dac/detail/KeyGenerator}, {@code KeyGeneratorLogTally},
 * {@code KeyGeneratorSampleLogTally}, {@code KeyGeneratorSpecialForestProduct},
 * {@code KeyGeneratorWeighSlip} and the ledger branch of {@code P700Action}.
 *
 * <p>The legacy code computed the time part from {@code java.util.Date}
 * millisecond differences in the server's zone (Pacific), so daylight saving
 * changes the result by an hour; that is reproduced by doing the arithmetic on
 * epoch milliseconds of Pacific local times.
 */
public final class KeyGenerator {

  /** {@code KeyGenerator.BASE_DATE}: 2000-01-01 00:00 Pacific. */
  static final LocalDate BASE_DATE = LocalDate.of(2000, 1, 1);
  static final int RADIX = 36;
  static final String BASE_DETAIL_DOCUMENT_NUMBER = "0000000000000";

  /** {@code DacConstants.SCALE_SITE_ARRIVAL_LEDGER_PREFIX} / {@code ..._DEPARTURE_...}. */
  public static final String ARRIVAL_LEDGER_PREFIX = "ARL";
  public static final String DEPARTURE_LEDGER_PREFIX = "DEP";

  private KeyGenerator() {
  }

  private static long millis(LocalDateTime t) {
    return ZonedDateTime.of(t, Audit.ZONE).toInstant().toEpochMilli();
  }

  /** {@code encodeMinutesPastBase}. */
  static String encodeMinutesPastBase(LocalDateTime date) {
    long minutes = (millis(date) - millis(BASE_DATE.atStartOfDay())) / 1000 / 60;
    return Long.toString(minutes, RADIX);
  }

  /** {@code encodeDaysPastBase}: one day is added first (DateUtil.addDays), then whole days are counted. */
  static String encodeDaysPastBase(LocalDateTime date) {
    long days = (millis(date.plusDays(1)) - millis(BASE_DATE.atStartOfDay())) / 1000 / 60 / 60 / 24;
    return Long.toString(days, RADIX);
  }

  /**
   * DDN of a log, sample or SFP tally (the three generators are identical):
   * licence (4) + return number (4) + base-36 minutes since 2000-01-01 (5),
   * then scrambled.
   *
   * @throws IllegalArgumentException with the legacy message when a part is too long
   */
  public static String tally(String primaryLicenseNumber, long returnNumber, LocalDateTime scaleDate) {
    String timeCode = encodeMinutesPastBase(scaleDate);
    String ret = Long.toString(returnNumber);
    if (primaryLicenseNumber.length() > 4) {
      throw new IllegalArgumentException("Primary license number has exceeded max length allowed: 4; value = "
          + primaryLicenseNumber);
    } else if (ret.length() > 4) {
      throw new IllegalArgumentException("Primary return number has exceeded max length allowed: 4; value = "
          + returnNumber);
    } else if (timeCode.length() > 5) {
      throw new IllegalArgumentException("Time code has exceeded max length allowed: 5; value = " + timeCode);
    }
    StringBuilder c = new StringBuilder(BASE_DETAIL_DOCUMENT_NUMBER);
    c.replace(4 - primaryLicenseNumber.length(), 4, primaryLicenseNumber);
    c.replace(8 - ret.length(), 8, ret);
    c.replace(13 - timeCode.length(), 13, timeCode);
    String candidate = c.toString().toUpperCase(Locale.ROOT);
    return candidate.substring(0, 2) + candidate.substring(4, 5) + candidate.substring(10, 12)
        + candidate.substring(5, 6) + candidate.substring(8, 10) + candidate.substring(6, 7)
        + candidate.substring(2, 4) + candidate.substring(7, 8) + candidate.substring(12, 13);
  }

  /**
   * DDN of a weigh slip: site (right-padded with 0 to 4) + last 6 characters of
   * the weigh slip number (left-padded with 0) + base-36 days since 2000-01-01
   * (left-padded to 3), then scrambled.
   */
  public static String weighSlip(String scaleSiteId, String weighSlipNumber, LocalDateTime scaleDate) {
    String timeCode = encodeDaysPastBase(scaleDate);
    if (scaleSiteId.length() > 4) {
      throw new IllegalArgumentException("Scale site has exceeded max length allowed: 4; value = " + scaleSiteId);
    } else if (timeCode.length() > 3) {
      // legacy message quotes KeyLength.LogTally.TIME (5)
      throw new IllegalArgumentException("Time code has exceeded max length allowed: 5; value = " + timeCode);
    }
    String slip = weighSlipNumber.length() > 6 ? weighSlipNumber.substring(weighSlipNumber.length() - 6)
        : weighSlipNumber;
    String candidate = (rightPad(scaleSiteId, 4) + leftPad(slip, 6) + leftPad(timeCode, 3))
        .toUpperCase(Locale.ROOT);
    return candidate.substring(0, 2) + candidate.substring(4, 6) + candidate.substring(10, 11)
        + candidate.substring(2, 4) + candidate.substring(6, 8) + candidate.substring(11, 12)
        + candidate.substring(8, 10) + candidate.substring(12, 13);
  }

  /** Ledger DDN: ARL/DEP + SCL_SITE_LEDGER_SEQ left-padded with 0 to 10 ({@code P700Action}). */
  public static String ledger(boolean arrival, long ledgerId) {
    return (arrival ? ARRIVAL_LEDGER_PREFIX : DEPARTURE_LEDGER_PREFIX) + leftPad(Long.toString(ledgerId), 10);
  }

  /** {@code StringUtil.padString}: no change when already long enough. */
  static String leftPad(String s, int len) {
    return s.length() >= len ? s : "0".repeat(len - s.length()) + s;
  }

  static String rightPad(String s, int len) {
    return s.length() >= len ? s : s + "0".repeat(len - s.length());
  }
}
