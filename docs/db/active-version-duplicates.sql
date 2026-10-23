-- =============================================================================
-- MONITORING (read-only) — detail documents with more than one ACTIVE version.
--
-- Replaces the unique "one active version" indexes first proposed for the
-- detail-return port, which must NOT be created: both the legacy code and its
-- port briefly hold two active versions inside one workflow (e.g.
-- WorkflowManagerBean.process stores the AWP version before deactivating the
-- previous invoiced one), and the legacy app autocommits every proc call, so a
-- unique index would make those calls fail in legacy batch and the new app.
--
-- Run as: any user with SELECT on the tables (e.g. the HBS proxy user), before
-- go-live and then periodically. Expected result: no rows. Each row is a
-- document to repair by data fix (docs/pinch-points.md §12).
-- =============================================================================

SELECT 'LOG_TALLY' AS version_table, detail_document_number, COUNT(*) AS active_versions,
       LISTAGG(version || ':' || scl_rtn_version_state_code, ', ') WITHIN GROUP (ORDER BY version) AS versions
  FROM the.log_tally WHERE active_version_ind = 'Y'
 GROUP BY detail_document_number HAVING COUNT(*) > 1
UNION ALL
SELECT 'WEIGH_SLIP', detail_document_number, COUNT(*),
       LISTAGG(version || ':' || scl_rtn_version_state_code, ', ') WITHIN GROUP (ORDER BY version)
  FROM the.weigh_slip WHERE active_version_ind = 'Y'
 GROUP BY detail_document_number HAVING COUNT(*) > 1
UNION ALL
SELECT 'SAMPLE_LOG_TALLY', detail_document_number, COUNT(*),
       LISTAGG(version || ':' || scl_rtn_version_state_code, ', ') WITHIN GROUP (ORDER BY version)
  FROM the.sample_log_tally WHERE active_version_ind = 'Y'
 GROUP BY detail_document_number HAVING COUNT(*) > 1
UNION ALL
SELECT 'SPECIAL_FOREST_PRODUCT_TALLY', detail_document_number, COUNT(*),
       LISTAGG(version || ':' || scl_rtn_version_state_code, ', ') WITHIN GROUP (ORDER BY version)
  FROM the.special_forest_product_tally WHERE active_version_ind = 'Y'
 GROUP BY detail_document_number HAVING COUNT(*) > 1
UNION ALL
SELECT 'SCL_SITE_ARRIVAL_LEDGER', detail_document_number, COUNT(*),
       LISTAGG(version || ':' || scl_rtn_version_state_code, ', ') WITHIN GROUP (ORDER BY version)
  FROM the.scl_site_arrival_ledger WHERE active_version_ind = 'Y'
 GROUP BY detail_document_number HAVING COUNT(*) > 1
UNION ALL
SELECT 'SCL_SITE_DEPARTURE_LEDGER', detail_document_number, COUNT(*),
       LISTAGG(version || ':' || scl_rtn_version_state_code, ', ') WITHIN GROUP (ORDER BY version)
  FROM the.scl_site_departure_ledger WHERE active_version_ind = 'Y'
 GROUP BY detail_document_number HAVING COUNT(*) > 1
ORDER BY 1, 2
;
