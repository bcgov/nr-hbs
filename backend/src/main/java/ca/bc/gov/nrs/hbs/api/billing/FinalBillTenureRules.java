package ca.bc.gov.nrs.hbs.api.billing;

import ca.bc.gov.nrs.hbs.api.legacyrules.Inputs;
import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The P835 / P836 form validations ({@code P835Action.validateForm},
 * {@code P836Action.validateForm}) in their legacy order, sharing the
 * timber-mark renewability rule of {@code TimberMark.isRenewable()}.
 *
 * <p>"Invoices or statements exist" is legacy's YTD_CYCLIC_BILLING check
 * ({@code doesPopulationSamplingYearTimberMarkExist} — note its inverted name:
 * it returned true when <i>no</i> cyclic billing row exists).
 */
@Component
public class FinalBillTenureRules {

  /** TimberMark.RENEWABLE_FILE_TYPES. */
  static final Set<String> RENEWABLE_FILE_TYPES = Set.of("A01", "A02", "A03", "A04", "A05", "A28", "B01");
  /** TimberMark.RENEWABLE_QUOTA_TYPES. */
  static final Set<String> RENEWABLE_QUOTA_TYPES = Set.of("A", "B");
  /** SmpConstants.DEFAULT_RATIO_TYPE. */
  static final String DEFAULT_RATIO_TYPE = "D";

  private final BillingReads reads;

  public FinalBillTenureRules(BillingReads reads) {
    this.reads = reads;
  }

  /** The three key fields as typed (trimmed; population and mark upper-cased). */
  public record Keys(String population, String samplingYear, String timberMark) {
    long year() {
      return Long.parseLong(samplingYear);
    }
  }

  /** TimberMark.isRenewable(): renewable file type AND quota type (trimmed; null = ""). */
  static boolean isRenewable(Map<String, Object> timberMark) {
    String fileType = trim(Inputs.colText(timberMark, "FILE_TYPE_CODE"));
    String quotaType = trim(Inputs.colText(timberMark, "QUOTA_TYPE_CODE"));
    return RENEWABLE_FILE_TYPES.contains(fileType) && RENEWABLE_QUOTA_TYPES.contains(quotaType);
  }

  private static String trim(String s) {
    return s == null ? "" : s.trim();
  }

  static boolean allNumbers(String s) {
    return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
  }

  /**
   * P835Action.validateForm for a new election. Like legacy, an unparseable
   * sampling year stops the validation at the cyclic-billing check
   * ({@code new Long(...)} threw NumberFormatException, caught silently).
   */
  public List<Problem> validateCreate(Keys k) {
    List<Problem> errors = new ArrayList<>();
    boolean popFlag = population(k.population(), false, errors);
    boolean yearFlag = year(k.samplingYear(), errors);
    if (popFlag && yearFlag) {
      Map<String, Object> pop = reads.population(k.population(), k.year());
      if (pop != null && DEFAULT_RATIO_TYPE.equals(Inputs.colText(pop, "RATIO_TYPE_CODE"))) {
        popFlag = false;
        errors.add(new Problem("populationNumber", LegacyMessages.FB_DEFAULT_RATIO));
      }
    }
    boolean markFlag = true;
    if (k.timberMark() == null) {
      errors.add(new Problem("timberMark", LegacyMessages.TIMBER_MARK_REQUIRED));
      markFlag = false;
    }
    if (!allNumbers(k.samplingYear())) {
      return errors; // legacy NumberFormatException: the remaining checks never ran
    }
    if (k.timberMark() != null && popFlag && yearFlag
        && reads.cyclicBillingExists(k.population(), k.year(), k.timberMark())) {
      errors.add(new Problem("timberMark", LegacyMessages.FB_INSERT_ERROR));
      markFlag = false;
    }
    if (k.timberMark() != null) {
      Map<String, Object> mark = reads.timberMark(k.timberMark());
      if (mark == null) {
        errors.add(new Problem("timberMark", LegacyMessages.TIMBER_MARK_INVALID));
        markFlag = false;
      }
      if (markFlag && isRenewable(mark)) {
        errors.add(new Problem("timberMark", LegacyMessages.TIMBER_MARK_RENEWABLE));
      }
    }
    if (markFlag && popFlag && yearFlag) {
      electionChecks(k, errors);
    }
    return errors;
  }

  /**
   * P836Action.validateForm for changed keys (Save): the new keys' field
   * checks (population must be 4 digits here), the timber mark checks, then
   * Pop/Yr existence and duplicate election. No Default-ratio check on P836.
   * Returns whether the duplicate check passed (legacy {@code isSelected}).
   */
  public boolean validateNewKeys(Keys k, List<Problem> errors) {
    boolean popFlag = population(k.population(), true, errors);
    boolean yearFlag = year(k.samplingYear(), errors);
    boolean markFlag = true;
    if (k.timberMark() == null) {
      errors.add(new Problem("timberMark", LegacyMessages.TIMBER_MARK_REQUIRED));
      markFlag = false;
    } else {
      Map<String, Object> mark = reads.timberMark(k.timberMark());
      if (mark == null) {
        errors.add(new Problem("timberMark", LegacyMessages.TIMBER_MARK_INVALID));
        markFlag = false;
      } else if (isRenewable(mark)) {
        errors.add(new Problem("timberMark", LegacyMessages.TIMBER_MARK_RENEWABLE));
        markFlag = false;
      }
    }
    if (markFlag && popFlag && yearFlag) {
      return electionChecks(k, errors);
    }
    return true;
  }

  /** Population / Sampling Year must exist; the Pop/Yr/Mark must not already be elected. */
  private boolean electionChecks(Keys k, List<Problem> errors) {
    if (reads.population(k.population(), k.year()) == null) {
      errors.add(new Problem("populationNumber", LegacyMessages.FB_POP_YEAR_INVALID));
    }
    if (reads.finalBillMark(k.population(), k.year(), k.timberMark()) != null) {
      errors.add(new Problem("timberMark", LegacyMessages.FB_ALREADY_ELECTED));
      return false;
    }
    return true;
  }

  private boolean population(String population, boolean fourDigits, List<Problem> errors) {
    if (population == null) {
      errors.add(new Problem("populationNumber", LegacyMessages.POPULATION_REQUIRED));
      return false;
    }
    if (!allNumbers(population) || (fourDigits && population.length() < 4)) {
      errors.add(new Problem("populationNumber", LegacyMessages.POPULATION_DIGITS));
      return false;
    }
    if (!reads.populationExists(population)) {
      errors.add(new Problem("populationNumber", LegacyMessages.FB_POPULATION_INVALID));
      return false;
    }
    return true;
  }

  private static boolean year(String year, List<Problem> errors) {
    if (year == null) {
      errors.add(new Problem("samplingYear", LegacyMessages.FB_YEAR_REQUIRED));
      return false;
    }
    if (!allNumbers(year) || year.length() < 4 || year.length() > 9
        || Long.parseLong(year) > 2099 || Long.parseLong(year) < 2000) {
      errors.add(new Problem("samplingYear", LegacyMessages.FB_YEAR_INVALID));
      return false;
    }
    return true;
  }

  public boolean cyclicBillingExists(Keys k) {
    return reads.cyclicBillingExists(k.population(), k.year(), k.timberMark());
  }
}
