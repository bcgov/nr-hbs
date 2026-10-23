package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_FORMAT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_LOOKUP;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_RELATED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_SCALING;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.EditError.CATEGORY_VALUE;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Population;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Stratum;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code $E/dac/summary/control/SampleScaleValidator}. */
class SampleEdits extends ReturnEdits {

  SampleEdits(SummaryReturn ret, Lookups lookups, Clock clock) {
    super(ret, lookups, clock);
  }

  private String population() {
    return str("population_number");
  }

  private String stratumNo() {
    return str("stratum_number");
  }

  private Long year() {
    return version.lng("sampling_year");
  }

  // ── Input fields ─────────────────────────────────────────────────────────

  /** {@code validateAndMoveInputFields}: licence (SL01), timber mark (TM01), population / stratum / year (ST01), species/product/grade. */
  @Override
  protected void validateAndMoveInputFields() {
    String licence = str("input_scaler_license_num");
    if (empty(str("license_number")) && !empty(licence)) {
      if (lookups.quantLicenseExists(licence, str("quant_lic_type_cd"))) {
        version.set("license_number", licence);
      } else {
        versionError(CATEGORY_LOOKUP, EditCodes.SL01);
      }
    }
    if (empty(str("timber_mark"))) {
      if (onFileTimberMark(str("input_timber_mark"))) {
        version.set("timber_mark", str("input_timber_mark"));
      } else {
        versionError(CATEGORY_LOOKUP, EditCodes.TM01);
      }
    }
    if (emptyPopStratumYear()) {
      validateAndMoveInputPopStratumYear();
    }
    validateSpeciesProductGrade();
  }

  /**
   * {@code validateAndMoveInputPopStratumYear}: ST01, or the input stratum is moved and checked
   * against the scale date at once (SD04 / SD05). Legacy defect kept: {@link #validate} checks the
   * stratum dates again, so a moved stratum raises SD04 / SD05 twice.
   */
  private void validateAndMoveInputPopStratumYear() {
    try {
      Long year = convertYearToLong(str("input_sampling_year"));
      if (stratum(str("input_population"), str("input_stratum"), year).isPresent()) {
        setPopStratumYearFromInput();
        validatePopStratumYearExtra();
      } else {
        versionError(CATEGORY_LOOKUP, EditCodes.ST01);
      }
    } catch (InvalidYearException e) {
      versionError(CATEGORY_LOOKUP, EditCodes.ST01);
    }
  }

  // ── validate() ───────────────────────────────────────────────────────────

  /**
   * {@code validate}: SD03, NP01 / LC01, NW01, LA00, CBSS00, TM07 / TM11 / TM08, clients, SRT02
   * (BT 14020), stratum dates and grade constraint, then per segregation SV00, SG03 and ISB789.
   */
  @Override
  protected void validate() {
    if (!scaleDateNotInFuture()) {
      versionError(CATEGORY_RELATED, EditCodes.SD03);
    }
    validatePieceNumber();
    if (!(version.lng("net_weight") > 0)) {
      versionError(CATEGORY_VALUE, EditCodes.NW01);
    }
    if (!isValidLoadArrivalNumber(str("load_arrival_number"))) {
      versionError(CATEGORY_VALUE, EditCodes.LA00);
    }
    validateNotCruiseBasedScaleSite();
    validateHeaderTimberMark();
    validateClients();
    validatePopulation();
    if (!emptyPopStratumYear()) {
      validatePopStratumYearExtra();
      validateGradeConstraint(str("timber_mark"), population(), stratumNo(), year(), false);
    }
    Set<List<String>> keys = new HashSet<>();
    for (Row seg : ret.segregations()) {
      if (seg.get("volume") == null) {
        versionError(CATEGORY_FORMAT, EditCodes.SV00); // legacy raised it on the version
      }
      if (seg.str("scale_species_code") != null || seg.str("scale_product_code") != null
          || seg.str("scale_grade_code") != null) {
        List<String> key = Arrays.asList(nz(seg.str("scale_species_code")), nz(seg.str("scale_product_code")),
            nz(seg.str("scale_grade_code")));
        if (!keys.add(key)) {
          segregationError(seg, CATEGORY_FORMAT, EditCodes.SG03);
        }
        if (EditCodes.INTERIOR_CB_GRADES.contains(seg.str("scale_grade_code"))) {
          segregationError(seg, CATEGORY_SCALING, EditCodes.ISB789);
        }
      }
    }
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  /** {@code validatePieceNumber}: NP01 per segregation with no pieces; LC01 when the pieces do not add up to the log count. */
  private void validatePieceNumber() {
    long total = 0;
    for (Row seg : ret.segregations()) {
      long pieces = seg.lng("number_of_pieces");
      if (pieces <= 0) {
        segregationError(seg, CATEGORY_VALUE, EditCodes.NP01);
      }
      total += pieces;
    }
    if (!Long.valueOf(total).equals(version.lng("log_count"))) {
      versionError(CATEGORY_RELATED, EditCodes.LC01);
    }
  }

  /** {@code isValidLA00}: a whole number of at least 1. */
  static boolean isValidLoadArrivalNumber(String value) {
    try {
      return Integer.parseInt(value) >= 1;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** {@code validatePopStratumYearExtra}: SD04 before the stratum's effective date, SD05 after its expiry. */
  private void validatePopStratumYearExtra() {
    Stratum stratum = stratum(population(), stratumNo(), year())
        .orElseThrow(() -> legacyFailure("validatePopStratumYearExtra: Stratum not found for population="
            + population() + " stratum=" + stratumNo() + " year=" + year()));
    LocalDateTime scaleDate = scaleDate();
    if (scaleDate.isBefore(stratum.effectiveDate())) {
      versionError(CATEGORY_RELATED, EditCodes.SD04);
    }
    if (scaleDate.isAfter(stratum.expiryDate())) {
      versionError(CATEGORY_RELATED, EditCodes.SD05);
    }
  }

  /** {@code validateHeaderTimberMark}: TM07 (cruise based) or TM11 (area based); TM08 (cash sale). */
  private void validateHeaderTimberMark() {
    String markCode = str("timber_mark");
    if (empty(markCode)) return;
    TimberMark mark = requiredTimberMark(markCode, "Previously validated mark not found!");
    if (mark.isCruiseBased()) {
      versionError(CATEGORY_RELATED, EditCodes.TM07);
    } else if (mark.isAreaBased()) {
      versionError(CATEGORY_RELATED, EditCodes.TM11);
    }
    if (mark.isCashSale()) {
      versionError(CATEGORY_RELATED, EditCodes.TM08);
    }
  }

  /**
   * {@code validateClients}: bill-to is the population owner, copy-to the stratum owner
   * ({@code XmlStatementHelper.lookupPopulationOwner / lookupStratumOwner}); either missing was an
   * {@code XmlCreateException} (runtime failure) in legacy, also for a return without a stratum.
   */
  @Override
  protected void validateClients() {
    Population population = lookups.population(population(), year())
        .orElseThrow(() -> legacyFailure("Can't find owner for pop" + population() + ":" + year()));
    validateBillToClientAndLocation(fetchClientLocation(population.clientNumber(), population.clientLocnCode()));
    Stratum stratum = stratum(population(), stratumNo(), year())
        .orElseThrow(() -> legacyFailure("Can't find owner for pop" + population() + ":" + year()));
    validateCopyToClientAndLocation(fetchClientLocation(stratum.clientNumber(), stratum.clientLocnCode()));
  }

  /** {@code validatePopulation} (BT 14020): SRT02 when the population uses default ratios. */
  private void validatePopulation() {
    String pop = population();
    if (pop == null || pop.trim().isEmpty() || year() == null) return;
    Population population = lookups.population(pop, year())
        .orElseThrow(() -> legacyFailure("getPopulation: population not found " + pop + ":" + year()));
    if (EditCodes.RATIO_TYPE_DEFAULT.equals(population.ratioTypeCode())) {
      versionError(CATEGORY_SCALING, EditCodes.SRT02);
    }
  }
}
