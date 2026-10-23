package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

/**
 * A population / stratum / year ("PSY") split into its parts, ported line by
 * line from {@code $E/dac/detail/PopStratYearParser} (with
 * {@code HdbsUtilities.convertYearToLong} / {@code getCentury} for two-digit
 * years). The PSY is {@code PPPPSSYY} (8, "billable length") or
 * {@code PPPPSSYYYY} (10, with century); a 7-character PSY carries a
 * one-digit year (BT 8873).
 *
 * <p>Legacy quirks kept on purpose:
 * <ul>
 *   <li>validity is decided on the length of the <em>untrimmed</em> input,
 *       while the parts are cut from the trimmed input;</li>
 *   <li>a 9-character PSY (or 11+) fails to cut its year and is invalid; an
 *       11+ character PSY still yields a four-digit year;</li>
 *   <li>a four-digit year starting with "0" falls back to the two-digit
 *       conversion of its last two digits;</li>
 *   <li>{@link #year()} throws for a missing or non-numeric year (legacy
 *       {@code InvalidYearException}) even when the PSY is otherwise unused.</li>
 * </ul>
 * Used by {@link WeighSlipValidator} and {@link SampleLogTallyValidator}
 * (legacy {@code DetailScaleDocumentValidator.getPopulation / getStratum /
 * getYear / isPSYBillableLength}).
 */
public final class PopStratYear {

  /** {@code InvalidYearException}: the year part cannot be turned into a four-digit year. */
  public static final class InvalidYearException extends Exception {
    public InvalidYearException(String message) {
      super(message);
    }
  }

  static final int BILLABLE_LENGTH = 8;
  static final int BILLABLE_LENGTH_WITH_CENTURY = 10;

  private final String inputPsy;
  private String population;
  private String stratum;
  private String twoDigitYear;
  private String fourDigitYear;
  private String oneDigitYear;
  private boolean valid = true;

  private PopStratYear(String rawPsy) {
    this.inputPsy = rawPsy != null ? rawPsy.trim() : "";
    // legacy: StringUtil.empty (no trim) and the length of the raw input
    if (rawPsy == null || rawPsy.isEmpty()
        || !(rawPsy.length() == BILLABLE_LENGTH || rawPsy.length() == BILLABLE_LENGTH_WITH_CENTURY)) {
      valid = false;
    }
    split(inputPsy);
  }

  /** {@code new PopStratYearParser(inputPSY)}. */
  public static PopStratYear parse(String psy) {
    return new PopStratYear(psy);
  }

  /** {@code new PopStratYearParser(pop, stratum, sampleYear)}: the parts concatenated, then parsed. */
  public static PopStratYear of(String population, String stratum, Long samplingYear) {
    StringBuilder psy = new StringBuilder();
    psy.append(population != null ? population : "");
    psy.append(stratum != null ? stratum : "");
    if (samplingYear != null) {
      psy.append(samplingYear);
    }
    return new PopStratYear(psy.toString());
  }

  private void split(String psy) {
    if (psy.length() >= 4) {
      population = psy.substring(0, 4);
    } else {
      valid = false;
    }
    if (psy.length() >= 6) {
      stratum = psy.substring(4, 6);
    } else {
      valid = false;
    }
    // BT 8658 / BT 8873
    if (psy.length() == BILLABLE_LENGTH) {
      twoDigitYear = psy.substring(6, 8);
    } else if (psy.length() > BILLABLE_LENGTH) {
      if (psy.length() >= 10) {
        fourDigitYear = psy.substring(6, 10);
        twoDigitYear = psy.substring(8, 10);
      } else {
        valid = false; // substring(6, 10) of a 9-character PSY threw
      }
    } else if (psy.length() == BILLABLE_LENGTH - 1) {
      oneDigitYear = psy.substring(6, 7);
    }
  }

  /** {@code getPopulation}: characters 1-4, or null when the PSY is shorter. */
  public String population() {
    return population;
  }

  /** {@code getStratum}: characters 5-6, or null when the PSY is shorter. */
  public String stratum() {
    return stratum;
  }

  /**
   * {@code getYear}: the four-digit year (not starting with 0), else the
   * one-digit year, else the two-digit year with its century (95-99 → 19xx,
   * otherwise 20xx).
   */
  public Long year() throws InvalidYearException {
    if (!emptyNoTrim(fourDigitYear) && !allNumbers(fourDigitYear)) {
      throw new InvalidYearException(fourDigitYear + " is not numeric and invalid.");
    } else if (!emptyNoTrim(fourDigitYear) && !fourDigitYear.startsWith("0")) {
      return Long.valueOf(fourDigitYear);
    }
    if (!emptyNoTrim(oneDigitYear) && !allNumbers(oneDigitYear)) {
      throw new InvalidYearException(oneDigitYear + " is not numeric.");
    } else if (!emptyNoTrim(oneDigitYear)) {
      return Long.valueOf(oneDigitYear);
    }
    return convertYearToLong(twoDigitYear);
  }

  /** {@code getYear} without the exception: null when the year is invalid. */
  public Long yearOrNull() {
    try {
      return year();
    } catch (InvalidYearException e) {
      return null;
    }
  }

  /** {@code isPSYValid}. */
  public boolean isValid() {
    return valid;
  }

  public String twoDigitYear() {
    return twoDigitYear;
  }

  public String oneDigitYear() {
    return oneDigitYear;
  }

  /** {@code isBillableLength}: the trimmed PSY is 8 or 10 characters. */
  public boolean isBillableLength() {
    return inputPsy.length() == BILLABLE_LENGTH || inputPsy.length() == BILLABLE_LENGTH_WITH_CENTURY;
  }

  /** {@code getCompanyStratum}: up to 8 characters as given, longer ones without the century. */
  public String companyStratum() {
    if (BILLABLE_LENGTH >= inputPsy.length()) {
      return inputPsy.substring(0, Math.min(BILLABLE_LENGTH, inputPsy.length()));
    }
    return psySansCentury();
  }

  /** {@code getPSY}: the trimmed input. */
  public String psy() {
    return inputPsy;
  }

  /** {@code getPSYsansCentury}: deletes (length % 8) characters from position 7. */
  public String psySansCentury() {
    StringBuilder value = new StringBuilder(inputPsy);
    if (value.length() > BILLABLE_LENGTH) {
      int centuryStart = 6;
      int centuryEnd = 6 + (value.length() % BILLABLE_LENGTH);
      value.delete(centuryStart, centuryEnd);
    }
    return value.toString();
  }

  /** {@code HdbsUtilities.convertYearToLong} / {@code convertYear} / {@code getCentury}. */
  static Long convertYearToLong(String twoDigitYear) throws InvalidYearException {
    if (!allNumbers(twoDigitYear)) {
      throw new InvalidYearException(twoDigitYear + " is not numeric.");
    }
    int year = Integer.parseInt(twoDigitYear);
    int century;
    if (year >= 95 && year <= 99) {
      century = 19;
    } else if (year >= 0) {
      century = 20;
    } else {
      throw new InvalidYearException("Invalid year: " + year);
    }
    return Long.valueOf(century + twoDigitYear);
  }

  /** {@code StringUtil.empty(String)}: null or "" (no trim). */
  static boolean emptyNoTrim(String s) {
    return s == null || s.isEmpty();
  }

  /** {@code StringUtil.allNumbers}: non-empty and every character a digit. */
  static boolean allNumbers(String s) {
    if (emptyNoTrim(s)) return false;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isDigit(s.charAt(i))) return false;
    }
    return true;
  }

  /** {@code StringUtil.allLetters}: non-empty and every character a letter. */
  static boolean allLetters(String s) {
    if (emptyNoTrim(s)) return false;
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isLetter(s.charAt(i))) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return inputPsy;
  }
}
