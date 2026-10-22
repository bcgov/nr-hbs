package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The header fields of the add/update pages ({@code P050Form}, {@code P100Form}, {@code P150Form})
 * keyed by the JSON names the SPA sends. Values are trimmed text, blank is null; the fields the
 * legacy form setters upper-cased are upper-cased here too.
 *
 * <p>On update the legacy page posted the session form, which {@code updateForm} had filled from
 * the active version, so a field the page did not render (e.g. the scaler licence of an area-based
 * return) kept the stored value. {@link #forUpdate} does the same: a key absent from the body
 * keeps the version's value; a key present and blank clears it.
 */
public final class HeaderForm {

  // JSON keys (the contract with the SPA)
  public static final String PSSMRY_TYPE = "pssmryType";
  public static final String CATEGORY = "category";
  public static final String SCALER_LICENCE = "scalerLicence";
  public static final String TIMBER_MARK = "timberMark";
  public static final String NUMBER_OF_CHAINS = "numberOfChains";
  public static final String CUT_BLOCK_ID = "cutBlockId";
  public static final String RETURN_NUMBER = "returnNumber";
  public static final String BILL_TO_CLIENT = "billToClientNumber";
  public static final String BILL_TO_LOCN = "billToClientLocn";
  public static final String CAMP_BOOM = "campBoomNumber";
  public static final String SCALE_SITE = "scaleSite";
  public static final String COPY_TO_CLIENT = "copyToClientNumber";
  public static final String COPY_TO_LOCN = "copyToClientLocn";
  public static final String NUMBER_OF_SECTIONS = "numberOfSections";
  public static final String SCALE_DATE = "scaleDate";
  public static final String LOG_COUNT = "logCount";
  public static final String PLACE_OF_SCALE = "placeOfScale";
  public static final String FIELD_DECK_ID = "fieldScaleDeckId";
  public static final String AREA_CUT = "areaCut";
  public static final String BATCH_SLIP_ID = "batchSlipId";
  public static final String COMMENT = "commentText";
  public static final String POPULATION = "populationNumber";
  public static final String STRATUM = "stratumNumber";
  public static final String SAMPLING_YEAR = "samplingYear";
  public static final String LOAD_ARRIVAL = "loadArrivalNumber";
  public static final String NET_WEIGHT = "netWeight";
  public static final String WEIGH_SLIP = "weighSlipNumber";
  public static final String CONFIRM_CUSTODY_TRANSFER = "confirmCustodyTransfer";

  /** JSON key → version column, per return type (the batch slip lives on HBS_SCALE_RETURN). */
  static final Map<String, String> PIECE_COLUMNS = columns(
      CATEGORY, "scl_rtn_category_code", SCALER_LICENCE, "license_number", TIMBER_MARK, "timber_mark",
      NUMBER_OF_CHAINS, "number_of_chains", CUT_BLOCK_ID, "cut_block_id", RETURN_NUMBER, "return_number",
      BILL_TO_CLIENT, "client_number_paid_by", BILL_TO_LOCN, "client_locn_code_paid_by",
      CAMP_BOOM, "camp_boom_number", SCALE_SITE, "scale_site_id_nmbr",
      COPY_TO_CLIENT, "client_number_copied_to", COPY_TO_LOCN, "client_locn_code_copied_to",
      NUMBER_OF_SECTIONS, "number_of_sections", SCALE_DATE, "scale_date", LOG_COUNT, "log_count",
      PLACE_OF_SCALE, "place_of_scale", FIELD_DECK_ID, "field_scale_deck_id", AREA_CUT, "area_cut",
      COMMENT, "version_comment");
  static final Map<String, String> WEIGHT_COLUMNS = columns(
      CATEGORY, "scl_rtn_category_code", POPULATION, "population_number", STRATUM, "stratum_number",
      SAMPLING_YEAR, "sampling_year", TIMBER_MARK, "timber_mark", SCALE_SITE, "scale_site_id_nmbr",
      BILL_TO_CLIENT, "client_number_paid_by", BILL_TO_LOCN, "client_locn_code_paid_by",
      COPY_TO_CLIENT, "client_number_copied_to", COPY_TO_LOCN, "client_locn_code_copied_to",
      SCALE_DATE, "scale_date", FIELD_DECK_ID, "field_scale_deck_id");
  static final Map<String, String> SAMPLE_COLUMNS = columns(
      CATEGORY, "scl_rtn_category_code", POPULATION, "population_number", STRATUM, "stratum_number",
      SAMPLING_YEAR, "sampling_year", TIMBER_MARK, "timber_mark", SCALER_LICENCE, "license_number",
      LOAD_ARRIVAL, "load_arrival_number", NET_WEIGHT, "net_weight", RETURN_NUMBER, "return_number",
      LOG_COUNT, "log_count", SCALE_SITE, "scale_site_id_nmbr", WEIGH_SLIP, "weigh_slip_number",
      SCALE_DATE, "scale_date");

  /** Fields whose legacy form setter upper-cased the input. */
  private static final Set<String> UPPER = Set.of(PSSMRY_TYPE, SCALER_LICENCE, NUMBER_OF_CHAINS, CAMP_BOOM,
      PLACE_OF_SCALE, TIMBER_MARK, SCALE_SITE, RETURN_NUMBER, CUT_BLOCK_ID, FIELD_DECK_ID, AREA_CUT,
      POPULATION, STRATUM, LOAD_ARRIVAL, WEIGH_SLIP);

  private final SummaryReturnType type;
  private final Map<String, String> values = new LinkedHashMap<>();
  private final boolean confirmCustodyTransfer;

  private HeaderForm(SummaryReturnType type, boolean confirmCustodyTransfer) {
    this.type = type;
    this.confirmCustodyTransfer = confirmCustodyTransfer;
  }

  private static Map<String, String> columns(String... pairs) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }

  public static Map<String, String> columnsOf(SummaryReturnType type) {
    return switch (type) {
      case PIECE -> PIECE_COLUMNS;
      case WEIGHT -> WEIGHT_COLUMNS;
      case SAMPLE -> SAMPLE_COLUMNS;
    };
  }

  private static List<String> keysOf(SummaryReturnType type) {
    List<String> keys = new java.util.ArrayList<>(columnsOf(type).keySet());
    if (type == SummaryReturnType.PIECE) {
      keys.add(0, PSSMRY_TYPE);
      keys.add(BATCH_SLIP_ID);
    }
    return keys;
  }

  /** The add page: every field from the body. */
  public static HeaderForm forAdd(SummaryReturnType type, Map<String, Object> body) {
    HeaderForm f = new HeaderForm(type, false);
    for (String key : keysOf(type)) {
      f.values.put(key, clean(key, body.get(key)));
    }
    return f;
  }

  /** The update page: body fields over the active version's values ({@code updateForm}). */
  public static HeaderForm forUpdate(SummaryReturn ret, Map<String, Object> body) {
    HeaderForm f = new HeaderForm(ret.type(), "Y".equalsIgnoreCase(String.valueOf(body.get(CONFIRM_CUSTODY_TRANSFER))));
    Map<String, String> cols = columnsOf(ret.type());
    for (String key : keysOf(ret.type())) {
      if (PSSMRY_TYPE.equals(key)) {
        f.values.put(key, ret.pssmryType()); // the summary type never changes on update
      } else if (body.containsKey(key)) {
        f.values.put(key, clean(key, body.get(key)));
      } else if (BATCH_SLIP_ID.equals(key)) {
        f.values.put(key, display(ret.header().get("volest_batch_id")));
      } else {
        f.values.put(key, display(ret.version().get(cols.get(key))));
      }
    }
    return f;
  }

  static String clean(String key, Object raw) {
    if (raw == null) return null;
    String s = raw instanceof BigDecimal b ? b.toPlainString() : raw.toString();
    s = s.trim();
    if (s.isEmpty()) return null;
    return UPPER.contains(key) ? s.toUpperCase(Locale.ROOT) : s;
  }

  /** A stored value as the legacy form showed it (numbers without trailing zeros, dates yyyy-MM-dd). */
  static String display(Object v) {
    if (v == null) return null;
    if (v instanceof Timestamp t) return t.toLocalDateTime().toLocalDate().toString();
    if (v instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toLocalDate().toString();
    if (v instanceof java.time.LocalDateTime t) return t.toLocalDate().toString();
    if (v instanceof java.time.LocalDate d) return d.toString();
    if (v instanceof Number n) {
      BigDecimal b = new BigDecimal(n.toString()).stripTrailingZeros();
      return b.scale() < 0 ? b.setScale(0).toPlainString() : b.toPlainString();
    }
    String s = v.toString().trim();
    return s.isEmpty() ? null : s;
  }

  public SummaryReturnType type() {
    return type;
  }

  public String get(String key) {
    return values.get(key);
  }

  public void set(String key, String value) {
    values.put(key, value);
  }

  public boolean empty(String key) {
    return values.get(key) == null;
  }

  public boolean confirmCustodyTransfer() {
    return confirmCustodyTransfer;
  }

  /** The field as a Long; the validations have checked it. */
  public Long lng(String key) {
    String s = values.get(key);
    return s == null ? null : Long.valueOf(s);
  }

  /** The field as a number (sections, area cut: Double in the legacy value objects). */
  public BigDecimal num(String key) {
    String s = values.get(key);
    return s == null ? null : new BigDecimal(s);
  }

  /** Copies every field onto its version column (the {@code update*Version} setters). */
  void copyTo(Row version, Set<String> skip) {
    columnsOf(type).forEach((key, column) -> {
      if (skip.contains(key)) return;
      version.set(column, switch (key) {
        case NUMBER_OF_SECTIONS, AREA_CUT -> num(key);
        case LOG_COUNT, SAMPLING_YEAR, NET_WEIGHT -> lng(key);
        case SCALE_DATE -> EntryRules.startOfDay(EntryRules.parseDate(get(key)));
        default -> get(key);
      });
    });
  }

  @Override
  public String toString() {
    return type.code() + values;
  }
}
