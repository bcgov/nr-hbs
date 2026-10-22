package ca.bc.gov.nrs.hbs.api.detailreturns.validation;

/**
 * Detail-return edit codes of {@code $E/dac/detail/control/CrossReferenceEdit}
 * (interfaces become nested classes), copied verbatim (generated from the
 * legacy source).
 */
public final class CrossReferenceEdit {

  private CrossReferenceEdit() {
  }

  public static final class Common {
    private Common() {
    }
    public static final String SIGNATURE_FAILURE = "DIGSIG";
    public static final String SCALE_SITE_XREF = "SS01";
    public static final String DESTINATION_SITE_XREF = "DSS01"; // BT 5646
    public static final String SCALE_SITE_WASTE = "SSZZ";
    public static final String TIMBERMARK_XREF = "TM01";
    public static final String TIMBERBRAND_XREF = "TB01";
    public static final String TIMBERMARK_INACTIVE = "TM02";
    public static final String CUTBLOCK_INVALID = "CTB00";
    public static final String CUTBLOCK_VALID = "CTB01"; //5705
    public static final String CUTBLOCK_NOTVALID = "CTB02"; //5705
    public static final String SCALE_DATE_CURRENT_DATE = "SD03";
    public static final String ARRIVAL_LIMIT_DATE = "AD02"; //10747
    public static final String DEPARTURE_LIMIT_DATE = "DD02"; //10747
    public static final String ARRIVAL_DATE_CURRENT_DATE = "AD03"; // BT 5646
    public static final String ARRIVAL_DATE_BEFORE_SCALE_DATE = "AD04"; //BT 2912
    public static final String DEPARTURE_DATE_CURRENT_DATE = "DD03"; // BT 5646
    public static final String SCALE_DATE_CONTRACT = "SD06";
    public static final String SCALE_DATE_NO_RATE = "SD08";
    public static final String SCALE_DATE_NO_STUMPAGE_RATE = "SD10";
    public static final String SCALE_DATE_MULTI_RATE = "SD11";
    public static final String PRIMARY_SCALER_LICENSE_XREF = "PSL01";
    public static final String SECONDARY_SCALER_LICENSE_XREF = "SSL01";
    public static final String SIGNING_SCALER_LICENSE_XREF = "SGL01";
    public static final String SIGNING_SCALER_LICENSE_MATCH = "SGL02";
    public static final String WS_NUMBER_INVALID = "WSN01";
    public static final String PARCEL_ID_PARCEL_COUNT_DEPENDENCY = "PI00";
    public static final String PARCEL_ID_LOWER_BOUND = "PI02";
    public static final String PARCEL_COUNT_PARCEL_ID_DEPENDENCY = "PC00";
    public static final String PARCEL_COUNT_LOWER_BOUND = "PC02";
    public static final String FIELD_SCALE_DECK_ID_NOT_PRESENT = "FSD02";
    public static final String FIELD_SCALE_DECK_ID = "FSD01";
    public static final String FIELD_SCALE_DECK_ID_MATCH = "FSX04";
    public static final String SCALE_DATE_LIMIT = "SD02"; //10747
    public static final String SIGNATURE_NOT_PROVIDED = "DSNP01"; // Digital Signature not provided
    public static final String SIGNATURE_KEY_UNKNOWN = "DSEK01"; // Unknown Encryption Key
    public static final String SIGNATURE_KEY_INACTIVE = "DSEK02"; // key exist by inactive
    public static final String SIGNATURE_DECRYPT_FAILURE = "DSFMT1"; //cannot decrypt
    public static final String SIGNATURE_VALIDATION_FAILURE = "DSFLR1"; //decrypted, but not valid
  }
  public static final class LogTallyCommon {
    private LogTallyCommon() {
    }
    public static final String STRATUM_XREF = "ST01";
    public static final String ORIGINATING_SACLE_SITE_XREF = "OSS01";
    public static final String ORIGINAL_SCALER_LICENSE_XREF = "OSL01";
    public static final String TIMBERBRAND_SAME_SCALE_SITE = "TB02";
    public static final String LOG_COUNT = "LC01";
    public static final class Log {
      private Log() {
      }
      public static final String SPECIES_PRODUCT_GRADE_NOT_EXIT = "SP01";
      public static final String SPECIES_PRODUCT_GRADE_NOT_EFFECT = "SP02";
      public static final String TIMBER_MARK_COASTAL_GRADE = "CG01";
      public static final String TIMBER_MARK_INTERIOR_GRADE = "IG01";
      public static final String TIMBER_MARK_COASTAL_GRADE_NOT_EFFECT = "CG02";
      public static final String TIMBER_MARK_INTERIOR_GRADE_NOT_EFFECT = "IG02";
      public static final String TIMBER_MARK_COASTAL_STRATUM = "CG03";
      public static final String TIMBER_MARK_INTERIOR_STRATUM = "IG03";
      public static final String WEIGH_TIMBER_MARK_COASTAL_STRATUM = "CG04";
      public static final String WEIGH_TIMBER_MARK_INTERIOR_STRATUM = "IG04";
      public static final String SPECIES_PRODUCT_GRADE = "GR01";
      public static final String LENGTH = "LT01";
      public static final String TOP = "TP01";
      public static final String BUTT = "BT01";
      public static final String TOP_MAX = "TPMax";
      public static final String BUTT_MAX = "BTMax";
      public static final String DUPLICATE_LOG_NUMBER = "LG00"; //BT14847
    }
  }
  public static final class SpecialForestProduct {
    private SpecialForestProduct() {
    }
    public static final String SCALE_METHOD = "SM22";
    public static final String BATCH_CONTROL_TOTAL = "FBC05";
    public static final String DETAIL_RECORD_COUNT = "FTBH23";
    public static final class ProratedPieceCount {
      private ProratedPieceCount() {
      }
      public static final String RANGE = "PPC01";
      public static final String REQUIRED = "FTAH32";
      public static final String NOT_PERMITTED = "FTBH32";
    }
    public static final String LOG_NUMBER = "FTAS02";
    public static final String NET_VOLUME = "FTAS08";
    public static final String STACK_NUMBER = "FTAS09";
    public static final String SOLID_VOLUME = "FTAS13";
    public static final String BUNDLER_NUMBER = "FTAS14";
    public static final String AVERAGE_BUNDLE_NUMBER = "FTAS16";
    public static final class DetailRecordCommon {
      private DetailRecordCommon() {
      }
      public static final String SPECIES_PRODUCT_GRADE = "GR02";
    }
    public static final class Stack {
      private Stack() {
      }
      public static final String WIDTH = "WD01";
      public static final String HEIGHT = "HT01";
      public static final String LENGTH = "LN02";
      public static final String CONVERSION_FACTOR = "CF01";
    }
  }
  public static final class LogTally {
    private LogTally() {
    }
    public static final String PARCEL_ID = "PI01";
    public static final String NOT_BEACHCOMB = "BC01";
    public static final String BEACHCOMB = "BC02";
    public static final String FIELD_DECK_ID = "FSX01"; //8253
    public static final String FIELD_DECK_CASE = "FSX03"; //8253
    public static final class Log {
      private Log() {
      }
      public static final String TIMBERMARK = "TM04";
      public static final String NMV_N = "NM04";
      public static final String NMV_Y = "NM02";
    }
  }
  public static final class SampleLogTally {
    private SampleLogTally() {
    }
    public static final String STRATUM_XREF = "ST01";
    public static final String SAMPLE_WEIGHT = "SW01";
    public static final String SCALE_DATE_STRATUM_EFF = "SD04";
    public static final String SCALE_DATE_STRATUM_EXP = "SD05";
    public static final String TIMBERMARK_CASH_SCALE = "TM08"; //BT5884
    public static final String TIMBERMARK_CRUISE_BASED = "TM07"; //BT5884
    public static final String TIMBERMARK_AREA_BASED = "TM11"; //BT5884
    public static final String SAMPLE_RATIO = "SR01"; // BT 5216
    public static final String DEFAULT_RATIO = "SRT01";
    public static final String WEIGH_SLIP = "MM01";
    public static final String WEIGH_SLIP_REMOTE = "MM03";
    public static final String WEIGH_SLIP_FOUND_MORE = "MM11";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_EVENTTYPE = "MM21";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_POP = "MM22";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_YEAR = "MM23";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_STRAT = "MM24";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_TIMBERMARK = "MM25";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_WEIGHTS = "MM26";
    public static final String WEIGH_SLIP_FOUND_MORE_REMOTE = "MM13";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_EVENTTYPE = "MM41";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_POP = "MM42";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_YEAR = "MM43";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_STRAT = "MM44";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_TIMBERMARK = "MM45";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_WEIGHTS = "MM46";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_DESTINATION = "MM47";
    public static final String WEIGH_SLIP_FOUND_UNMATCHING_SAMPLE_BEFORE_SLIP = "MM09";
    public static final String WEIGH_SLIP_REMOTE_FOUND_UNMATCHING_SAMPLE_BEFORE_SLIP = "MM10";
  }
  public static final class WeighSlip {
    private WeighSlip() {
    }
    public static final String GROSS_WEIGHT = "GW01";
    public static final String TARE_WEIGHT = "TW01";
    public static final String GRAPPLEWEIGHT_LESSTHAN_TAREWEIGHT = "SDW01";
    public static final String GRAPPLEWEIGHT_MORETHAN_GROSSWEIGHT = "SDW02";
    public static final String STRATUM_XREF = "ST01";
    public static final String STRATUM_NON_BILLABLE = "ST02";
    public static final String PSY_LENGTH_LESS = "ST03"; //BT8183
    public static final String PSY_LENGTH_TWO_DIGIT_YEAR = "ST04"; //BT 8183
    public static final String NON_BILLABLE_COMPANY = "ST05"; //11263
    public static final String SCALE_DATE_STRATUM_EFF = "SD04";
    public static final String SCALE_DATE_STRATUM_EXP = "SD05";
    public static final String SAMPLE_TYPE_SAMPLE = "SAM01";
    public static final String SAMPLE_TYPE_PRIMARY = "SAM02";
    public static final String TIMBERMARK_CASH_SCALE = "TM08"; //BT5884
    public static final String TIMBERMARK_CRUISE_BASED = "TM07"; //BT5884
    public static final String TIMBERMARK_AREA_BASED = "TM11"; //BT5884
    public static final String TIMBERBRAND_SAME_SCALE_SITE = "TB03";
    public static final String WEIGH_SLIP_NUMBER_INVALID = Common.WS_NUMBER_INVALID;
    public static final String LOCAL_REMOTE_SAMPLE = "MM05";
    public static final String LOCAL_REMOTE_REDTAG = "MM06";
    public static final String FIELD_DECK_ID = "FSX02";
    public static final String FIELD_DECK_ID_CASE = "FSX03";
    public static final String FIELD_DECK_FOR_DEFAULT_RATIO = "FSD03";
    public static final String DEFAULT_RATIO = "SRT03";
  }
  public static final class CheckScale {
    private CheckScale() {
    }
    public static final String NO_ORIGINAL = "CHKS01";
    public static final String MULTIPLE_ORIGINALS = "CHKS02";
    public static final String ORIGINAL_CHECKSSCALE = "CHKS03";
    public static final String AFTER_CHECKSCALE = "CHKSD1";
    public static final String BEFORE_CHECKSCALE = "CHKSD2";
    public static final String EQUAL_LICENCENO = "CHKS00";
    public static final String CHECK_SB4 = "CHKS04";
    public static final String CHECK_COMPANY = "CHKS05";
    public static final String CHECKSCALE = "CHKS06";
    public static final String INVALID_STATE = "CHKS07";
    public static final String SCALE_SITE = "CHKS08";
    public static final String POPULATION = "CHKS09";
    public static final String NO_ACTIVE_ORIGINAL = "CHKS11";
    public static final String SCALER_LICENSE = "CHKS12";
    public static final String RETURN_NUMBER = "CHKS13";
    public static final String LOCAL_SAMPLE = "MM07";
    public static final String REMOTE_SAMPLE = "MM08";
    public static final String WEIGH_SLIP_FOUND_MORE_CHECK_SCALE = "MM17";
    public static final String WEIGH_SLIP_FOUND_MORE_REMOTE_CHECK_SCALE = "MM18";
  }
  public static final class CategoryCode {
    private CategoryCode() {
    }
    public static final String CATEGORY_CODE_SCALING = "SCL";
    public static final String CATEGORY_CODE_BILLING = "BIL";
    public static final String CATEGORY_CODE_DATE = "DTE"; //10747
    public static final String CATEGORY_CODE_LOOKUP = "LKP";
    public static final String CATEGORY_CODE_RELATED = "REL";
    public static final String CATEGORY_CODE_FORMAT = "FMT";
  }
}
