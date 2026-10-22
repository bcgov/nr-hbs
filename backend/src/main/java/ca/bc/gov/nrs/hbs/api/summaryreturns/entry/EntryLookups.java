package ca.bc.gov.nrs.hbs.api.summaryreturns.entry;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The reference-data reads of the summary-return data-entry screens: the view-tier helpers
 * {@code InvoiceHelper / OpqHelper} ({@code findMark}, {@code findScaleSite}, {@code findClientLocation},
 * {@code isValidSPGCombination}), {@code DacHelper} ({@code isValidLicence}, {@code isValidCode},
 * {@code findPaperBatchByPK}, {@code findCutBlocksByCriteria}, {@code isValidCutBlockId}),
 * {@code PresentationHelper.isValidPopStratYear}, {@code SmpHelper.findPopulationByPK},
 * {@code SummaryScaleReturnHelper.derive*} and the BT15212 area-based finders
 * ({@code SummaryScaleReturnQuery.getAreaBased*}).
 *
 * <p>Plain SQL, every value bound, no schema prefix. FOREST_CLIENT is not granted to the proxy
 * user (docs/database.md), so client locations are read through V_CLIENT_PUBLIC + CLIENT_LOCATION.
 * Every method is public and overridable so the command tests can serve answers from memory.
 */
@Component
public class EntryLookups {

  private final NamedParameterJdbcTemplate jdbc;

  public EntryLookups(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@code legacy/ftas/TimberMark} as the entry validations use it: TIMBER_MARK joined to
   * MARK_BILLED_CLI ("Timber mark must be in Timber Mark table and in Mark Billed Client table").
   */
  public record Mark(String timberMark, String fileTypeCode, String cruiseBasedInd, String clientNumber,
      String clientLocnCode) {

    /** {@code TimberMark.isPrivateMark}: file types B08, B09, B13–B16. */
    public boolean isPrivateMark() {
      return List.of("B08", "B09", "B13", "B14", "B15", "B16").contains(trim(fileTypeCode));
    }

    /** {@code TimberMark.isCruiseBased} (BT5884): cruise based and not an oil-and-gas (A11) mark. */
    public boolean isCruiseBased() {
      return !"A11".equals(trim(fileTypeCode)) && "Y".equals(trim(cruiseBasedInd));
    }

    private static String trim(String s) {
      return s == null ? "" : s.trim();
    }
  }

  // ── Marks, sites, licences, clients ──────────────────────────────────────

  /** {@code InvoiceHelper.findMark} → {@code TimberMarkReader.getTimberMarkByMark}. */
  public Optional<Mark> timberMark(String timberMark) {
    if (timberMark == null) return Optional.empty();
    return first("""
        SELECT t.timber_mark, b.file_type_code, t.cruise_based_ind, b.client_number, b.client_locn_code
          FROM timber_mark t
          JOIN mark_billed_cli b ON b.timber_mark = t.timber_mark
         WHERE t.timber_mark = :mark""", params().addValue("mark", timberMark))
        .map(r -> new Mark(r.str("timber_mark"), r.str("file_type_code"), r.str("cruise_based_ind"),
            r.str("client_number"), r.str("client_locn_code")));
  }

  /** {@code InvoiceHelper.findScaleSite} ({@code ScaleSiteQuery.selectByPK}) found a row. */
  public boolean scaleSiteExists(String scaleSite) {
    return count("SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = :site",
        params().addValue("site", scaleSite)) > 0;
  }

  /** {@code DacHelper.isValidLicence(licence, type)} → {@code QuantLicenseManager.isValidLicense}. */
  public boolean licenceExists(String licence, String quantLicTypeCd) {
    return count("SELECT COUNT(*) FROM quant_license WHERE license_number = :licence AND quant_lic_type_cd = :type",
        params().addValue("licence", licence).addValue("type", quantLicTypeCd)) > 0;
  }

  /**
   * {@code OpqHelper.findClientLocation(client, locn)}: the location exists (both upper-cased, the
   * client number zero-padded to 8 and the location to 2, as {@code ClientEntityBean} did).
   */
  public boolean clientLocationExists(String clientNumber, String clientLocnCode) {
    if (clientNumber == null || clientLocnCode == null) return false;
    return count("""
        SELECT COUNT(*) FROM v_client_public c
          JOIN client_location l ON l.client_number = c.client_number
         WHERE c.client_number = :client AND l.client_locn_code = :locn""",
        params().addValue("client", pad(clientNumber.toUpperCase(Locale.ROOT), 8))
            .addValue("locn", pad(clientLocnCode.toUpperCase(Locale.ROOT), 2))) > 0;
  }

  /**
   * {@code deriveBillToClientAndLocationFromTimberMark} returned a location: the mark holder's
   * (MARK_BILLED_CLI) client location exists.
   */
  public boolean billToDerivable(String timberMark) {
    return timberMark(timberMark).map(m -> clientLocationExists(m.clientNumber(), m.clientLocnCode())).orElse(false);
  }

  /** {@code deriveCopyToClientLocationFromStratum} returned a location: the stratum owner's location exists. */
  public boolean copyToDerivable(String population, String stratum, Long year) {
    if (population == null || stratum == null || year == null || year == 0) return false;
    return first("""
        SELECT client_number, client_locn_code FROM stratum
         WHERE population_number = :population AND stratum_number = :stratum AND sampling_year = :year""",
        params().addValue("population", population).addValue("stratum", stratum).addValue("year", year))
        .map(r -> clientLocationExists(r.str("client_number"), r.str("client_locn_code"))).orElse(false);
  }

  // ── Codes ────────────────────────────────────────────────────────────────

  /** {@code DacHelper.isValidCode(code, "SCL_RTN_CATEGORY_CODE")} ({@code CodeReader.getCode}: any row, dated or not). */
  public boolean categoryCodeExists(String code) {
    return count("SELECT COUNT(*) FROM scl_rtn_category_code WHERE scl_rtn_category_code = :code",
        params().addValue("code", code)) > 0;
  }

  /** {@code OpqHelper.isValidSPGCombination(sp, prod, grade, null, null)}: the combination is in the xref (any date). */
  public boolean speciesProductGradeExists(String species, String product, String grade) {
    return count("""
        SELECT COUNT(*) FROM species_product_grade_xref
         WHERE scale_species_code = :species AND scale_product_code = :product AND scale_grade_code = :grade""",
        params().addValue("species", species).addValue("product", product).addValue("grade", grade)) > 0;
  }

  /**
   * The legacy {@code HBSErrors2Tag} rendering of a {@code MSGCODE:code,category} message:
   * "code description" from HBS_EDIT_ERR_MESSAGE_CODE, or the raw key when the code is missing.
   */
  public String editErrorMessage(String code, String category) {
    return first("""
        SELECT description FROM hbs_edit_err_message_code
         WHERE hbs_edit_err_message_code = :code AND hbs_edit_err_category_code = :category""",
        params().addValue("code", code).addValue("category", category))
        .map(r -> code + " " + r.str("description"))
        .orElse("MSGCODE:" + code + "," + category);
  }

  // ── Population / stratum (weight, sample) ────────────────────────────────

  /** {@code PresentationHelper.isValidPopStratYear} → {@code SrtsManager.findStratumByPrimaryKey}. */
  public boolean stratumExists(String population, String stratum, long year) {
    return count("""
        SELECT COUNT(*) FROM stratum
         WHERE population_number = :population AND stratum_number = :stratum AND sampling_year = :year""",
        params().addValue("population", population).addValue("stratum", stratum).addValue("year", year)) > 0;
  }

  /** {@code SmpHelper.findPopulationByPK(pop, year).getRatioTypeCode()} (BT14020). */
  public Optional<String> populationRatioType(String population, long year) {
    return first("SELECT ratio_type_code FROM population WHERE population_number = :population AND sampling_year = :year",
        params().addValue("population", population).addValue("year", year))
        .map(r -> r.str("ratio_type_code"));
  }

  // ── Piece estimates ──────────────────────────────────────────────────────

  /** {@code DacHelper.findPaperBatchByPK}: the HBS_PAPER_BATCH status of the batch slip, empty when not found. */
  public Optional<String> paperBatchStatus(long batchId) {
    return first("SELECT paper_batch_status_code FROM hbs_paper_batch WHERE batch_id = :id",
        params().addValue("id", batchId)).map(r -> r.str("paper_batch_status_code"));
  }

  /** {@code findCutBlocksByCriteria(null, null, null, mark, …).size()}. */
  public long cutBlockCount(String timberMark) {
    return count("SELECT COUNT(*) FROM cut_block WHERE timber_mark = :mark", params().addValue("mark", timberMark));
  }

  /** {@code DacHelper.isValidCutBlockId(cutBlockId, mark)}. */
  public boolean cutBlockExists(String cutBlockId, String timberMark) {
    return count("SELECT COUNT(*) FROM cut_block WHERE cut_block_id = :cutBlock AND timber_mark = :mark",
        params().addValue("cutBlock", cutBlockId).addValue("mark", timberMark)) > 0;
  }

  /**
   * BT15212 {@code SummaryScaleReturnQuery.getAreaBasedLicenseNo}: the default cruise-based scaler
   * licence of the mark's region (A999 coast, K999 southern interior, G000 northern interior,
   * '???' otherwise); "" when the mark has no MARK_BILLED_CLI row.
   */
  public String areaBasedLicence(String timberMark) {
    return first("""
        SELECT DISTINCT CASE region_code
                 WHEN 'RCO' THEN 'A999' WHEN 'RSI' THEN 'K999' WHEN 'RNI' THEN 'G000' WHEN 'RSK' THEN 'G000'
                 WHEN 'RNO' THEN 'G000' WHEN 'ROM' THEN 'G000' WHEN 'RTO' THEN 'K999' WHEN 'RCB' THEN 'K999'
                 WHEN 'RKB' THEN 'K999' WHEN 'RSC' THEN 'A999' WHEN 'RWC' THEN 'A999' ELSE '???' END AS cbb_scaler
          FROM mark_billed_cli
         WHERE timber_mark = :mark""", params().addValue("mark", timberMark))
        .map(r -> r.str("cbb_scaler")).orElse("");
  }

  /** BT15212 {@code getAreaBasedReturnNo}: MAX(RETURN_NUMBER) of the mark's piece versions (null when none). */
  public String maxPieceReturnNumber(String timberMark) {
    return jdbc.queryForList("SELECT MAX(return_number) FROM piece_scale_summary WHERE timber_mark = :mark",
        params().addValue("mark", timberMark), String.class).stream().findFirst().orElse(null);
  }

  /**
   * BT15212 {@code getAreaBasedScaleSiteNo}: the district's cruise scale site (site name contains
   * CRUISE and starts with the mark's district code); "ZZZ" when there is none.
   */
  public String areaBasedScaleSite(String timberMark) {
    return first("""
        SELECT ss.scale_site_id_nmbr
          FROM scale_site ss, org_unit ou, mark_billed_cli mbc
         WHERE UPPER(ss.site_name) LIKE '%CRUISE%'
           AND SUBSTR(ss.site_name, 1, 3) = ou.org_unit_code
           AND mbc.district_code = ou.org_unit_code
           AND mbc.timber_mark = :mark""", params().addValue("mark", timberMark))
        .map(r -> r.str("scale_site_id_nmbr")).orElse(EntryRules.WASTE_SCALE_SITE);
  }

  // ── Plumbing ─────────────────────────────────────────────────────────────

  protected List<Row> rows(String sql, MapSqlParameterSource params) {
    return jdbc.queryForList(sql, params).stream().map(Row::of).toList();
  }

  protected Optional<Row> first(String sql, MapSqlParameterSource params) {
    return rows(sql, params).stream().findFirst();
  }

  protected long count(String sql, MapSqlParameterSource params) {
    Long n = jdbc.queryForObject(sql, params, Long.class);
    return n == null ? 0 : n;
  }

  private static MapSqlParameterSource params() {
    return new MapSqlParameterSource();
  }

  private static String pad(String value, int length) {
    StringBuilder s = new StringBuilder(value.trim());
    while (s.length() < length) s.insert(0, '0');
    return s.toString();
  }
}
