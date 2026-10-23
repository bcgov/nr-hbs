package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import java.util.Map;

/**
 * The legacy message texts and field labels the detail entry forms used, copied
 * from {@code ear/hbs-view/src/main/resources/ca/bc/gov/mof/hbs/presentation/ApplicationResources.properties}
 * (keys kept), with Struts' {@code {0}}/{@code {1}}/{@code {2}} substitution.
 */
final class FormMessages {

  static final Map<String, String> TEXT = Map.ofEntries(
      // validator-rules.xml default messages
      Map.entry("errors.required", "{0} is required."),
      Map.entry("errors.integer", "{0} must be numeric."),
      Map.entry("errors.float", "{0} must be a number."),
      Map.entry("errors.date", "{0} is not a valid date."),
      Map.entry("errors.timbermark-ref", "Timber mark must be in Timber Mark table and in Mark Billed Client table."),
      Map.entry("errors.scale-site-ref", "Scale Site does not exist."),
      Map.entry("errors.scaler-licence-ref", "Scaler Licence does not exist."),
      Map.entry("errors.field-scale-deck-id.empty", "Field Scale Deck Id cannot be null"),
      Map.entry("errors.pop-strat-year-ref", "Population / Stratum / Year combination is invalid."),
      Map.entry("errors.pop-strat-year-req", "Population / Stratum / Year combination is required."),
      Map.entry("errors.scale-site-user-access", "User not authorized to add a scale return for this scale site."),
      Map.entry("errors.scalar-licence-user-access",
          "User not authorized to add a scale return for this scaler licence."),
      Map.entry("error.value-is.zero", "{0} should not = 0."),
      Map.entry("errors.scalesite-user-access",
          "No scale site profile exists to permit submission of detail scale returns for this site."),
      Map.entry("error.value-is.greater-than", "{0} cannot be greater than {1}."),
      Map.entry("error.value-is.less-than", "{0} cannot be less than {2}."),
      Map.entry("errors.does-not-exist", "{0} does not exist."),
      Map.entry("errors.speciesgrade.invalid", "Species and grade combination is invalid."),
      Map.entry("errors.not-in-range", "{0} is not in the specified range."),
      Map.entry("errors.integer2", "{0} must be an integer."),
      Map.entry("error.originalscalesite.match", "Originating scale site cannot be the same as the Scale Site."),
      Map.entry("errors.originalscalesite.invalid", "Originating scale site not in the scale site table."),
      Map.entry("errors.destinationscalesite.invalid", "Destination scale site not in the scale site table.  "),
      Map.entry("errors.detailscaledocument-ref", "There is no active tally with the specified Original DDN."),
      Map.entry("errors.replacementInd.un-checked", "Replacement indicator cannot be unchecked."),
      // HBSValidator / form / action messages
      Map.entry("errors.limit.scaledate", "Year of scale date is more than seven years prior to current year"),
      Map.entry("errors.limit.arrivaldate", "Year of arrival date is more than seven years prior to current year"),
      Map.entry("errors.limit.departuredate",
          "Year of departure date is more than seven years prior to current year"),
      Map.entry("errors.billable-event-mark-requires-stratum",
          "A weigh slip with a billable event type and a billable mark requires a billable stratum."),
      Map.entry("errors.pop-strat-year-length", "The PSY must contain 8 or 10 characters."),
      Map.entry("errors.pop-strat-year-notnumeric", "The sampling year must be numeric."),
      Map.entry("errors.nonbillable-mark-requires-company-stratum",
          " A weigh slip with a billable event type and a non-billable mark requires a company-use stratum"),
      Map.entry("errors.nonbillable-event-requires-company-stratum",
          "A weigh slip with a non-billable event type requires a company-use stratum"),
      Map.entry("error.maxlength.invalid", "Log length must be less than 100 metres"),
      Map.entry("error.lengthformat.invalid", "Length must be an unsigned valid number"),
      Map.entry("error.integerformat.invalid", "{0} must be an unsigned integer"),
      Map.entry("error.length.decimalinvalid", "Length can have no more than one digit to the right of the decimal point"),
      Map.entry("error.netvolume.greaterinvalid", "Net volume must be less than 1000 m3"),
      Map.entry("error.netvolume.lessinvalid", "Net volume cannot be less than zero"),
      Map.entry("error.defectnumber.required", "Defect Number must be a valid number."),
      Map.entry("error.defectnumber.unique", "Defect Number must be unique."),
      Map.entry("error.volume.required", "Volume must be a valid number."),
      Map.entry("error.defectvolume.invalid", "Defect Volume must be a valid number less than 1000.0."),
      Map.entry("error.defectvolume.decimal.invalid", "Defect Volume must have 3 or less decimal places."),
      Map.entry("error.defecttype.required", "A defect code is required."),
      Map.entry("error.description.required", "A defect description is required."),
      Map.entry("error.lognumber.required", "Log Number must be a valid number."),
      Map.entry("error.species.required", "A species is required."));

  /** Field labels ({@code arg0} keys of validation.xml). */
  static final Map<String, String> LABEL = Map.ofEntries(
      Map.entry("detail.version.scale-event-type-code", "Scenario"),
      Map.entry("detail.log-tally.parcels", "Parcel Count"),
      Map.entry("detail.log-tally.parcel-id", "Parcel ID"),
      Map.entry("detail.log-tally.return", "Return Number"),
      Map.entry("detail.log-tally.timber-mark", "Timber Mark"),
      Map.entry("detail.log-tally.logs", "Log Count"),
      Map.entry("detail.log-tally.scale-date", "Scale Date"),
      Map.entry("detail.log-tally.site", "Scale Site"),
      Map.entry("detail.log-tally.field-scale", "Field Scale"),
      Map.entry("detail.log-tally.scaler", "Primary Scaler"),
      Map.entry("detail.log-tally.load-description-slip.number", "LDS No"),
      Map.entry("detail.log-tally.cut-block", "Cut Block"),
      Map.entry("detail.log-tally.transport-id", "Transport ID"),
      Map.entry("detail.log-tally.timber-brand", "Timber Brand"),
      Map.entry("detail.log-tally.load-arrival", "Load Arrival"),
      Map.entry("detail.log-tally.original-scaler", "Original Scaler"),
      Map.entry("detail.log-tally.original-scaler-return", "Original Scale Return"),
      Map.entry("detail.log-tally.original-ddn", "Original DDN"),
      Map.entry("detail.log-tally.original-scale-site", "Originating Scale Site"),
      Map.entry("detail.weigh-slip.site", "Scale Site"),
      Map.entry("grappleWeight", "Wt less Subsample"),
      Map.entry("detail.weigh-slip.primary-scaler", "Primary Scaler"),
      Map.entry("detail.weigh-slip.timber-mark", "Timber Mark"),
      Map.entry("detail.weigh-slip.lds.number.incoming", "LDS In"),
      Map.entry("detail.weigh-slip.timber-brand", "Timber Brand"),
      Map.entry("detail.weigh-slip.destination", "Destination"),
      Map.entry("detail.weigh-slip.lds.number.outgoing", "LDS Out"),
      Map.entry("detail.weigh-slip.cut-block", "Cut Block"),
      Map.entry("detail.weigh-slip.scale-date", "Scale Date"),
      Map.entry("detail.weigh-slip.field-scale", "Field Scale"),
      Map.entry("detail.weigh-slip.gross-weight", "Gross Weight"),
      Map.entry("detail.weigh-slip.tare-weight", "Tare Weight"),
      Map.entry("detail.weigh-slip.transport-id", "Transport ID"),
      Map.entry("detail.sample-log-tally.return", "Return Number"),
      Map.entry("detail.sample-log-tally.logs", "Log Count"),
      Map.entry("detail.sample-log-tally.scale-date", "Scale Date"),
      Map.entry("detail.sample-log-tally.timber-mark", "Timber Mark"),
      Map.entry("detail.sample-log-tally.site", "Scale Site"),
      Map.entry("detail.sample-log-tally.primary-scaler", "Primary Scaler"),
      Map.entry("detail.sample-log-tally.lds.number.incoming", "LDS In"),
      Map.entry("detail.sample-log-tally.transport-id", "Transport ID"),
      Map.entry("detail.sample-log-tally.load-arrival", "Load Arrival"),
      Map.entry("detail.sample-log-tally.original-scaler-return", "Original Scaler Return"),
      Map.entry("detail.sample-log-tally.original-red-tag-site-number", "Originating Scale Site"),
      Map.entry("detail.sample-log-tally.original-scaler", "Original Scaler"),
      Map.entry("detail.sample-log-tally.sample-weight", "Sample Weight"),
      Map.entry("detail.ledger.site", "Scale Site"),
      Map.entry("detail.ledger.primary-scaler", "Primary Scaler"),
      Map.entry("detail.ledger.return-number", "Return Number"),
      Map.entry("detail.ledger.arrival-date", "Arrival Date"),
      Map.entry("detail.ledger.scale-date", "Scale Date"),
      Map.entry("detail.ledger.departure-date", "Departure Date"),
      Map.entry("detail.ledger.load-arrival-number", "Load Arrival Number"),
      Map.entry("detail.ledger.load-departure-number", "Load Departure Number"),
      Map.entry("detail.ledger.lds-no-in", "LDS In"),
      Map.entry("detail.ledger.timber-mark", "Timber Mark"),
      Map.entry("detail.ledger.lds-no-out", "LDS Out"),
      Map.entry("detail.ledger.transport-in", "Transport In"),
      Map.entry("detail.ledger.cutblock", "Cutblock"),
      Map.entry("detail.ledger.transport-out", "Transport Out"),
      Map.entry("detail.ledger.timber-brand", "Timber Brand"),
      Map.entry("detail.ledger.destination", "Destination"),
      Map.entry("detail.ledger.orig-scale-site", "Originating Scale Site"),
      Map.entry("detail.version.prorated-piece-count", "Prorated Piece Count"),
      Map.entry("detail.sfp-tally.scale-type-code", "Scale method"),
      Map.entry("detail.version.log.length", "Length"),
      Map.entry("detail.version.log.log-number", "Log Number"),
      Map.entry("detail.version.log.species", "Species"),
      Map.entry("detail.version.log.top", "Top"),
      Map.entry("detail.version.log.butt", "Butt"),
      Map.entry("detail.version.log.stack-length", "Stack Length"),
      Map.entry("detail.version.log.stack-height", "Stack Height"),
      Map.entry("detail.version.log.net-volume", "Net Volume"),
      Map.entry("detail.version.log.piece-count", "Piece Count"),
      Map.entry("detail.version.log.conversion-factor", "Conversion Factor"));

  /** {@code P600Form.POP_STRAT_YEAR_LABEL} / {@code COMPANY_STRATUM_LABEL}. */
  static final String POP_STRAT_YEAR_LABEL = "Pop/Strat/Year";
  static final String COMPANY_STRATUM_LABEL = "Company Stratum";

  /** {@code DacConstants.ScaleReturnAudit.LOG_TOP_FIELD_NAME} / {@code LOG_BUTT_FIELD_NAME}. */
  static final String LOG_TOP_FIELD_NAME = "Top";
  static final String LOG_BUTT_FIELD_NAME = "Butt";

  private FormMessages() {
  }

  /** A label key's text; an unknown key renders like Struts' missing resource ({@code ???key???}). */
  static String label(String key) {
    String text = LABEL.get(key);
    return text != null ? text : "???" + key + "???";
  }

  /** The message with {@code {i}} replaced by the given (already resolved) arguments. */
  static String text(String key, String... args) {
    String text = TEXT.get(key);
    if (text == null) {
      throw new IllegalArgumentException("No legacy message " + key);
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i] != null) {
        text = text.replace("{" + i + "}", args[i]);
      }
    }
    return text;
  }
}
