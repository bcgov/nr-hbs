package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import ca.bc.gov.nrs.hbs.api.summaryreturns.EditError;
import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturn;
import ca.bc.gov.nrs.hbs.api.summaryreturns.SummaryReturnType;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Client;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ClientLocation;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.DateWindow;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Population;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.ScaleSite;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Stratum;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.TimberMark;
import ca.bc.gov.nrs.hbs.api.summaryreturns.edits.Lookups.Worksheet;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A clean piece, weight and sample return (no edit errors) on in-memory reference data, the real
 * {@link Rates} on a {@link RecordingTableApi}, and a clock fixed at 2026-10-21 10:00.
 */
class EditsFixture {

  static final String MARK = "ABC123";
  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0);
  static final Timestamp SCALE_DATE = Timestamp.valueOf("2026-09-15 00:00:00");
  static final LocalDateTime FAR_PAST = LocalDateTime.of(1990, 1, 1, 0, 0);
  static final LocalDateTime FAR_FUTURE = LocalDateTime.of(9999, 12, 31, 0, 0);

  final FakeLookups lookups = new FakeLookups();
  final RecordingTableApi api = new RecordingTableApi();
  final Rates rates = new Rates(api, lookups);
  final Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
  final SummaryReleaseEdits edits = new SummaryReleaseEdits(lookups, rates, clock);

  /** HBS_RATES answers: rule/appraisal found, every normal / waste / beachcomb rate found. */
  final Map<String, Object> ruleAndAppraisal = new HashMap<>(Map.of(
      "o_rate_rule_found", "N", "o_rate_rule_overlap", "N", "o_worksheet_found", "Y", "o_stumpage_found", "Y"));
  final Map<String, Object> rate = new HashMap<>(Map.of("o_rate_found", "Y"));

  EditsFixture() {
    lookups.marks.put(MARK, mark("A01", "N", "S"));
    Client client = new Client("00012345", "ACME LOGGING", null, null, "ACT", "C");
    lookups.clients.put("00012345", client);
    lookups.locations.put("00012345/00", location(client, "00"));
    Client owner = new Client("00099999", "SITE OWNER", null, null, "ACT", "C");
    lookups.clients.put("00099999", owner);
    lookups.locations.put("00099999/01", location(owner, "01"));
    lookups.sites.put("S123", new ScaleSite("S123", "00099999", "01", "Mill yard", 10L));
    lookups.licences.add("K123/S");
    lookups.spg.put("FI/ /U", new DateWindow(FAR_PAST, FAR_FUTURE));
    lookups.spg.put("FI/ /7", new DateWindow(FAR_PAST, FAR_FUTURE));
    lookups.grades.put("U/C", new DateWindow(FAR_PAST, FAR_FUTURE));
    lookups.grades.put("7/C", new DateWindow(FAR_PAST, FAR_FUTURE));
    lookups.worksheets.add(new Worksheet(MARK, FAR_PAST, "C", "S", "S"));
    lookups.strata.put("P1/S1/2026", new Stratum("00099999", "01", FAR_PAST, FAR_FUTURE, "C"));
    lookups.populations.put("P1/2026", new Population("00012345", "00", "S"));
    lookups.ratios.add(new Lookups.RatioStatistic("FI", " ", "U"));
    api.answer(Rates.GET_OVERRIDE_AND_APPRAISAL, c -> ruleAndAppraisal);
    api.answer(Rates.RETRIEVE_NORMAL_RATE, c -> rate);
    api.answer(Rates.RETRIEVE_WASTE_RATE, c -> rate);
    api.answer(Rates.RETRIEVE_BEACHCOMB_RATE, c -> rate);
  }

  static TimberMark mark(String fileType, String cruiseBased, String paymentMethod) {
    return new TimberMark(MARK, fileType, cruiseBased, paymentMethod, 10, "A", "00012345", "00");
  }

  static ClientLocation location(Client client, String locn) {
    return new ClientLocation(client, client.clientNumber(), locn, "Victoria", "BC", "V8W1A1", "N");
  }

  // ── Returns ──────────────────────────────────────────────────────────────

  static Row common(Row v) {
    return v.set("document_control_number", 1001L).set("version", 1L)
        .set("scl_rtn_version_state_code", "ACC").set("scl_rtn_category_code", "PR")
        .set("scale_date", SCALE_DATE).set("scale_site_id_nmbr", "S123")
        .set("license_number", "K123").set("quant_lic_type_cd", "S")
        .set("timber_mark", MARK).set("input_timber_mark", MARK)
        .set("entry_timestamp", Timestamp.valueOf("2026-09-16 08:00:00"));
  }

  static Row header() {
    return new Row().set("document_control_number", 1001L).set("generated_summary_ind", "N")
        .set("scale_return_state_code", "OIP");
  }

  final List<Row> pieceSegs = new ArrayList<>();
  /** A piece return without segregations (else {@link #piece} adds one clean segregation). */
  boolean noPieceSegs;
  final Row pieceVersion = common(new Row())
      .set("pssmry_type", "SMRYDOC").set("return_number", "R1").set("log_count", 10L)
      .set("client_number_paid_by", "00012345").set("client_locn_code_paid_by", "00")
      .set("client_number_copied_to", null).set("client_locn_code_copied_to", null)
      .set("input_scaler_license_num", "K123").set("area_cut", null)
      .set("field_scale_deck_id", null).set("cut_block_id", null);

  static Row pieceSeg(long id, String species, String grade, long pieces) {
    return new Row().set("segregation_id", id).set("psseg_type", "PRIMARY")
        .set("scale_species_code", species).set("scale_product_code", " ").set("scale_grade_code", grade)
        .set("number_of_pieces", pieces).set("volume", new BigDecimal("12.5")).set("timber_mark", null)
        .set("nmv_ind", "N").set("sb4_ind", "N").set("avoidable_ind", null)
        .set("other_description_type_code", null).set("input_other_desc", null)
        .set("input_species", null).set("input_product", null).set("input_grade", null)
        .set("input_bcomb_timber_mark", null);
  }

  SummaryReturn piece() {
    if (pieceSegs.isEmpty() && !noPieceSegs) pieceSegs.add(pieceSeg(11, "FI", "U", 10));
    return new SummaryReturn(SummaryReturnType.PIECE, header(), pieceVersion, pieceSegs, new ArrayList<>(), List.of());
  }

  final List<Row> dailyLoads = new ArrayList<>(List.of(load(10, 30000), load(15, 25000)));
  final Row weightVersion = common(new Row())
      .set("population_number", "P1").set("stratum_number", "S1").set("sampling_year", 2026L)
      .set("input_population", "P1").set("input_stratum", "S1").set("input_sampling_year", "26")
      .set("client_number_paid_by", null).set("client_locn_code_paid_by", null)
      .set("client_number_copied_to", null).set("client_locn_code_copied_to", null)
      .set("field_scale_deck_id", null);

  static Row load(long day, long weight) {
    return new Row().set("day", day).set("number_of_loads", 1L).set("weight", weight);
  }

  SummaryReturn weight() {
    return new SummaryReturn(SummaryReturnType.WEIGHT, header(), weightVersion, List.of(), new ArrayList<>(), dailyLoads);
  }

  final List<Row> sampleSegs = new ArrayList<>();
  final Row sampleVersion = common(new Row())
      .set("population_number", "P1").set("stratum_number", "S1").set("sampling_year", 2026L)
      .set("input_population", "P1").set("input_stratum", "S1").set("input_sampling_year", "26")
      .set("input_scaler_license_num", "K123").set("net_weight", 30000L).set("load_arrival_number", "3")
      .set("log_count", 10L).set("return_number", "R1");

  static Row sampleSeg(long id, String species, String grade, long pieces) {
    return new Row().set("segregation_id", id).set("scale_species_code", species).set("scale_product_code", " ")
        .set("scale_grade_code", grade).set("number_of_pieces", pieces).set("volume", new BigDecimal("3.2"))
        .set("input_species", null).set("input_product", null).set("input_grade", null);
  }

  SummaryReturn sample() {
    if (sampleSegs.isEmpty()) sampleSegs.add(sampleSeg(21, "FI", "U", 10));
    return new SummaryReturn(SummaryReturnType.SAMPLE, header(), sampleVersion, sampleSegs, new ArrayList<>(), List.of());
  }

  static List<String> codes(List<EditError> errors) {
    return errors.stream().map(EditError::message).toList();
  }
}
