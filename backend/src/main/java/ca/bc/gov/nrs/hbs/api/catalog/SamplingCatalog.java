package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stratum Planner (legacy tab "Stratum Planner", module {@code smp}, landing
 * page P850 "Stratum Advisor Menu").
 *
 * <p>Read side only. SELECTs ported from the REF-CURSOR procs HBS3R880
 * (sampling plans), HBS3R852 (populations), HBS3R860 (strata), the
 * {@code hbs/smp} entity queries (SAMPLE_PLAN, STRATUM_PLAN,
 * STRATUM_PLAN_COMPOSITION) and {@code hbs/vrc/PopulationStatisticsQuery} /
 * {@code StratumStatisticsQuery}.
 *
 * <p>Every plan write (add / edit / calculate / propose / approve / reject /
 * delete / copy / create-from-actuals, stratum plan and composition edits) is
 * Java business logic with a district-data-domain or plan-owner authorization
 * check, ported as workflow commands in {@code ca.bc.gov.nrs.hbs.api.sampling}
 * ({@code SamplingCommands}, ids {@code sampling.plan.*},
 * {@code sampling.stratumPlan.*}, {@code sampling.stratumComposition.*}). The
 * queries below return the {@code updateTimestamp} those commands lock on.
 */
@Component
public class SamplingCatalog implements QueryCatalog {

  /**
   * HBS3R880 / P854Action visibility: industry users (SP_UsersClientNo set)
   * see Active plans plus their own client's plans in any status; ministry
   * users see everything. (Anonymous / inactive users were forced to ACT; the
   * new app has no anonymous access.)
   */
  private static final String PLAN_VISIBLE =
      "(:hbsIsMinistry = 'Y' OR sp.sample_plan_status_code = 'ACT' OR sp.client_number = :hbsViewerClient)";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P854 Plan Status: SAMPLE_PLAN_STATUS_CODE excluding CLS.
        QueryDefinition.builder("codes.sampling.planStatuses")
            .legacy("P854")
            .capability(Capability.ANY_USER)
            .sql("""
                SELECT spsc.sample_plan_status_code AS code, spsc.description
                  FROM sample_plan_status_code spsc
                 WHERE spsc.sample_plan_status_code <> 'CLS'
                   AND SYSDATE BETWEEN spsc.effective_date AND spsc.expiry_date""")
            .orderBy("spsc.description")
            .maxRows(50)
            .build(),
        CodesCatalog.codeTable("codes.sampling.ratioTypes", "RATIO_TYPE_CODE"),
        // P886 "Product Schedule": ScaleGradeDAO.getProductScheduleList — the products a ratio type
        // allows (RATIO_TYPE_PRODUCT_XREF); listed for every ratio type, the stratum form names both.
        QueryDefinition.builder("codes.sampling.productSchedules")
            .legacy("P886")
            .capability(Capability.ANY_USER)
            .sql("""
                SELECT DISTINCT xref.scale_product_code AS code, prdcode.description
                  FROM ratio_type_product_xref xref
                  JOIN scale_product_code prdcode ON prdcode.scale_product_code = xref.scale_product_code
                 WHERE 1=1""")
            .filter(QueryFilter.upper("ratioTypeCode", "AND xref.ratio_type_code = :ratioTypeCode"))
            .orderBy("2")
            .maxRows(100)
            .build(),

        // ── P854/P880 Search For Sampling Plans / List of Sampling Plans (HBS3R880) ──
        QueryDefinition.builder("sampling.plans.search")
            .legacy("P854/P880")
            .capability(Capability.SAMPLING_VIEW)
            .sql(planSql() + "\n WHERE " + PLAN_VISIBLE)
            .filter(QueryFilter.eq("population", "AND sp.population_number = :population"))
            .filter(QueryFilter.number("samplingYear", "AND sp.sampling_year = :samplingYear"))
            .filter(QueryFilter.date("effectiveOnOrBefore", "AND sp.effective_date <= :effectiveOnOrBefore"))
            .filter(QueryFilter.date("expiringOnOrAfter", "AND sp.expiry_date >= :expiringOnOrAfter"))
            .filter(QueryFilter.upper("planStatus", "AND sp.sample_plan_status_code = :planStatus"))
            // P854 actionType=Submit preset for SMP admins (Home links): plans in effect today.
            .filter(QueryFilter.upper("currentYear",
                "AND (:currentYear <> 'Y' OR (sp.effective_date <= SYSDATE AND sp.expiry_date >= TRUNC(SYSDATE)))"))
            // "In Region/District": a district, or every district rolled up to a region.
            .filter(QueryFilter.number("orgUnitNo",
                "AND (sp.org_unit_no = :orgUnitNo OR sp.org_unit_no IN"
                    + " (SELECT ou2.org_unit_no FROM org_unit ou2 WHERE ou2.rollup_region_no = :orgUnitNo))"))
            .filter(QueryFilter.eq("clientNumber", "AND sp.client_number = :clientNumber"))
            .filter(QueryFilter.upper("clientLocnCode", "AND sp.client_locn_code = :clientLocnCode"))
            // Home "Sampling Plans" for HBS_SMP_ADMIN: districtScope=ASSOCIATED → HBS3R880
            // sp.org_unit_no IN (HBS_USER_DIST_DATA_DOMAIN of the user id).
            .filter(QueryFilter.upper("districtScope",
                "AND (:districtScope <> 'ASSOCIATED' OR sp.org_unit_no IN"
                    + " (SELECT dd.org_unit_no FROM hbs_user_dist_data_domain dd WHERE dd.user_id = :hbsUserId))"))
            .sort("populationNumber", "sp.population_number")
            .sort("samplingYear", "sp.sampling_year")
            .sort("district", "ou.org_unit_code")
            .sort("effectiveDate", "sp.effective_date")
            .sort("expiryDate", "sp.expiry_date")
            .sort("planStatus", "spsc.description")
            .orderBy("sp.sampling_year, sp.population_number")
            .build(),

        // ── P883 View Sampling Plan (header = smp/include/planlayout.jsp) ──
        QueryDefinition.builder("sampling.plans.detail")
            .legacy("P883")
            .capability(Capability.SAMPLING_VIEW)
            .sql(planSql() + "\n WHERE " + PLAN_VISIBLE)
            .filter(QueryFilter.required("planId", "AND sp.plan_id = :planId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // P881 Edit Sampling Plan (form load): the raw values the sampling.plan.calculate command takes.
        QueryDefinition.builder("sampling.plans.edit")
            .legacy("P881")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT sp.plan_id,
                       sp.population_number,
                       sp.sampling_year,
                       sp.plan_abreviation AS plan_abbreviation,
                       sp.plan_name,
                       sp.ratio_type_code,
                       TO_CHAR(sp.precision_limit, 'FM990.99') AS precision_limit,
                       sp.sample_floor,
                       sp.current_estimate_type AS estimate_type,
                       sp.current_constraint_type AS constraint_type,
                       TO_CHAR(sp.org_unit_no) AS org_unit_no,
                       sp.client_number,
                       sp.client_locn_code,
                       sp.effective_date,
                       sp.expiry_date,
                       sp.sample_plan_status_code,
                       sp.update_timestamp
                  FROM sample_plan sp
                 WHERE """ + PLAN_VISIBLE)
            .filter(QueryFilter.required("planId", "AND sp.plan_id = :planId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // P883 stratum plan grid (StratumPlanQuery by plan id; Segs = composition rows).
        QueryDefinition.builder("sampling.stratumPlans.list")
            .legacy("P883/P881")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT stp.stratum_plan_id,
                       stp.plan_id,
                       stp.stratum_number,
                       stp.stratum_name,
                       (SELECT COUNT(*) FROM stratum_plan_composition spc
                         WHERE spc.stratum_plan_id = stp.stratum_plan_id) AS segs,
                       stp.estimated_volume,
                       stp.estimated_loads,
                       stp.estimated_load_size,
                       stp.estimated_standard_deviation,
                       stp.optimum_sample_calc,
                       stp.optimum_precision_calc,
                       stp.optimum_frequency_calc,
                       stp.sample_count_constraint,
                       stp.precision_constraint,
                       stp.target_sample_calc,
                       stp.target_precision_calc,
                       stp.target_frequency_calc
                  FROM stratum_plan stp
                  JOIN sample_plan sp ON sp.plan_id = stp.plan_id
                 WHERE """ + PLAN_VISIBLE)
            .filter(QueryFilter.required("planId", "AND stp.plan_id = :planId", Type.NUMBER))
            .orderBy("stp.stratum_number")
            .maxRows(200)
            .build(),

        // ── P888 View Stratum Plan Details ──
        QueryDefinition.builder("sampling.stratumPlans.detail")
            .legacy("P888")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT stp.stratum_plan_id,
                       stp.plan_id,
                       sp.population_number,
                       sp.sampling_year,
                       sp.plan_abreviation AS plan_abbreviation,
                       sp.plan_name,
                       stp.stratum_number,
                       stp.stratum_name,
                       stp.effective_date,
                       stp.expiry_date,
                       stp.client_number,
                       stp.client_locn_code,
                       stp.stratum_desc,
                       stp.timber_source_desc,
                       stp.species_grade_profile_desc,
                       stp.scale_product_code,
                       spc.description AS product_schedule,
                       stp.grade_schedule_code,
                       gsc.expanded_result AS grade_schedule,
                       stp.estimated_volume,
                       stp.estimated_loads,
                       stp.estimated_load_size,
                       stp.estimated_standard_deviation,
                       stp.sample_count_constraint,
                       stp.precision_constraint,
                       sp.sample_plan_status_code,
                       sp.ratio_type_code,
                       sp.current_estimate_type AS estimate_type,
                       sp.current_constraint_type AS constraint_type,
                       stp.update_timestamp
                  FROM stratum_plan stp
                  JOIN sample_plan sp ON sp.plan_id = stp.plan_id
                  LEFT JOIN scale_product_code spc ON spc.scale_product_code = stp.scale_product_code
                  LEFT JOIN code_list_table gsc ON gsc.column_name = 'GRADE_SCHEDULE_CD' AND gsc.code_argument = stp.grade_schedule_code
                 WHERE """ + PLAN_VISIBLE)
            .filter(QueryFilter.required("stratumPlanId", "AND stp.stratum_plan_id = :stratumPlanId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // ── P884 View Default Ratios (StratumPlanCompositionQuery; Fraction = hbs3:calculateFraction) ──
        QueryDefinition.builder("sampling.stratumComposition.list")
            .legacy("P884/P885")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT c.stratum_plan_id,
                       c.scale_species_code,
                       c.scale_grade_code,
                       c.scale_product_code,
                       c.default_ratio,
                       ROUND(100 * c.default_ratio / NULLIF(SUM(c.default_ratio) OVER (), 0), 2) AS fraction,
                       c.scale_species_code AS original_scale_species_code,
                       c.scale_grade_code AS original_scale_grade_code,
                       NVL(c.update_timestamp, c.entry_timestamp) AS update_timestamp
                  FROM stratum_plan_composition c
                  JOIN stratum_plan stp ON stp.stratum_plan_id = c.stratum_plan_id
                  JOIN sample_plan sp ON sp.plan_id = stp.plan_id
                 WHERE """ + PLAN_VISIBLE)
            .filter(QueryFilter.required("stratumPlanId", "AND c.stratum_plan_id = :stratumPlanId", Type.NUMBER))
            // One row (P885 row edit): species + grade (a blank grade is stored as ' ').
            .filter(QueryFilter.upper("scaleSpeciesCode", "AND c.scale_species_code = :scaleSpeciesCode"))
            .filter(QueryFilter.eq("scaleGradeCode", "AND NVL(TRIM(c.scale_grade_code), ' ') = NVL(TRIM(:scaleGradeCode), ' ')"))
            .orderBy("c.scale_species_code, c.scale_grade_code")
            .maxRows(500)
            .build(),

        // ── P882 Sampling Plan Comparison (RetrieveStratumActualPlanDataTag) ──
        QueryDefinition.builder("sampling.planComparison.list")
            .legacy("P882")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT s.stratum_number,
                       s.stratum_name,
                       stp.target_sample_calc AS sample_loads_plan,
                       s.sample_loads AS sample_loads_actual,
                       stp.estimated_load_size AS load_size_plan,
                       s.sample_load_size AS load_size_actual,
                       stp.estimated_standard_deviation AS st_dev_plan,
                       s.sample_standard_deviation AS st_dev_actual,
                       stp.target_frequency_calc AS frequency_plan,
                       s.sampling_frequency AS frequency_actual,
                       stp.estimated_loads AS billed_loads_plan,
                       s.billed_loads AS billed_loads_actual,
                       stp.estimated_volume AS volume_plan,
                       s.billed_volume AS volume_actual,
                       stp.target_precision_calc AS precision_plan,
                       s.precision AS precision_actual
                  FROM sample_plan sp
                  JOIN stratum s ON s.population_number = sp.population_number
                                AND s.sampling_year = sp.sampling_year
                  JOIN stratum_plan stp ON stp.stratum_plan_id = s.stratum_plan_id
                 WHERE """ + PLAN_VISIBLE)
            .filter(QueryFilter.required("planId", "AND sp.plan_id = :planId", Type.NUMBER))
            .orderBy("s.stratum_number")
            .maxRows(200)
            .build(),

        // ── P851/P852 Search For Populations / List of Populations (HBS3R852) ──
        QueryDefinition.builder("sampling.populations.search")
            .legacy("P851/P852")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT pop.population_number,
                       pop.sampling_year,
                       pop.client_number,
                       pop.client_locn_code,
                       pop.stratum_count,
                       pop.sample_loads,
                       pop.sample_volume,
                       pop.sample_weight,
                       pop.ratio,
                       pop.billed_loads,
                       pop.billed_volume,
                       pop.billed_weight,
                       pop.precision
                  FROM population pop
                 WHERE 1=1""")
            .filter(QueryFilter.number("samplingYear", "AND pop.sampling_year = :samplingYear"))
            .filter(QueryFilter.eq("population", "AND pop.population_number = :population"))
            .filter(QueryFilter.eq("clientNumber", "AND pop.client_number = :clientNumber"))
            .filter(QueryFilter.upper("clientLocnCode", "AND pop.client_locn_code = :clientLocnCode"))
            .sort("samplingYear", "pop.sampling_year")
            .sort("populationNumber", "pop.population_number")
            .sort("clientNumber", "pop.client_number")
            .orderBy("pop.sampling_year, pop.population_number, pop.client_number")
            .maxRows(2000)
            .build(),

        // ── P853/P860 Search For Strata / List of Strata in a Population (HBS3R860) ──
        QueryDefinition.builder("sampling.strata.search")
            .legacy("P853/P860")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT stratum.population_number,
                       stratum.sampling_year,
                       stratum.stratum_plan_id,
                       stratum.client_number,
                       stratum.client_locn_code,
                       stratum.stratum_number,
                       stratum.stratum_name,
                       stratum.sample_loads,
                       stratum.sample_volume,
                       stratum.sample_weight,
                       stratum.ratio,
                       stratum.sample_load_size,
                       stratum.sample_standard_deviation,
                       stratum.sampling_frequency,
                       stratum.billed_loads,
                       stratum.billed_weight,
                       stratum.billed_volume,
                       stratum.precision
                  FROM client_location, stratum
                 WHERE stratum.client_number = client_location.client_number
                   AND stratum.client_locn_code = client_location.client_locn_code""")
            .filter(QueryFilter.required("samplingYear", "AND stratum.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("population", "AND stratum.population_number = :population", Type.STRING))
            .orderBy("stratum.stratum_number")
            .maxRows(500)
            .build(),

        // ── P861/P862/P863 Species / Grades / Segregations in Population ──
        // PopulationStatisticsQuery (species / grade / segregation stats).
        QueryDefinition.builder("sampling.populationStats.search")
            .legacy("P861/P862/P863")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT * FROM (
                  SELECT 'SPC' AS stat_type, pss.population_number, pss.sampling_year,
                         pss.scale_species_code, CAST(NULL AS VARCHAR2(2)) AS scale_product_code,
                         CAST(NULL AS VARCHAR2(1)) AS scale_grade_code,
                         pss.sample_loads, pss.sample_volume, pss.volume_fraction, pss.ratio,
                         ROUND((pss.ratio * p.billed_weight) / 1000, 3) AS billed_volume, pss.precision
                    FROM population_species_stats pss
                    JOIN population p ON p.population_number = pss.population_number
                                     AND p.sampling_year = pss.sampling_year
                  UNION ALL
                  SELECT 'GRD', pgs.population_number, pgs.sampling_year,
                         CAST(NULL AS VARCHAR2(2)), CAST(NULL AS VARCHAR2(2)), pgs.scale_grade_code,
                         pgs.sample_loads, pgs.sample_volume, pgs.volume_fraction, pgs.ratio,
                         ROUND((pgs.ratio * p.billed_weight) / 1000, 3), pgs.precision
                    FROM population_grade_stats pgs
                    JOIN population p ON p.population_number = pgs.population_number
                                     AND p.sampling_year = pgs.sampling_year
                  UNION ALL
                  SELECT 'SGR', pst.population_number, pst.sampling_year,
                         pst.scale_species_code, pst.scale_product_code, pst.scale_grade_code,
                         pst.sample_loads, pst.sample_volume, pst.volume_fraction, pst.ratio,
                         ROUND((pst.ratio * p.billed_weight) / 1000, 3), pst.precision
                    FROM population_segregation_stats pst
                    JOIN population p ON p.population_number = pst.population_number
                                     AND p.sampling_year = pst.sampling_year
                ) st
                 WHERE 1=1""")
            .filter(QueryFilter.required("type", "AND st.stat_type = :type", Type.UPPER))
            .filter(QueryFilter.required("population", "AND st.population_number = :population", Type.STRING))
            .filter(QueryFilter.required("samplingYear", "AND st.sampling_year = :samplingYear", Type.NUMBER))
            .orderBy("st.scale_species_code, st.scale_product_code, st.scale_grade_code")
            .maxRows(1000)
            .build(),

        // ── P866/P867/P868 Species / Grades / Segregations in Stratum (StratumStatisticsQuery) ──
        QueryDefinition.builder("sampling.stratumStats.search")
            .legacy("P866/P867/P868")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT * FROM (
                  SELECT 'SPC' AS stat_type, sps.population_number, sps.sampling_year, sps.stratum_number,
                         sps.scale_species_code, CAST(NULL AS VARCHAR2(2)) AS scale_product_code,
                         CAST(NULL AS VARCHAR2(1)) AS scale_grade_code,
                         sps.sample_loads, sps.sample_volume, sps.volume_fraction, sps.ratio,
                         ROUND((sps.ratio * s.billed_weight) / 1000, 3) AS billed_volume, sps.precision
                    FROM stratum_species_stats sps
                    JOIN stratum s ON s.population_number = sps.population_number
                                  AND s.sampling_year = sps.sampling_year
                                  AND s.stratum_number = sps.stratum_number
                  UNION ALL
                  SELECT 'GRD', sgs.population_number, sgs.sampling_year, sgs.stratum_number,
                         CAST(NULL AS VARCHAR2(2)), CAST(NULL AS VARCHAR2(2)), sgs.scale_grade_code,
                         sgs.sample_loads, sgs.sample_volume, sgs.volume_fraction, sgs.ratio,
                         ROUND((sgs.ratio * s.billed_weight) / 1000, 3), sgs.precision
                    FROM stratum_grade_stats sgs
                    JOIN stratum s ON s.population_number = sgs.population_number
                                  AND s.sampling_year = sgs.sampling_year
                                  AND s.stratum_number = sgs.stratum_number
                  UNION ALL
                  SELECT 'SGR', sss.population_number, sss.sampling_year, sss.stratum_number,
                         sss.scale_species_code, sss.scale_product_code, sss.scale_grade_code,
                         sss.sample_loads, sss.sample_volume, sss.volume_fraction, sss.ratio,
                         ROUND((sss.ratio * s.billed_weight) / 1000, 3), sss.precision
                    FROM stratum_segregation_stats sss
                    JOIN stratum s ON s.population_number = sss.population_number
                                  AND s.sampling_year = sss.sampling_year
                                  AND s.stratum_number = sss.stratum_number
                ) st
                 WHERE 1=1""")
            .filter(QueryFilter.required("type", "AND st.stat_type = :type", Type.UPPER))
            .filter(QueryFilter.required("population", "AND st.population_number = :population", Type.STRING))
            .filter(QueryFilter.required("samplingYear", "AND st.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("stratumNumber", "AND st.stratum_number = :stratumNumber", Type.STRING))
            .orderBy("st.scale_species_code, st.scale_product_code, st.scale_grade_code")
            .maxRows(1000)
            .build(),

        // ── P865 Samples in Stratum: header + Stratum Totals ──
        // $W/smp/P865.jsp (hbs3:retrieveStratum, retrievePopulations ratio type,
        // hbs3:forestStratum owner name — FOI-severed here like every client name).
        QueryDefinition.builder("sampling.stratum.detail")
            .legacy("P865/P870/P875")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT s.population_number, s.sampling_year, s.stratum_number, s.stratum_name,
                       p.ratio_type_code, rtc.description AS ratio_type,
                       DECODE(s.grade_schedule_code, 'C', 'Coast Grades', 'I', 'Interior Grades',
                              s.grade_schedule_code) AS grade_schedule,
                       s.scale_product_code, spc.description AS product_schedule,
                       s.client_number, s.client_locn_code,
                       CASE WHEN :hbsIsMinistry = 'Y' OR vc.client_type_code <> 'I'
                                 OR vc.client_number = :hbsViewerClient
                            THEN CASE WHEN vc.client_type_code = 'I'
                                      THEN vc.client_name || ',' || vc.legal_first_name || ' ' || vc.legal_middle_name
                                      ELSE vc.client_name END
                            ELSE 'Not Releasable' END AS client_name,
                       s.effective_date, s.expiry_date,
                       s.sample_loads, s.sample_volume, s.sample_weight, s.ratio, s.sample_load_size,
                       s.sample_standard_deviation, s.sampling_frequency, s.precision
                  FROM stratum s
                  JOIN population p ON p.population_number = s.population_number
                                   AND p.sampling_year = s.sampling_year
                  LEFT JOIN ratio_type_code rtc ON rtc.ratio_type_code = p.ratio_type_code
                  LEFT JOIN scale_product_code spc ON spc.scale_product_code = s.scale_product_code
                  LEFT JOIN v_client_public vc ON vc.client_number = s.client_number
                 WHERE 1=1""")
            .filter(QueryFilter.required("population", "AND s.population_number = :population", Type.STRING))
            .filter(QueryFilter.required("samplingYear", "AND s.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("stratumNumber", "AND s.stratum_number = :stratumNumber", Type.STRING))
            .maxRows(1)
            .build(),

        // ── P865 List of Samples in a Stratum ──
        // StatisticsManagerBean.findSampleCompilations: SampleCompilationDAO.SELECT_ISSUED_TXNS
        // (the issued sample versions, the latest transaction of each, or — for a sample tally
        // whose last event is a debit — every txn of the tally's summaries), each with its sample
        // (SummarySampleScaleReturnVersion: segregation count, total volume = Σ volume at 3 dp,
        // net weight), SampleCompilation.getRatio (StatisticsCalculator.calculateRatioPerTonne:
        // volume / (weight / 1000), 5 dp HALF_UP; 0 when the weight is 0) and SAMPLE_CATEGORIZATION.
        // Also the data of the P876 (ratio vs scale date) and P877 (volume vs weight) charts.
        QueryDefinition.builder("sampling.stratumSamples.list")
            .legacy("P865/P876/P877")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT s.document_control_number, s.version, t.trx_id,
                       s.population_number, s.sampling_year, s.stratum_number,
                       s.license_number, s.return_number, s.scale_site_id_nmbr AS scale_site, s.scale_date,
                       s.timber_mark, s.load_arrival_number,
                       NVL(seg.segregation_count, 0) AS segregation_count,
                       NVL(seg.total_volume, 0) AS total_sample_volume,
                       s.net_weight AS total_sample_weight,
                       CASE WHEN NVL(s.net_weight, 0) = 0 THEN 0
                            ELSE ROUND(NVL(seg.total_volume, 0) / (s.net_weight / 1000), 5) END AS ratio,
                       DECODE(cat.ratio_outlier_ind, 'Y', 'Y', NULL, NULL, 'N') AS ratio_outlier,
                       cat.predominant_species, cat.first_major_species, cat.second_major_species,
                       cat.predominant_grade, cat.first_major_grade, cat.second_major_grade,
                       'S' AS return_type
                  FROM hbs_scale_return sr
                  JOIN sample_scale_summary s ON s.document_control_number = sr.document_control_number
                  JOIN sample_scl_smry_txn t ON t.document_control_number = s.document_control_number
                                            AND t.version = s.version
                  LEFT JOIN (SELECT document_control_number, version, COUNT(*) AS segregation_count,
                                    ROUND(SUM(volume), 3) AS total_volume
                               FROM sample_segregation
                              GROUP BY document_control_number, version) seg
                         ON seg.document_control_number = s.document_control_number AND seg.version = s.version
                  LEFT JOIN sample_categorization cat ON cat.trx_id = t.trx_id
                 WHERE s.scl_rtn_version_state_code = 'ISS'
                   AND (t.event_sequence = (SELECT MAX(t2.event_sequence) FROM sample_scl_smry_txn t2
                                             WHERE t2.document_control_number = s.document_control_number
                                               AND t2.version = s.version
                                               AND NOT EXISTS (SELECT 1 FROM sample_tally_txn tx
                                                                WHERE tx.document_control_number = s.document_control_number))
                        OR t.document_control_number IN (
                             SELECT DISTINCT st1.document_control_number FROM sample_tally_txn st1
                              WHERE st1.detail_document_number =
                                      (SELECT st.detail_document_number FROM sample_tally_txn st
                                        WHERE st.document_control_number = s.document_control_number)
                                AND st1.detail_document_version =
                                      (SELECT MAX(st2.detail_document_version) FROM sample_tally_txn st2
                                        WHERE st2.detail_document_number = st1.detail_document_number)
                                AND 'DR' = (SELECT stt1.debit_credit_type FROM sample_tally_txn stt1
                                             WHERE stt1.detail_document_number = st1.detail_document_number
                                               AND stt1.event_sequence IN (
                                                     SELECT MAX(st3.event_sequence) FROM sample_tally_txn st3
                                                      WHERE st3.detail_document_number = st1.detail_document_number))))""")
            .filter(QueryFilter.required("population", "AND s.population_number = :population", Type.STRING))
            .filter(QueryFilter.required("samplingYear", "AND s.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("stratumNumber", "AND s.stratum_number = :stratumNumber", Type.STRING))
            .sort("documentControlNumber", "s.document_control_number")
            .sort("scaleDate", "s.scale_date")
            .orderBy("s.document_control_number")
            .maxRows(2000)
            .build(),

        // ── P870 Sample Categories in a Stratum ──
        // StatisticsManagerBean.find{Species,Grade}StratumCategorizations →
        // StratumStatisticsDAO.selectStratum{Species,Grade}Categories: Predominant, Twin Major,
        // Single Major and None categories (StratumStatisticsQuery.summarize*Categorization, VrcSql:
        // nsamples = ORG + RWC + RWN − CAN transactions, CAN volumes negative), fractions =
        // StatisticsCalculator.calculateVolumeFraction(volume, STRATUM.SAMPLE_VOLUME) (4 dp → percent).
        QueryDefinition.builder("sampling.stratumCategories.list")
            .legacy("P870")
            .capability(Capability.SAMPLING_VIEW)
            .sql(stratumCategoriesSql())
            .filter(QueryFilter.required("type", "AND c.cat_kind = :type", Type.UPPER))
            .filter(QueryFilter.required("population", "AND c.population_number = :population", Type.STRING))
            .filter(QueryFilter.required("samplingYear", "AND c.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("stratumNumber", "AND c.stratum_number = :stratumNumber", Type.STRING))
            .orderBy("c.cat_order, c.predominant, c.first_major, c.second_major")
            .maxRows(500)
            .build(),

        // ── P875 Chart of Sample Loads vs Scale Month (data as a table: no chart library) ──
        // RetrieveSampleLoadsTag: the months from the stratum's effective month through its
        // expiry month + 3, at least 12; per month DacHelper.findSampleLoadCountByDate = the
        // active, visible sample summary versions of the stratum scaled in that month.
        QueryDefinition.builder("sampling.stratumLoadsByMonth.list")
            .legacy("P875")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT m.scale_month, TO_CHAR(m.scale_month, 'Mon-YY', 'NLS_DATE_LANGUAGE=ENGLISH') AS month_label,
                       (SELECT COUNT(*) FROM sample_scale_summary ss
                         WHERE ss.population_number = m.population_number
                           AND ss.sampling_year = m.sampling_year
                           AND ss.stratum_number = m.stratum_number
                           AND ss.active_version_ind = 'Y' AND ss.visibility_ind = 'Y'
                           AND ss.scale_date >= m.scale_month
                           AND ss.scale_date < ADD_MONTHS(m.scale_month, 1)) AS sample_loads
                  FROM (SELECT s.population_number, s.sampling_year, s.stratum_number,
                               ADD_MONTHS(TRUNC(s.effective_date, 'MM'), n.i) AS scale_month
                          FROM stratum s
                          JOIN (SELECT LEVEL - 1 AS i FROM dual CONNECT BY LEVEL <= 400) n
                            ON n.i <= GREATEST(11, MONTHS_BETWEEN(ADD_MONTHS(TRUNC(s.expiry_date, 'MM'), 3),
                                                                  TRUNC(s.effective_date, 'MM')))
                         WHERE s.effective_date IS NOT NULL AND s.expiry_date IS NOT NULL) m
                 WHERE 1=1""")
            .filter(QueryFilter.required("population", "AND m.population_number = :population", Type.STRING))
            .filter(QueryFilter.required("samplingYear", "AND m.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("stratumNumber", "AND m.stratum_number = :stratumNumber", Type.STRING))
            .orderBy("m.scale_month")
            .maxRows(400)
            .build(),

        // ── P871/P872/P873 header: the sample of one transaction (P871.jsp sampleCompilation) ──
        QueryDefinition.builder("sampling.sample.detail")
            .legacy("P871/P872/P873")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT t.trx_id, s.document_control_number, s.version,
                       s.sampling_year, s.population_number, s.stratum_number, s.timber_mark,
                       s.license_number, s.return_number, s.scale_site_id_nmbr AS scale_site, s.scale_date,
                       s.load_arrival_number,
                       NVL(seg.total_volume, 0) AS total_sample_volume, s.net_weight AS total_sample_weight
                  FROM sample_scl_smry_txn t
                  JOIN sample_scale_summary s ON s.document_control_number = t.document_control_number
                                             AND s.version = t.version
                  LEFT JOIN (SELECT document_control_number, version, ROUND(SUM(volume), 3) AS total_volume
                               FROM sample_segregation
                              GROUP BY document_control_number, version) seg
                         ON seg.document_control_number = s.document_control_number AND seg.version = s.version
                 WHERE 1=1""")
            .filter(QueryFilter.required("trxId", "AND t.trx_id = :trxId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // ── P871/P872/P873 List of Species / Grades / Segregations in a Sample ──
        // SampleStatisticsQuery: SAMPLE_SPECIES_STATS / SAMPLE_GRADE_STATS / SAMPLE_SEGREGATION_STATS by TRX_ID.
        QueryDefinition.builder("sampling.sampleStats.list")
            .legacy("P871/P872/P873")
            .capability(Capability.SAMPLING_VIEW)
            .sql("""
                SELECT * FROM (
                  SELECT 'SPC' AS stat_type, trx_id, scale_species_code,
                         CAST(NULL AS VARCHAR2(2)) AS scale_product_code, CAST(NULL AS VARCHAR2(1)) AS scale_grade_code,
                         sample_volume, volume_fraction, ratio
                    FROM sample_species_stats
                  UNION ALL
                  SELECT 'GRD', trx_id, CAST(NULL AS VARCHAR2(2)), CAST(NULL AS VARCHAR2(2)), scale_grade_code,
                         sample_volume, volume_fraction, ratio
                    FROM sample_grade_stats
                  UNION ALL
                  SELECT 'SGR', trx_id, scale_species_code, scale_product_code, scale_grade_code,
                         sample_volume, volume_fraction, ratio
                    FROM sample_segregation_stats
                ) st
                 WHERE 1=1""")
            .filter(QueryFilter.required("type", "AND st.stat_type = :type", Type.UPPER))
            .filter(QueryFilter.required("trxId", "AND st.trx_id = :trxId", Type.NUMBER))
            .orderBy("st.scale_species_code, st.scale_grade_code")
            .maxRows(500)
            .build()
    );
  }

  /**
   * P870: one row per category group, by species (SPC) or grade (GRD), in
   * StratumStatisticsDAO order (Predominant, Twin Major, Single Major, None).
   */
  private static String stratumCategoriesSql() {
    String byKind = """
        SELECT '%1$s' AS cat_kind, 1 AS cat_order, 'P' AS category_type, %2$s, %3$s AS number_loads,
               seg.scale_%4$s_code AS predominant, SUM(%5$s) AS pred_volume,
               CAST(NULL AS VARCHAR2(2)) AS first_major, CAST(NULL AS NUMBER) AS first_volume,
               CAST(NULL AS VARCHAR2(2)) AS second_major, CAST(NULL AS NUMBER) AS second_volume
          %6$s
           AND cat.%4$s_category_type = 'P' AND cat.predominant_%4$s = seg.scale_%4$s_code
         GROUP BY %2$s, seg.scale_%4$s_code
        UNION ALL
        SELECT '%1$s', 2, 'T', %2$s, %3$s, NULL, NULL,
               cat.first_major_%4$s, SUM(DECODE(cat.first_major_%4$s, seg.scale_%4$s_code, %5$s, 0)),
               cat.second_major_%4$s, SUM(DECODE(cat.second_major_%4$s, seg.scale_%4$s_code, %5$s, 0))
          %6$s
           AND cat.%4$s_category_type = 'T'
         GROUP BY %2$s, cat.first_major_%4$s, cat.second_major_%4$s
        UNION ALL
        SELECT '%1$s', 3, 'S', %2$s, %3$s, NULL, NULL, seg.scale_%4$s_code, SUM(%5$s), NULL, NULL
          %6$s
           AND cat.%4$s_category_type = 'S' AND cat.first_major_%4$s = seg.scale_%4$s_code
         GROUP BY %2$s, seg.scale_%4$s_code
        UNION ALL
        SELECT '%1$s', 4, 'N', %2$s, %3$s, NULL, NULL, NULL, NULL, NULL, NULL
          %6$s
           AND cat.%4$s_category_type = 'N'
         GROUP BY %2$s""";
    String keys = "sample.population_number, sample.sampling_year, sample.stratum_number";
    String nsamples = """
        COUNT(DISTINCT DECODE(txn.hbs_txn_type_code, 'ORG', txn.trx_id, NULL))
          + COUNT(DISTINCT DECODE(txn.hbs_txn_type_code, 'RWC', txn.trx_id, NULL))
          + COUNT(DISTINCT DECODE(txn.hbs_txn_type_code, 'RWN', txn.trx_id, NULL))
          - COUNT(DISTINCT DECODE(txn.hbs_txn_type_code, 'CAN', txn.trx_id, NULL))""";
    String volume = "DECODE(txn.hbs_txn_type_code, 'CAN', -seg.volume, seg.volume)";
    String from = """
        FROM sample_scale_summary sample
          JOIN sample_segregation seg ON seg.document_control_number = sample.document_control_number
                                     AND seg.version = sample.version
          JOIN sample_scl_smry_txn txn ON txn.document_control_number = sample.document_control_number
                                      AND txn.version = sample.version
          JOIN sample_categorization cat ON cat.trx_id = txn.trx_id
         WHERE 1=1""";
    String species = byKind.formatted("SPC", keys, nsamples, "species", volume, from);
    String grades = byKind.formatted("GRD", keys, nsamples, "grade", volume, from);
    // StatisticsCalculator.calculateVolumeFraction(volume, STRATUM.SAMPLE_VOLUME): 4 dp HALF_UP as a
    // percent, 0 when the stratum has no volume.
    String frac = "CASE WHEN %1$s IS NULL THEN NULL WHEN NVL(st.sample_volume, 0) = 0 THEN 0"
        + " ELSE ROUND(%1$s / st.sample_volume, 4) * 100 END";
    return """
        SELECT c.cat_kind, c.population_number, c.sampling_year, c.stratum_number, c.cat_order,
               c.number_loads, c.category_type,
               DECODE(c.category_type, 'P', 'Predominant', 'T', 'Twin Major', 'S', 'Single Major', 'N', 'None')
                 AS category,
               c.predominant, %s AS predominant_fraction,
               c.first_major, %s AS first_major_fraction,
               c.second_major, %s AS second_major_fraction
          FROM (%s
                UNION ALL
                %s) c
          JOIN stratum st ON st.population_number = c.population_number
                         AND st.sampling_year = c.sampling_year AND st.stratum_number = c.stratum_number
         WHERE c.number_loads > 0""".formatted(frac.formatted("c.pred_volume"), frac.formatted("c.first_volume"),
        frac.formatted("c.second_volume"), species, grades);
  }

  /** SAMPLE_PLAN with the planlayout.jsp / P880 display columns. */
  private static String planSql() {
    return """
        SELECT sp.plan_id,
               sp.population_number,
               sp.sampling_year,
               sp.org_unit_no,
               ou.org_unit_code AS district,
               ou.org_unit_name AS district_name,
               sp.effective_date,
               sp.expiry_date,
               sp.client_number,
               sp.client_locn_code,
               sp.plan_abreviation AS plan_abbreviation,
               sp.plan_name,
               sp.sample_plan_status_code,
               spsc.description AS plan_status,
               sp.ratio_type_code,
               rtc.description AS ratio_type,
               CASE WHEN sp.ratio_type_code = 'D' THEN 'N/A' ELSE TO_CHAR(sp.precision_limit) END AS precision_limit,
               CASE WHEN sp.ratio_type_code = 'D' THEN 'N/A' ELSE TO_CHAR(sp.sample_floor) END AS sample_floor,
               DECODE(sp.current_estimate_type, 'VL', 'Enter Volume and Loads', 'VS', 'Enter Volume and Size',
                      'LS', 'Enter Loads and Size', sp.current_estimate_type) AS estimate_method,
               CASE WHEN sp.ratio_type_code = 'D' THEN 'N/A'
                    ELSE DECODE(sp.current_constraint_type, 'S', 'Constrain Samples', 'P', 'Constrain Precision',
                                sp.current_constraint_type) END AS constraint_method,
               sp.optimum_precision_calc,
               sp.target_precision_calc,
               sp.accepted_plan_ind,
               sp.precision_limit AS precision_limit_value,
               sp.sample_floor AS sample_floor_value,
               sp.current_estimate_type AS estimate_type,
               sp.current_constraint_type AS constraint_type,
               sp.update_timestamp
          FROM sample_plan sp
          LEFT JOIN org_unit ou ON ou.org_unit_no = sp.org_unit_no
          LEFT JOIN sample_plan_status_code spsc ON spsc.sample_plan_status_code = sp.sample_plan_status_code
          LEFT JOIN ratio_type_code rtc ON rtc.ratio_type_code = sp.ratio_type_code""";
  }
}
