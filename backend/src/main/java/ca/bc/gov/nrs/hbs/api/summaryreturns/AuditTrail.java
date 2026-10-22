package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_DELETE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_INSERT;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.ACTION_UPDATE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.OBJECT_DAILY_LOAD;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.OBJECT_NOTATION;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.OBJECT_SEGREGATION;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnRules.OBJECT_SUMMARY_SCALE;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The change-log details ({@code HBS_SCALE_RETURN_AUDIT}) a summary-return save records.
 * The legacy value objects recorded one {@code AuditDetail} per setter call that changed
 * a value ({@code SummaryScaleReturn.recordFieldEdit / recordSegregationEdit /
 * recordDailyLoadEdit / recordNotationEdit / …Add / …Delete}); this compares the row
 * before and after the change instead, with the same field names
 * ({@code DacConstants.ScaleReturnAudit}) and value formats.
 */
public final class AuditTrail {

  /** One {@code HBS_SCALE_RETURN_AUDIT} row. */
  public record Detail(String fieldName, String actionType, String objectType, String oldValue, String newValue) {}

  private AuditTrail() {
  }

  /** Columns stored as Long in the legacy value objects ("" + Long). */
  private static final Set<String> LONG_COLUMNS = Set.of("log_count", "net_weight", "sampling_year",
      "number_of_pieces", "number_of_loads", "weight", "volest_batch_id", "day");
  /** Columns stored as Double ("" + Double, e.g. "2.0"). */
  private static final Set<String> DOUBLE_COLUMNS = Set.of("number_of_sections", "area_cut", "volume");
  /** Y/N columns stored as Boolean ("" + Boolean). */
  private static final Set<String> FLAG_COLUMNS = Set.of("check_scale_ind", "nmv_ind", "sb4_ind", "avoidable_ind");

  private static final Map<String, String> COMMON = fields(
      "license_number", "Scaler_License",
      "scale_site_id_nmbr", "Scale_Site_Id",
      "scl_rtn_version_state_code", "Version_Status",
      "scl_rtn_category_code", "Return_Category",
      "timber_mark", "Timber_Mark",
      "scale_date", "Scale_Date");

  /** {@code SummaryBillableScaleReturnVersion} (piece and weight). */
  private static final Map<String, String> BILLABLE = fields(
      "client_locn_code_copied_to", "CopyTo_Client_Location",
      "client_number_copied_to", "CopyTo_Client_Number",
      "client_locn_code_paid_by", "PaidBy_Client_Location",
      "client_number_paid_by", "PaidBy_Client_Number");

  private static final Map<String, String> PIECE = fields(
      "camp_boom_number", "Camp_Boom",
      "log_count", "Log_Count",
      "number_of_chains", "Number_Chains",
      "number_of_sections", "Number_Sections",
      "place_of_scale", "Place_Scale",
      "return_number", "Return_No",
      "version_comment", "Version_Comment",
      "cut_block_id", "Cutblock_ID",
      "field_scale_deck_id", "Deck_Id",
      "area_cut", "Area_Cut");

  private static final Map<String, String> WEIGHT = fields(
      "population_number", "Population_Number",
      "sampling_year", "Sampling_Year",
      "stratum_number", "Stratum_Number",
      "field_scale_deck_id", "Deck_Id");

  /** {@code SummarySampleScaleReturnVersion}; CHECK_SCALE_FIELD_NAME really is "Number_Sections" in legacy. */
  private static final Map<String, String> SAMPLE = fields(
      "log_count", "Log_Count",
      "net_weight", "Net_Weight",
      "sampling_year", "Sampling_Year",
      "check_scale_ind", "Number_Sections",
      "load_arrival_number", "Load_Arrival_Number",
      "population_number", "Population_Number",
      "return_number", "Return_No",
      "stratum_number", "Stratum_Number",
      "weigh_slip_number", "Weight_Slip_Number");

  private static final Map<String, String> SEGREGATION = fields(
      "number_of_pieces", "No_Of_Pieces",
      "scale_grade_code", "Grade",
      "scale_product_code", "Product",
      "scale_species_code", "Species",
      "volume", "Volume");

  private static final Map<String, String> PIECE_SEGREGATION = fields(
      "archive_timber_mark", "Archive_Timber_Mark",
      "avoidable_ind", "Avoidable_Flag",
      "input_bcomb_timber_mark", "Input_Bcomb_Timber_Mark",
      "nmv_ind", "NMV_Flag",
      "other_description_type_code", "Other_Desc_Code",
      "sb4_ind", "SB4_Flag",
      "timber_mark", "Timber_Mark");

  private static final Map<String, String> DAILY_LOAD = fields(
      "number_of_loads", "No_Of_Loads",
      "weight", "Weight");

  private static final Map<String, String> NOTATION = fields("text", "Text");

  private static Map<String, String> fields(String... pairs) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }

  /** Field edits between two states of a version row (and of the document's batch-slip id). */
  public static List<Detail> versionEdits(SummaryReturnType type, Row header0, Row header1, Row before, Row after) {
    List<Detail> out = new ArrayList<>();
    if (header0 != null && header1 != null) {
      diff(out, header0, header1, Map.of("volest_batch_id", "Batch_Slip_Id"), "", OBJECT_SUMMARY_SCALE);
    }
    Map<String, String> audited = new LinkedHashMap<>(COMMON);
    switch (type) {
      case PIECE -> {
        audited.putAll(BILLABLE);
        audited.putAll(PIECE);
      }
      case WEIGHT -> {
        audited.putAll(BILLABLE);
        audited.putAll(WEIGHT);
      }
      case SAMPLE -> audited.putAll(SAMPLE);
    }
    diff(out, before, after, audited, "", OBJECT_SUMMARY_SCALE);
    return out;
  }

  /** {@code SummaryScaleSegregation.getUniquelyIdentifyingString}: "SP/PR/G (id 123)". */
  public static String segregationId(Row seg) {
    StringBuilder b = new StringBuilder();
    b.append(firstNonEmpty(seg.str("scale_species_code"), seg.str("input_species"))).append('/');
    b.append(firstNonEmpty(seg.str("scale_product_code"), seg.str("input_product"))).append('/');
    b.append(firstNonEmpty(seg.str("scale_grade_code"), seg.str("input_grade")));
    if (seg.get("segregation_id") != null) b.append(" (id ").append(seg.lng("segregation_id")).append(')');
    return b.toString();
  }

  public static List<Detail> segregationEdits(SummaryReturnType type, Row before, Row after) {
    List<Detail> out = new ArrayList<>();
    Map<String, String> audited = new LinkedHashMap<>(SEGREGATION);
    if (type == SummaryReturnType.PIECE) audited.putAll(PIECE_SEGREGATION);
    diff(out, before, after, audited, segregationId(after) + " ", OBJECT_SEGREGATION);
    return out;
  }

  public static Detail segregationAdded(Row seg) {
    return new Detail(segregationId(seg), ACTION_INSERT, OBJECT_SEGREGATION, "", "");
  }

  public static Detail segregationDeleted(Row seg) {
    return new Detail(segregationId(seg), ACTION_DELETE, OBJECT_SEGREGATION, "", "");
  }

  /** {@code WeightScaleDailyLoadSummary.getUniquelyIdentifyingString}: "DLS/12". */
  public static String dailyLoadId(Row load) {
    return OBJECT_DAILY_LOAD + "/" + load.lng("day");
  }

  public static List<Detail> dailyLoadEdits(Row before, Row after) {
    List<Detail> out = new ArrayList<>();
    diff(out, before, after, DAILY_LOAD, dailyLoadId(after) + " ", OBJECT_DAILY_LOAD);
    return out;
  }

  public static Detail dailyLoadAdded(Row load) {
    return new Detail(dailyLoadId(load), ACTION_INSERT, OBJECT_DAILY_LOAD, "", "");
  }

  public static Detail dailyLoadDeleted(Row load) {
    return new Detail(dailyLoadId(load), ACTION_DELETE, OBJECT_DAILY_LOAD, "", "");
  }

  /** {@code SummaryScaleNotation.getTypeNumber}: "NOT/50". */
  public static String notationId(Row notation) {
    return notation.str("notation_type_code") + "/" + notation.lng("notation_number");
  }

  public static List<Detail> notationEdits(Row before, Row after) {
    List<Detail> out = new ArrayList<>();
    diff(out, before, after, NOTATION, notationId(after) + " ", OBJECT_NOTATION);
    return out;
  }

  public static Detail notationAdded(Row notation) {
    return new Detail(notationId(notation), ACTION_INSERT, OBJECT_NOTATION, "", "");
  }

  public static Detail notationDeleted(Row notation) {
    return new Detail(notationId(notation), ACTION_DELETE, OBJECT_NOTATION, "", "");
  }

  private static void diff(List<Detail> out, Row before, Row after, Map<String, String> audited,
      String prefix, String objectType) {
    audited.forEach((column, field) -> {
      Object a = before.get(column);
      Object b = after.get(column);
      // Oracle keeps '' as NULL, so blank and null are the same value here.
      if (!Row.sameValue(blankToNull(a), blankToNull(b))) {
        out.add(new Detail(prefix + field, ACTION_UPDATE, objectType, format(column, a), format(column, b)));
      }
    });
  }

  private static Object blankToNull(Object v) {
    return v instanceof String s && s.isEmpty() ? null : v;
  }

  /** The legacy string form of a value: dates yyyy.MM.dd, Longs as integers, Doubles as Java doubles. */
  static String format(String column, Object v) {
    if ("scale_date".equals(column)) {
      if (v == null) return null;
      return new SimpleDateFormat("yyyy.MM.dd", Locale.CANADA).format(v instanceof java.util.Date d ? d
          : java.sql.Timestamp.valueOf(v.toString().length() == 10 ? v + " 00:00:00" : v.toString()));
    }
    if (LONG_COLUMNS.contains(column)) {
      return v == null ? "null" : String.valueOf(new BigDecimal(v.toString()).longValue());
    }
    if (DOUBLE_COLUMNS.contains(column)) {
      return v == null ? "null" : String.valueOf(new BigDecimal(v.toString()).doubleValue());
    }
    if (FLAG_COLUMNS.contains(column)) {
      return v == null ? "null" : String.valueOf("Y".equalsIgnoreCase(v.toString()));
    }
    return v == null ? null : v.toString();
  }

  private static String firstNonEmpty(String a, String b) {
    if (a != null && !a.isEmpty()) return a;
    return b != null && !b.isEmpty() ? b : "";
  }
}
