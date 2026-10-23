package ca.bc.gov.nrs.hbs.api.detailreturns.validation.form;

import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator.blank;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator.emptyKeep;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator.isInt;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator.str;
import static ca.bc.gov.nrs.hbs.api.detailreturns.validation.form.DetailFormValidator.yes;

import ca.bc.gov.nrs.hbs.api.detailreturns.LogVolume;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The log-line and defect checks of the detail entry forms (part of
 * {@link DetailFormValidator}):
 * <ul>
 *   <li>log tally lines: P552Form's row validators ({@code length_},
 *       {@code logNumber_}, {@code timbermark_}, {@code species_},
 *       {@code spcProdGrd_}, {@code top_}, {@code butt_}) and the
 *       {@code P552Form.validate} net volume range;</li>
 *   <li>sample tally lines: {@code P658Action.validateLog} / {@code validateNetVolume};</li>
 *   <li>log and sample defects: {@code P559Action} / {@code P659Action.validateDefect};</li>
 *   <li>SFP lines: P752Form's row validators.</li>
 * </ul>
 * A row validator applies to every non-empty line (legacy {@code skipIfRowEmpty})
 * and, like a Struts field, stops the field's chain for all lines once a line
 * fails. Values are the posted strings (legacy request parameters); the legacy
 * parsers' conversions are reproduced where the legacy check used the parsed value.
 */
final class FormLineChecks {

  /** {@code SpeciesProductGrade.Product.LOG} and {@code LogTallyCommon.GRADE_VALUE} (one space). */
  static final String DEFAULT_PRODUCT = " ";
  static final String DEFAULT_GRADE = " ";

  /** {@code LogDetailFields}: log tally line fields available for scale method G and N. */
  static final Set<String> LOG_DETAIL_FIELDS = Set.of("LOG_NUMBER", "BEACH_COMB_MARK", "NMV", "SPECIES", "GRADE",
      "LENGTH", "TOP", "BUTT", "NET_VOLUME", "DEFECT_COUNT");

  /** {@code SFPDetailFields}: SFP line fields per scale method (L, P, K, C). */
  static final Map<String, Set<String>> SFP_DETAIL_FIELDS = Map.of(
      "L", Set.of("PRODUCT", "LOG_NUMBER", "SPECIES", "LENGTH", "TOP", "BUTT"),
      "P", Set.of("PRORATED_PIECE_COUNT", "PRODUCT", "SPECIES", "GRADE", "LENGTH", "TOP", "BUTT", "LOG_NUMBER",
          "PRORATED_VOLUME"),
      "K", Set.of("PRODUCT", "SPECIES", "STACK_NUMBER", "STACK_LENGTH", "STACK_WIDTH", "STACK_HEIGHT",
          "CONVERSION_FACTOR", "SOLID_VOLUME"),
      "C", Set.of("PRODUCT", "SPECIES", "GRADE", "BUNDLE_NUMBER", "PIECE_COUNT"));

  /** The line columns behind the legacy row parameter names. */
  private record Keys(String logNumber, String length, String top, String butt, String inputMark,
                      String timberMark, List<String> rowParams) {
  }

  private static final Keys LOG_KEYS = new Keys("logNumber", "length", "top", "butt", "inputBcombTimberMark",
      "timberMark", List.of("logNumber", "inputBcombTimberMark", "timberMark", "nmvInd", "scaleSpeciesCode",
          "scaleProductCode", "scaleGradeCode", "length", "top", "butt"));

  private static final Keys SFP_KEYS = new Keys("sfpNumber", "prodLogLength", "prodLogTop", "prodLogButt", null,
      null, List.of("sfpNumber", "scaleSpeciesCode", "scaleProductCode", "scaleGradeCode", "prodLogLength",
          "prodLogTop", "prodLogButt", "pieceCount", "conversionFactor", "stackHeight", "stackLength", "stackWidth",
          "netVolume"));

  private final FormLookups lookups;
  private final DocType type;
  private final Map<String, Object> header;
  private final List<Map<String, Object>> logs;
  private final List<Problem> problems;

  FormLineChecks(FormLookups lookups, DocType type, Map<String, Object> header, List<Map<String, Object>> logs,
                 List<Problem> problems) {
    this.lookups = lookups;
    this.type = type;
    this.header = header;
    this.logs = logs;
    this.problems = problems;
  }

  void run() {
    if (logs.isEmpty()) {
      return;
    }
    switch (type) {
      case LOG_TALLY -> logTally();
      case SAMPLE_LOG_TALLY -> sampleTally();
      case SFP_TALLY -> sfpTally();
      default -> { }
    }
  }

  // ── log tally (P552Form / P558) ─────────────────────────────────────

  private void logTally() {
    String sm = str(header.get("volumeCalcMethodType"));
    Keys k = LOG_KEYS;
    // length_ [requestParamsRequiredBasedOnScaleMethod, logDetails]
    if (requiredRows(k, "length", logScaleMethod(sm, "LENGTH"))) {
      logDetails(k);
    }
    // logNumber_ [requestParamsRequiredBasedOnScaleMethod, requestInteger]
    if (requiredRows(k, "logNumber", logScaleMethod(sm, "LOG_NUMBER"))) {
      requestInteger(k, k.logNumber(), "detail.version.log.log-number");
    }
    // timbermark_ [requestTimbermarkCrossRef] msg error.log_bcombmark.invalid (MSGCODE:BM02,SCL)
    timberMarkRows(k);
    // species_ [requestParamsRequiredBasedOnScaleMethod]
    requiredRows(k, "species", logScaleMethod(sm, "SPECIES"));
    // spcProdGrd_ [requestSpeciesProductGrade]
    speciesProductGradeRows(k);
    requiredRows(k, "top", logScaleMethod(sm, "TOP"));
    requiredRows(k, "butt", logScaleMethod(sm, "BUTT"));
    // P552Form.validate: the net volume of each non-empty line without errors
    for (int i = 0; i < logs.size(); i++) {
      LogLine line = parsedLine(i, true);
      if (!helperRowEmpty(line, true) && !rowHasErrors(i)) {
        netVolumeRange(i, line);
      }
    }
    defects();
  }

  private static boolean logScaleMethod(String sm, String fieldCode) {
    return ("G".equals(sm) || "N".equals(sm)) && LOG_DETAIL_FIELDS.contains(fieldCode);
  }

  /**
   * {@code HBSValidator.validateLogDetails} (on {@code length_}): length below
   * 100 and unsigned with one decimal, top and butt unsigned integers.
   */
  private void logDetails(Keys k) {
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String length = val(i, k.length());
      if (!emptyKeep(length)) {
        try {
          float value = Float.parseFloat(length);
          if (value >= 100.0f) {
            add(i, k.length(), FormMessages.text("error.maxlength.invalid"));
          }
          if (value < 0.0f) {
            add(i, k.length(), FormMessages.text("error.lengthformat.invalid"));
          }
        } catch (NumberFormatException e) {
          add(i, k.length(), FormMessages.text("error.lengthformat.invalid"));
        }
        try {
          float value = Float.parseFloat(length);
          DecimalFormat format = new DecimalFormat("####.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
          if (value != Float.parseFloat(format.format(value))) {
            add(i, k.length(), FormMessages.text("error.length.decimalinvalid"));
          }
        } catch (NumberFormatException e) {
          // legacy: ignored
        }
      }
      unsignedInteger(i, k.top(), FormMessages.LOG_TOP_FIELD_NAME);
      unsignedInteger(i, k.butt(), FormMessages.LOG_BUTT_FIELD_NAME);
    }
  }

  /** {@code HBSValidator.validateIntegerFormat}: {@code error.integerformat.invalid}. */
  private void unsignedInteger(int i, String key, String fieldName) {
    String v = val(i, key);
    if (blank(v)) return;
    try {
      if (Integer.parseInt(v) < 0) {
        add(i, key, FormMessages.text("error.integerformat.invalid", fieldName));
      }
    } catch (NumberFormatException e) {
      add(i, key, FormMessages.text("error.integerformat.invalid", fieldName));
    }
  }

  /** {@code validateRequestTimbermarkCrossRef} with the BM02 edit message. */
  private void timberMarkRows(Keys k) {
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String mark = val(i, k.timberMark());
      if (!emptyKeep(mark) && !lookups.timberMarkExists(mark.toUpperCase(Locale.ROOT))) {
        add(i, k.timberMark(), editMessage("BM02", "SCL"));
      }
    }
  }

  /** {@code HBSErrors2Tag.getHbsEditErrorMessage}: "MSGCODE:code,category" → "code description". */
  private String editMessage(String code, String category) {
    String description = lookups.editErrorDescription(code, category);
    return description == null ? "MSGCODE:" + code + "," + category : code + " " + description;
  }

  // ── sample tally (P658Action / P659Action) ──────────────────────────

  /** {@code P658Action.validateSampleTallyDetails} / {@code validateLog} on each non-empty line. */
  private void sampleTally() {
    for (int i = 0; i < logs.size(); i++) {
      LogLine line = parsedLine(i, false);
      Row r = line.row();
      if (helperRowEmpty(line, false)) continue;
      boolean errorFlag = false;
      Long logNumber = r.lng("log_number");
      if (logNumber == null || logNumber <= 0) {
        add(i, "logNumber", FormMessages.text("error.lognumber.required"));
        errorFlag = true;
      }
      String species = r.str("scale_species_code");
      String grade = r.str("scale_grade_code"); // parser default " " (never empty, so error.grade.required cannot fire)
      boolean length = r.get("length") != null;
      boolean top = r.get("top") != null;
      boolean butt = r.get("butt") != null;
      if (emptyKeep(species) && (length || top || butt || !emptyKeep(grade))) { // BT13306
        add(i, "scaleSpeciesCode", FormMessages.text("error.species.required"));
        errorFlag = true;
      }
      if (!emptyKeep(species) && !emptyKeep(grade)
          && !lookups.speciesProductGradeExists(species, r.str("scale_product_code"), grade)) {
        add(i, "scaleSpeciesCode", FormMessages.text("errors.speciesgrade.invalid"));
        errorFlag = true;
      }
      BigDecimal len = r.num("length");
      if ((len == null && (!emptyKeep(species) || top || butt || !emptyKeep(grade)))
          || (len != null && (int) len.floatValue() < 0)) { // Float.intValue, as legacy
        add(i, "length", FormMessages.text("error.lengthformat.invalid"));
        errorFlag = true;
      } else if (len != null && !(len.floatValue() < 100.0f)) {
        add(i, "length", FormMessages.text("error.maxlength.invalid"));
        errorFlag = true;
      }
      if (len != null) {
        DecimalFormat format = new DecimalFormat("####.#", DecimalFormatSymbols.getInstance(Locale.ROOT));
        float value = len.floatValue();
        if (value != Float.parseFloat(format.format(value))) {
          add(i, "length", FormMessages.text("error.length.decimalinvalid"));
          errorFlag = true;
        }
      }
      Long topValue = r.lng("top");
      if ((topValue == null && (!emptyKeep(species) || length || butt || !emptyKeep(grade)))
          || (topValue != null && topValue < 0)) {
        add(i, "top", FormMessages.text("error.integerformat.invalid", FormMessages.LOG_TOP_FIELD_NAME));
        errorFlag = true;
      }
      Long buttValue = r.lng("butt");
      if ((buttValue == null && (!emptyKeep(species) || length || top || !emptyKeep(grade)))
          || (buttValue != null && buttValue < 0)) {
        add(i, "butt", FormMessages.text("error.integerformat.invalid", FormMessages.LOG_BUTT_FIELD_NAME));
        errorFlag = true;
      }
      // the net volume is recalculated (and checked) only when the line is clean
      if (!errorFlag) {
        netVolumeRange(i, line);
      }
    }
    defects();
  }

  // ── SFP tally (P752Form) ────────────────────────────────────────────

  private void sfpTally() {
    String sm = str(header.get("hbsSfpScaleTypeCode"));
    if (sm == null && !header.containsKey("hbsSfpScaleTypeCode")) {
      sm = "L"; // P752Form.Default.SCALE_METHOD (LOG_SCALE)
    }
    Set<String> available = SFP_DETAIL_FIELDS.getOrDefault(sm, Set.of());
    Keys k = SFP_KEYS;
    // row_ [requestSpeciesProductGrade]
    speciesProductGradeRows(k);
    // length_ [requestParamsRequiredBasedOnScaleMethod, requestFloatBetween 0..100]
    if (requiredRows(k, "length", available.contains("LENGTH"))) {
      floatBetween(k, k.length(), 0.0f, 100.0f, "detail.version.log.length");
    }
    // stackLength_: the "required" branch has no case for it (legacy no-op)
    floatBetween(k, "stackLength", 0.0f, 100.0f, "detail.version.log.stack-length");
    // logNumber_ [required…, requestPropertyUnique (no message for P752Form)]
    requiredRows(k, "logNumber", available.contains("LOG_NUMBER"));
    requiredRows(k, "species", available.contains("SPECIES"));
    if (requiredRows(k, "top", available.contains("TOP"))) {
      requestInteger(k, k.top(), "detail.version.log.top");
    }
    if (requiredRows(k, "butt", available.contains("BUTT"))) {
      requestInteger(k, k.butt(), "detail.version.log.butt");
    }
    // volume_ / proratedVolume_ (both the posted net volume)
    floatBetween(k, "netVolume", 0.0f, 999.999f, "detail.version.log.net-volume");
    // stackWidth_ (legacy label: Stack Length)
    floatBetween(k, "stackWidth", 0.0f, 999.999f, "detail.version.log.stack-length");
    floatBetween(k, "stackHeight", 0.0f, 999.999f, "detail.version.log.stack-height");
    integerBetween(k, "pieceCount", 0, 100, "detail.version.log.piece-count");
    floatBetween(k, "conversionFactor", 0.0f, 999.999f, "detail.version.log.conversion-factor");
  }

  // ── row validators (HBSValidator.validateRequest*) ──────────────────

  /**
   * {@code HBSValidator.validateRequestParamsRequiredBasedOnScaleMethod} /
   * {@code validateRequestParamsRequired}: a blank length, top, butt, species or
   * log number on a line where another of those (or the grade or a mark) is
   * entered is {@code errors.required}. Only top, butt and species make the
   * field invalid (legacy), which stops their chain.
   */
  private boolean requiredRows(Keys k, String param, boolean requiredForScaleMethod) {
    if (!requiredForScaleMethod) {
      return true;
    }
    boolean valid = true;
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String length = val(i, k.length());
      String top = val(i, k.top());
      String butt = val(i, k.butt());
      String species = val(i, "scaleSpeciesCode");
      boolean grade = !blank(val(i, "scaleGradeCode"));
      boolean marks = !emptyKeep(val(i, k.inputMark())) || !emptyKeep(val(i, k.timberMark()));
      switch (param) {
        case "length" -> {
          if (emptyKeep(length) && (!emptyKeep(top) || !emptyKeep(butt) || !emptyKeep(species) || grade || marks)) {
            add(i, k.length(), required("detail.version.log.length"));
          }
        }
        case "butt" -> {
          if (emptyKeep(butt) && (!emptyKeep(length) || !emptyKeep(top) || !emptyKeep(species) || grade || marks)) {
            valid = false;
            add(i, k.butt(), required("detail.version.log.butt"));
          }
        }
        case "top" -> {
          if (emptyKeep(top) && (!emptyKeep(length) || !emptyKeep(butt) || !emptyKeep(species) || grade || marks)) {
            valid = false;
            add(i, k.top(), required("detail.version.log.top"));
          }
        }
        case "species" -> { // keepWhitespace=false
          if (blank(species) && (!emptyKeep(length) || !emptyKeep(top) || !emptyKeep(butt) || grade || marks)) {
            valid = false;
            add(i, "scaleSpeciesCode", required("detail.version.log.species"));
          }
        }
        case "logNumber" -> {
          String logNumber = val(i, k.logNumber());
          if (emptyKeep(logNumber) && (!emptyKeep(species) || !emptyKeep(length) || !emptyKeep(top)
              || !emptyKeep(butt) || grade || marks)) {
            add(i, k.logNumber(), required("detail.version.log.log-number"));
          }
        }
        default -> { }
      }
    }
    return valid;
  }

  private static String required(String label) {
    return FormMessages.text("errors.required", FormMessages.label(label));
  }

  /** {@code HBSValidator.validateRequestInteger}: a positive integer ({@code errors.integer2}). */
  private void requestInteger(Keys k, String key, String label) {
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String v = val(i, key);
      if (!emptyKeep(v) && (!isInt(v) || Integer.parseInt(v) <= 0)) {
        add(i, key, FormMessages.text("errors.integer2", FormMessages.label(label)));
      }
    }
  }

  /**
   * {@code HBSValidator.validateRequestSpeciesProductGrade}: species (upper
   * case), product (default " ") and grade (default " ") must be a
   * SPECIES_PRODUCT_GRADE_XREF combination ({@code errors.speciesgrade.invalid}).
   */
  private void speciesProductGradeRows(Keys k) {
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String species = val(i, "scaleSpeciesCode");
      String product = val(i, "scaleProductCode");
      String grade = val(i, "scaleGradeCode");
      if (emptyKeep(product)) product = DEFAULT_PRODUCT;
      if (emptyKeep(grade)) grade = DEFAULT_GRADE;
      if (!emptyKeep(species) && !lookups.speciesProductGradeExists(species.toUpperCase(Locale.ROOT),
          product.toUpperCase(Locale.ROOT), grade.toUpperCase(Locale.ROOT))) {
        add(i, "scaleSpeciesCode", FormMessages.text("errors.speciesgrade.invalid"));
      }
    }
  }

  /**
   * {@code HBSValidator.validateRequestFloatBetween}: strictly between the
   * bounds ({@code errors.not-in-range}); a non-number became
   * {@code Float.MIN_VALUE} and so passed (legacy quirk, kept).
   */
  private void floatBetween(Keys k, String key, float lower, float upper, String label) {
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String v = val(i, key);
      if (emptyKeep(v)) continue;
      float value;
      try {
        value = Float.parseFloat(v);
      } catch (NumberFormatException e) {
        value = Float.MIN_VALUE;
      }
      if (outOfBounds(lower, upper, value)) {
        add(i, key, FormMessages.text("errors.not-in-range", FormMessages.label(label)));
      }
    }
  }

  /** {@code HBSValidator.validateRequestIntegerBetween} (a non-number is out of range). */
  private void integerBetween(Keys k, String key, int lower, int upper, String label) {
    for (int i = 0; i < logs.size(); i++) {
      if (rowEmpty(i, k)) continue;
      String v = val(i, key);
      if (emptyKeep(v)) continue;
      int value;
      try {
        value = Integer.parseInt(v);
      } catch (NumberFormatException e) {
        value = Integer.MIN_VALUE;
      }
      if (outOfBounds(lower, upper, value)) {
        add(i, key, FormMessages.text("errors.not-in-range", FormMessages.label(label)));
      }
    }
  }

  /** {@code HBSValidatorHelper.isOutOfBounds}: exclusive bounds. */
  static boolean outOfBounds(double lower, double upper, double value) {
    return !(lower < value && value < upper && lower < upper);
  }

  // ── net volume and defects ──────────────────────────────────────────

  /** {@code error.netvolume.greaterinvalid} / {@code lessinvalid} on the line's recomputed net volume. */
  private void netVolumeRange(int i, LogLine line) {
    float vol = LogVolume.netVolume(line);
    if (vol > 999.999f) {
      add(i, "netVolume", FormMessages.text("error.netvolume.greaterinvalid"));
    } else if (vol < 0) {
      add(i, "netVolume", FormMessages.text("error.netvolume.lessinvalid"));
    }
  }

  /** {@code P559Action} / {@code P659Action.validateLogDefects}: each non-blank defect of each line. */
  @SuppressWarnings("unchecked")
  private void defects() {
    for (int i = 0; i < logs.size(); i++) {
      if (!(logs.get(i).get("defects") instanceof List<?> defects)) continue;
      Set<Integer> numbers = new HashSet<>();
      for (int j = 0; j < defects.size(); j++) {
        Object o = ((Map<String, Object>) defects.get(j)).getOrDefault("fields", Map.of());
        Map<String, Object> d = (Map<String, Object>) o;
        Integer number = toInteger(str(d.get("defectNumber")));
        Float volume = toFloat(str(d.get("volume")));
        String defectType = trimmed(str(d.get("defectType")));
        String descriptor = trimmed(str(d.get("descriptor")));
        if (number == null && emptyKeep(defectType) && emptyKeep(descriptor) && volume == null) {
          continue; // an unused defect row
        }
        String base = "logs[" + i + "].defects[" + j + "].";
        if (number == null || number == 0) {
          problems.add(new Problem(base + "defectNumber", FormMessages.text("error.defectnumber.required")));
        } else if (!numbers.add(number)) {
          problems.add(new Problem(base + "defectNumber", FormMessages.text("error.defectnumber.unique")));
        }
        if (volume == null) {
          problems.add(new Problem(base + "volume", FormMessages.text("error.volume.required")));
        } else if (volume > 999.999) {
          problems.add(new Problem(base + "volume", FormMessages.text("error.defectvolume.invalid")));
        } else if (volume.toString().length() > 4 && !isValidPrecision(volume.toString(), 3)) {
          problems.add(new Problem(base + "volume", FormMessages.text("error.defectvolume.decimal.invalid")));
        }
        if (emptyKeep(defectType)) {
          problems.add(new Problem(base + "defectType", FormMessages.text("error.defecttype.required")));
        }
        if (emptyKeep(descriptor)) {
          problems.add(new Problem(base + "descriptor", FormMessages.text("error.description.required")));
        }
      }
    }
  }

  /**
   * {@code HBSValidatorHelper.isValidPrecision}, line by line (its trailing-zero
   * loop drops two characters at a time — legacy quirk, kept).
   */
  static boolean isValidPrecision(String value, int decimalPlaces) {
    boolean valid = true;
    int index = value.indexOf('.');
    if (index >= 0) {
      String rightOfPoint = value.substring(index + 1);
      int lastIndexRop = rightOfPoint.length() - 1;
      while (lastIndexRop > 2) {
        if (rightOfPoint.endsWith("0")) {
          rightOfPoint = rightOfPoint.substring(0, lastIndexRop - 1);
        }
        lastIndexRop--;
      }
      if (rightOfPoint.length() > decimalPlaces) {
        valid = false;
      }
    }
    return valid;
  }

  // ── line plumbing ───────────────────────────────────────────────────

  @SuppressWarnings("unchecked")
  private Map<String, Object> fields(int i) {
    Object o = logs.get(i).get("fields");
    return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
  }

  private String val(int i, String key) {
    if (key == null) return null;
    Object v = fields(i).get(key);
    if (key.equals("nmvInd")) {
      return yes(v) ? "on" : null; // an unticked check box was not posted
    }
    return str(v);
  }

  /**
   * {@code HBSValidatorHelper.isRowEmpty(request, rowParams, row)}: every row
   * parameter (with the line id) blank — such a line is skipped.
   */
  private boolean rowEmpty(int i, Keys k) {
    if (logs.get(i).get("logdtlId") != null) return false;
    for (String p : k.rowParams()) {
      if (!emptyKeep(val(i, p))) return false;
    }
    return true;
  }

  /** {@code DetailScaleDocumentHelper.isErrorExists}: a problem already reported on the line. */
  private boolean rowHasErrors(int i) {
    String prefix = "logs[" + i + "].";
    return problems.stream().anyMatch(p -> p.field().startsWith(prefix));
  }

  /**
   * The line as the legacy parser built it ({@code LogDetailParser} /
   * {@code SampleDetailParser}): codes upper-cased, product " ", grade
   * defaulted to " ", numbers converted (null when not a number), with the
   * payload's defects for the net volume.
   */
  @SuppressWarnings("unchecked")
  private LogLine parsedLine(int i, boolean withMarks) {
    Row r = new Row();
    r.set("log_number", toInteger(val(i, "logNumber")));
    if (withMarks) {
      r.set("input_bcomb_timber_mark", upperOrNull(val(i, "inputBcombTimberMark")));
      r.set("timber_mark", upperOrNull(val(i, "timberMark")));
    }
    r.set("nmv_ind", yes(fields(i).get("nmvInd")) ? "Y" : "N");
    r.set("scale_species_code", upperOrNull(val(i, "scaleSpeciesCode")));
    r.set("scale_product_code", DEFAULT_PRODUCT);
    String grade = upperOrNull(val(i, "scaleGradeCode"));
    r.set("scale_grade_code", grade == null ? DEFAULT_GRADE : grade);
    Float length = toFloat(val(i, "length"));
    r.set("length", length == null ? null : new BigDecimal(length.toString()));
    r.set("top", toInteger(val(i, "top")));
    r.set("butt", toInteger(val(i, "butt")));
    LogLine line = new LogLine(r);
    if (logs.get(i).get("defects") instanceof List<?> defects) {
      for (Object o : defects) {
        Map<String, Object> d = (Map<String, Object>) ((Map<String, Object>) o).getOrDefault("fields", Map.of());
        Float volume = toFloat(str(d.get("volume")));
        if (volume != null) {
          line.defects().add(new Row().set("volume", new BigDecimal(volume.toString())));
        }
      }
    }
    return line;
  }

  /** {@code DetailScaleDocumentHelper.isRowEmpty(LogDetail)} on the parsed line. */
  private static boolean helperRowEmpty(LogLine line, boolean withMarks) {
    Row r = line.row();
    return !"Y".equals(r.str("nmv_ind"))
        && (!withMarks || (emptyKeep(r.str("timber_mark")) && emptyKeep(r.str("input_bcomb_timber_mark"))))
        && blank(r.str("scale_species_code")) && blank(r.str("scale_grade_code"))
        && r.get("length") == null && r.get("top") == null && r.get("butt") == null;
  }

  private void add(int i, String key, String message) {
    problems.add(new Problem("logs[" + i + "]." + key, message));
  }

  /** {@code DetailScaleDocumentHelper.convertToInteger} (null for blank or not a number). */
  static Integer toInteger(String s) {
    if (blank(s)) return null;
    try {
      return Integer.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** {@code DetailScaleDocumentHelper.convertToFloat} (null for blank or not a number). */
  static Float toFloat(String s) {
    if (blank(s)) return null;
    try {
      return Float.valueOf(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String upperOrNull(String s) {
    return blank(s) ? null : s.toUpperCase(Locale.ROOT);
  }

  private static String trimmed(String s) {
    return s == null ? null : s.trim();
  }
}
