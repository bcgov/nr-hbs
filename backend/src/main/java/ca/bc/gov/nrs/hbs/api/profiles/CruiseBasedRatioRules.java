package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The per-row checks of P383 Update Cruise Based Billing Profile —
 * {@code $V/presentation/cpm/P383Action.validateInputs} — applied to the one
 * row the new screens add or change at a time.
 *
 * <p>Steps, as in the legacy method (each step only runs when the previous
 * one passed): species and grade given; the species/grade combination exists
 * in SPECIES_PRODUCT_GRADE_XREF (any product); a coniferous species is on
 * the appraisal worksheet (APP_SPECIES), a deciduous species needs an
 * appraised deciduous volume; an Interior appraisal ('I') uses grade 7 or 8;
 * 0 &lt; m3/Ha &lt; 1000; no duplicate species/grade. A species in neither
 * legacy list skips the remaining steps, as it did in the legacy action.
 *
 * <p>Deviation: the legacy worksheet lookup built Coastal appraisal species
 * as {@code ApprisalSpecieCoast}, which the P383 {@code instanceof
 * ApprisalSpecie} test never matched, so a coniferous species was always
 * rejected on a Coastal appraisal. Both read APP_SPECIES for the mark and
 * effective date; this port checks APP_SPECIES for every appraisal method
 * (docs/areas/profiles.md lists it as an open question).
 */
@Component
class CruiseBasedRatioRules {

  /** OpqConstants.CONIFEROUS_SPECIES */
  static final Set<String> CONIFEROUS = Set.of("BA", "CE", "CY", "FI", "HE", "LA", "LO", "SP", "UU", "WB", "WH", "YE");
  /** OpqConstants.DECIDUOUS_SPECIES */
  static final Set<String> DECIDUOUS = Set.of("AL", "AR", "AS", "BI", "CO", "MA", "WI");

  // ApplicationResources.properties
  static final String UPSTREAM_DATA_PROBLEM = "Upstream data problem. Please advise help desk.";
  static final String SPECIES_REQUIRED = "A species is required.";
  static final String GRADE_REQUIRED = "A grade is required.";
  static final String SPG_INVALID = "Species, product and grade combination is invalid.";
  static final String CONIFEROUS_NOT_APPRAISED = "Coniferous Species not on worksheet";
  static final String DECIDUOUS_NOT_APPRAISED = "Deciduous Species not on worksheet";
  static final String INTERIOR_GRADE = "ICB789 Interior cruise-based volume estimate returns must use grade 7 or grade 8";
  static final String M3HA_NUMERIC = "m3/Ha must be numeric";
  static final String M3HA_TOO_SMALL = "m3/Ha must be greater than zero";
  static final String M3HA_TOO_LARGE = "m3/Ha must be less than 1000";
  static final String DUPLICATE = "Duplicate Species Grade combination";

  private final ProfileReads reads;

  CruiseBasedRatioRules(ProfileReads reads) {
    this.reads = reads;
  }

  /** The validated row values. */
  record Row(String timberMark, LocalDate effectiveDate, String species, String grade, BigDecimal volume) {}

  /** The appraisal (APP_WORKSHEET) the profile belongs to; 404 when the request names none. */
  Map<String, Object> appraisal(String timberMark, LocalDate effectiveDate) {
    if (timberMark == null || effectiveDate == null) {
      throw new IllegalArgumentException("'timberMark' and 'effectiveDate' are required");
    }
    Map<String, Object> appraisal = reads.appraisal(timberMark, effectiveDate);
    if (appraisal == null) {
      // P383Action.queryTimbermark: no worksheet for the mark / effective date
      new ProfileRules.Problems().add("timberMark", UPSTREAM_DATA_PROBLEM).throwIfAny();
    }
    return appraisal;
  }

  /**
   * Runs {@code validateInputs} for one row. {@code isNew} adds the duplicate
   * check against the rows already stored (the legacy screen compared the
   * whole grid).
   */
  Row validate(Map<String, Object> body, boolean isNew) {
    String timberMark = ProfileRules.upper(body, "timberMark");
    LocalDate effective = ProfileRules.date(body, "effectiveDate");
    String species = ProfileRules.upper(body, "speciesCode");
    String grade = ProfileRules.upper(body, "gradeCode");
    String volumeText = ProfileRules.text(body, "volumePerHectare");
    Map<String, Object> appraisal = appraisal(timberMark, effective);

    ProfileRules.Problems problems = new ProfileRules.Problems();
    if (species == null) problems.add("speciesCode", SPECIES_REQUIRED);
    if (grade == null) problems.add("gradeCode", GRADE_REQUIRED);
    problems.throwIfAny();

    if (!reads.validSpeciesGrade(species, grade)) {
      problems.add("speciesCode", SPG_INVALID).throwIfAny();
    }
    boolean step4;
    if (CONIFEROUS.contains(species)) {
      List<String> appraised = reads.appraisedSpecies(timberMark, effective);
      step4 = appraised.stream().anyMatch(species::equals);
      if (!step4) problems.add("speciesCode", CONIFEROUS_NOT_APPRAISED);
    } else if (DECIDUOUS.contains(species)) {
      Object decVol = appraisal.get("int_deciduous_vol");
      step4 = decVol instanceof Number n && n.longValue() != 0;
      if (!step4) problems.add("speciesCode", DECIDUOUS_NOT_APPRAISED);
    } else {
      step4 = false;
    }
    problems.throwIfAny();

    BigDecimal volume = parse(volumeText);
    if (step4) {
      Object method = appraisal.get("appraisal_mthd_cd");
      if (method != null && "I".equalsIgnoreCase(method.toString().trim()) && !"7".equals(grade) && !"8".equals(grade)) {
        problems.add("gradeCode", INTERIOR_GRADE).throwIfAny();
      }
      if (volume == null) {
        problems.add("volumePerHectare", M3HA_NUMERIC);
      } else {
        if (volume.signum() <= 0) problems.add("volumePerHectare", M3HA_TOO_SMALL);
        if (volume.compareTo(BigDecimal.valueOf(1000)) >= 0) problems.add("volumePerHectare", M3HA_TOO_LARGE);
      }
      if (isNew && reads.ratio(timberMark, effective, species, grade) != null) {
        problems.add("speciesCode", DUPLICATE);
      }
    } else if (volume == null) {
      // not validated by the legacy action for other species, but the proc needs a number
      problems.add("volumePerHectare", M3HA_NUMERIC);
    }
    problems.throwIfAny();
    return new Row(timberMark, effective, species, grade, volume);
  }

  private static BigDecimal parse(String s) {
    if (s == null) return null;
    try {
      return new BigDecimal(s);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /** The stored row for an update / 404. */
  Map<String, Object> existing(Row r) {
    Map<String, Object> row = reads.ratio(r.timberMark(), r.effectiveDate(), r.species(), r.grade());
    if (row == null) {
      throw new EntityNotFoundException(Map.class, "speciesCode", r.species() + "/" + r.grade());
    }
    return row;
  }
}
