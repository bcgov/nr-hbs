package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Date parsing exactly as the legacy XML classes did it ({@code DateUtil} and
 * the {@code SimpleDateFormat}s in the Castor-bound setters), including
 * {@code SimpleDateFormat}'s lenient default, so the same input gives the same
 * dates and the same format edits. Dates are {@link Date}s in the JVM zone,
 * written to Oracle DATE columns as wall-clock values like the legacy app did.
 */
public final class LegacyDates {

  public static final String DATE_FORMAT_NO_TIME = "yyyyMMdd";
  public static final String DATE_FORMAT_WITH_DASHES = "yyyy-MM-dd";
  public static final String TIME_FORMAT = "HHmmss";
  public static final String DATE_TIME_ISO = "yyyy-MM-dd'T'HH:mm:ss";

  private LegacyDates() {
  }

  /** {@code DateUtil.getErrorDate()}: 9999-12-31 23:59:59, the marker for an unparseable batch date. */
  public static Date errorDate() {
    GregorianCalendar cal = new GregorianCalendar(9999, Calendar.DECEMBER, 31, 23, 59, 59);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTime();
  }

  /** Lenient {@code SimpleDateFormat(pattern, Locale.CANADA).parse}, null when it fails. */
  public static Date parseLenient(String pattern, String text) {
    if (text == null) {
      return null;
    }
    try {
      return new SimpleDateFormat(pattern, Locale.CANADA).parse(text);
    } catch (ParseException ex) {
      return null;
    }
  }

  /** {@code DateUtil.getDate(text, pattern, NOT_LENIENT)}; throws when invalid. */
  public static Date parseStrict(String pattern, String text) throws ParseException {
    if (text == null) {
      return null;
    }
    SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.CANADA);
    format.setLenient(false);
    return format.parse(text);
  }

  public static String format(String pattern, Date date) {
    return new SimpleDateFormat(pattern, Locale.CANADA).format(date);
  }
}
