package ca.bc.gov.nrs.hbs.api.summaryreturns;

/**
 * One release edit failure ({@code HbsEditError}), saved to the version, segregation
 * or daily-load error table of the return's type by {@link SummaryReturnWriter#saveErrors}.
 *
 * @param target        where the legacy factory put it ({@code HbsEditErrorFactory.create*Error})
 * @param category      {@code HBS_EDIT_ERR_CATEGORY_CODE} (FMT, SCL, STR, RTE, DTE, LKP, REL, VAL)
 * @param message       {@code HBS_EDIT_ERR_MESSAGE_CODE} (e.g. TM01, SD17)
 * @param segregationId for {@link Target#SEGREGATION}
 * @param day           for {@link Target#DAILY_LOAD}
 */
public record EditError(Target target, String category, String message, Long segregationId, Long day) {

  public enum Target { VERSION, SEGREGATION, DAILY_LOAD }

  public static final String CATEGORY_FORMAT = "FMT";
  public static final String CATEGORY_SCALING = "SCL";
  public static final String CATEGORY_STRUCTURE = "STR";
  public static final String CATEGORY_RATE = "RTE";
  public static final String CATEGORY_DATE = "DTE";
  public static final String CATEGORY_LOOKUP = "LKP";
  public static final String CATEGORY_RELATED = "REL";
  public static final String CATEGORY_VALUE = "VAL";

  public static EditError version(String category, String message) {
    return new EditError(Target.VERSION, category, message, null, null);
  }

  public static EditError segregation(long segregationId, String category, String message) {
    return new EditError(Target.SEGREGATION, category, message, segregationId, null);
  }

  public static EditError dailyLoad(long day, String category, String message) {
    return new EditError(Target.DAILY_LOAD, category, message, null, day);
  }
}
