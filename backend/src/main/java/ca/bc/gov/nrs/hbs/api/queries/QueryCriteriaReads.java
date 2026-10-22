package ca.bc.gov.nrs.hbs.api.queries;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reference look-ups behind the legacy Queries-tab cross-reference validators
 * ({@code HBSValidator} / {@code OpqHelper} / {@code TimberMarkHelper}) and the
 * P450 FOI check. Bound parameters only; table names as the legacy queries
 * used them (public synonyms).
 */
@Component
public class QueryCriteriaReads {

  private final NamedParameterJdbcTemplate jdbc;

  public QueryCriteriaReads(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * {@code TimberMarkReader.getTimberMarkByMark} — {@code TimberMarkQuery.SELECT_LOAD}
   * joins TIMBER_MARK and MARK_BILLED_CLI (validator {@code timbermarkCrossRef},
   * {@code OpqHelper.findMark}).
   */
  public boolean billedMarkExists(String timberMark) {
    return exists("""
        SELECT COUNT(*) FROM timber_mark t JOIN mark_billed_cli b ON b.timber_mark = t.timber_mark
         WHERE t.timber_mark = :tm""", new MapSqlParameterSource("tm", timberMark));
  }

  /** {@code TimberMarkReader.getTimberMarkByLicence} (validator {@code timberLicenseCrossRef}): any billed mark of the licence. */
  public boolean licenceHasMarks(String forestFileId) {
    return exists("""
        SELECT COUNT(*) FROM timber_mark t JOIN mark_billed_cli b ON b.timber_mark = t.timber_mark
         WHERE t.forest_file_id = :ffid""", new MapSqlParameterSource("ffid", forestFileId));
  }

  /** {@code OpqHelper.findClientLocationsByClientNumber} (validator {@code clientNumberValid}). */
  public boolean clientHasLocations(String clientNumber) {
    return exists("SELECT COUNT(*) FROM client_location WHERE client_number = :client",
        new MapSqlParameterSource("client", clientNumber));
  }

  /** {@code OpqHelper.findClientLocation} (validator {@code clientNumberAndClientLocation}). */
  public boolean clientLocationExists(String clientNumber, String location) {
    return exists("SELECT COUNT(*) FROM client_location WHERE client_number = :client AND client_locn_code = :loc",
        new MapSqlParameterSource("client", clientNumber).addValue("loc", location));
  }

  /** {@code InvoiceHelper.findScaleSite} / validator {@code scaleSiteNoCrossRef}. */
  public boolean scaleSiteExists(String scaleSite) {
    return exists("SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = :site",
        new MapSqlParameterSource("site", scaleSite));
  }

  /** Validator {@code regionDistrictCrossRef} ({@code OrgUnitHelper}): the org unit exists. */
  public boolean orgUnitExists(long orgUnitNo) {
    return exists("SELECT COUNT(*) FROM org_unit WHERE org_unit_no = :ou", new MapSqlParameterSource("ou", orgUnitNo));
  }

  /** {@code DacHelper.isValidLicence} — the scaler licence is on QUANT_LICENSE. */
  public boolean scalerLicenceExists(String licence) {
    return exists("SELECT COUNT(*) FROM quant_license WHERE license_number = :lic",
        new MapSqlParameterSource("lic", licence));
  }

  /**
   * {@code CodeReaderBean.isValidSPGCombination} → {@code SpeciesProductGradeQuery.selectByCriteria}:
   * the codes given (blank = any, {@code Query.appendWhereClause} skips empty values)
   * with {@code effective_date <= to} and {@code expiry_date >= from}. Legacy
   * {@code MarkMonthlyBillingAction} passed the To date as the effective date
   * and the From date as the expiry date, so this is "in effect at some time in
   * the range".
   */
  public boolean speciesProductGradeInEffect(String species, String product, String grade,
      LocalDate from, LocalDate to) {
    StringBuilder sql = new StringBuilder("""
        SELECT COUNT(*) FROM species_product_grade_xref
         WHERE effective_date <= :toDate AND expiry_date >= :fromDate""");
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("fromDate", java.sql.Date.valueOf(from))
        .addValue("toDate", java.sql.Date.valueOf(to));
    if (species != null) {
      sql.append(" AND scale_species_code = :species");
      p.addValue("species", species);
    }
    if (product != null) {
      sql.append(" AND scale_product_code = :product");
      p.addValue("product", product);
    }
    if (grade != null) {
      sql.append(" AND scale_grade_code = :grade");
      p.addValue("grade", grade);
    }
    return exists(sql.toString(), p);
  }

  /**
   * {@code TransactionStatementQuery.getNotReleasableIndustryInfoCount} (BT17247), bound:
   * any piece / weight / compiled-sample transaction of these documents whose
   * paid-by / copied-to clients are both not the viewer's client while one of
   * them is an Individual ({@code V_CLIENT_PUBLIC.CLIENT_TYPE_CODE = 'I'}).
   * Legacy matched the parties against the viewer's {@code HBS_USER.CLIENT_NUMBER};
   * the new app uses the active client of the token (the same client — the
   * HBS_USER row is provisioned from it).
   *
   * @param kind P (piece), W (weight) or S (compiled sample)
   */
  public boolean industryNotReleasable(char kind, Collection<Long> documentControlNumbers, String viewerClient) {
    if (documentControlNumbers.isEmpty()) {
      return false;
    }
    String tx = switch (kind) {
      case 'P' -> """
          SELECT DISTINCT client_number_paid_by pbc, client_number_copied_to scc
            FROM piece_scl_smry_txn WHERE document_control_number IN (:dcns)""";
      case 'W' -> """
          SELECT DISTINCT client_number_paid_by pbc, client_number_copied_to scc
            FROM weight_scl_smry_txn WHERE document_control_number IN (:dcns)""";
      case 'S' -> """
          SELECT DISTINCT scss.client_number_send_to pbc, scss.client_number_copy_to scc
            FROM hbs_compiled_sample_stmnt scss
            JOIN sample_scl_smry_txn ssst ON ssst.statement_number = scss.statement_number
           WHERE ssst.document_control_number IN (:dcns)""";
      default -> throw new IllegalArgumentException("kind " + kind);
    };
    String sql = """
        SELECT COUNT(*)
          FROM (%s) tx
          LEFT JOIN v_client_public vc_pb ON vc_pb.client_number = tx.pbc
          LEFT JOIN v_client_public vc_sc ON vc_sc.client_number = tx.scc
         WHERE NVL(tx.pbc, '~') <> :viewerClient AND NVL(tx.scc, '~') <> :viewerClient
           AND (vc_pb.client_type_code = 'I' OR vc_sc.client_type_code = 'I')""".formatted(tx);
    List<Long> all = List.copyOf(documentControlNumbers);
    // Legacy split the list in chunks of 300; Oracle allows 1,000 binds per IN list.
    for (int i = 0; i < all.size(); i += 900) {
      MapSqlParameterSource p = new MapSqlParameterSource()
          .addValue("dcns", all.subList(i, Math.min(all.size(), i + 900)))
          .addValue("viewerClient", viewerClient == null ? "" : viewerClient);
      if (exists(sql, p)) {
        return true;
      }
    }
    return false;
  }

  private boolean exists(String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n != null && n > 0;
  }
}
