package ca.bc.gov.nrs.hbs.api.sampling;

import java.time.LocalDate;

/**
 * {@code $V/smp/CalculateNewStrataDate.getPlanEffectExpDate}: a stratum's
 * period moved into a new plan period (P897 "Create New Plan From Actuals"),
 * keeping its month offsets in proportion to the plan durations. Ported with
 * the legacy month arithmetic (a period may cross at most one year end; integer
 * division; the expiry is moved to the last day of its month).
 */
final class StrataDates {

  private StrataDates() {
  }

  /**
   * @param effective true for the new stratum effective date, false for the expiry date
   */
  static LocalDate scaledDate(LocalDate newEffect, LocalDate newExp, LocalDate orgEffect, LocalDate orgExp,
      LocalDate orgStrataEffect, LocalDate orgStrataExp, boolean effective) {
    int newPopDuration = months(newEffect, newExp);
    if (newExp.getDayOfMonth() == newExp.lengthOfMonth()) {
      newPopDuration = newPopDuration + 1;
    }
    int popDuration = months(orgEffect, orgExp);
    if (orgExp.getDayOfMonth() == orgExp.lengthOfMonth()) {
      popDuration = popDuration + 1;
    }
    int orgStrataEffectOffset;
    if (orgStrataEffect.getYear() > orgEffect.getYear()) {
      orgStrataEffectOffset = (12 + month(orgStrataEffect)) - month(orgEffect);
    } else {
      orgStrataEffectOffset = month(orgStrataEffect) - month(orgEffect);
    }
    int orgStrataExpOffset;
    if (orgExp.getYear() > orgStrataExp.getYear()) {
      orgStrataExpOffset = month(orgStrataExp) - (12 + month(orgExp));
    } else {
      orgStrataExpOffset = month(orgStrataExp) - month(orgExp);
    }
    if (effective) {
      int offset = popDuration != 0 ? orgStrataEffectOffset * newPopDuration / popDuration : 0;
      return newEffect.plusMonths(offset);
    }
    int offset = popDuration != 0 ? orgStrataExpOffset * newPopDuration / popDuration : 0;
    LocalDate d = newExp.plusMonths(offset);
    return d.withDayOfMonth(d.lengthOfMonth());
  }

  /** Calendar.MONTH difference as the legacy code computed it (exp year > eff year → +12). */
  private static int months(LocalDate from, LocalDate to) {
    if (to.getYear() > from.getYear()) {
      return (12 + month(to)) - month(from);
    }
    return month(to) - month(from);
  }

  /** Calendar.MONTH (0-based). */
  private static int month(LocalDate d) {
    return d.getMonthValue() - 1;
  }
}
