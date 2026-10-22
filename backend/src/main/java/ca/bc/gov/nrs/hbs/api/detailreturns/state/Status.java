package ca.bc.gov.nrs.hbs.api.detailreturns.state;

/**
 * The two state code sets of a detail scale document, as the legacy
 * {@code ca.bc.gov.mof.hbs.dac.DacConstants} declared them.
 *
 * <ul>
 *   <li>Document state ({@code DETAIL_SCALE_DOCUMENT.SCALE_RETURN_STATE_CODE},
 *       {@code DacConstants.ScaleReturnStatus}).</li>
 *   <li>Version state ({@code <version table>.SCL_RTN_VERSION_STATE_CODE},
 *       {@code DacConstants.VersionStatus}), paired with ACTIVE_VERSION_IND.</li>
 * </ul>
 */
public final class Status {

  private Status() {
  }

  /** {@code DacConstants.ScaleReturnStatus}. */
  public static final class Document {
    public static final String ORIGINAL_IN_PROGRESS = "OIP";
    public static final String ORIGINAL_COMPLETE = "OCM";
    public static final String ORIGINAL_DISCARDED = "DIS";
    public static final String CANCEL_IN_PROGRESS = "CIP";
    public static final String CANCEL_COMPLETE = "CCM";
    public static final String CANCEL_WITH_REPLACE_NO_CHANGES = "CWR";
    public static final String REPLACE_NO_CHANGES_IN_PROGRESS = "RNP";
    public static final String REPLACE_NO_CHANGES_COMPLETE = "RNC";
    public static final String CANCEL_WITH_REPLACE_WITH_CHANGES = "CWC";
    public static final String REPLACE_WITH_CHANGES_IN_PROGRESS = "RCP";
    public static final String REPLACE_WITH_CHANGES_COMPLETE = "RCC";

    private Document() {
    }
  }

  /** {@code DacConstants.VersionStatus}. */
  public static final class Version {
    public static final String ACCEPTED = "ACC";
    public static final String AWAITING_APPROVAL = "AWP";
    public static final String CANCELLED_AND_INVOICED = "CAN";
    public static final String DISCARDED = "DEL";
    public static final String SIGNATURE_FAILED = "DSF";
    public static final String ERROR = "ERR";
    public static final String FAILED = "FAL";
    public static final String HELD = "HLD";
    public static final String INCOMPLETE = "INC";
    public static final String READY = "RDY";
    public static final String LOCKED = "LCK";
    public static final String INVOICED = "ISS";
    public static final String CORRECTION_PENDING = "PND";
    public static final String REPLACEMENT_VERSION_PENDING = "PNV";
    public static final String SUPERCEDED = "SPR";
    public static final String NOT_TO_BE_BILLED = "NTI";
    public static final String INVALID_STATE = "INV";

    private Version() {
    }
  }
}
