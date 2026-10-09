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
 * Java business logic in {@code SmpHelper} / {@code P891Action} with a
 * district-data-domain or plan-owner authorization check, so none is exposed as
 * a plain command; see docs/areas/rating-and-sampling.md.
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
            // districtScope=ASSOCIATED (HBS_USER_DIST_DATA_DOMAIN by USER_ID) needs a
            // viewer user-id bind the query framework does not provide yet — see the area doc.
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
                       stp.estimated_load_size
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
                       ROUND(100 * c.default_ratio / NULLIF(SUM(c.default_ratio) OVER (), 0), 2) AS fraction
                  FROM stratum_plan_composition c
                  JOIN stratum_plan stp ON stp.stratum_plan_id = c.stratum_plan_id
                  JOIN sample_plan sp ON sp.plan_id = stp.plan_id
                 WHERE """ + PLAN_VISIBLE)
            .filter(QueryFilter.required("stratumPlanId", "AND c.stratum_plan_id = :stratumPlanId", Type.NUMBER))
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
            .build()
    );
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
               sp.accepted_plan_ind
          FROM sample_plan sp
          LEFT JOIN org_unit ou ON ou.org_unit_no = sp.org_unit_no
          LEFT JOIN sample_plan_status_code spsc ON spsc.sample_plan_status_code = sp.sample_plan_status_code
          LEFT JOIN ratio_type_code rtc ON rtc.ratio_type_code = sp.ratio_type_code""";
  }
}
