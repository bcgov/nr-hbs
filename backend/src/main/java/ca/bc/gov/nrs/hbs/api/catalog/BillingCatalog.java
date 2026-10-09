package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Billing area (legacy Billing tab, P800 /ivs/*): process scheduling (force
 * summarization / ratio computation dates), weight-scale final billing,
 * non-renewable tenures and field-scaled decks.
 *
 * <p>SQL is ported from the legacy {@code ivs.config} EJBs and the
 * {@code HBS3R829 / HBS3R832 / HBS3R834 / HBS3R841_A / HBS3R841_B /
 * HBS3R842 / HBS3R843 / HBS3R844} REF-CURSOR procs (which only built dynamic
 * SQL by string concatenation; here every value is a bound parameter).
 * See docs/areas/billing-and-admin.md for the screen-by-screen mapping.
 */
@Component
public class BillingCatalog implements QueryCatalog {

  /**
   * Legacy {@code HBSConfiguration.getDefaultRatio()} — deploy token
   * {@code cfg_p_default_ratio=1.24856} (ear/configure.properties) passed by
   * P840Action to HBS3R841_A/842/843. A compile-time constant, never user
   * input. TODO: move to application configuration.
   */
  static final String DEFAULT_RATIO = "1.24856";

  /** Version states the field-scale procs count (HBS3R841_A..844). */
  private static final String FS_STATES = "('AWP','RDY','ERR','DSF','HLD','LCK','ISS','CAN','NTI')";

  /** Timber mark + field scale deck predicate; {@code '*'} = all decks (legacy blank). */
  private static String deck(String alias) {
    return " AND " + alias + ".timber_mark = :timberMark"
        + " AND " + alias + ".field_scale_deck_id IS NOT NULL"
        + " AND (:fieldScaleDeckId = '*' OR " + alias + ".field_scale_deck_id = :fieldScaleDeckId)";
  }

  /** Check-scale replacement rule shared by sample/log tallies. */
  private static final String CHECK_SCALE_RULE =
      " AND ((a.scale_event_type_code = 'CS' AND a.check_replaces_original_ind = 'Y')"
          + " OR (a.scale_event_type_code <> 'CS' AND (a.check_replaces_original_ind = 'N'"
          + " OR a.check_replaces_original_ind IS NULL)))";

  /** Ratio joins used by the weigh-slip / sample-tally procs (stratum → ratio statement). */
  private static final String RATIO_FROM =
      ", stratum b, hbs_ratio_statement r"
          + ", (SELECT statement_number, SUM(ratio) ratio FROM ratio_history GROUP BY statement_number) e"
          + ", (SELECT stratum_plan_id, SUM(default_ratio) ratio FROM stratum_plan_composition"
          + " GROUP BY stratum_plan_id) f";

  private static final String RATIO_WHERE =
      " AND a.population_number = b.population_number(+) AND a.stratum_number = b.stratum_number(+)"
          + " AND a.sampling_year = b.sampling_year(+)"
          + " AND b.ratio_statement_number = r.statement_number(+)"
          + " AND b.ratio_statement_number = e.statement_number(+)"
          + " AND r.stratum_plan_id = f.stratum_plan_id(+)";

  private static final String RATIO_EXPR =
      "NVL2(a.population_number, NVL2(b.ratio_statement_number,"
          + " CASE WHEN r.ratio_type_code = 'S' THEN e.ratio WHEN r.ratio_type_code = 'D' THEN f.ratio"
          + " WHEN r.ratio_type_code = 'Z' THEN " + DEFAULT_RATIO + " END, " + DEFAULT_RATIO + "), "
          + DEFAULT_RATIO + ")";

  private static final String RATIO_SOURCE =
      "NVL2(a.population_number, DECODE(r.ratio_type_code, 'Z', 'Average',"
          + " NVL2(b.ratio_statement_number, b.ratio_statement_number, 'Average')), 'Average')";

  private static final String PSY =
      "NVL2(a.population_number, a.population_number || '-' || a.stratum_number || '-' || a.sampling_year, ' Unknown')";

  /** Status-count columns of P842 (no field-scale flag). */
  private static String sums842(String m) {
    return "NVL(SUM(CASE WHEN v.st = 'HLD' THEN " + m + " ELSE 0 END), 0) AS held"
        + ", NVL(SUM(CASE WHEN v.st = 'RDY' THEN " + m + " ELSE 0 END), 0) AS ready"
        + ", NVL(SUM(CASE WHEN v.st = 'ERR' THEN " + m + " ELSE 0 END), 0) AS error"
        + ", NVL(SUM(CASE WHEN v.st IN ('DSF','AWP','NTI') THEN " + m + " ELSE 0 END), 0) AS other"
        + ", NVL(SUM(CASE WHEN v.st = 'LCK' THEN " + m + " ELSE 0 END), 0) AS locked"
        + ", NVL(SUM(CASE WHEN v.st = 'ISS' THEN " + m + " ELSE 0 END), 0) AS issued"
        + ", NVL(SUM(CASE WHEN v.st IN ('HLD','RDY','ERR','DSF','AWP','NTI','LCK','ISS') THEN " + m
        + " ELSE 0 END), 0) AS total"
        + ", NVL(SUM(CASE WHEN v.st = 'CAN' THEN " + m + " ELSE 0 END), 0) AS cancelled";
  }

  /** Status-count columns of P843 / P844 (field-scale-flagged documents counted under FS Flag). */
  private static String sumsFs(String m) {
    return "NVL(SUM(CASE WHEN v.fs = 'Y' AND v.st IN ('HLD','RDY','ERR','NTI') THEN " + m + " ELSE 0 END), 0) AS fs_flag"
        + ", NVL(SUM(CASE WHEN v.fs = 'N' AND v.st = 'HLD' THEN " + m + " ELSE 0 END), 0) AS held"
        + ", NVL(SUM(CASE WHEN v.fs = 'N' AND v.st = 'RDY' THEN " + m + " ELSE 0 END), 0) AS ready"
        + ", NVL(SUM(CASE WHEN v.fs = 'N' AND v.st = 'ERR' THEN " + m + " ELSE 0 END), 0) AS error"
        + ", NVL(SUM(CASE WHEN v.st IN ('DSF','AWP') OR (v.fs = 'N' AND v.st = 'NTI') THEN " + m
        + " ELSE 0 END), 0) AS other"
        + ", NVL(SUM(CASE WHEN v.st = 'LCK' THEN " + m + " ELSE 0 END), 0) AS locked"
        + ", NVL(SUM(CASE WHEN v.st = 'ISS' THEN " + m + " ELSE 0 END), 0) AS issued"
        + ", NVL(SUM(CASE WHEN v.st IN ('HLD','RDY','ERR','DSF','AWP','NTI','LCK','ISS') THEN " + m
        + " ELSE 0 END), 0) AS total"
        + ", NVL(SUM(CASE WHEN v.st = 'CAN' THEN " + m + " ELSE 0 END), 0) AS cancelled";
  }

  /** The two binds every field-scale query needs; fragments are no-ops on the outer view. */
  private static QueryDefinition.Builder deckFilters(QueryDefinition.Builder b) {
    return b.filter(QueryFilter.required("timberMark", "AND :timberMark IS NOT NULL", Type.UPPER))
        .filter(QueryFilter.required("fieldScaleDeckId", "AND :fieldScaleDeckId IS NOT NULL", Type.UPPER));
  }

  // ---------------------------------------------------------------------------------------------
  // HBS3R829 — final bill status summary (P829 by population, P830 by stratum)
  // ---------------------------------------------------------------------------------------------

  /** Eligible PSYMarks: renewable (file types/quota A,B) marks + elected dropout marks. */
  private static String eligibleX(boolean singlePop) {
    String pop = singlePop ? " AND ycb.population_number = :population" : "";
    return "SELECT 'ELIGIBLE' AS eligibility_type, ycb.population_number, ycb.sampling_year,"
        + " ycb.stratum_number, ycb.timber_mark"
        + " FROM ytd_cyclic_billing ycb,"
        + " (SELECT m.timber_mark FROM mark_billed_cli m, timber_mark t"
        + "   WHERE m.file_type_code IN ('A01','A02','A03','A04','A05','A28','B01')"
        + "     AND t.timber_mark = m.timber_mark AND t.quota_type_code IN ('A','B')) m"
        + " WHERE m.timber_mark = ycb.timber_mark AND ycb.sampling_year = :samplingYear" + pop
        + " UNION"
        + " SELECT 'DROPOUT', ycb.population_number, ycb.sampling_year, ycb.stratum_number, ycb.timber_mark"
        + " FROM ytd_cyclic_billing ycb, final_billable_dropout_mark d"
        + " WHERE ycb.sampling_year = :samplingYear" + pop
        + "   AND d.population_number = ycb.population_number AND d.sampling_year = ycb.sampling_year"
        + "   AND d.timber_mark = ycb.timber_mark";
  }

  /** Final-bill summary returns by status (active FB weight-scale summaries). */
  private static String countsY(boolean byStratum) {
    String strat = byStratum ? ", wss.stratum_number" : "";
    String pop = byStratum ? " AND wss.population_number = :population" : "";
    return "SELECT wss.population_number, wss.sampling_year" + strat
        + ", MAX(TRUNC(wss.entry_timestamp)) AS last_gen_date"
        + ", COUNT(wss.scl_rtn_version_state_code) AS total"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'ISS', 1, 0)) AS issued"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'RDY', 1, 0)) AS ready"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'HLD', 1, 0)) AS held"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'ERR', 1, 0)) AS error"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'CAN', 1, 0)) AS cancelled"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'DEL', 1, 0)) AS deleted"
        + ", SUM(DECODE(wss.scl_rtn_version_state_code, 'ISS', 0, 'RDY', 0, 'HLD', 0, 'ERR', 0, 'CAN', 0,"
        + " 'DEL', 0, 1)) AS other"
        + " FROM weight_scale_summary wss"
        + " WHERE wss.sampling_year = :samplingYear" + pop
        + "   AND wss.active_version_ind = 'Y' AND wss.scl_rtn_category_code = 'FB'"
        + " GROUP BY wss.population_number, wss.sampling_year" + strat;
  }

  private static final String Y_COLS =
      "y.last_gen_date, y.total, y.issued, y.ready, y.held, y.error, y.cancelled, y.deleted, y.other";

  private static final String ACTIVE_PLAN =
      " AND EXISTS (SELECT 1 FROM sample_plan s WHERE s.population_number = p.population_number"
          + " AND s.sampling_year = p.sampling_year AND s.sample_plan_status_code = 'ACT')";

  /** Outer projection; '*' when the population/stratum may be final-billed now. */
  private static String finalBillOuter(String inner, String stratumCol) {
    return "SELECT * FROM ("
        + " SELECT e.population_number, " + stratumCol + " e.sampling_year, e.expiry_date AS pop_expiry,"
        + " e.eligible_psym, e.last_gen_date, e.total, e.issued, e.ready, e.held, e.error, e.cancelled,"
        + " e.deleted, e.other,"
        + " CASE WHEN e.eligible_psym > NVL(e.total, 0) AND e.eligible_psym > 0 AND e.expiry_date <= SYSDATE"
        + " THEN '*' END AS eligible_psym_flag,"
        + " e.org_unit_no, e.client_number, e.client_locn_code"
        + " FROM (" + inner + ") e"
        + ") fb WHERE 1=1";
  }

  private static final String FINAL_BILL_BY_POPULATION = finalBillOuter(
      "SELECT p.population_number, p.sampling_year, p.expiry_date, p.org_unit_no, p.client_number,"
          + " p.client_locn_code, SUM(DECODE(x.eligibility_type, NULL, 0, 1)) AS eligible_psym, " + Y_COLS
          + " FROM population p, (" + eligibleX(false) + ") x, (" + countsY(false) + ") y"
          + " WHERE p.sampling_year = :samplingYear AND NVL(p.ratio_type_code, 'X') = 'S'"
          + "   AND x.population_number(+) = p.population_number AND x.sampling_year(+) = p.sampling_year"
          + "   AND y.population_number(+) = p.population_number AND y.sampling_year(+) = p.sampling_year"
          + ACTIVE_PLAN
          + " GROUP BY p.population_number, p.sampling_year, p.expiry_date, p.org_unit_no, p.client_number,"
          + " p.client_locn_code, " + Y_COLS,
      "");

  private static final String FINAL_BILL_BY_STRATUM = finalBillOuter(
      "SELECT p.population_number, p.stratum_number, p.sampling_year, p.expiry_date, p.org_unit_no,"
          + " p.client_number, p.client_locn_code, SUM(DECODE(x.eligibility_type, NULL, 0, 1)) AS eligible_psym, "
          + Y_COLS
          + " FROM (SELECT pp.population_number, s.stratum_number, pp.sampling_year, s.expiry_date,"
          + "        pp.org_unit_no, pp.client_number, pp.client_locn_code"
          + "   FROM population pp, stratum s"
          + "  WHERE pp.sampling_year = :samplingYear AND pp.population_number = :population"
          + "    AND NVL(pp.ratio_type_code, 'X') = 'S'"
          + "    AND s.population_number = pp.population_number AND s.sampling_year = pp.sampling_year) p,"
          + " (" + eligibleX(true) + ") x, (" + countsY(true) + ") y"
          + " WHERE x.population_number(+) = p.population_number AND x.stratum_number(+) = p.stratum_number"
          + "   AND x.sampling_year(+) = p.sampling_year"
          + "   AND y.population_number(+) = p.population_number AND y.stratum_number(+) = p.stratum_number"
          + "   AND y.sampling_year(+) = p.sampling_year"
          + ACTIVE_PLAN
          + " GROUP BY p.population_number, p.stratum_number, p.sampling_year, p.expiry_date, p.org_unit_no,"
          + " p.client_number, p.client_locn_code, " + Y_COLS,
      "e.stratum_number,");

  private static QueryDefinition.Builder finalBillSuppressFilters(QueryDefinition.Builder b) {
    return b
        .filter(QueryFilter.date("expiringBefore", "AND fb.pop_expiry < :expiringBefore"))
        .filter(QueryFilter.eq("suppressCompleted",
            "AND (:suppressCompleted <> 'Y' OR fb.eligible_psym > NVL(fb.total, 0))"))
        .filter(QueryFilter.eq("suppressInProgress",
            "AND (:suppressInProgress <> 'Y' OR fb.pop_expiry < SYSDATE)"))
        .filter(QueryFilter.eq("suppressNoEligible",
            "AND (:suppressNoEligible <> 'Y' OR fb.eligible_psym > 0)"));
  }

  // ---------------------------------------------------------------------------------------------
  // HBS3R832 — unissued weight-scale final bills (P832)
  // ---------------------------------------------------------------------------------------------

  private static final String UNISSUED_COMMON_COLS =
      "wss.document_control_number, wss.version, wss.scale_date, srvsc.description AS status,"
          + " wss.scl_rtn_version_state_code AS version_state_code, wss.scale_site_id_nmbr AS scale_site,"
          + " wss.population_number, wss.stratum_number, wss.sampling_year, wss.timber_mark,"
          + " wss.client_number_paid_by, wss.client_locn_code_paid_by,"
          + " wss.client_number_copied_to, wss.client_locn_code_copied_to,"
          + " mbc.client_number AS mark_client_number, mbc.client_locn_code AS mark_client_locn_code,"
          + " pop.client_number AS pop_client_number, pop.client_locn_code AS pop_client_locn_code";

  private static final String UNISSUED_GROUP =
      " GROUP BY wss.document_control_number, wss.version, wss.scale_date, srvsc.description,"
          + " wss.scl_rtn_version_state_code, wss.scale_site_id_nmbr, wss.population_number,"
          + " wss.stratum_number, wss.sampling_year, wss.timber_mark, wss.client_number_paid_by,"
          + " wss.client_locn_code_paid_by, wss.client_number_copied_to, wss.client_locn_code_copied_to,"
          + " mbc.client_number, mbc.client_locn_code, pop.client_number, pop.client_locn_code";

  /** Latest ratio per PSY joined to YTD cyclic weight per mark (HBS3R832 "rh"). */
  private static final String UNISSUED_RH =
      "(SELECT rh1.*, cb.timber_mark, cb.ytd_weight"
          + " FROM (SELECT * FROM ratio_history WHERE ratio_timestamp IN (SELECT MAX(ratio_timestamp)"
          + "        FROM ratio_history GROUP BY population_number, sampling_year, stratum_number)) rh1,"
          + "      (SELECT population_number, sampling_year, stratum_number, timber_mark,"
          + "              SUM(ytd_weight) AS ytd_weight"
          + "         FROM ytd_cyclic_billing GROUP BY population_number, sampling_year, stratum_number,"
          + "              timber_mark) cb"
          + " WHERE cb.population_number = rh1.population_number AND cb.sampling_year = rh1.sampling_year"
          + "   AND cb.stratum_number = rh1.stratum_number) rh";

  private static final String UNISSUED_FROM =
      " FROM weight_scale_summary wss, weight_scl_smry_txn wsst, ytd_cyclic_billing_seg ycbs,"
          + " mark_billed_cli mbc, (SELECT * FROM population WHERE NVL(ratio_type_code, 'X') = 'S') pop, "
          + UNISSUED_RH + ", scl_rtn_version_state_code srvsc"
          + " WHERE wss.scl_rtn_category_code = 'FB'"
          + "   AND wss.scl_rtn_version_state_code IN ('RDY','ERR','HLD')"
          + "   AND wss.timber_mark = mbc.timber_mark(+)"
          + "   AND wss.population_number = pop.population_number AND wss.sampling_year = pop.sampling_year"
          + "   AND wss.document_control_number = wsst.document_control_number(+)"
          + "   AND wss.version = wsst.version(+)"
          + "   AND wss.scl_rtn_version_state_code = srvsc.scl_rtn_version_state_code(+)";

  private static final String UNISSUED_SQL =
      "SELECT * FROM ("
          + " SELECT u.document_control_number, u.version, u.scale_date, u.status, u.version_state_code,"
          + " u.scale_site, u.population_number, u.stratum_number, u.sampling_year,"
          + " u.population_number || '/' || u.stratum_number || '/' || u.sampling_year AS pop_str_year,"
          + " u.timber_mark, u.client_number_paid_by || '-' || u.client_locn_code_paid_by AS invoice_to,"
          + " u.client_number_copied_to || NVL2(u.client_number_copied_to, '-', '') || u.client_locn_code_copied_to"
          + " AS copy_to,"
          + " u.mark_client_number, u.mark_client_locn_code, u.pop_client_number, u.pop_client_locn_code,"
          + " SUM(u.ytd_volume) AS estimated_volume, SUM(u.ytd_value) AS estimated_value"
          + " FROM ("
          // Branch 1: segments with a latest ratio (volume/value still to bill)
          + "SELECT " + UNISSUED_COMMON_COLS
          + ", SUM(DECODE(ROUND(ycbs.ytd_volume, 3), NULL, ROUND((rh.ytd_weight / 1000 * rh.ratio), 3),"
          + " ROUND((rh.ytd_weight / 1000 * rh.ratio) - ycbs.ytd_volume, 3))) AS ytd_volume"
          + ", SUM(DECODE(ROUND(ycbs.ytd_value, 2), NULL, ROUND((hbs_rates.stumpage_rate(wss.timber_mark,"
          + " wss.field_scale_deck_id, rh.scale_species_code, ' ', rh.scale_grade_code, pop.expiry_date, '', '',"
          + " 'Y') * NVL(rh.ratio, 0) * rh.ytd_weight / 1000), 2), ROUND((ycbs.ytd_weight_x_rate / 1000"
          + " * NVL(rh.ratio, 0)) - ycbs.ytd_value, 2))) AS ytd_value"
          + UNISSUED_FROM
          + "   AND wss.population_number = rh.population_number AND wss.sampling_year = rh.sampling_year"
          + "   AND wss.stratum_number = rh.stratum_number AND wss.timber_mark = rh.timber_mark"
          + "   AND ycbs.scale_species_code(+) = rh.scale_species_code"
          + "   AND ycbs.scale_product_code(+) = rh.scale_product_code"
          + "   AND ycbs.scale_grade_code(+) = rh.scale_grade_code"
          + "   AND ycbs.population_number(+) = rh.population_number"
          + "   AND ycbs.stratum_number(+) = rh.stratum_number"
          + "   AND ycbs.sampling_year(+) = rh.sampling_year"
          + "   AND ycbs.timber_mark(+) = rh.timber_mark"
          + UNISSUED_GROUP
          + " UNION "
          // Branch 2: billed segments with no latest-ratio species row (reverse what was billed)
          + "SELECT " + UNISSUED_COMMON_COLS
          + ", SUM(DECODE(ROUND(rh.sample_volume, 3), NULL, -ycbs.ytd_volume, 0)) AS ytd_volume"
          + ", SUM(DECODE(ROUND(rh.sample_volume, 3), NULL, -ycbs.ytd_value, 0)) AS ytd_value"
          + UNISSUED_FROM
          + "   AND wss.population_number = ycbs.population_number AND wss.sampling_year = ycbs.sampling_year"
          + "   AND wss.stratum_number = ycbs.stratum_number AND wss.timber_mark = ycbs.timber_mark"
          + "   AND ycbs.scale_species_code = rh.scale_species_code(+)"
          + "   AND ycbs.scale_product_code = rh.scale_product_code(+)"
          + "   AND ycbs.scale_grade_code = rh.scale_grade_code(+)"
          + "   AND ycbs.population_number = rh.population_number(+)"
          + "   AND ycbs.stratum_number = rh.stratum_number(+)"
          + "   AND ycbs.sampling_year = rh.sampling_year(+)"
          + "   AND ycbs.timber_mark = rh.timber_mark(+)"
          + UNISSUED_GROUP
          + ") u"
          + " GROUP BY u.document_control_number, u.version, u.scale_date, u.status, u.version_state_code,"
          + " u.scale_site, u.population_number, u.stratum_number, u.sampling_year, u.timber_mark,"
          + " u.client_number_paid_by, u.client_locn_code_paid_by, u.client_number_copied_to,"
          + " u.client_locn_code_copied_to, u.mark_client_number, u.mark_client_locn_code,"
          + " u.pop_client_number, u.pop_client_locn_code"
          + ") fb WHERE 1=1";

  // ---------------------------------------------------------------------------------------------
  // HBS3R841_A / _B / 842 / 843 / 844 — field scaled decks (P841-P844)
  // ---------------------------------------------------------------------------------------------

  private static final String OVERVIEW_SQL =
      "SELECT * FROM ("
          + "SELECT 1 AS seq, 'Sample Tallies' AS doc_type, SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1))"
          + " AS detail_returns, NULL AS summary_returns,"
          + " ROUND(DECODE(SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)), 0, 0,"
          + " SUM(DECODE(a.scl_rtn_version_state_code, 'ISS', 1, 0)) * 100"
          + " / SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)))) AS issued_fraction,"
          + " MIN(CASE WHEN a.scl_rtn_version_state_code = 'CAN' THEN NULL ELSE a.scale_date END) AS earliest_scale_date,"
          + " MAX(CASE WHEN a.scl_rtn_version_state_code = 'CAN' THEN NULL ELSE a.scale_date END) AS latest_scale_date,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, a.net_volume)), 0) AS scaled_volume,"
          + " NULL AS estimated_volume,"
          + " COUNT(DISTINCT NVL2(a.population_number, a.population_number || a.stratum_number || a.sampling_year,"
          + " '9999-99-99')) AS strata_count"
          + " FROM sample_log_tally a"
          + " WHERE a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN " + FS_STATES
          + CHECK_SCALE_RULE + deck("a")
          + " UNION ALL "
          + "SELECT 2, 'Weigh Slips', SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)), NULL,"
          + " ROUND(DECODE(SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)), 0, 0,"
          + " SUM(DECODE(a.scl_rtn_version_state_code, 'ISS', 1, 0)) * 100"
          + " / SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)))),"
          + " MIN(CASE WHEN a.scl_rtn_version_state_code = 'CAN' THEN NULL ELSE a.scale_date END),"
          + " MAX(CASE WHEN a.scl_rtn_version_state_code = 'CAN' THEN NULL ELSE a.scale_date END),"
          + " NULL,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, ((a.gross_weight - a.tare_weight) / 1000) * "
          + RATIO_EXPR + ")), 0),"
          + " COUNT(DISTINCT NVL2(a.population_number, a.population_number || a.stratum_number || a.sampling_year,"
          + " '9999-99-99'))"
          + " FROM weigh_slip a" + RATIO_FROM
          + " WHERE a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN " + FS_STATES
          + " AND a.scale_event_type_code IN ('PS','SS','RS','PD')" + RATIO_WHERE + deck("a")
          + " UNION ALL "
          + "SELECT 3, 'Log Tallies', SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)), NULL,"
          + " ROUND(DECODE(SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)), 0, 0,"
          + " SUM(DECODE(a.scl_rtn_version_state_code, 'ISS', 1, 0)) * 100"
          + " / SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, 1)))),"
          + " MIN(CASE WHEN a.scl_rtn_version_state_code = 'CAN' THEN NULL ELSE a.scale_date END),"
          + " MAX(CASE WHEN a.scl_rtn_version_state_code = 'CAN' THEN NULL ELSE a.scale_date END),"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'CAN', 0, a.net_volume)), 0), NULL, NULL"
          + " FROM log_tally a"
          + " WHERE a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN " + FS_STATES
          + CHECK_SCALE_RULE + " AND a.scale_event_type_code IN ('PS','FD','RT','RR','CS')" + deck("a")
          + " UNION ALL "
          + "SELECT 4, 'Volume Estimates', NULL, COUNT(a.document_control_number),"
          + " ROUND(DECODE(COUNT(a.document_control_number), 0, 0,"
          + " SUM(DECODE(a.scl_rtn_version_state_code, 'ISS', 1, 0)) * 100 / COUNT(a.document_control_number))),"
          + " MIN(a.scale_date), MAX(a.scale_date), NULL, NVL(SUM(b.volume), 0), NULL"
          + " FROM piece_scale_summary a, (SELECT document_control_number, version, SUM(volume) volume"
          + "   FROM piece_scl_segregation GROUP BY document_control_number, version) b"
          + " WHERE a.document_control_number = b.document_control_number(+) AND a.version = b.version(+)"
          + " AND a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN " + FS_STATES
          + " AND a.pssmry_type = 'FLDSCLEST'" + deck("a")
          + ") ov WHERE 1=1";

  /**
   * HBS3R841_B. The legacy action computed the scale-date window in Java from
   * the weigh slip / log tally / volume estimate rows of HBS3R841_A; here the
   * same window is computed inline.
   */
  private static final String OVERRIDE_RULES_SQL =
      "SELECT * FROM ("
          + "SELECT NVL(SUM(CASE WHEN o.override_rule_status_code = 'INA'"
          + "   AND o.processing_expiry_date >= DATE '2000-12-31' THEN 1 END), 0) AS inactive,"
          + " NVL(SUM(CASE WHEN o.override_rule_status_code = 'PND'"
          + "   AND o.processing_expiry_date >= DATE '2000-12-31' THEN 1 END), 0) AS pending,"
          + " NVL(SUM(CASE WHEN o.override_rule_status_code = 'ACT'"
          + "   AND o.processing_expiry_date >= DATE '2000-12-31' AND o.processing_effective_date <= SYSDATE - 1"
          + "   THEN 1 END), 0) AS past,"
          + " NVL(SUM(CASE WHEN o.override_rule_status_code = 'ACT'"
          + "   AND o.processing_expiry_date >= SYSDATE - 1 AND o.processing_effective_date <= SYSDATE"
          + "   THEN 1 END), 0) AS current_count,"
          + " NVL(SUM(CASE WHEN o.override_rule_status_code = 'ACT'"
          + "   AND o.processing_expiry_date >= SYSDATE THEN 1 END), 0) AS future,"
          + " MIN(dt.es) AS earliest_scale_date, MIN(dt.ls) AS latest_scale_date"
          + " FROM override_rating_rule o,"
          + " (SELECT MIN(sd) es, MAX(sd) ls FROM ("
          + "   SELECT a.scale_date sd FROM weigh_slip a WHERE a.active_version_ind = 'Y'"
          + "     AND a.scl_rtn_version_state_code IN ('AWP','RDY','ERR','DSF','HLD','LCK','ISS','NTI')"
          + "     AND a.scale_event_type_code IN ('PS','SS','RS','PD')" + deck("a")
          + "   UNION ALL SELECT a.scale_date FROM log_tally a WHERE a.active_version_ind = 'Y'"
          + "     AND a.scl_rtn_version_state_code IN ('AWP','RDY','ERR','DSF','HLD','LCK','ISS','NTI')"
          + CHECK_SCALE_RULE + "     AND a.scale_event_type_code IN ('PS','FD','RT','RR','CS')" + deck("a")
          + "   UNION ALL SELECT a.scale_date FROM piece_scale_summary a WHERE a.active_version_ind = 'Y'"
          + "     AND a.scl_rtn_version_state_code IN " + FS_STATES
          + "     AND a.pssmry_type = 'FLDSCLEST'" + deck("a")
          + " )) dt"
          + " WHERE ((dt.ls >= o.scale_effective_date AND dt.ls <= o.scale_expiry_date)"
          + "     OR (dt.es >= o.scale_effective_date AND dt.es <= o.scale_expiry_date)"
          + "     OR (dt.es < o.scale_effective_date AND dt.ls > o.scale_expiry_date))"
          + deck("o")
          + ") orr WHERE 1=1";

  private static final String SAMPLE_TALLY_BASE =
      "(SELECT a.scl_rtn_version_state_code st, a.net_volume, " + PSY + " AS psy, ROUND(" + RATIO_EXPR
          + ", 5) AS ytd_ratio, " + RATIO_SOURCE + " AS ratio_source"
          + " FROM sample_log_tally a" + RATIO_FROM
          + " WHERE a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN " + FS_STATES
          + CHECK_SCALE_RULE + RATIO_WHERE + deck("a") + ") v";

  private static final String SAMPLE_TALLIES_SQL =
      "SELECT * FROM ("
          + "SELECT 1 AS seq, v.psy, v.ytd_ratio, v.ratio_source, " + sums842("1")
          + " FROM " + SAMPLE_TALLY_BASE + " GROUP BY v.psy, v.ytd_ratio, v.ratio_source"
          + " UNION ALL SELECT 2, 'Total', NULL, NULL, " + sums842("1") + " FROM " + SAMPLE_TALLY_BASE
          + " UNION ALL SELECT 3, 'Volume (M3)', NULL, NULL, " + sums842("v.net_volume")
          + " FROM " + SAMPLE_TALLY_BASE
          + ") st WHERE 1=1";

  private static final String WEIGH_SLIP_BASE =
      "(SELECT a.scl_rtn_version_state_code st, NVL(a.field_scale_ind, 'N') fs,"
          + " (NVL(a.gross_weight, 0) - NVL(a.tare_weight, 0)) / 1000 AS wt,"
          + " ((a.gross_weight - a.tare_weight) / 1000) * " + RATIO_EXPR + " AS est_volume,"
          + " " + PSY + " AS psy, ROUND(" + RATIO_EXPR + ", 5) AS ytd_ratio, " + RATIO_SOURCE + " AS ratio_source"
          + " FROM weigh_slip a" + RATIO_FROM
          + " WHERE a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN " + FS_STATES
          + " AND a.scale_event_type_code IN ('PS','SS','RS','PD')" + RATIO_WHERE + deck("a") + ") v";

  private static final String WEIGH_SLIPS_SQL =
      "SELECT * FROM ("
          + "SELECT 1 AS seq, v.psy, v.ytd_ratio, v.ratio_source, " + sumsFs("1")
          + " FROM " + WEIGH_SLIP_BASE + " GROUP BY v.psy, v.ytd_ratio, v.ratio_source"
          + " UNION ALL SELECT 2, 'Total', NULL, NULL, " + sumsFs("1") + " FROM " + WEIGH_SLIP_BASE
          + " UNION ALL SELECT 3, 'Weight (tonnes)', NULL, NULL, " + sumsFs("v.wt") + " FROM " + WEIGH_SLIP_BASE
          + " UNION ALL SELECT 4, 'Est.Volume (M3)', NULL, NULL, " + sumsFs("v.est_volume")
          + " FROM " + WEIGH_SLIP_BASE
          + ") ws WHERE 1=1";

  private static final String RED_TAGS_SQL =
      "SELECT * FROM ("
          + "SELECT DECODE(b.scale_event_type_code, 'RT', 'Local Red Tag', b.description) AS event_type,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'NTI', 1, 0)), 0) AS nti,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'HLD', 1, 0)), 0) AS held,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'ERR', 1, 0)), 0) AS error,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'DSF', 1, 0)), 0) AS other,"
          + " NVL(SUM(DECODE(a.scl_rtn_version_state_code, 'NTI', 1, 'HLD', 1, 'ERR', 1, 'DSF', 1, 0)), 0) AS total"
          + " FROM weigh_slip a, scale_event_type_code b"
          + " WHERE a.active_version_ind = 'Y' AND a.scl_rtn_version_state_code IN ('ERR','DSF','HLD','NTI')"
          + " AND a.scale_event_type_code = b.scale_event_type_code AND a.scale_event_type_code IN ('RT','RR')"
          + deck("a")
          + " GROUP BY DECODE(b.scale_event_type_code, 'RT', 'Local Red Tag', b.description)"
          + ") rt WHERE 1=1";

  private static final String LOG_TALLY_BASE =
      "(SELECT a.scl_rtn_version_state_code st, NVL(a.field_scale_ind, 'N') fs, a.net_volume,"
          + " DECODE(b.scale_event_type_code, 'PS', 1, 'FD', 2, 'RT', 3, 'RR', 4, 'CS', 5) AS seq,"
          + " DECODE(b.scale_event_type_code, 'RT', 'Local Red Tag', b.description) AS event_type"
          + " FROM log_tally a, scale_event_type_code b"
          + " WHERE a.active_version_ind = 'Y' AND a.scale_event_type_code = b.scale_event_type_code"
          + " AND a.scl_rtn_version_state_code IN " + FS_STATES + CHECK_SCALE_RULE
          + " AND a.scale_event_type_code IN ('PS','FD','RT','RR','CS')" + deck("a") + ") v";

  private static final String LOG_TALLIES_SQL =
      "SELECT * FROM ("
          + "SELECT v.seq, v.event_type, " + sumsFs("1") + " FROM " + LOG_TALLY_BASE
          + " GROUP BY v.seq, v.event_type"
          + " UNION ALL SELECT 6, 'Total', " + sumsFs("1") + " FROM " + LOG_TALLY_BASE
          + " UNION ALL SELECT 7, 'Volume (M3)', " + sumsFs("v.net_volume") + " FROM " + LOG_TALLY_BASE
          + ") lt WHERE 1=1";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // ---- P811/P812, P813/P814: Force next summarization date (Mark Holder Profiles) ----
        forceSummarization("billing.forceSummarization.pieceProfiles", "P811/P812", "P"),
        forceSummarization("billing.forceSummarization.weighSlipProfiles", "P813/P814", "W"),

        // ---- P823/P824: Force Ratio Computation (Population Profiles) ----
        // Ported from PopulationProfileQuery.selectByRClientCriteria (+ global profile popprof_id = 1).
        QueryDefinition.builder("billing.forceRatioComputation.profiles")
            .legacy("P823/P824")
            .capability(Capability.BILLING_FORCE)
            .sql("""
                SELECT pp.popprof_id, pp.population_number, pp.sampling_year,
                       pp.hbs_frequency_type_code, ftc.description AS frequency_description,
                       pp.effective_date, pp.expiry_date, pp.last_ratio_stmt_date,
                       pp.next_ratio_stmt_date, pp.start_ratio_stmt_date,
                       pp.entry_userid, pp.entry_timestamp, :forceDate AS force_date,
                       CASE WHEN pp.popprof_id = 1 THEN 'Y' ELSE 'N' END AS global_profile
                  FROM population_profile pp, hbs_frequency_type_code ftc
                 WHERE pp.hbs_frequency_type_code = ftc.hbs_frequency_type_code(+)""")
            .filter(QueryFilter.required("forceDate",
                "AND :forceDate >= TRUNC(pp.effective_date) AND :forceDate <= pp.expiry_date", Type.DATE))
            .filter(QueryFilter.upper("population", "AND pp.population_number = :population"))
            .filter(QueryFilter.number("samplingYear", "AND pp.sampling_year = :samplingYear"))
            .filter(QueryFilter.date("effectiveDate", "AND TRUNC(pp.effective_date) = :effectiveDate"))
            .filter(QueryFilter.eq("globalProfile", "AND (:globalProfile <> 'Y' OR pp.popprof_id = 1)"))
            .sort("populationNumber", "pp.population_number")
            .sort("samplingYear", "pp.sampling_year")
            .sort("effectiveDate", "pp.effective_date")
            .orderBy("pp.population_number, pp.sampling_year, pp.effective_date")
            .build(),

        // ---- P828/P829: Final Bill Status Summary (HBS3R829, by population) ----
        finalBillSuppressFilters(QueryDefinition.builder("billing.finalBillStatus.populations")
            .legacy("P828/P829")
            .capability(Capability.BILLING_VIEW)
            .sql(FINAL_BILL_BY_POPULATION)
            .filter(QueryFilter.required("samplingYear", "AND fb.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.number("orgUnit",
                "AND (fb.org_unit_no = :orgUnit OR fb.org_unit_no IN"
                    + " (SELECT ou.org_unit_no FROM org_unit ou WHERE ou.rollup_region_no = :orgUnit))"))
            .filter(QueryFilter.upper("population", "AND fb.population_number = :population"))
            .filter(QueryFilter.eq("clientNumber", "AND fb.client_number = :clientNumber"))
            .filter(QueryFilter.eq("clientLocnCode", "AND fb.client_locn_code = :clientLocnCode")))
            .clientScope("AND fb.client_number = :scopeClientNumber")
            .sort("populationNumber", "fb.population_number")
            .sort("popExpiry", "fb.pop_expiry")
            .sort("eligiblePsym", "fb.eligible_psym")
            .orderBy("fb.population_number")
            .build(),

        // ---- P830: Final Bill Status Summary (single population with stratum list) ----
        finalBillSuppressFilters(QueryDefinition.builder("billing.finalBillStatus.strata")
            .legacy("P830")
            .capability(Capability.BILLING_VIEW)
            .sql(FINAL_BILL_BY_STRATUM)
            .filter(QueryFilter.required("samplingYear", "AND fb.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("population", "AND fb.population_number = :population", Type.UPPER)))
            .clientScope("AND fb.client_number = :scopeClientNumber")
            .sort("stratumNumber", "fb.stratum_number")
            .orderBy("fb.population_number, fb.stratum_number")
            .build(),

        // ---- P831/P832: Unissued Final Bills (HBS3R832) ----
        QueryDefinition.builder("billing.unissuedFinalBills.search")
            .legacy("P831/P832")
            .capability(Capability.FINAL_BILL_ADMIN)
            .sql(UNISSUED_SQL)
            .filter(QueryFilter.upper("timberMark", "AND fb.timber_mark = :timberMark"))
            .filter(QueryFilter.upper("population", "AND fb.population_number = :population"))
            .filter(QueryFilter.upper("stratum", "AND fb.stratum_number = :stratum"))
            .filter(QueryFilter.number("samplingYear", "AND fb.sampling_year = :samplingYear"))
            .filter(QueryFilter.upper("versionStatus", "AND fb.version_state_code = :versionStatus"))
            .filter(QueryFilter.eq("markClientNumber", "AND fb.mark_client_number = :markClientNumber"))
            .filter(QueryFilter.eq("markClientLocnCode", "AND fb.mark_client_locn_code = :markClientLocnCode"))
            .filter(QueryFilter.eq("popClientNumber", "AND fb.pop_client_number = :popClientNumber"))
            .filter(QueryFilter.eq("popClientLocnCode", "AND fb.pop_client_locn_code = :popClientLocnCode"))
            .sort("documentControlNumber", "fb.document_control_number")
            .sort("scaleDate", "fb.scale_date")
            .sort("timberMark", "fb.timber_mark")
            .orderBy("fb.document_control_number, fb.version")
            .build(),

        // ---- P833/P834: Non-renewable tenures that receive final bills (HBS3R834) ----
        QueryDefinition.builder("billing.finalBillTenures.search")
            .legacy("P833/P834")
            .capability(Capability.FINAL_BILL_ADMIN)
            .sql(FINAL_BILL_TENURE_SQL)
            .filter(QueryFilter.upper("timberMark", "AND fbdm.timber_mark = :timberMark"))
            .filter(QueryFilter.upper("population", "AND fbdm.population_number = :population"))
            .filter(QueryFilter.number("samplingYear", "AND fbdm.sampling_year = :samplingYear"))
            .filter(QueryFilter.eq("markClientNumber", "AND mbc.client_number = :markClientNumber"))
            .filter(QueryFilter.eq("markClientLocnCode", "AND mbc.client_locn_code = :markClientLocnCode"))
            .filter(QueryFilter.eq("popClientNumber", "AND p.client_number = :popClientNumber"))
            .filter(QueryFilter.eq("popClientLocnCode", "AND p.client_locn_code = :popClientLocnCode"))
            .sort("populationNumber", "fbdm.population_number")
            .sort("samplingYear", "fbdm.sampling_year")
            .sort("timberMark", "fbdm.timber_mark")
            .orderBy("fbdm.population_number, fbdm.sampling_year, fbdm.timber_mark")
            .build(),

        // ---- P836: one non-renewable tenure (FinalBillableDropoutMarkQuery.selectByPK) ----
        QueryDefinition.builder("billing.finalBillTenures.detail")
            .legacy("P836")
            .capability(Capability.FINAL_BILL_ADMIN)
            .sql(FINAL_BILL_TENURE_SQL)
            .filter(QueryFilter.required("population", "AND fbdm.population_number = :population", Type.UPPER))
            .filter(QueryFilter.required("samplingYear", "AND fbdm.sampling_year = :samplingYear", Type.NUMBER))
            .filter(QueryFilter.required("timberMark", "AND fbdm.timber_mark = :timberMark", Type.UPPER))
            .orderBy("fbdm.timber_mark")
            .maxRows(1)
            .build(),

        // ---- P837/P838: Non-renewable tenures NOT receiving final bills (frozen ratio) ----
        // ProfileManagerBean.findFrozenRatioRange: DROPOUT_MARK_LATEST_RATIO + population + mark.
        QueryDefinition.builder("billing.frozenRatioTenures.search")
            .legacy("P837/P838")
            .capability(Capability.FINAL_BILL_ADMIN)
            .sql("""
                SELECT d.population_number, d.stratum_number, d.sampling_year, d.timber_mark,
                       d.statement_number,
                       p.client_number AS pop_client_number, p.client_locn_code AS pop_client_locn_code,
                       p.client_number || '-' || p.client_locn_code AS population_owner,
                       mbc.client_number AS mark_client_number, mbc.client_locn_code AS mark_client_locn_code,
                       mbc.client_number || '-' || mbc.client_locn_code AS licensee,
                       mbc.file_type_code
                  FROM dropout_mark_latest_ratio d, population p, mark_billed_cli mbc
                 WHERE p.population_number(+) = d.population_number
                   AND p.sampling_year(+) = d.sampling_year
                   AND mbc.timber_mark(+) = d.timber_mark""")
            .filter(QueryFilter.upper("timberMark", "AND d.timber_mark = :timberMark"))
            .filter(QueryFilter.upper("population", "AND d.population_number = :population"))
            .filter(QueryFilter.number("samplingYear", "AND d.sampling_year = :samplingYear"))
            .filter(QueryFilter.eq("markClientNumber", "AND mbc.client_number = :markClientNumber"))
            .filter(QueryFilter.eq("markClientLocnCode", "AND mbc.client_locn_code = :markClientLocnCode"))
            .filter(QueryFilter.eq("popClientNumber", "AND p.client_number = :popClientNumber"))
            .filter(QueryFilter.eq("popClientLocnCode", "AND p.client_locn_code = :popClientLocnCode"))
            .sort("populationNumber", "d.population_number")
            .sort("timberMark", "d.timber_mark")
            .orderBy("d.population_number, d.sampling_year, d.stratum_number, d.timber_mark")
            .build(),

        // ---- P840: Search for scale returns from field scaled decks (mark header) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.mark")
            .legacy("P840")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql("""
                SELECT mbc.timber_mark, :fieldScaleDeckId AS field_scale_deck_id,
                       DECODE(:fieldScaleDeckId, '*', 'All', :fieldScaleDeckId) AS field_scale_deck_label,
                       mbc.file_type_code, mbc.forest_file_id, mbc.cutting_permit_id,
                       mbc.client_number || '-' || mbc.client_locn_code AS licensee
                  FROM mark_billed_cli mbc
                 WHERE mbc.timber_mark = :timberMark"""))
            .orderBy("mbc.timber_mark")
            .build(),

        // ---- P841: Field Scale Overview (HBS3R841_A) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.overview")
            .legacy("P841")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql(OVERVIEW_SQL))
            .orderBy("ov.seq")
            .maxRows(10)
            .build(),

        // ---- P841: Override Rules (HBS3R841_B) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.overrideRules")
            .legacy("P841")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql(OVERRIDE_RULES_SQL))
            .orderBy("1")
            .maxRows(1)
            .build(),

        // ---- P842: Sample Tallies From Field Scaled Decks (HBS3R842) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.sampleTallies")
            .legacy("P842")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql(SAMPLE_TALLIES_SQL))
            .orderBy("st.seq, st.psy")
            .build(),

        // ---- P843: Weigh Slips From Field Scaled Decks (HBS3R843 rows 1-4) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.weighSlips")
            .legacy("P843")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql(WEIGH_SLIPS_SQL))
            .orderBy("ws.seq, ws.psy")
            .build(),

        // ---- P843: red tag table (HBS3R843 row 5) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.redTags")
            .legacy("P843")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql(RED_TAGS_SQL))
            .orderBy("rt.event_type")
            .build(),

        // ---- P844: Log Tallies From Field Scaled Decks (HBS3R844) ----
        deckFilters(QueryDefinition.builder("billing.fieldScaledDecks.logTallies")
            .legacy("P844")
            .capability(Capability.FIELD_SCALED_DECKS)
            .sql(LOG_TALLIES_SQL))
            .orderBy("lt.seq")
            .build()
    );
  }

  /** HBS3R834 select (+ population owner for the P834 "Population Owned By" header). */
  private static final String FINAL_BILL_TENURE_SQL = """
      SELECT fbdm.population_number, fbdm.sampling_year, fbdm.timber_mark, mbc.file_type_code,
             mbc.client_number AS mark_client_number, mbc.client_locn_code AS mark_client_locn_code,
             p.client_number AS pop_client_number, p.client_locn_code AS pop_client_locn_code,
             fbdm.entry_userid, fbdm.entry_timestamp, fbdm.update_userid, fbdm.update_timestamp
        FROM final_billable_dropout_mark fbdm, mark_billed_cli mbc, population p
       WHERE mbc.timber_mark = fbdm.timber_mark
         AND p.population_number(+) = fbdm.population_number
         AND p.sampling_year(+) = fbdm.sampling_year""";

  /**
   * MarkHolderProfileQuery.selectByCriteria / selectCountByCriteria (P811/P813):
   * profiles of the return type active on the force date. The legacy "For All
   * Client With Custom Mark Holder Profile" option also updated the default
   * profile (mhprof_id 1 = piece, 2 = weight); it is listed here, flagged.
   */
  private static QueryDefinition forceSummarization(String id, String legacy, String returnType) {
    String defaultId = "P".equals(returnType) ? "1" : "2";
    return QueryDefinition.builder(id)
        .legacy(legacy)
        .capability(Capability.BILLING_FORCE)
        .sql("SELECT mhp.mhprof_id, mhp.client_number, mhp.client_locn_code, mhp.hbs_return_type_code,"
            + " mhp.hbs_frequency_type_code, ftc.description AS frequency_description,"
            + " mhp.forced_summarization_date, mhp.effective_date, mhp.expiry_date,"
            + " mhp.entry_userid, mhp.entry_timestamp, :forceDate AS force_date,"
            + " CASE WHEN mhp.mhprof_id IN (1, 2) THEN 'Y' ELSE 'N' END AS default_profile"
            + " FROM hbs_mark_holder_profile mhp, hbs_frequency_type_code ftc"
            + " WHERE mhp.hbs_frequency_type_code = ftc.hbs_frequency_type_code(+)"
            + " AND mhp.hbs_return_type_code = '" + returnType + "'"
            + " AND (mhp.mhprof_id NOT IN (1, 2) OR mhp.mhprof_id = " + defaultId + ")")
        .filter(QueryFilter.required("forceDate",
            "AND :forceDate >= TRUNC(mhp.effective_date) AND :forceDate <= mhp.expiry_date", Type.DATE))
        .filter(QueryFilter.upper("frequency", "AND mhp.hbs_frequency_type_code = :frequency"))
        .filter(QueryFilter.eq("clientNumber", "AND mhp.client_number = :clientNumber"))
        .filter(QueryFilter.eq("clientLocnCode", "AND mhp.client_locn_code = :clientLocnCode"))
        .sort("clientNumber", "mhp.client_number")
        .sort("effectiveDate", "mhp.effective_date")
        .orderBy("mhp.client_number NULLS FIRST, mhp.client_locn_code, mhp.effective_date")
        .build();
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P812 / P814 "Yes": set FORCED_SUMMARIZATION_DATE on one profile.
        // HBS_STORE_HBS_MARK_PROFILE rewrites every column, so the row's current
        // values are posted back unchanged except the forced date and audit columns.
        CommandDefinition.builder("billing.markHolderProfile.forceSummarizationDate")
            .legacy("P811/P812, P813/P814")
            .capability(Capability.BILLING_FORCE)
            .procedure("HBS_STORE_HBS_MARK_PROFILE")
            .existingRow("SELECT entry_userid, entry_timestamp FROM hbs_mark_holder_profile WHERE mhprof_id = :mhprofId")
            .requiredBody("mhprofId", Type.NUMBER)
            .text("clientNumber")
            .text("clientLocnCode")
            .requiredBody("hbsReturnTypeCode", Type.UPPER)
            .requiredBody("hbsFrequencyTypeCode", Type.UPPER)
            .requiredBody("forcedSummarizationDate", Type.DATE)
            .requiredBody("effectiveDate", Type.DATE)
            .requiredBody("expiryDate", Type.DATE)
            .existing("entryUserid")
            .existing("entryTimestamp")
            .auditUser()
            .now()
            .build(),

        // P824 "Yes": set NEXT_RATIO_STMT_DATE on one population profile.
        CommandDefinition.builder("billing.populationProfile.forceRatioComputationDate")
            .legacy("P823/P824")
            .capability(Capability.BILLING_FORCE)
            .procedure("HBS_STORE_POPULATION_PROFILE")
            .existingRow("SELECT entry_userid, entry_timestamp FROM population_profile WHERE popprof_id = :popprofId")
            .requiredBody("popprofId", Type.NUMBER)
            .requiredBody("populationNumber", Type.STRING)
            .requiredBody("samplingYear", Type.NUMBER)
            .requiredBody("hbsFrequencyTypeCode", Type.UPPER)
            .requiredBody("nextRatioStmtDate", Type.DATE)
            .requiredBody("lastRatioStmtDate", Type.DATE)
            .date("startRatioStmtDate")
            .requiredBody("effectiveDate", Type.DATE)
            .date("expiryDate")
            .existing("entryUserid")
            .existing("entryTimestamp")
            .auditUser()
            .now()
            .build(),

        // P835 Save: elect a non-renewable mark for final billing.
        CommandDefinition.builder("billing.finalBillTenure.create")
            .legacy("P835")
            .capability(Capability.FINAL_BILL_ADMIN)
            .procedure("HBS_CREATE_FINAL_BILL_MARK")
            .requiredBody("populationNumber", Type.UPPER)
            .requiredBody("samplingYear", Type.NUMBER)
            .requiredBody("timberMark", Type.UPPER)
            .auditUser()
            .now()
            .auditUser()
            .now()
            .build(),

        // P836 Delete.
        CommandDefinition.builder("billing.finalBillTenure.remove")
            .legacy("P836")
            .capability(Capability.FINAL_BILL_ADMIN)
            .procedure("HBS_REMOVE_FINAL_BILL_MARK")
            .requiredBody("populationNumber", Type.UPPER)
            .requiredBody("samplingYear", Type.NUMBER)
            .requiredBody("timberMark", Type.UPPER)
            .build()
    );
  }
}
