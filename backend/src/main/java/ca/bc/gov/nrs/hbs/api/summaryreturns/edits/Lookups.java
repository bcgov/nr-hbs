package ca.bc.gov.nrs.hbs.api.summaryreturns.edits;

import ca.bc.gov.nrs.hbs.api.summaryreturns.Row;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The reference data the release edits read: the legacy readers
 * ({@code TimberMarkReader}, {@code ClientReader}, {@code ScaleSiteReader},
 * {@code QuantLicenseManager}, {@code CodeReader}, {@code SrtsManager}, {@code SamplePlanner},
 * {@code CutBlockManager}, {@code RateManagerBean} finders, {@code StatisticsManager},
 * {@code SummaryScaleReturnManagerBean} BT15212 finders) as plain SQL. Every value is bound,
 * no schema prefix. Read-only.
 *
 * <p>Every method is overridable so the tests can answer from memory.
 */
@Component
public class Lookups {

  private final NamedParameterJdbcTemplate jdbc;

  public Lookups(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // ── Value objects ────────────────────────────────────────────────────────

  /** {@code legacy/ftas/TimberMark} (the columns the edits use). */
  public record TimberMark(String timberMark, String fileTypeCode, String cruiseBasedInd, String paymentMethodCode,
      Integer forestDistrict, String quotaTypeCode, String clientNumber, String clientLocnCode) {

    /** {@code isPrivateMark}: file types B08, B09, B13–B16. */
    public boolean isPrivateMark() {
      return List.of("B08", "B09", "B13", "B14", "B15", "B16").contains(trim(fileTypeCode));
    }

    /** {@code isCashSale}: payment method C. */
    public boolean isCashSale() {
      return paymentMethodCode != null && "C".equals(paymentMethodCode.trim());
    }

    /**
     * {@code isCruiseBased} (BT5884): cruise based and not an oil-and-gas (A11) mark. Legacy threw a
     * NullPointerException on a null CRUISE_BASED_IND; here null is "not cruise based".
     */
    public boolean isCruiseBased() {
      return !"A11".equals(fileTypeCode) && "Y".equals(cruiseBasedInd);
    }

    /** {@code isAreaBased} (BT11514): an A11 mark flagged cruise based. */
    public boolean isAreaBased() {
      return "A11".equals(fileTypeCode) && "Y".equals(cruiseBasedInd);
    }

    /** {@code isBeachComb} (BT10941): file type B17. */
    public boolean isBeachComb() {
      return "B17".equals(fileTypeCode);
    }

    /**
     * {@code isRenewable}: file types A01–A05, A28, B01 with quota type A or B. Legacy threw on a null
     * quota type; here null is "not renewable".
     */
    public boolean isRenewable() {
      return List.of("A01", "A02", "A03", "A04", "A05", "A28", "B01").contains(trim(fileTypeCode))
          && List.of("A", "B").contains(trim(quotaTypeCode));
    }

    private static String trim(String s) {
      return s == null ? "" : s.trim();
    }
  }

  /** {@code legacy/cli/Client} (from V_CLIENT_PUBLIC). */
  public record Client(String clientNumber, String clientName, String legalFirstName, String legalMiddleName,
      String clientStatusCode, String clientTypeCode) {

    /** {@code isDeactivated}: status DAC. */
    public boolean isDeactivated() {
      return "DAC".equals(clientStatusCode);
    }

    /** {@code Constants.ClientType.INDIVIDUAL}. */
    public boolean isIndividual() {
      return "I".equals(clientTypeCode);
    }
  }

  /** {@code legacy/cli/ClientLocation} with its client. */
  public record ClientLocation(Client client, String clientNumber, String clientLocnCode, String city,
      String province, String postalCode, String locnExpiredInd) {

    /** {@code isExpired}: LOCN_EXPIRED_IND = Y. */
    public boolean isExpired() {
      return "Y".equals(locnExpiredInd);
    }
  }

  /** {@code legacy/scs/ScaleSite}. */
  public record ScaleSite(String scaleSiteIdNmbr, String ownerCliNumber, String ownerCliLocnCd, String siteName,
      Long orgUnitNo) {

    /** {@code isDedicatedCruiseBasedSite}: characters 5–21 of the site name are "Cruise Based Site". */
    public boolean isDedicatedCruiseBasedSite() {
      if (siteName == null || siteName.isEmpty()) return false;
      try {
        return "Cruise Based Site".equalsIgnoreCase(siteName.substring(4, 21));
      } catch (IndexOutOfBoundsException ex) {
        return false;
      }
    }
  }

  /** {@code smp/Stratum}: owner, effective window, grade constraint (GRADE_SCHEDULE_CODE). */
  public record Stratum(String clientNumber, String clientLocnCode, LocalDateTime effectiveDate,
      LocalDateTime expiryDate, String gradeConstraint) {
  }

  /** {@code smp/Population}: owner and ratio type. */
  public record Population(String clientNumber, String clientLocnCode, String ratioTypeCode) {
  }

  /** {@code plu/AppraisalWorksheet} (the columns the edits use). */
  public record Worksheet(String timberMark, LocalDateTime appEffectiveDate, String appraisalMthdCd,
      String conifStandRateEligibilityCode, String decidStandRateEligibilityCode) {
  }

  /** Effective / expiry dates of a code row (SPG cross reference, scale grade). */
  public record DateWindow(LocalDateTime effectiveDate, LocalDateTime expiryDate) {
  }

  /** An active volume estimate ({@code findActiveVolumeEstimatesByCriteria}). */
  public record VolumeEstimate(long documentControlNumber, long version, LocalDateTime entryTimestamp) {
  }

  /** The species/product/grade of one latest stratum ratio ({@code RatioHistory.getStatistic()}). */
  public record RatioStatistic(String species, String product, String grade) {
  }

  /** One row of {@code CRUISE_BASED_SP_GRD_RATIO} for the return's confirmed worksheet. */
  public record CruiseRatio(String species, String grade, BigDecimal volumePerHectare) {
  }

  /**
   * {@code findAreaBasedAppWorksheetVals}: DIFF_M3_HA, MAX_M3_HA, DIFF_VOL of the mark's
   * confirmed worksheets.
   */
  public record WorksheetVals(double diffM3Ha, double maxM3Ha, double diffVol) {
  }

  // ── Helpers ──────────────────────────────────────────────────────────────

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

  static LocalDateTime dateTime(Row row, String column) {
    Timestamp ts = row.date(column);
    return ts == null ? null : ts.toLocalDateTime();
  }

  private static double dbl(Row row, String column) {
    BigDecimal n = row.num(column);
    return n == null ? 0d : n.doubleValue();
  }

  /** A date-only bind, as the legacy {@code convertDateToOracleDateOnlyString} literal. */
  private static java.sql.Date day(LocalDate date) {
    return java.sql.Date.valueOf(date);
  }

  /** {@code ClientEntityBean.padClientNumber}: zero-pad to 8. */
  static String padClientNumber(String clientNumber) {
    StringBuilder s = new StringBuilder(clientNumber);
    while (s.length() < 8) s.insert(0, '0');
    return s.toString();
  }

  /** {@code ClientEntityBean.padLocationCode}: zero-pad to 2. */
  static String padLocationCode(String locationCode) {
    StringBuilder s = new StringBuilder(locationCode);
    while (s.length() < 2) s.insert(0, '0');
    return s.toString();
  }

  // ── Timber mark (TimberMarkQuery.SELECT_LOAD) ────────────────────────────

  /**
   * {@code TimberMarkReader.getTimberMarkByMark}: TIMBER_MARK joined to MARK_BILLED_CLI (the
   * entity's PK finder required both) and, outer, HARVEST_SALE for the payment method.
   */
  public Optional<TimberMark> timberMark(String mark) {
    return first("""
        SELECT t.timber_mark, t.forest_district, t.cruise_based_ind, t.quota_type_code,
               b.file_type_code, b.client_number, b.client_locn_code, s.payment_method_cd
          FROM timber_mark t
          JOIN mark_billed_cli b ON b.timber_mark = t.timber_mark
          LEFT JOIN harvest_sale s ON s.forest_file_id = b.forest_file_id
         WHERE t.timber_mark = :mark""", params().addValue("mark", mark))
        .map(r -> new TimberMark(r.str("timber_mark"), r.str("file_type_code"), r.str("cruise_based_ind"),
            r.str("payment_method_cd"), r.lng("forest_district") == null ? null : r.lng("forest_district").intValue(),
            r.str("quota_type_code"), r.str("client_number"), r.str("client_locn_code")));
  }

  // ── Clients (ClientReaderBean; FOREST_CLIENT is read through V_CLIENT_PUBLIC) ──

  private static Client client(Row r) {
    return new Client(r.str("client_number"), r.str("client_name"), r.str("legal_first_name"),
        r.str("legal_middle_name"), r.str("client_status_code"), r.str("client_type_code"));
  }

  /**
   * {@code findClientByNumber}: the client of the first location (by location code); a client
   * without a location is not found, as the legacy join returned no row.
   */
  public Optional<Client> client(String clientNumber) {
    return first("""
        SELECT RTRIM(c.client_number) AS client_number, c.client_name, c.legal_first_name, c.legal_middle_name,
               c.client_status_code, c.client_type_code
          FROM v_client_public c
          JOIN client_location l ON l.client_number = c.client_number
         WHERE c.client_number = :clientNumber
         ORDER BY l.client_locn_code""", params().addValue("clientNumber", padClientNumber(clientNumber)))
        .map(Lookups::client);
  }

  /** {@code findClientLocation(clientNumber, clientLocationCode)}. */
  public Optional<ClientLocation> clientLocation(String clientNumber, String clientLocnCode) {
    return first("""
        SELECT RTRIM(c.client_number) AS client_number, c.client_name, c.legal_first_name, c.legal_middle_name,
               c.client_status_code, c.client_type_code, RTRIM(l.client_locn_code) AS client_locn_code,
               l.city, l.province, l.postal_code, l.locn_expired_ind
          FROM v_client_public c
          JOIN client_location l ON l.client_number = c.client_number
         WHERE c.client_number = :clientNumber AND l.client_locn_code = :clientLocnCode""",
        params().addValue("clientNumber", padClientNumber(clientNumber))
            .addValue("clientLocnCode", padLocationCode(clientLocnCode)))
        .map(r -> new ClientLocation(client(r), r.str("client_number"), r.str("client_locn_code"), r.str("city"),
            r.str("province"), r.str("postal_code"), r.str("locn_expired_ind")));
  }

  // ── Scale site, licence, codes ───────────────────────────────────────────

  /** {@code ScaleSiteReader.getScaleSite} ({@code ScaleSiteQuery.selectByPK}). */
  public Optional<ScaleSite> scaleSite(String scaleSiteIdNmbr) {
    return first("""
        SELECT scale_site_id_nmbr, owner_cli_number, owner_cli_locn_cd, site_name, org_unit_no
          FROM scale_site
         WHERE scale_site_id_nmbr = :site""", params().addValue("site", scaleSiteIdNmbr))
        .map(r -> new ScaleSite(r.str("scale_site_id_nmbr"), r.str("owner_cli_number"), r.str("owner_cli_locn_cd"),
            r.str("site_name"), r.lng("org_unit_no")));
  }

  /**
   * {@code QuantLicenseManager.findQuantLicenseByPrimaryKey} / {@code isValidLicense}
   * (QUANT_LICENSE is a view over the scaler licences). A null type matches nothing.
   */
  public boolean quantLicenseExists(String licenseNumber, String quantLicTypeCd) {
    return count("""
        SELECT COUNT(*) FROM quant_license
         WHERE license_number = :licence AND quant_lic_type_cd = :type""",
        params().addValue("licence", licenseNumber).addValue("type", quantLicTypeCd)) > 0;
  }

  /** {@code CodeReader.getCode(code, "OTHER_DESCRIPTION_TYPE_CODE")} ({@code LocalCodeBean}: any row, dated or not). */
  public boolean otherDescriptionTypeExists(String code) {
    return count("SELECT COUNT(*) FROM other_description_type_code WHERE other_description_type_code = :code",
        params().addValue("code", code)) > 0;
  }

  /** {@code CodeReader.findSpeciesProductGradeByPrimaryKey} ({@code SpeciesProductGradeQuery.selectByPK}). */
  public Optional<DateWindow> speciesProductGrade(String species, String product, String grade) {
    return first("""
        SELECT effective_date, expiry_date FROM species_product_grade_xref
         WHERE scale_product_code = :product AND scale_grade_code = :grade AND scale_species_code = :species""",
        params().addValue("product", product).addValue("grade", grade).addValue("species", species))
        .map(r -> new DateWindow(dateTime(r, "effective_date"), dateTime(r, "expiry_date")));
  }

  /**
   * {@code CodeReader.findCostalorInteriorScaleGradeByPrimaryKey} ({@code ScaleGradeDAO.getScaleGradeInfo}):
   * the grade in COAST_SCALE_GRADE_CODE (schedule C) or INTERIOR_SCALE_GRADE_CODE (schedule I).
   */
  public Optional<DateWindow> scaleGrade(String grade, String schedule) {
    String sql;
    if (schedule == null) {
      return Optional.empty();
    } else if (EditCodes.COASTAL_SCHEDULE.equalsIgnoreCase(schedule.trim())) {
      sql = "SELECT effective_date, expiry_date FROM coast_scale_grade_code WHERE coast_scale_grade_code = :grade";
    } else if (EditCodes.INTERIOR_SCHEDULE.equalsIgnoreCase(schedule.trim())) {
      sql = "SELECT effective_date, expiry_date FROM interior_scale_grade_code WHERE interior_scale_grade_code = :grade";
    } else {
      return Optional.empty();
    }
    List<Row> found = rows(sql, params().addValue("grade", grade));
    // the DAO kept the last row it read
    return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1))
        .map(r -> new DateWindow(dateTime(r, "effective_date"), dateTime(r, "expiry_date")));
  }

  /**
   * {@code PolicyDates.getInteriorMPBDate}: start of the day interior grade 7 became effective
   * (legacy read it once at class load; empty when the grade is missing, as legacy left it null).
   */
  public Optional<LocalDateTime> interiorMpbPolicyDate() {
    return scaleGrade("7", EditCodes.INTERIOR_SCHEDULE)
        .map(DateWindow::effectiveDate)
        .map(d -> d.toLocalDate().atStartOfDay());
  }

  /**
   * {@code RateManagerBean.getGradeScheduleType} ({@code AppraisalWorksheetQuery.selectGradeScheduleTypeCode}):
   * the appraisal method of the confirmed worksheet covering the scale date, else the schedule of
   * the mark's forest region. May be null (no worksheet and no region match).
   */
  public String gradeScheduleType(String timberMark, LocalDate scaleDate) {
    return first("""
        SELECT NVL(code,
                   (SELECT DECODE(MAX(o.org_unit_code),
                                  'RNI', 'I', 'RSI', 'I', 'RCO', 'C',
                                  'RSK', 'I', 'RNO', 'I', 'ROM', 'I', 'RTO', 'I', 'RCB', 'I', 'RKB', 'I',
                                  'RSC', 'C', 'RWC', 'C')
                      FROM org_unit o, mark_billed_cli m
                     WHERE m.timber_mark = :mark AND m.forest_region = o.org_unit_no)) AS code
          FROM (SELECT MAX(a.appraisal_mthd_cd) code
                  FROM app_worksheet a, timber_mark t
                 WHERE t.timber_mark = :mark
                   AND a.timber_mark = t.timber_mark
                   AND :scaleDate BETWEEN a.app_effective_date AND a.expiry_date
                   AND a.appraisal_sts_st = 'CNF')""",
        params().addValue("mark", timberMark).addValue("scaleDate", day(scaleDate)))
        .map(r -> r.str("code")).orElse(null);
  }

  // ── Sampling (SrtsManager, SamplePlanner, StatisticsManager, profiles) ───

  /** {@code SrtsManager.findStratumByPrimaryKey} ({@code StratumQuery.selectByPK}). */
  public Optional<Stratum> stratum(String populationNumber, String stratumNumber, Long samplingYear) {
    return first("""
        SELECT client_number, client_locn_code, effective_date, expiry_date, grade_schedule_code
          FROM stratum
         WHERE population_number = :population AND sampling_year = :year AND stratum_number = :stratum""",
        params().addValue("population", populationNumber).addValue("year", samplingYear)
            .addValue("stratum", stratumNumber))
        .map(r -> new Stratum(r.str("client_number"), r.str("client_locn_code"), dateTime(r, "effective_date"),
            dateTime(r, "expiry_date"), r.str("grade_schedule_code")));
  }

  /** {@code SamplePlanner.findPopulationByPrimaryKey} / {@code XmlStatementHelper.lookupPopulationOwner}. */
  public Optional<Population> population(String populationNumber, Long samplingYear) {
    return first("""
        SELECT client_number, client_locn_code, ratio_type_code
          FROM population
         WHERE population_number = :population AND sampling_year = :year""",
        params().addValue("population", populationNumber).addValue("year", samplingYear))
        .map(r -> new Population(r.str("client_number"), r.str("client_locn_code"), r.str("ratio_type_code")));
  }

  /** {@code ProfileManager.findFinalBillableDropoutMarkByPrimaryKey} ({@code FinalBillableDropoutMarkQuery.selectPKByPK}). */
  public boolean finalBillableDropoutMark(String populationNumber, Long samplingYear, String timberMark) {
    return count("""
        SELECT COUNT(*) FROM final_billable_dropout_mark
         WHERE population_number = :population AND sampling_year = :year AND timber_mark = :mark""",
        params().addValue("population", populationNumber).addValue("year", samplingYear)
            .addValue("mark", timberMark)) > 0;
  }

  /** {@code StatisticsManager.findLatestRatios(stratum)} ({@code RatioHistoryQuery.findLatestRatios}). */
  public List<RatioStatistic> latestRatios(String populationNumber, String stratumNumber, Long samplingYear) {
    return rows("""
        SELECT scale_species_code, scale_product_code, scale_grade_code
          FROM ratio_history
         WHERE population_number = :population AND stratum_number = :stratum AND sampling_year = :year
           AND ratio_timestamp = (SELECT MAX(ratio_timestamp) FROM ratio_history
                                   WHERE population_number = :population AND stratum_number = :stratum
                                     AND sampling_year = :year)
         ORDER BY ratio_hist_id""",
        params().addValue("population", populationNumber).addValue("stratum", stratumNumber)
            .addValue("year", samplingYear))
        .stream()
        .map(r -> new RatioStatistic(r.str("scale_species_code"), r.str("scale_product_code"), r.str("scale_grade_code")))
        .toList();
  }

  // ── Cut blocks (CutBlockManagerBean, BT 5705) ────────────────────────────

  /** {@code findCutBlockByPrimaryKey(cutBlockId, timberMark)}. */
  public boolean cutBlockExists(String cutBlockId, String timberMark) {
    return count("SELECT COUNT(*) FROM cut_block WHERE cut_block_id = :cutBlock AND timber_mark = :mark",
        params().addValue("cutBlock", cutBlockId).addValue("mark", timberMark)) > 0;
  }

  /** {@code findCutBlockByPrimaryKeyN(timberMark)}: the mark has any cut block. */
  public boolean markHasCutBlock(String timberMark) {
    return count("SELECT COUNT(*) FROM cut_block WHERE timber_mark = :mark", params().addValue("mark", timberMark)) > 0;
  }

  // ── Summary returns ──────────────────────────────────────────────────────

  /**
   * {@code findActiveVolumeEstimatesByCriteria} ({@code SummaryPieceScaleReturnVersionQuery
   * .selectActiveVolumeEstimatesByCriteria}): active, not discarded, same type / scale day /
   * licence / return number.
   */
  public List<VolumeEstimate> activeVolumeEstimates(String pssmryType, LocalDate scaleDate, String licenseNumber,
      String returnNumber) {
    return rows("""
        SELECT document_control_number, version, entry_timestamp
          FROM piece_scale_summary
         WHERE active_version_ind = 'Y'
           AND pssmry_type = :type
           AND TRUNC(scale_date) = :scaleDate
           AND license_number = :licence
           AND return_number = :returnNumber
           AND scl_rtn_version_state_code != 'DEL'""",
        params().addValue("type", pssmryType).addValue("scaleDate", day(scaleDate))
            .addValue("licence", licenseNumber).addValue("returnNumber", returnNumber))
        .stream()
        .map(r -> new VolumeEstimate(r.lng("document_control_number"), r.lng("version"), dateTime(r, "entry_timestamp")))
        .toList();
  }

  // ── Appraisals and rates (RateManagerBean) ───────────────────────────────

  /**
   * {@code findWorksheetsByMarkAndDateAndStatus(mark, startDayToEndDay(scaleDate), CNF)}
   * ({@code AppraisalWorksheetQuery.selectByTimberMarkAndDateRange}): confirmed worksheets covering
   * the scale day. Legacy had no ORDER BY; ordered by effective date here for a stable first row.
   */
  public List<Worksheet> confirmedWorksheets(String timberMark, LocalDate scaleDate) {
    return rows("""
        SELECT timber_mark, app_effective_date, appraisal_mthd_cd, conif_stand_rate_elig_code, decid_stand_rate_elig_code
          FROM app_worksheet
         WHERE timber_mark = :mark
           AND :scaleDate <= expiry_date AND :scaleDate >= app_effective_date
           AND appraisal_sts_st = 'CNF'
         ORDER BY app_effective_date""",
        params().addValue("mark", timberMark).addValue("scaleDate", day(scaleDate)))
        .stream()
        .map(r -> new Worksheet(r.str("timber_mark"), dateTime(r, "app_effective_date"), r.str("appraisal_mthd_cd"),
            r.str("conif_stand_rate_elig_code"), r.str("decid_stand_rate_elig_code")))
        .toList();
  }

  /**
   * {@code RateManagerBean.hasSplitRate} ({@code getSplitRateDate}): a confirmed worksheet expires
   * inside the interval ({@code selectRateEndThatSplitsInterval}), or a stumpage rate of a
   * confirmed worksheet starts inside it ({@code selectRateStartThatSplitsInterval}). Day precision.
   */
  public boolean hasSplitRate(String timberMark, LocalDate start, LocalDate end) {
    MapSqlParameterSource p = params().addValue("mark", timberMark).addValue("startDate", day(start))
        .addValue("endDate", day(end));
    if (count("""
        SELECT COUNT(*) FROM app_worksheet
         WHERE timber_mark = :mark AND appraisal_sts_st = 'CNF'
           AND expiry_date >= :startDate AND expiry_date < :endDate""", p) > 0) {
      return true;
    }
    return count("""
        SELECT COUNT(*) FROM app_stmpg_rate stmp, app_worksheet wkst
         WHERE wkst.timber_mark = stmp.timber_mark
           AND wkst.timber_mark = :mark
           AND wkst.appraisal_sts_st = 'CNF'
           AND wkst.app_effective_date = stmp.app_effective_date
           AND stmp.stmpg_rte_efctv_dt >= (:startDate + 1)
           AND stmp.stmpg_rte_efctv_dt < (:endDate + 1)""", p) > 0;
  }

  /**
   * {@code RateManagerBean.hasWasteRate(cutBlock, mark, range)}
   * ({@code findWasteResidueRatesCountByCriteria} → {@code WasteResidueRateQuery.selectByCriteria}):
   * a waste rate of the mark / cut block overlapping the range. A cut block "ALL" is not filtered,
   * as the legacy query builder skipped it.
   */
  public boolean hasWasteRate(String cutBlockId, String timberMark, LocalDateTime start, LocalDateTime end) {
    boolean filterCutBlock = !"ALL".equals(cutBlockId.toUpperCase(Locale.ROOT));
    return count("SELECT COUNT(*) FROM waste_residue_rate WHERE timber_mark = :mark"
            + (filterCutBlock ? " AND cut_block_id = :cutBlock" : "")
            + " AND effective_date <= :endDate AND expiry_date >= :startDate",
        params().addValue("mark", timberMark).addValue("cutBlock", cutBlockId)
            .addValue("startDate", Timestamp.valueOf(start)).addValue("endDate", Timestamp.valueOf(end))) > 0;
  }

  // ── BT15212 area based (SummaryScaleReturnQuery, by DCN and version) ─────
  //
  // These read the PIECE_SCALE_SUMMARY row as stored, as the legacy finders did.

  private static MapSqlParameterSource doc(long dcn, long version) {
    return params().addValue("dcn", dcn).addValue("version", version);
  }

  /**
   * {@code findAreaBasedAppWorksheetExpDate} → {@code SummaryScaleReturnHelper}: true (valid) when
   * the scale date is the last day of its month or the expiry date of the confirmed worksheet
   * covering it ({@code getAreaBasedAppWorksheetDates}); false when neither, or no return row.
   */
  public boolean areaBasedHarvestDateValid(long dcn, long version) {
    return first("""
        SELECT LAST_DAY(pss.scale_date) - pss.scale_date AS sd_is_end_of_month,
               NVL(apw.expiry_date - pss.scale_date, -1) AS sd_is_end_of_app_worksheet
          FROM piece_scale_summary pss
          LEFT OUTER JOIN app_worksheet apw
            ON apw.timber_mark = pss.timber_mark
           AND apw.app_effective_date <= pss.scale_date
           AND apw.expiry_date >= pss.scale_date
           AND apw.appraisal_sts_st = 'CNF'
         WHERE pss.document_control_number = :dcn AND pss.version = :version""", doc(dcn, version))
        // ResultSet.getInt truncated the day fractions; a NULL read as 0
        .map(r -> intOf(r, "sd_is_end_of_month") == 0 || intOf(r, "sd_is_end_of_app_worksheet") == 0)
        .orElse(false);
  }

  private static int intOf(Row r, String column) {
    BigDecimal n = r.num(column);
    return n == null ? 0 : n.intValue();
  }

  /** {@code findAreaBasedTMCruiseProfile} (CBP_M3_HA): 0 when there is no cruise profile. */
  public double areaBasedCruiseProfile(long dcn, long version) {
    return first("""
        SELECT SUM(cbsgr.volume_per_hectare) AS cbp_m3_ha
          FROM app_worksheet apw, cruise_based_sp_grd_ratio cbsgr, piece_scale_summary pss
         WHERE apw.timber_mark = pss.timber_mark
           AND apw.appraisal_sts_st = 'CNF'
           AND apw.app_effective_date <= pss.scale_date
           AND apw.expiry_date >= pss.scale_date
           AND apw.app_effective_date = cbsgr.app_effective_date
           AND apw.timber_mark = cbsgr.timber_mark
           AND pss.document_control_number = :dcn AND pss.version = :version
         GROUP BY cbsgr.app_effective_date, cbsgr.timber_mark""", doc(dcn, version))
        .map(r -> dbl(r, "cbp_m3_ha")).orElse(0d);
  }

  /** {@code findAreaBasedAppWorksheetVals} ({@code getAreaBasedAppWorksheetVals}); empty when no row. */
  public Optional<WorksheetVals> areaBasedWorksheetVals(long dcn, long version) {
    return first("""
        SELECT cbchk.diff_vol, cbchk.max_m3_ha, cbchk.diff_m3_ha
          FROM piece_scale_summary pss,
               (SELECT apw.timber_mark,
                       MAX(net_cruise_volume + int_deciduous_vol) - MIN(net_cruise_volume + int_deciduous_vol) AS diff_vol,
                       MAX(ROUND((net_cruise_volume + int_deciduous_vol) / ttl_merchntbl_area, 3)) AS max_m3_ha,
                       MAX(ROUND((net_cruise_volume + int_deciduous_vol) / ttl_merchntbl_area, 3))
                         - MIN(ROUND((net_cruise_volume + int_deciduous_vol) / ttl_merchntbl_area, 3)) AS diff_m3_ha
                  FROM app_worksheet apw, mark_billed_cli mbc
                 WHERE mbc.timber_mark = apw.timber_mark
                   AND apw.appraisal_sts_st = 'CNF'
                   AND mbc.cruise_based_ind = 'Y'
                   AND NVL(apw.ttl_merchntbl_area, 0) <> 0
                   AND apw.app_effective_date >= TO_DATE('20010630', 'YYYYMMDD')
                 GROUP BY apw.timber_mark) cbchk
         WHERE cbchk.timber_mark = pss.timber_mark
           AND pss.document_control_number = :dcn AND pss.version = :version""", doc(dcn, version))
        .map(r -> new WorksheetVals(dbl(r, "diff_m3_ha"), dbl(r, "max_m3_ha"), dbl(r, "diff_vol")));
  }

  /** {@code findAreaBasedActiveAppWrkshtForCrtScaleRtn}: VOL_HA of the worksheet covering the scale date, else 0. */
  public double areaBasedActiveWorksheetVolumePerHectare(long dcn, long version) {
    return first("""
        SELECT CASE WHEN NVL(apw.ttl_merchntbl_area, 0) = 0 THEN 0
                    ELSE ROUND((apw.net_cruise_volume + apw.int_deciduous_vol) / apw.ttl_merchntbl_area, 3)
               END AS vol_ha
          FROM app_worksheet apw, piece_scale_summary pss
         WHERE apw.timber_mark = pss.timber_mark
           AND apw.app_effective_date <= pss.scale_date
           AND apw.expiry_date >= pss.scale_date
           AND apw.appraisal_sts_st = 'CNF'
           AND pss.document_control_number = :dcn AND pss.version = :version""", doc(dcn, version))
        .map(r -> dbl(r, "vol_ha")).orElse(0d);
  }

  /**
   * {@code findAreaBasedScaleDateForDateAndMark}: returns of the same mark and scale date that are
   * active and not INC/CAN/DEL, plus this version.
   */
  public int areaBasedSameScaleDateCount(long dcn, long version) {
    return first("""
        SELECT COUNT(*) AS doc_count
          FROM piece_scale_summary pss,
               (SELECT timber_mark, scale_date FROM piece_scale_summary
                 WHERE document_control_number = :dcn AND version = :version) pss_filter
         WHERE pss.timber_mark = pss_filter.timber_mark
           AND pss.scale_date = pss_filter.scale_date
           AND ((pss.active_version_ind = 'Y' AND pss.scl_rtn_version_state_code NOT IN ('INC', 'CAN', 'DEL'))
                OR (pss.document_control_number = :dcn AND pss.version = :version))
         GROUP BY pss.timber_mark, pss.scale_date""", doc(dcn, version))
        .map(r -> intOf(r, "doc_count")).orElse(0);
  }

  /** {@code findAreaBasedTotalAreaCut}: area cut to date on the mark (active returns plus this version). */
  public double areaBasedTotalAreaCut(long dcn, long version) {
    return first("""
        SELECT SUM(NVL(pss.area_cut, 0)) AS total_area
          FROM piece_scale_summary pss,
               (SELECT timber_mark FROM piece_scale_summary
                 WHERE document_control_number = :dcn AND version = :version) pss_filter
         WHERE pss.timber_mark = pss_filter.timber_mark
           AND ((pss.active_version_ind = 'Y' AND pss.scl_rtn_version_state_code NOT IN ('INC', 'CAN', 'DEL'))
                OR (pss.document_control_number = :dcn AND pss.version = :version))
         GROUP BY pss.timber_mark""", doc(dcn, version))
        .map(r -> dbl(r, "total_area")).orElse(0d);
  }

  /** {@code findAreaBasedTtlMerchntblArea}: TTL_MERCHNTBL_AREA of the mark's latest confirmed worksheet. */
  public double areaBasedTotalMerchantableArea(long dcn, long version) {
    return first("""
        SELECT apw.ttl_merchntbl_area
          FROM app_worksheet apw,
               (SELECT apw.timber_mark, MAX(apw.app_effective_date) AS last_app_effective_date
                  FROM app_worksheet apw, piece_scale_summary pss
                 WHERE apw.timber_mark = pss.timber_mark
                   AND apw.appraisal_sts_st = 'CNF'
                   AND pss.document_control_number = :dcn AND pss.version = :version
                 GROUP BY apw.timber_mark) last_apw
         WHERE apw.timber_mark = last_apw.timber_mark
           AND apw.app_effective_date = last_apw.last_app_effective_date""", doc(dcn, version))
        .map(r -> dbl(r, "ttl_merchntbl_area")).orElse(0d);
  }

  /**
   * {@code getAreaBasedCruiseBasedSpGradeRatios}: the species/grade volumes per hectare of the
   * confirmed worksheet covering the return's scale date.
   */
  public List<CruiseRatio> areaBasedCruiseRatios(long dcn, long version) {
    return rows("""
        SELECT cbsgr.scale_species_code, cbsgr.scale_grade_code, cbsgr.volume_per_hectare
          FROM app_worksheet apw, cruise_based_sp_grd_ratio cbsgr, piece_scale_summary pss
         WHERE apw.timber_mark = pss.timber_mark
           AND apw.appraisal_sts_st = 'CNF'
           AND apw.app_effective_date <= pss.scale_date
           AND apw.expiry_date >= pss.scale_date
           AND apw.app_effective_date = cbsgr.app_effective_date
           AND apw.timber_mark = cbsgr.timber_mark
           AND pss.document_control_number = :dcn AND pss.version = :version""", doc(dcn, version))
        .stream()
        .map(r -> new CruiseRatio(r.str("scale_species_code"), r.str("scale_grade_code"), r.num("volume_per_hectare")))
        .toList();
  }
}
