package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Caller;
import ca.bc.gov.nrs.hbs.api.detailreturns.FieldAvailability;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.validation.PopStratYear;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

/**
 * The request-level checks of the detail data-entry forms, run before the
 * workflow; a non-empty result refuses the request with the legacy messages.
 * Ported from:
 * <ul>
 *   <li>the Struts {@code validation.xml} forms P552Form (log tally header and
 *       lines), P600Form (weigh slip), P650Form (sample tally), P700Form
 *       (ledgers) and P752Form (SFP tally), each field's {@code depends} chain
 *       evaluated like Commons Validator (in order, stopping at the field's
 *       first failing validator, validator-level {@code depends} run first);</li>
 *   <li>the {@code $V/HBSValidator} methods those chains name (cited per
 *       method below) and {@code HBSValidatorHelper} / {@code HBSValidatorLookup};</li>
 *   <li>{@code P552Form.validate} (net volume range per log line),
 *       {@code P658Action.validateLog} (sample tally lines),
 *       {@code P559Action} / {@code P659Action.validateDefect} (log defects).</li>
 * </ul>
 * See {@link FormLineChecks} for the line rules.
 *
 * <p>Payload ({@code DetailPayload}): {@code fields} are version columns keyed
 * by camelCase; {@code logs} are {@code {logdtlId, fields, defects}}. A Save may
 * send only the changed fields, so unless {@code adding}, a field's checks run
 * only when its key is present, and a rule that needs another field (event
 * type, scale date, scale site…) runs only when that key is present too.
 * Problem fields are the payload keys ({@code logs[i].key} for lines).
 *
 * <p>{@code replacementInd} (P650Form, {@code validateReplacementIndicator}) needs
 * the document's state and previous version: the caller passes them as a
 * {@link Context} ({@link #validate(DocType, boolean, Map, List, Context)}).
 *
 * <p>Not ported (and why):
 * <ul>
 *   <li>{@code hbsDate} on the scale site, {@code toggle} and
 *       {@code populationStratumYearAllowed}: always valid for these forms;
 *       {@code scaleDateReqdandValid} (the dependency of {@code scaleSiteProfile})
 *       had no message, the missing date is reported by {@code dateRequired};</li>
 *   <li>{@code requestPropertyUnique} on the SFP log number: legacy added an
 *       error only for P552Form, so it never refused an SFP tally;</li>
 *   <li>{@code P758Action} is not mapped in struts-config (dead code).</li>
 * </ul>
 */
@Component
public class DetailFormValidator {

  /** Billable weigh slip event types ({@code P600Form.isBillableEventType}). */
  static final Set<String> BILLABLE_WEIGH_SLIP_EVENTS = Set.of("PS", "SS", "RS", "PD");

  private final FormLookups lookups;

  public DetailFormValidator(FormLookups lookups) {
    this.lookups = lookups;
  }

  /**
   * @param type   the document type
   * @param adding true for Add (legacy {@code actionType == "Add"}), false for Save/Release/Re-Edit
   * @param fields camelCase version columns as the SPA sent them
   * @param logs   the SPA's log lines ({@code {logdtlId, fields, defects}})
   */
  public List<Problem> validate(DocType type, boolean adding, Map<String, Object> fields,
                                List<Map<String, Object>> logs) {
    return validate(type, adding, fields, logs, Context.NONE);
  }

  /**
   * The stored document a Save edits: its state and the previous version
   * ({@code DetailManagerBean.findPreviousDocumentAndVersion}: the highest
   * version below the edited one), for the P650 replacement-indicator rule.
   *
   * @param documentState              SCALE_RETURN_STATE_CODE of the document (null on Add)
   * @param previousReplacesOriginal   the previous version's CHECK_REPLACES_ORIGINAL_IND (null: no previous version)
   * @param previousVersionState       the previous version's SCL_RTN_VERSION_STATE_CODE
   */
  public record Context(String documentState, Boolean previousReplacesOriginal, String previousVersionState) {
    public static final Context NONE = new Context(null, null, null);
  }

  /** {@link #validate(DocType, boolean, Map, List)} with the stored document's {@link Context}. */
  public List<Problem> validate(DocType type, boolean adding, Map<String, Object> fields,
                                List<Map<String, Object>> logs, Context context) {
    Run r = new Run(type, adding, fields == null ? Map.of() : fields, logs == null ? List.of() : logs);
    r.context = context == null ? Context.NONE : context;
    switch (type) {
      case LOG_TALLY -> r.p552();
      case WEIGH_SLIP -> r.p600();
      case SAMPLE_LOG_TALLY -> r.p650();
      case ARRIVAL_LEDGER, DEPARTURE_LEDGER -> r.p700();
      case SFP_TALLY -> r.p752();
    }
    new FormLineChecks(lookups, type, r.f, logs == null ? List.of() : logs, r.problems).run();
    return r.problems;
  }

  /** One validation run (the Struts form bean plus its ActionErrors). */
  final class Run {
    final DocType type;
    final boolean adding;
    final Map<String, Object> f;
    final List<Map<String, Object>> logs;
    final List<Problem> problems = new ArrayList<>();
    /** The event type when known (always on Add; on Save only when sent). */
    final String event;
    private Caller caller;
    Context context = Context.NONE;

    Run(DocType type, boolean adding, Map<String, Object> f, List<Map<String, Object>> logs) {
      this.type = type;
      this.adding = adding;
      this.f = f;
      this.logs = logs;
      String e = str(f.get("scaleEventTypeCode"));
      this.event = blank(e) ? null : e.trim().toUpperCase(Locale.ROOT);
    }

    // ── forms ────────────────────────────────────────────────────────

    /** {@code validation.xml} P552Form (log tally header; the lines are in {@link FormLineChecks}). */
    void p552() {
      String m = "LOG_TALLY";
      field("scaleEventTypeCode", () -> required("scaleEventTypeCode", "detail.version.scale-event-type-code"));
      field("parcelCount", () -> integer("parcelCount", "detail.log-tally.parcels"));
      field("parcelIdentifier", () -> integer("parcelIdentifier", "detail.log-tally.parcel-id"));
      field("returnNumber", () -> required("returnNumber", "detail.log-tally.return"),
          () -> integer("returnNumber", "detail.log-tally.return"));
      field("timberMark", () -> requiredForEvent("timberMark", "detail.log-tally.timber-mark", m, "TIMBER_MARK"),
          () -> timberMarkRef("timberMark"));
      field("logCount", () -> requiredForEvent("logCount", "detail.log-tally.logs", m, "LOG_COUNT"),
          () -> valueNotZeroForEvent("logCount", "detail.log-tally.logs", m, "LOG_COUNT"));
      scaleDate("detail.log-tally.scale-date");
      scaleSite("detail.log-tally.site", true);
      field("fieldScaleInd", () -> fsd01("fieldScaleInd", "fieldScaleDeckId"));
      primaryScaler("primaryLicenseNumber", "detail.log-tally.scaler", true);
      field("secondaryLicenseNumber", () -> scalerLicenceRef("secondaryLicenseNumber"));
      field("signingLicenseNumber", () -> scalerLicenceRef("signingLicenseNumber"));
      eventRequired("incomingLdsNumber", "detail.log-tally.load-description-slip.number", m, "LDS_NO");
      eventRequired("cutBlockId", "detail.log-tally.cut-block", m, "CUT_BLOCK");
      eventRequired("transportIdentifier", "detail.log-tally.transport-id", m, "TRANSPORT_ID");
      eventRequired("timberBrand", "detail.log-tally.timber-brand", m, "TIMBER_BRAND");
      eventRequired("loadArrivalNumber", "detail.log-tally.load-arrival", m, "LOAD_ARRIVAL");
      eventRequired("origChkSclLicenseNumber", "detail.log-tally.original-scaler", m, "ORIGINAL_SCALER");
      eventRequired("origChkSclReturnNumber", "detail.log-tally.original-scaler-return", m, "ORIGINAL_RETURN");
      field("originalDdn", () -> detailDocumentRef("originalDdn"));
      originalSite("detail.log-tally.original-scale-site", m);
    }

    /** {@code validation.xml} P600Form (weigh slip). */
    void p600() {
      String m = "WEIGH_SLIP";
      scaleSite("detail.weigh-slip.site", false);
      field("sampleDeductionWeight", () -> integer("sampleDeductionWeight", "grappleWeight"));
      primaryScaler("primaryLicenseNumber", "detail.weigh-slip.primary-scaler", false);
      field("secondaryLicenseNumber", () -> scalerLicenceRef("secondaryLicenseNumber"));
      field("signingLicenseNumber", () -> scalerLicenceRef("signingLicenseNumber"));
      field("timberMark", () -> requiredForEvent("timberMark", "detail.weigh-slip.timber-mark", m, "TIMBER_MARK"),
          () -> timberMarkRef("timberMark"));
      eventRequired("incomingLdsNumber", "detail.weigh-slip.lds.number.incoming", m, "LDS_IN");
      eventRequired("timberBrand", "detail.weigh-slip.timber-brand", m, "TIMBER_BRAND");
      field("destination", () -> requiredForEvent("destination", "detail.weigh-slip.destination", m, "DESTINATION"),
          () -> scaleSiteRef("destination", "errors.scale-site-ref"));
      eventRequired("outgoingLdsNumber", "detail.weigh-slip.lds.number.outgoing", m, "LDS_OUT");
      eventRequired("cutBlockId", "detail.weigh-slip.cut-block", m, "CUT_BLOCK");
      scaleDate("detail.weigh-slip.scale-date");
      field("fieldScaleInd", () -> fsd01("fieldScaleInd", "fieldScaleDeckId"));
      field("grossWeight", () -> requiredForEvent("grossWeight", "detail.weigh-slip.gross-weight", m, "GROSS_WEIGHT"),
          () -> integer("grossWeight", "detail.weigh-slip.gross-weight"));
      field("tareWeight", () -> requiredForEvent("tareWeight", "detail.weigh-slip.tare-weight", m, "TARE_WEIGHT"),
          () -> integer("tareWeight", "detail.weigh-slip.tare-weight"));
      eventRequired("transportIdentifier", "detail.weigh-slip.transport-id", m, "TRANSPORT_ID");
      if (adding || hasAny("populationNumber", "stratumNumber", "samplingYear", "companyUseStratum")) {
        chain(this::weighSlipPsyRequired, this::billableAndSetPsyOrCs);
      }
      field("scaleEventTypeCode", () -> required("scaleEventTypeCode", "detail.version.scale-event-type-code"));
    }

    /** {@code validation.xml} P650Form (sample tally header; P658/P659 lines in {@link FormLineChecks}). */
    void p650() {
      String m = "SAMPLE_LOG_TALLY";
      field("returnNumber", () -> required("returnNumber", "detail.sample-log-tally.return"),
          () -> integer("returnNumber", "detail.sample-log-tally.return"));
      field("logCount", () -> required("logCount", "detail.sample-log-tally.logs"),
          () -> integer("logCount", "detail.sample-log-tally.logs"),
          () -> greaterThan("logCount", "detail.sample-log-tally.logs", 9999f, "9999"),
          () -> lessThan("logCount", "detail.sample-log-tally.logs", 1f, "1"));
      scaleDate("detail.sample-log-tally.scale-date");
      field("timberMark", () -> required("timberMark", "detail.sample-log-tally.timber-mark"),
          () -> timberMarkRef("timberMark"));
      scaleSite("detail.sample-log-tally.site", true);
      primaryScaler("primaryLicenseNumber", "detail.sample-log-tally.primary-scaler", true);
      field("secondaryLicenseNumber", () -> scalerLicenceRef("secondaryLicenseNumber"));
      field("signingLicenseNumber", () -> scalerLicenceRef("signingLicenseNumber"));
      eventRequired("incomingLdsNumber", "detail.sample-log-tally.lds.number.incoming", m, "LDS_IN");
      eventRequired("transportIdentifier", "detail.sample-log-tally.transport-id", m, "TRANSPORT_ID");
      eventRequired("loadArrivalNumber", "detail.sample-log-tally.load-arrival", m, "LOAD_ARRIVAL");
      field("origChkSclReturnNumber",
          () -> requiredForEvent("origChkSclReturnNumber", "detail.sample-log-tally.original-scaler-return", m,
              "ORIGINAL_RETURN"),
          () -> integer("origChkSclReturnNumber", "detail.sample-log-tally.original-scaler-return"));
      originalSite("detail.sample-log-tally.original-red-tag-site-number", m);
      field("origChkSclLicenseNumber",
          () -> requiredForEvent("origChkSclLicenseNumber", "detail.sample-log-tally.original-scaler", m,
              "ORIGINAL_SCALER"),
          () -> scalerLicenceRef("origChkSclLicenseNumber"));
      field("originalDdn", () -> detailDocumentRef("originalDdn"));
      field("sampleWeight", () -> required("sampleWeight", "detail.sample-log-tally.sample-weight"),
          () -> integer("sampleWeight", "detail.sample-log-tally.sample-weight"));
      if (adding || hasAny("populationNumber", "stratumNumber", "samplingYear")) {
        chain(this::samplePsyRequired, this::samplePsyRef);
      }
      field("scaleEventTypeCode", () -> required("scaleEventTypeCode", "detail.version.scale-event-type-code"));
      field("checkReplacesOriginalInd", this::replacementIndicator);
    }

    /**
     * {@code HBSValidator.validateReplacementIndicator} (P650Form
     * {@code sampleLogTally.replacementInd}): un-ticking "replaces original" on a
     * saved sample tally is refused when the previous version replaced the
     * original, unless the document is still "original in progress" or that
     * previous version is Ready or In Error. Nothing to check on Add (no DDN yet)
     * or without a previous version.
     */
    private boolean replacementIndicator() {
      if (adding || yes(f.get("checkReplacesOriginalInd")) || context.previousReplacesOriginal() == null) {
        return true;
      }
      if (!context.previousReplacesOriginal()) {
        return true;
      }
      String previousState = context.previousVersionState();
      boolean allowed = "OIP".equals(context.documentState())
          || "RDY".equals(previousState) || "ERR".equals(previousState);
      if (!allowed) {
        add("checkReplacesOriginalInd", FormMessages.text("errors.replacementInd.un-checked"));
      }
      return allowed;
    }

    /** {@code validation.xml} P700Form (arrival and departure ledgers; one page for both). */
    void p700() {
      String m = "LEDGER";
      boolean arrival = type == DocType.ARRIVAL_LEDGER;
      field("scaleSiteIdNmbr", () -> requiredForEvent("scaleSiteIdNmbr", "detail.ledger.site", m, "SITE"),
          () -> scaleSiteRef("scaleSiteIdNmbr", "errors.scale-site-ref"),
          () -> scaleSiteAndUser("scaleSiteIdNmbr", "licenseNumber"));
      field("licenseNumber",
          () -> requiredForEvent("licenseNumber", "detail.ledger.primary-scaler", m, "PRIMARY_SCALER"),
          () -> scalerLicenceRef("licenseNumber"),
          () -> licenceAndUser("licenseNumber", "scaleSiteIdNmbr"));
      eventRequired("returnNumber", "detail.ledger.return-number", m, "RETURN_NUMBER");
      field("arrivalDate", () -> hbsDate("arrivalDate", "detail.ledger.arrival-date"),
          () -> requiredForEvent("arrivalDate", "detail.ledger.arrival-date", m, "ARRIVAL_DATE"),
          () -> arrival ? arrivalDateLimit() : true);
      field("scaleDate", () -> hbsDate("scaleDate", "detail.ledger.scale-date"),
          () -> requiredForEvent("scaleDate", "detail.ledger.scale-date", m, "SCALE_DATE"));
      field("departureDate", () -> hbsDate("departureDate", "detail.ledger.departure-date"),
          () -> requiredForEvent("departureDate", "detail.ledger.departure-date", m, "DEPARTURE_DATE"),
          () -> arrival || yearLimit("departureDate", "errors.limit.departuredate"));
      eventRequired("loadArrivalNumber", "detail.ledger.load-arrival-number", m, "LOAD_ARRIVAL");
      eventRequired("loadDepartureNumber", "detail.ledger.load-departure-number", m, "LOAD_DEPARTURE");
      eventRequired("incomingLdsNumber", "detail.ledger.lds-no-in", m, "LDS_IN");
      field("timberMark", () -> requiredForEvent("timberMark", "detail.ledger.timber-mark", m, "TIMBER_MARK"),
          () -> timberMarkRef("timberMark"));
      eventRequired("outgoingLdsNumber", "detail.ledger.lds-no-out", m, "LDS_OUT");
      eventRequired("transportIdentifier", "detail.ledger.transport-in", m, "TRANSPORT_ID_IN");
      eventRequired("cutBlockId", "detail.ledger.cutblock", m, "CUT_BLOCK");
      eventRequired("outgoingTransportId", "detail.ledger.transport-out", m, "TRANSPORT_ID_OUT");
      eventRequired("timberBrand", "detail.ledger.timber-brand", m, "TIMBER_BRAND");
      field("destination", () -> requiredForEvent("destination", "detail.ledger.destination", m, "DESTINATION"),
          () -> scaleSiteRef("destination", "errors.destinationscalesite.invalid"));
      field("origScaleSite",
          () -> requiredForEvent("origScaleSite", "detail.ledger.orig-scale-site", m, "ORIGINAL_SITE_NUMBER"),
          () -> scaleSiteRef("origScaleSite", "errors.originalscalesite.invalid"));
    }

    /** {@code validation.xml} P752Form (SFP tally header; the lines are in {@link FormLineChecks}). */
    void p752() {
      String m = "SFP_TALLY";
      field("scaleEventTypeCode", () -> required("scaleEventTypeCode", "detail.version.scale-event-type-code"));
      field("parcelCount", () -> integer("parcelCount", "detail.log-tally.parcels"));
      field("parcelIdentifier", () -> integer("parcelIdentifier", "detail.log-tally.parcel-id"));
      field("returnNumber", () -> required("returnNumber", "detail.log-tally.return"),
          () -> integer("returnNumber", "detail.log-tally.return"));
      field("timberMark", () -> requiredForEvent("timberMark", "detail.log-tally.timber-mark", m, "TIMBER_MARK"),
          () -> timberMarkRef("timberMark"));
      // valueNonZeroBasedOnEventType looks up the property name "logCount", which
      // SPSFields does not know, so (legacy quirk) it never fires for an SFP tally.
      field("logCount", () -> requiredForEvent("logCount", "detail.log-tally.logs", m, "LOG_COUNT"),
          () -> integer("logCount", "detail.log-tally.logs"));
      scaleDate("detail.log-tally.scale-date");
      scaleSite("detail.log-tally.site", true);
      field("fieldScaleInd", () -> fsd01("fieldScaleInd", "fieldScaleDeckId"));
      primaryScaler("primaryLicenseNumber", "detail.log-tally.scaler", true);
      field("secondaryLicenseNumber", () -> scalerLicenceRef("secondaryLicenseNumber"));
      field("signingLicenseNumber", () -> scalerLicenceRef("signingLicenseNumber"));
      eventRequired("incomingLdsNumber", "detail.log-tally.load-description-slip.number", m, "LDS_NO");
      eventRequired("cutBlockId", "detail.log-tally.cut-block", m, "CUT_BLOCK");
      eventRequired("transportIdentifier", "detail.log-tally.transport-id", m, "TRANSPORT_ID");
      eventRequired("timberBrand", "detail.log-tally.timber-brand", m, "TIMBER_BRAND");
      eventRequired("loadArrivalNumber", "detail.log-tally.load-arrival", m, "LOAD_ARRIVAL");
      eventRequired("origChkSclLicenseNumber", "detail.log-tally.original-scaler", m, "ORIGINAL_SCALER");
      eventRequired("origChkSclReturnNumber", "detail.log-tally.original-scaler-return", m, "ORIGINAL_RETURN");
      // samplePieceCount [requiredBasedOnEventType, lessThan]: no fieldCode, so the
      // event rule never applies; lessThan depends on "required,float", which run
      // (and report) first; lessThan itself reads a minValue the form never set.
      field("samplePieceCount", () -> required("samplePieceCount", "detail.version.prorated-piece-count"),
          () -> isFloat("samplePieceCount", "detail.version.prorated-piece-count"));
      // the form always posted its default "N"; only an explicit blank fails
      if (f.containsKey("volumeCalcMethodType")) {
        field("volumeCalcMethodType", () -> required("volumeCalcMethodType", "detail.sfp-tally.scale-type-code"));
      }
    }

    // ── shared field chains ──────────────────────────────────────────

    /** {@code scaleDate [dateRequired, hbsDate, limitingDate]}. */
    private void scaleDate(String label) {
      field("scaleDate", () -> required("scaleDate", label), () -> hbsDate("scaleDate", label),
          () -> yearLimit("scaleDate", "errors.limit.scaledate"));
    }

    /** {@code scaleSite [required, (hbsDate), scaleSiteNoCrossRef, scaleSiteAndUser, scaleSiteProfile]}. */
    private void scaleSite(String label, boolean withOriginal) {
      field("scaleSiteIdNmbr", () -> required("scaleSiteIdNmbr", label),
          () -> scaleSiteRef("scaleSiteIdNmbr", "errors.scale-site-ref"),
          () -> withOriginal
              ? scaleSiteAndUser("scaleSiteIdNmbr", "primaryLicenseNumber", "secondaryLicenseNumber",
                  "signingLicenseNumber", "origChkSclLicenseNumber")
              : scaleSiteAndUser("scaleSiteIdNmbr", "primaryLicenseNumber", "secondaryLicenseNumber",
                  "signingLicenseNumber"),
          () -> scaleSiteProfile("scaleSiteIdNmbr"));
    }

    /** {@code primaryLicenseNumber [required, scalerLicenseCrossRef, licenceAndUser]}. */
    private void primaryScaler(String key, String label, boolean withOriginal) {
      field(key, () -> required(key, label), () -> scalerLicenceRef(key),
          () -> withOriginal
              ? licenceAndUser(key, "scaleSiteIdNmbr", "secondaryLicenseNumber", "signingLicenseNumber",
                  "origChkSclLicenseNumber")
              : licenceAndUser(key, "scaleSiteIdNmbr", "secondaryLicenseNumber", "signingLicenseNumber"));
    }

    /** {@code originalRedTagSiteNumber [requiredBasedOnEventType, originalSiteNoCrossRef, scaleAndOriginalSiteMatch]}. */
    private void originalSite(String label, String matrix) {
      String key = "origRedtagScaleSiteIdNmbr";
      field(key, () -> requiredForEvent(key, label, matrix, "ORIGINAL_SITE_NUMBER"),
          () -> scaleSiteRef(key, "errors.originalscalesite.invalid"),
          () -> scaleAndOriginalSiteMatch(key, "scaleSiteIdNmbr"));
    }

    private void eventRequired(String key, String label, String matrix, String fieldCode) {
      field(key, () -> requiredForEvent(key, label, matrix, fieldCode));
    }

    // ── the validators ───────────────────────────────────────────────

    /** {@code FieldChecks.validateRequired} / {@code HBSValidator.validateDateRequired}: {@code errors.required}. */
    boolean required(String key, String label) {
      if (blank(str(f.get(key)))) {
        add(key, FormMessages.text("errors.required", FormMessages.label(label)));
        return false;
      }
      return true;
    }

    /** {@code FieldChecks.validateInteger} ({@code GenericTypeValidator.formatInt}): {@code errors.integer}. */
    boolean integer(String key, String label) {
      String v = str(f.get(key));
      if (!blank(v) && !isInt(v)) {
        add(key, FormMessages.text("errors.integer", FormMessages.label(label)));
        return false;
      }
      return true;
    }

    /** {@code FieldChecks.validateFloat} ({@code GenericTypeValidator.formatFloat}): {@code errors.float}. */
    boolean isFloat(String key, String label) {
      String v = str(f.get(key));
      if (!blank(v)) {
        try {
          Float.parseFloat(v);
        } catch (NumberFormatException e) {
          add(key, FormMessages.text("errors.float", FormMessages.label(label)));
          return false;
        }
      }
      return true;
    }

    /**
     * {@code HBSValidator.validateRequiredBasedOnEventType}: required when the
     * document type's field matrix shows the field for the event type
     * ({@code HBSValidatorHelper.isFieldRequiredForEventType}). An unknown
     * event type (not sent on Save) makes nothing required.
     */
    boolean requiredForEvent(String key, String label, String matrix, String fieldCode) {
      if (isRequiredForEvent(matrix, fieldCode) && blank(str(f.get(key)))) {
        add(key, FormMessages.text("errors.required", FormMessages.label(label)));
        return false;
      }
      return true;
    }

    private boolean isRequiredForEvent(String matrix, String fieldCode) {
      return event != null && FieldAvailability.isAvailable(matrix, event, fieldCode);
    }

    /**
     * {@code HBSValidator.validateValueNotZeroBasedOnEventType}: a value the event
     * requires must be &gt; 0 (a non-number counts as -1, {@code getPropertyAsLong}).
     */
    boolean valueNotZeroForEvent(String key, String label, String matrix, String fieldCode) {
      String v = str(f.get(key));
      if (isRequiredForEvent(matrix, fieldCode) && !emptyKeep(v)) {
        long value;
        try {
          value = Long.parseLong(v);
        } catch (NumberFormatException e) {
          value = -1;
        }
        if (value <= 0) {
          add(key, FormMessages.text("error.value-is.zero", FormMessages.label(label)));
          return false;
        }
      }
      return true;
    }

    /** {@code HBSValidator.validateGreaterThan}: {@code error.value-is.greater-than}. */
    boolean greaterThan(String key, String label, float max, String maxText) {
      String v = str(f.get(key));
      try {
        if (!emptyKeep(v) && max < Float.parseFloat(v)) {
          add(key, FormMessages.text("error.value-is.greater-than", FormMessages.label(label), maxText));
          return false;
        }
      } catch (NumberFormatException e) {
        // legacy: ignored
      }
      return true;
    }

    /** {@code HBSValidator.validateLessThan}: {@code error.value-is.less-than}. */
    boolean lessThan(String key, String label, float min, String minText) {
      String v = str(f.get(key));
      try {
        if (!emptyKeep(v) && min > Float.parseFloat(v)) {
          add(key, FormMessages.text("error.value-is.less-than", FormMessages.label(label), null, minText));
          return false;
        }
      } catch (NumberFormatException e) {
        // legacy: ignored
      }
      return true;
    }

    /** {@code HBSValidator.validateHbsDate}: a present date must be a real calendar date. */
    boolean hbsDate(String key, String label) {
      String v = str(f.get(key));
      if (!blank(v) && parseDate(v) == null) {
        add(key, FormMessages.text("errors.date", FormMessages.label(label)));
        return false;
      }
      return true;
    }

    /**
     * {@code HBSValidator.validateScaleDateLimit} ({@code DacValidator.compareForYearLimit}):
     * the date is before 1 January seven years back.
     */
    boolean yearLimit(String key, String messageKey) {
      LocalDate d = parseDate(str(f.get(key)));
      if (d != null && d.isBefore(LocalDate.of(lookups.today().getYear() - 7, 1, 1))) {
        add(key, FormMessages.text(messageKey));
        return false;
      }
      return true;
    }

    /**
     * {@code validateScaleDateLimit} for an arrival ledger's arrival date: the
     * arrival date limit, and (legacy) the scale date limit reported on scaleDate.
     */
    boolean arrivalDateLimit() {
      boolean valid = true;
      if (has("scaleDate")) {
        LocalDate scale = parseDate(str(f.get("scaleDate")));
        if (scale != null && scale.isBefore(LocalDate.of(lookups.today().getYear() - 7, 1, 1))) {
          valid = false;
          add("scaleDate", FormMessages.text("errors.limit.scaledate"));
        }
      }
      return yearLimit("arrivalDate", "errors.limit.arrivaldate") && valid;
    }

    /** {@code HBSValidator.validateTimbermarkCrossRef}: {@code errors.timbermark-ref}. */
    boolean timberMarkRef(String key) {
      String v = str(f.get(key));
      if (!emptyKeep(v) && !lookups.timberMarkExists(v.toUpperCase(Locale.ROOT))) {
        add(key, FormMessages.text("errors.timbermark-ref"));
        return false;
      }
      return true;
    }

    /** {@code HBSValidator.validateScaleSiteNoCrossRef} (scale, originating or destination site). */
    boolean scaleSiteRef(String key, String messageKey) {
      String v = str(f.get(key));
      if (!emptyKeep(v) && !lookups.scaleSiteExists(v.toUpperCase(Locale.ROOT))) {
        add(key, FormMessages.text(messageKey));
        return false;
      }
      return true;
    }

    /** {@code HBSValidator.validateScalerLicenseCrossRef}: {@code errors.scaler-licence-ref}. */
    boolean scalerLicenceRef(String key) {
      String v = str(f.get(key));
      if (!emptyKeep(v) && !lookups.scalerLicenceExists(v.toUpperCase(Locale.ROOT))) {
        add(key, FormMessages.text("errors.scaler-licence-ref"));
        return false;
      }
      return true;
    }

    /** {@code HBSValidator.validateFSD01}: a field-scaled return needs its deck id. */
    boolean fsd01(String key, String deckKey) {
      if (!has(deckKey)) {
        return true;
      }
      if (yes(f.get(key)) && emptyKeep(str(f.get(deckKey)))) {
        add(key, FormMessages.text("errors.field-scale-deck-id.empty"));
        return false;
      }
      return true;
    }

    /** {@code HBSValidator.validateScaleAndOriginalSiteMatch}: {@code error.originalscalesite.match}. */
    boolean scaleAndOriginalSiteMatch(String key, String siteKey) {
      String orig = str(f.get(key));
      String site = str(f.get(siteKey));
      if (!emptyKeep(orig) && !emptyKeep(site) && orig.equalsIgnoreCase(site)) {
        add(key, FormMessages.text("error.originalscalesite.match"));
        return false;
      }
      return true;
    }

    /** {@code HBSValidator.validateDetailDocumentNumberCrossRef}: {@code errors.detailscaledocument-ref}. */
    boolean detailDocumentRef(String key) {
      String v = str(f.get(key));
      if (!emptyKeep(v) && !lookups.activeDocumentExists(v, type)) {
        add(key, FormMessages.text("errors.detailscaledocument-ref"));
        return false;
      }
      return true;
    }

    /**
     * {@code HBSValidator.validateScaleSiteProfile}: an unexpired detail
     * submitter profile for the site, document type and scale date
     * ({@code errors.scalesite-user-access}). Skipped when the scale date is not
     * a valid date (its {@code scaleDateReqdandValid} dependency).
     */
    boolean scaleSiteProfile(String siteKey) {
      if (!has("scaleDate")) {
        return true;
      }
      LocalDate scaleDate = parseDate(str(f.get("scaleDate")));
      String site = str(f.get(siteKey));
      if (scaleDate == null) {
        return false;
      }
      if (emptyKeep(site)
          || !lookups.hasDetailSiteProfile(site.toUpperCase(Locale.ROOT), type,
              Timestamp.valueOf(scaleDate.atStartOfDay()))) {
        add(siteKey, FormMessages.text("errors.scalesite-user-access"));
        return false;
      }
      return true;
    }

    /**
     * {@code HBSValidator.validateScaleSiteAndUser} (Add only): a site admin or
     * scale admin must have the site in their org unit, or be a scaler holding
     * one of the return's licences ({@code errors.scale-site-user-access}).
     */
    boolean scaleSiteAndUser(String siteKey, String... licenceKeys) {
      if (!adding) {
        return true;
      }
      Caller c = caller();
      if (c.has(HbsRoles.CLI_SITE_ADMIN) || c.has(HbsRoles.SCALE_ADMIN)) {
        String site = upper(str(f.get(siteKey)));
        boolean valid = lookups.isScaleSiteAssociatedWithOrgUnit(site, c)
            || ((c.has(HbsRoles.CLI_SCALER) || c.has(HbsRoles.MOF_SCALER))
                && lookups.hasScalerAuthorization(c, values(licenceKeys)));
        if (!valid) {
          add(siteKey, FormMessages.text("errors.scale-site-user-access"));
          return false;
        }
      }
      return true;
    }

    /**
     * {@code HBSValidator.validateLicenceAndUser} (Add only): a scaler must hold
     * one of the return's licences, or be a site/scale admin with authority on
     * the site ({@code errors.scalar-licence-user-access}).
     *
     * @param otherKeys the site key, then the other licence keys
     */
    boolean licenceAndUser(String key, String siteKey, String... otherKeys) {
      if (!adding) {
        return true;
      }
      String primary = str(f.get(key));
      if (emptyKeep(primary)) {
        return true;
      }
      Caller c = caller();
      if (c.has(HbsRoles.CLI_SCALER) || c.has(HbsRoles.MOF_SCALER)) {
        String[] licences = new String[otherKeys.length + 1];
        licences[0] = primary;
        System.arraycopy(values(otherKeys), 0, licences, 1, otherKeys.length);
        boolean valid = lookups.hasScalerAuthorization(c, licences)
            || ((c.has(HbsRoles.CLI_SITE_ADMIN) || c.has(HbsRoles.SCALE_ADMIN))
                && lookups.hasScaleSiteAuthorization(upper(str(f.get(siteKey))), c));
        if (!valid) {
          add(key, FormMessages.text("errors.scalar-licence-user-access"));
          return false;
        }
      }
      return true;
    }

    /**
     * {@code HBSValidator.validatePSYorCSRequiredBasedOnEventType} (weigh slip):
     * the PSY or company stratum the event shows (WS_COMPANY_STRATUM) must be
     * entered; the message follows the page's label (billable event and mark →
     * "Pop/Strat/Year", else "Company Stratum").
     */
    boolean weighSlipPsyRequired() {
      if (!isRequiredForEvent("WEIGH_SLIP", "COMPANY_STRATUM") || !blank(weighSlipPsy())) {
        return true;
      }
      String mark = str(f.get("timberMark"));
      boolean billable = BILLABLE_WEIGH_SLIP_EVENTS.contains(event) && !blank(mark) && lookups.isBillableMark(mark);
      add("populationNumber", billable
          ? FormMessages.text("errors.pop-strat-year-req")
          : FormMessages.text("errors.required", FormMessages.COMPANY_STRATUM_LABEL));
      return false;
    }

    /**
     * {@code HBSValidator.validateBillableAndSetPSYorCS}: a billable event with a
     * billable mark needs an 8/10-character PSY with a numeric year whose stratum
     * is on file; a billable PSY is refused for a non-billable mark or event.
     * (The legacy copy of the PSY into population/stratum/year or the company
     * stratum is done by the weigh slip edits.)
     */
    boolean billableAndSetPsyOrCs() {
      if (event == null || !has("timberMark")) {
        return true;
      }
      String timberMark = str(f.get("timberMark"));
      PopStratYear parser = PopStratYear.parse(weighSlipPsy());
      boolean isBillableMark = !blank(timberMark) && lookups.isBillableMark(timberMark.toUpperCase(Locale.ROOT));
      boolean isValidPsy = false;
      boolean invalidYear = false;
      try {
        // the year is only read for a valid PSY (legacy short-circuit)
        if (parser.isValid() && lookups.stratumExists(parser.population(), parser.stratum(), parser.year())) {
          isValidPsy = true;
        }
      } catch (PopStratYear.InvalidYearException e) {
        invalidYear = true;
      }
      if (BILLABLE_WEIGH_SLIP_EVENTS.contains(event)) {
        if (isBillableMark) {
          if (!parser.isBillableLength()) {
            add("populationNumber", FormMessages.text("errors.pop-strat-year-length"));
          } else if (invalidYear) {
            add("populationNumber", FormMessages.text("errors.pop-strat-year-notnumeric"));
          } else if (!isValidPsy) {
            add("populationNumber", FormMessages.text("errors.billable-event-mark-requires-stratum"));
          }
        } else if (isValidPsy && !emptyKeep(timberMark)) {
          add("populationNumber", FormMessages.text("errors.nonbillable-mark-requires-company-stratum"));
        }
      } else if (isValidPsy) {
        add("populationNumber", FormMessages.text("errors.nonbillable-event-requires-company-stratum"));
      }
      return true; // legacy always returned true
    }

    /** The weigh slip's single PSY entry: the company stratum, else population + stratum + year. */
    private String weighSlipPsy() {
      String cs = str(f.get("companyUseStratum"));
      if (!blank(cs)) {
        return cs;
      }
      return nz(str(f.get("populationNumber"))) + nz(str(f.get("stratumNumber")))
          + nz(str(f.get("samplingYear")));
    }

    /** {@code HBSValidator.validatePopStratYearRequired} (sample tally, SS_POP_STRAT_YEAR). */
    boolean samplePsyRequired() {
      if (isRequiredForEvent("SAMPLE_LOG_TALLY", "POP_STRAT_YEAR") && blank(samplePsy())) {
        add("populationNumber", FormMessages.text("errors.pop-strat-year-req"));
        return false;
      }
      return true;
    }

    /**
     * {@code HBSValidator.validatePopStratYearCrossRef} (P650Form): the
     * combination must be entered and on file ({@code errors.pop-strat-year-ref}).
     */
    boolean samplePsyRef() {
      String pop = nz(str(f.get("populationNumber")));
      String strat = nz(str(f.get("stratumNumber")));
      String sYear = nz(str(f.get("samplingYear")));
      boolean valid = !emptyKeep(pop + strat + sYear);
      if (!emptyKeep(pop) || !emptyKeep(strat) || !emptyKeep(sYear)) {
        Long year = null;
        try {
          if (!emptyKeep(sYear)) {
            year = Long.valueOf(sYear);
          }
        } catch (NumberFormatException e) {
          valid = false;
        }
        if (!lookups.stratumExists(emptyKeep(pop) ? null : pop.toUpperCase(Locale.ROOT),
            emptyKeep(strat) ? null : strat.toUpperCase(Locale.ROOT), year)) {
          valid = false;
        }
      }
      if (!valid) {
        add("populationNumber", FormMessages.text("errors.pop-strat-year-ref"));
      }
      return valid;
    }

    private String samplePsy() {
      return nz(str(f.get("populationNumber"))) + nz(str(f.get("stratumNumber")))
          + nz(str(f.get("samplingYear")));
    }

    // ── plumbing ─────────────────────────────────────────────────────

    /** Runs a field's validator chain (Commons Validator: stop at the first failure). */
    private void field(String key, BooleanSupplier... steps) {
      if (!has(key)) {
        return;
      }
      chain(steps);
    }

    private void chain(BooleanSupplier... steps) {
      for (BooleanSupplier step : steps) {
        if (!step.getAsBoolean()) {
          return;
        }
      }
    }

    boolean has(String key) {
      return adding || f.containsKey(key);
    }

    private boolean hasAny(String... keys) {
      for (String k : keys) {
        if (f.containsKey(k)) return true;
      }
      return false;
    }

    private Caller caller() {
      if (caller == null) {
        caller = lookups.caller();
      }
      return caller;
    }

    private String[] values(String... keys) {
      String[] out = new String[keys.length];
      for (int i = 0; i < keys.length; i++) {
        out[i] = upper(str(f.get(keys[i])));
      }
      return out;
    }

    void add(String field, String message) {
      problems.add(new Problem(field, message));
    }
  }

  // ── static helpers (shared with FormLineChecks) ───────────────────

  /** A payload value as the posted string (numbers in plain notation). */
  static String str(Object v) {
    if (v == null) return null;
    if (v instanceof BigDecimal b) return b.toPlainString();
    return v.toString();
  }

  /** {@code GenericValidator.isBlankOrNull} / {@code StringUtil.empty(s, false)}: null or whitespace. */
  static boolean blank(String s) {
    return s == null || s.trim().isEmpty();
  }

  /** {@code StringUtil.empty(s)} (keepWhitespace): null or "" only. */
  static boolean emptyKeep(String s) {
    return s == null || s.isEmpty();
  }

  /** {@code GenericValidator.isInt}. */
  static boolean isInt(String s) {
    try {
      Integer.parseInt(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** A Y/N indicator, Boolean or legacy "true"/"on" check box. */
  static boolean yes(Object v) {
    if (v instanceof Boolean b) return b;
    String s = str(v);
    return s != null && ("Y".equalsIgnoreCase(s.trim()) || "true".equalsIgnoreCase(s.trim())
        || "on".equalsIgnoreCase(s.trim()));
  }

  /** The payload's ISO date ({@code yyyy-MM-dd}, or with a time for weigh slips); null when not a date. */
  static LocalDate parseDate(String s) {
    if (blank(s)) return null;
    String t = s.trim();
    try {
      if (t.length() > 10) {
        return LocalDateTime.parse(t.length() == 16 ? t + ":00" : t.replace(' ', 'T')).toLocalDate();
      }
      return LocalDate.parse(t);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static String upper(String s) {
    return s == null ? null : s.toUpperCase(Locale.ROOT);
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
