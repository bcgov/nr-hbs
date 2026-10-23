package ca.bc.gov.nrs.hbs.api.intake.xml;

/**
 * Format edit (message) codes the unpack records in the transmission, batch
 * and batch-document format error tables, category {@code FMT}. Copied from
 * the legacy {@code dac.detail.xml.FormatEdit}; the descriptions shown on the
 * transmission / batch screens come from {@code HBS_EDIT_ERR_MESSAGE_CODE}.
 */
public final class FormatEdit {

  public static final String CATEGORY = "FMT";

  private FormatEdit() {
  }

  /** Transmission level (TRANSMISSION_FORMAT_ERROR). */
  public static final class Transmission {
    public static final String VALIDATION = "FXA00";
    public static final String BATCH_COUNT = "FXA01";
    public static final String TYPE = "FXA02";
    public static final String CREATOR = "FXA03";
    public static final String SOFTWARE_VENDOR = "FXA04";

    private Transmission() {
    }
  }

  /** Batch level (DTL_BAT_FORMAT_ERROR). */
  public static final class Batch {
    public static final String SCALE_SITE = "FBA01";
    public static final String FROM_SCALE_DATE_VALID = "FBA02";
    public static final String TO_SCALE_DATE_VALID = "FBA03";
    public static final String DOCUMENT_COUNT = "FBA04";
    public static final String BATCH_CONTROL_TOTAL_LOG_COUNT = "FBA05";
    public static final String BATCH_CONTROL_TOTAL_GROSS_WEIGHT = "FBB05";
    public static final String BATCH_CONTROL_TOTAL_DOCUMENT_COUNT = "FBC05";
    public static final String SITE_SUBMITTER = "FBA06";

    private Batch() {
    }
  }

  /** Weigh slip documents (DTL_BAT_DOC_FORMAT_ERROR). */
  public static final class WeighSlip {
    public static final String WEIGHT_DOCUMENT_TYPE = "FWAH01";
    public static final String SCALE_SITE = "FWAH02";
    public static final String SCALE_DATE_VALID = "FWAH04";
    public static final String SCALE_TIME_VALID = "FWAH05";
    public static final String INCOMING_LDS_NUMBER_NOT_PERMITTED = "FWAH06";
    public static final String INCOMING_LDS_NUMBER_REQUIRED = "FWBH06";
    public static final String TRANSPORT_IDENTIFIER_NOT_PERMITTED = "FWAH07";
    public static final String TRANSPORT_IDENTIFIER_REQUIRED = "FWBH07";
    public static final String SAMPLE_DEDUCTION_NOT_PERMITTED = "FWAH10";
    public static final String SOURCE_NOT_PERMITTED = "FWAH11";
    public static final String SOURCE_REQUIRED = "FWBH11";
    public static final String PSY_NOT_PERMITTED = "FWAH15";
    public static final String PSY_REQUIRED = "FWBH15";
    public static final String OUTGOING_LDS_NUMBER_NOT_PERMITTED = "FWAH17";
    public static final String OUTGOING_LDS_NUMBER_REQUIRED = "FWBH17";
    public static final String DESTINATION_NOT_PERMITTED = "FWAH18";
    public static final String DESTINATION_REQUIRED = "FWBH18";
    public static final String DOCUMENT_VERSION = "FWAH24";
    public static final String HASH_TOTAL = "FWAH28";

    private WeighSlip() {
    }
  }

  /** Piece, sample, other and SFP tallies (DTL_BAT_DOC_FORMAT_ERROR). */
  public static final class LogTally {
    public static final String SAMPLE_DOCUMENT_TYPE = "FTAH01";
    public static final String PIECE_DOCUMENT_TYPE = "FTBH01";
    public static final String SCALE_SITE = "FTAH02";
    public static final String LOAD_ARRIVAL_NUMBER_NOT_PERMITTED = "FTAH03";
    public static final String LOAD_ARRIVAL_NUMBER_REQUIRED = "FTBH03";
    public static final String INCOMING_LSD_NUMBER_NOT_PERMITTED = "FTAH04";
    public static final String INCOMING_LSD_NUMBER_REQUIRED = "FTBH04";
    public static final String TRANSPORT_IDENTIFIER_NOT_PERMITTED = "FTAH05";
    public static final String TRANSPORT_IDENTIFIER_REQUIRED = "FTBH05";
    public static final String SOURCE_NOT_PERMITTED = "FTAH06";
    public static final String SOURCE_REQUIRED = "FTBH06";
    public static final String CUTBLOCK_ID_NOT_PERMITTED = "FTAH07";
    public static final String CUTBLOCK_ID_REQUIRED = "FTBH07";
    public static final String WEIGH_SLIP_NUMBER_NOT_PERMITTED = "FTAH11";
    public static final String WEIGH_SLIP_NUMBER_REQUIRED = "FTBH11";
    public static final String ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED = "FTAH13";
    public static final String ORIGINATING_SCALE_SITE_NUMBER_REQUIRED = "FTBH13";
    public static final String SPLIT_LOAD_NOT_PERMITTED = "FTAH17";
    public static final String CHECK_SCALE_NOT_PERMITTED = "FTAH19";
    public static final String CHECK_SCALE_REQUIRED = "FTBH19";
    public static final String SCALE_DATE_VALID = "FTAH14";
    public static final String LOG_COUNT = "FTAH23";
    public static final String DOCUMENT_VERSION = "FTAH29";
    public static final String HASH_TOTAL = "FTAH33";
    public static final String STACK_NOT_PERMITTED = "FTAH35";
    public static final String BUNDLE_NOT_PERMITTED = "FTAH36";
    public static final String SFP_LOG_MEASURE_NOT_PERMITTED = "FTAH37";
    public static final String PRORATED_PIECE_COUNT_NOT_PERMITTED = "FTAH32";
    public static final String PRORATED_PIECE_COUNT_REQUIRED = "FTBH32";

    public static final String LOG_NUMBER = "FTAL01";
    public static final String LOG_TIMBERMARK = "FTAL02";
    public static final String LOG_VOLUME = "FTAL09";
    public static final String LOG_DEFECT_COUNT = "FTAL10";
    public static final String DEFECT_NUMBER = "FTAD01";

    private LogTally() {
    }
  }

  /** Arrival / departure ledger entries (DTL_BAT_DOC_FORMAT_ERROR). */
  public static final class Ledger {
    public static final String SCALE_SITE = "FLA02";
    public static final String ARRIVAL_NOT_PERMITTED = "FLA03";
    public static final String ARRIVAL_REQUIRED = "FLB03";
    public static final String ARRIVAL_DATE_VALID = "FLA04";
    public static final String CUTBLOCK_ID_NOT_PERMITTED = "FLA08";
    public static final String CUTBLOCK_ID_REQUIRED = "FLB08";
    public static final String FIELD_SCALE_FLAG_NOT_PERMITTED = "FLA09";
    public static final String FIELD_SCALE_FLAG_REQUIRED = "FLB09";
    public static final String WEIGH_SLIP_NUMBER_NOT_PERMITTED = "FLA10";
    public static final String WEIGH_SLIP_NUMBER_REQUIRED = "FLB10";
    public static final String SCALE_NOT_PERMITTED = "FLA11";
    public static final String SCALE_REQUIRED = "FLB11";
    public static final String SCALE_DATE_VALID = "FLC11";
    public static final String SPLIT_LOAD_NOT_PERMITTED = "FLA14";
    public static final String DEPARTURE_DATE_VALID = "FLA17";
    public static final String FIELD_SCALE_DECK_ID_NOT_PERMITTED = "FLA21";
    public static final String FIELD_SCALE_DECK_ID_REQUIRED = "FLB21";
    public static final String ORIGINATING_SCALE_SITE_NUMBER_NOT_PERMITTED = "FLA22";
    public static final String ORIGINATING_SCALE_SITE_NUMBER_REQUIRED = "FLB22";
    public static final String FIELD_SCALE_DECK_ID_PRESENT_WHEN_FLAG_Y = "FSD01";

    private Ledger() {
    }
  }

  /** {@code ValidationHelper.restrictScaleDate} (BT6020): dates older than the seven-year limit. */
  public static final class YearLimit {
    public static final String DEPARTURE_DATE = "OLDDD1";
    public static final String ARRIVAL_DATE = "OLDAD1";
    public static final String SCALE_DATE = "OLDSD1";

    private YearLimit() {
    }
  }
}
