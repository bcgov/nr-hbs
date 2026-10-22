package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * The common blocks of weigh slips ({@code <WSCommon>}) and tallies
 * ({@code <PSCommon>}): {@code dac.detail.xml.WeighSlipCommon} and
 * {@code PieceScaleCommon}.
 */
public final class Commons {

  private Commons() {
  }

  /** {@code <WSCommon>}. */
  public static class WeighSlipCommon {
    public String scaleSite;
    /** Parsed from ScaleDate (yyyyMMdd before V3, yyyy-MM-dd in V3); null when unparseable. */
    public Date scaleDate;
    /** HHmmss as a number (V1/V2 ScaleTime, or the digits of the V3 xs:time). */
    public int scaleTimeValue;
    public int weighSlipNumber;
    public String primaryScalerLicence;
    public String secondaryScalerLicence;
    public String comment;
    public Parts.Software software;
    public Parts.Digest digest;
    public Parts.Signature signature;

    /** {@code setScaleTimeISO}: the first six digits of the time. */
    public void setScaleTimeIso(String iso) {
      this.scaleTimeValue = Integer.parseInt(iso.replaceAll("[^\\d.]", "").substring(0, 6));
    }

    public String scaleDateString() {
      return LegacyDates.format(LegacyDates.DATE_FORMAT_NO_TIME, scaleDate);
    }

    public String scaleTimeString() {
      StringBuilder value = new StringBuilder(Integer.toString(scaleTimeValue));
      while (value.length() < 6) {
        value.insert(0, '0');
      }
      return value.toString();
    }

    /** {@code getScaleDate2}: the hash-string form of the date (CRC32: yyyyMMdd, otherwise ISO). */
    String scaleDate2() {
      return HashValues.CRC32.equals(digest.method)
          ? LegacyDates.format(LegacyDates.DATE_FORMAT_NO_TIME, scaleDate)
          : LegacyDates.format(LegacyDates.DATE_FORMAT_WITH_DASHES, scaleDate);
    }

    /** {@code getScaleTime2}: HHmmss for CRC32, otherwise HH:mm:ss. */
    String scaleTime2() {
      String s = scaleTimeString();
      return HashValues.CRC32.equals(digest.method) ? s
          : s.substring(0, 2) + ":" + s.substring(2, 4) + ":" + s.substring(4, 6);
    }

    /** {@code getScaleDateTime}: non-lenient yyyyMMddHHmmss. */
    public Date scaleDateTime() throws ParseException {
      SimpleDateFormat df = new SimpleDateFormat(LegacyDates.DATE_FORMAT_NO_TIME + LegacyDates.TIME_FORMAT);
      df.setLenient(false);
      return df.parse(scaleDateString() + scaleTimeString());
    }

    /** {@code WeighSlipCommon.validate}: FWAH04 / FWAH05. */
    void validate() throws FormatEditException {
      if (scaleDate == null) {
        // The legacy setter left the date null and validate() then failed with a
        // NullPointerException that stopped the run; the edit is the intended outcome.
        Edits.fail(FormatEdit.WeighSlip.SCALE_DATE_VALID);
      }
      try {
        SimpleDateFormat df = new SimpleDateFormat(LegacyDates.DATE_FORMAT_NO_TIME);
        df.setLenient(false);
        Date date = df.parse(scaleDateString());
        if (!scaleDateString().equals(df.format(date))) {
          Edits.fail(FormatEdit.WeighSlip.SCALE_DATE_VALID);
        }
      } catch (ParseException e) {
        Edits.fail(FormatEdit.WeighSlip.SCALE_DATE_VALID);
      }
      try {
        SimpleDateFormat df = new SimpleDateFormat(LegacyDates.TIME_FORMAT);
        df.setLenient(false);
        Date time = df.parse(scaleTimeString());
        if (!scaleTimeString().equals(df.format(time))) {
          Edits.fail(FormatEdit.WeighSlip.SCALE_TIME_VALID);
        }
      } catch (ParseException e) {
        Edits.fail(FormatEdit.WeighSlip.SCALE_TIME_VALID);
      }
      try {
        scaleDateTime();
      } catch (ParseException e) {
        Edits.fail(FormatEdit.WeighSlip.SCALE_DATE_VALID);
      }
    }
  }

  /** {@code <PSCommon>}. */
  public static class PieceScaleCommon {
    public String scaleSite;
    public Date scaleDate;
    public String primaryScalerLicence;
    public short primaryScalerReturnNumber;
    public String secondaryScalerLicence;
    public Parts.Software software;
    public Parts.Digest digest;
    public Parts.Signature signature;
    public String comment;

    public String scaleDateString() {
      return LegacyDates.format(LegacyDates.DATE_FORMAT_NO_TIME, scaleDate);
    }

    String scaleDate2() {
      return HashValues.CRC32.equals(digest.method)
          ? LegacyDates.format(LegacyDates.DATE_FORMAT_NO_TIME, scaleDate)
          : LegacyDates.format(LegacyDates.DATE_FORMAT_WITH_DASHES, scaleDate);
    }

    /**
     * {@code PieceScaleCommon.validate} (FTAH14). The legacy check only called
     * the getter, which never failed; an unparseable date (null) failed later
     * with a NullPointerException. Here it is the format edit.
     */
    void validate() throws FormatEditException {
      if (scaleDate == null) {
        Edits.fail(FormatEdit.LogTally.SCALE_DATE_VALID);
      }
    }
  }
}
