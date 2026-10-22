package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.rating.RatingInput.DateValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.NumberValue;
import ca.bc.gov.nrs.hbs.api.rating.RatingInput.Problems;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The field rules the legacy Rating Struts actions and validation.xml ran
 * before calling the table-API procs. Pure functions over the request and
 * the rows a command has read, so each rule is unit-tested on its own.
 */
final class RatingRules {

  /** Appraisal method / grade schedule codes (AppraisalMethodTag, dac.common.Constants). */
  static final String COAST = "C";
  static final String INTERIOR = "I";

  private RatingRules() {
  }

  /** A closed day interval, as stored in the rate and code tables. */
  record Interval(LocalDate from, LocalDate to) {
    /** {@code DateRange.insideRange}: from ≤ day ≤ to (day precision, both ends inclusive). */
    boolean contains(LocalDate day) {
      return day != null && !day.isBefore(from) && !day.isAfter(to);
    }
  }

  /** An existing district default rate interval (P212/P213 overlap loop). */
  record RateInterval(long id, LocalDate from, LocalDate to) {
  }

  /**
   * The 7-year back-dating limit. Every legacy variant reduces to "the day is
   * before today less 7 years": P203/P204/P207 compare with
   * {@code startDay(now - 7y)}; P212/P222 with {@code now - 7y - 1 day} at the
   * current time ("to allow today's date 7 years ago"); P213/P223 with
   * midnight of {@code today - 7y} (P223 also minus a day).
   */
  static boolean olderThanSevenYears(LocalDate day, LocalDate today) {
    return day != null && day.isBefore(today.minusYears(7));
  }

  /** Which header date groups passed their format checks (P203/P204/P207 scaleDateError / processDateError). */
  record HeaderDates(boolean scaleOk, boolean processOk) {
  }

  /**
   * Override rule header dates — {@code P203Action.validateForm},
   * {@code P204Action/P207Action.validateOverrideRule}: each date valid (4-digit
   * year ≤ 2099; P203 also process years ≥ 2000), From ≤ To, scale From not
   * older than 7 years, process From today or later. The overlap check is the
   * caller's (it needs the database).
   */
  static HeaderDates overrideRuleDates(Problems p, DateValue scaleFrom, DateValue scaleTo, DateValue processFrom,
      DateValue processTo, int minProcessYear, LocalDate today) {
    boolean scaleOk = true;
    boolean processOk = true;
    if (!RatingInput.validYear(scaleFrom.date(), 0)) {
      p.add("scaleEffectiveDate", RatingMessages.SCALE_FROM_INVALID);
      scaleOk = false;
    }
    if (!RatingInput.validYear(scaleTo.date(), 0)) {
      p.add("scaleExpiryDate", RatingMessages.SCALE_TO_INVALID);
      scaleOk = false;
    }
    if (!RatingInput.validYear(processFrom.date(), minProcessYear)) {
      p.add("processingEffectiveDate", RatingMessages.PROCESS_FROM_INVALID);
      processOk = false;
    }
    if (!RatingInput.validYear(processTo.date(), minProcessYear)) {
      p.add("processingExpiryDate", RatingMessages.PROCESS_TO_INVALID);
      processOk = false;
    }
    if (scaleOk) {
      if (scaleFrom.date().isAfter(scaleTo.date())) {
        p.add("scaleEffectiveDate", RatingMessages.SCALE_RANGE_INVALID);
      } else if (olderThanSevenYears(scaleFrom.date(), today)) {
        p.add("scaleEffectiveDate", RatingMessages.SCALE_DATE_PAST);
      }
    }
    if (processOk) {
      if (processFrom.date().isAfter(processTo.date())) {
        p.add("processingEffectiveDate", RatingMessages.PROCESS_RANGE_INVALID);
      } else if (processFrom.date().isBefore(today)) {
        p.add("processingEffectiveDate", RatingMessages.PROCESS_FROM_PAST);
      }
    }
    return new HeaderDates(scaleOk, processOk);
  }

  /**
   * Field scale deck ID (BT10012): optional (blank = regular rule); when
   * given, letters/digits only ({@code StringUtil.isAlphaNumeric}); the
   * column is one character.
   */
  static void deck(Problems p, String deck) {
    if (deck != null && (deck.length() != 1 || !deck.chars().allMatch(Character::isLetterOrDigit))) {
      p.add("fieldScaleDeckId", RatingMessages.DECK_INVALID);
    }
  }

  /**
   * One override rate detail row — {@code P204Action/P207Action.validateRate}.
   * Species / grade null = "All" (allowed only for logs); otherwise the
   * combination must be in SPECIES_PRODUCT_GRADE_XREF. Rate ≥ 0, at most four
   * integer digits ($9,999.99) and two decimals.
   */
  static void overrideRateRow(Problems p, String species, String product, String grade, NumberValue rate,
      BooleanSupplier spgExists) {
    if (product == null || rate.missing()) {
      p.add("overrideRate", RatingMessages.RATE_ROW_INVALID);
    } else if (species == null || grade == null) {
      if (!RatingInput.LOGS.equals(product)) {
        p.add("overrideRate", RatingMessages.RATE_ALL_ONLY_LOGS);
      }
    } else if (!spgExists.getAsBoolean()) {
      p.add("overrideRate", RatingMessages.SPG_INVALID);
    }
    if (rate.missing()) {
      return;
    }
    if (rate.invalid()) {
      p.add("overrideRate", RatingMessages.notANumber("Rate"));
      return;
    }
    if (rate.value().signum() < 0) {
      p.add("overrideRate", RatingMessages.lessThan("Rate", "0.00"));
      return;
    }
    if (rate.value().compareTo(BigDecimal.valueOf(10_000)) >= 0) {
      p.add("overrideRate", RatingMessages.greaterThan("Rate", "$9,999.99"));
    }
    if (RatingInput.decimals(rate.raw()) > 2) {
      p.add("overrideRate", RatingMessages.decimals("Rate", 2));
    }
  }

  /**
   * The validation.xml chain on {@code wrate} (P212/P213: maxValue 9999.99,
   * label "$9,999.99"; P222/P223: 999.99): required, float, precision 2,
   * greaterThan, lessThan 0.00. Struts stops at a field's first failing
   * validator. Returns true when the value passed.
   */
  static boolean validatorRate(Problems p, String field, NumberValue rate, BigDecimal max, String maxLabel) {
    if (rate.missing()) {
      p.add(field, RatingMessages.required("Rate"));
    } else if (rate.invalid()) {
      p.add(field, RatingMessages.notANumber("Rate"));
    } else if (RatingInput.decimals(rate.raw()) > 2) {
      p.add(field, RatingMessages.decimals("Rate", 2));
    } else if (rate.value().compareTo(max) > 0) {
      p.add(field, RatingMessages.greaterThan("Rate", maxLabel));
    } else if (rate.value().signum() < 0) {
      p.add(field, RatingMessages.lessThan("Rate", "0.00"));
    } else {
      return true;
    }
    return false;
  }

  /** validation.xml {@code dateRequired, hbsDate}. Returns true when the date is usable. */
  static boolean validatorDate(Problems p, String field, String label, DateValue d) {
    if (d.missing()) {
      p.add(field, RatingMessages.required(label));
      return false;
    }
    if (d.invalid() || !RatingInput.validYear(d.date(), 0)) {
      p.add(field, RatingMessages.notADate(label));
      return false;
    }
    return true;
  }

  /**
   * District default rate interval check (BT8425) — {@code P212Action/P213Action.validateForm}:
   * against every active rate for the same district / method / species-product-grade
   * (P213 skips the rate itself), each of the three messages at most once.
   */
  static void districtIntervals(Problems p, LocalDate eff, LocalDate exp, List<RateInterval> existing, Long self) {
    boolean effFlag = false;
    boolean expFlag = false;
    boolean spanFlag = false;
    for (RateInterval r : existing) {
      if (self != null && self == r.id()) continue;
      if (!effFlag && !eff.isBefore(r.from()) && !eff.isAfter(r.to())) {
        p.add("effectiveDate", RatingMessages.DDR_EFF_IN_INTERVAL);
        effFlag = true;
      }
      if (!expFlag && !exp.isBefore(r.from()) && !exp.isAfter(r.to())) {
        p.add("expiryDate", RatingMessages.DDR_EXP_IN_INTERVAL);
        expFlag = true;
      }
      if (!spanFlag && eff.isBefore(r.from()) && exp.isAfter(r.to())) {
        p.add("effectiveDate", RatingMessages.DDR_INCLUDES_INTERVAL);
        spanFlag = true;
      }
      if (effFlag && expFlag && spanFlag) break;
    }
  }

  /**
   * Grade schedule check for a district default rate (ticket 7012) —
   * {@code P212Action/P213Action}: the grade must exist in the Coast or
   * Interior grade code table of the appraisal method; for logs it must also
   * be in effect on the effective and on the expiry date.
   */
  static void districtGradeSchedule(Problems p, String method, String product, Interval grade, LocalDate eff,
      LocalDate exp, boolean checkExists) {
    boolean coast = COAST.equalsIgnoreCase(method);
    boolean interior = INTERIOR.equalsIgnoreCase(method);
    if (grade == null) {
      if (checkExists || RatingInput.LOGS.equals(product)) {
        if (coast) p.add("scaleGradeCode", RatingMessages.COAST_GRADE_NOT_EXIST);
        else if (interior) p.add("scaleGradeCode", RatingMessages.INTERIOR_GRADE_NOT_EXIST);
      }
      return;
    }
    if (!RatingInput.LOGS.equals(product)) return;
    if (!grade.contains(eff)) {
      if (coast) p.add("effectiveDate", RatingMessages.COAST_GRADE_FROM);
      else if (interior) p.add("effectiveDate", RatingMessages.INTERIOR_GRADE_FROM);
    }
    if (!grade.contains(exp)) {
      if (coast) p.add("expiryDate", RatingMessages.COAST_GRADE_TO);
      else if (interior) p.add("expiryDate", RatingMessages.INTERIOR_GRADE_TO);
    }
  }

  /** {@code StringUtil.isAlphaNumericOrSpecialChar}: letters, digits and {@code < > : - ( / '}. */
  static boolean cutBlockCharacters(String cutBlock) {
    return cutBlock != null && !cutBlock.isEmpty()
        && cutBlock.chars().allMatch(c -> Character.isLetterOrDigit(c) || "<>:-(/'".indexOf(c) >= 0);
  }
}
