package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups.ScaleGradeRef;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.TallyLookups.SpeciesProductGradeRef;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

/**
 * Port of {@code $E/dac/common/ScaledLogXrefValidator} (species / product /
 * grade cross references, grade sub info, effective dates) and the date
 * helpers it used ({@code DateRange.insideRange}, {@code DateUtil.startDay} /
 * {@code endDay}). {@code getGradeScheduleType} is
 * {@link ReferenceData#gradeScheduleType}; {@code canStratumValidationPerform}
 * / {@code getStratumInfo} are {@link DetailValidator#canStratumValidationPerform}.
 *
 * <p>Dates are compared on the JDBC wall clock ({@link Timestamp#toLocalDateTime}),
 * which is what the legacy JVM-default-zone calendar arithmetic amounted to.
 */
public final class ScaledLogXref {

  private ScaledLogXref() {
  }

  /** {@code isInputSpeciesProductGradeValidationNeeded}: none of the three is empty ({@code StringUtil.empty}, no trim). */
  public static boolean isInputSpeciesProductGradeValidationNeeded(String species, String product, String grade) {
    return !legacyEmpty(species) && !legacyEmpty(product) && !legacyEmpty(grade);
  }

  /**
   * {@code getSpeciesProductGrade}: the SPECIES_PRODUCT_GRADE_XREF row with its
   * effective date at the start and expiry at the end of the day, or null.
   */
  public static SpeciesProductGradeRef getSpeciesProductGrade(TallyLookups lookups, String species, String product,
                                                              String grade) {
    return lookups.speciesProductGrade(product, grade, species)
        .map(s -> new SpeciesProductGradeRef(s.scaleProductCode(), s.scaleGradeCode(), s.scaleSpeciesCode(),
            startDay(s.effectiveDate()), endDay(s.expiryDate())))
        .orElse(null);
  }

  /** {@code isSpeciesProductGradeEffective}. */
  public static boolean isSpeciesProductGradeEffective(Date scaleDate, SpeciesProductGradeRef spg) {
    return insideDateRange(scaleDate, spg.effectiveDate(), spg.expiryDate());
  }

  /**
   * {@code findGradeSubInfo}: the grade in the coastal ("C") or interior ("I")
   * grade table, dates widened to whole days; null when the schedule is
   * neither or the grade is not on file.
   */
  public static ScaleGradeRef findGradeSubInfo(TallyLookups lookups, String scaleGradeCode, String gradeScheduleType) {
    String schedule;
    if (DetailValidator.COASTAL_SCHEDULE.equalsIgnoreCase(gradeScheduleType)) {
      schedule = DetailValidator.COASTAL_SCHEDULE;
    } else if (DetailValidator.INTERIOR_SCHEDULE.equalsIgnoreCase(gradeScheduleType)) {
      schedule = DetailValidator.INTERIOR_SCHEDULE;
    } else {
      return null;
    }
    return lookups.scheduleGrade(scaleGradeCode, schedule)
        .map(g -> new ScaleGradeRef(g.scaleGradeCode(), startDay(g.effectiveDate()), endDay(g.expiryDate())))
        .orElse(null);
  }

  /** {@code insideDateRange} = {@code DateRange.insideRange(effective, expiry, scaleDate)}: inclusive, false on a null or inverted range. */
  public static boolean insideDateRange(Date scaleDate, Date effectiveDate, Date expiryDate) {
    if (effectiveDate == null || expiryDate == null || effectiveDate.getTime() > expiryDate.getTime()) {
      return false;
    }
    if (scaleDate == null) {
      return false;
    }
    long d = scaleDate.getTime();
    return d >= effectiveDate.getTime() && d <= expiryDate.getTime();
  }

  /** {@code DateUtil.startDay}: 00:00:00.000 of the day (null stays null). */
  public static Timestamp startDay(Date d) {
    if (d == null) return null;
    return Timestamp.valueOf(wall(d).toLocalDate().atStartOfDay());
  }

  /** {@code DateUtil.endDay}: 23:59:59.000 of the day (null stays null). */
  public static Timestamp endDay(Date d) {
    if (d == null) return null;
    return Timestamp.valueOf(LocalDateTime.of(wall(d).toLocalDate(), LocalTime.of(23, 59, 59)));
  }

  /** {@code StringUtil.empty(str)}: null or "" — whitespace is NOT empty (legacy keepWhitespace = true). */
  public static boolean legacyEmpty(String s) {
    return s == null || s.isEmpty();
  }

  static LocalDateTime wall(Date d) {
    return new Timestamp(d.getTime()).toLocalDateTime();
  }
}
