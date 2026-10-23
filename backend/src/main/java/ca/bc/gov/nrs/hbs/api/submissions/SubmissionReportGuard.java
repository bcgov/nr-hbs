package ca.bc.gov.nrs.hbs.api.submissions;

import ca.bc.gov.nrs.hbs.api.service.v1.report.ReportRequestGuard;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Client fence for the P032 / P027 drill-down reports an industry user ran
 * from their own transmissions and batches: {@code HBS2R033} (Transmission
 * Detail Content Listing, P032 "File Name" link) and {@code HBS2R032}
 * (Transmission Detail Active Version Status, P032 "Xmit Id" / P027
 * "Rejected Documents"). Their procs take only the transmission / batch id,
 * so the report would list any client's documents; the request is allowed for
 * a non-ministry caller only when that transmission was created or received by
 * the caller's client (DTL_SCL_DOC_TRANSMISSION.CLIENT_NUMBER /
 * RECD_CLIENT_NUMBER) or, for a batch, the batch's transmission is the
 * client's or the batch's scale site is owned by the client (the
 * {@code SubmissionsCatalog.BATCH_SCOPE} rule of the P027 list).
 *
 * <p>{@code HBS2R027} (the P027 list) stays ministry-only: its proc fences an
 * industry caller only through {@code RB_SEARCH_BY_SITES = 'L' || user id},
 * which the report-prompt character whitelist cannot carry (the user id holds a
 * backslash).
 */
@Component
public class SubmissionReportGuard implements ReportRequestGuard {

  /** The look-ups, separated for tests. */
  interface Lookups {
    boolean ownTransmission(long transmissionId, String client);

    boolean ownBatch(long batchId, String client);
  }

  private final Lookups lookups;
  private final BooleanSupplier ministry;
  private final Supplier<String> client;

  @Autowired
  public SubmissionReportGuard(NamedParameterJdbcTemplate jdbc) {
    this(new Lookups() {
      @Override
      public boolean ownTransmission(long transmissionId, String client) {
        return count(jdbc, """
            SELECT COUNT(*) FROM dtl_scl_doc_transmission t
             WHERE t.transmission_id = :id
               AND (t.client_number = :client OR t.recd_client_number = :client)""",
            new MapSqlParameterSource("id", transmissionId).addValue("client", client)) > 0;
      }

      @Override
      public boolean ownBatch(long batchId, String client) {
        return count(jdbc, """
            SELECT COUNT(*) FROM dtl_doc_batch b
              JOIN dtl_scl_doc_transmission t ON t.transmission_id = b.transmission_id
              LEFT JOIN scale_site ss ON ss.scale_site_id_nmbr = b.scale_site_id_nmbr
             WHERE b.batch_id = :id
               AND (t.client_number = :client OR t.recd_client_number = :client
                    OR ss.owner_cli_number = :client)""",
            new MapSqlParameterSource("id", batchId).addValue("client", client)) > 0;
      }
    }, RequestUtil::isMinistryUser, RequestUtil::getCurrentClientNumber);
  }

  SubmissionReportGuard(Lookups lookups, BooleanSupplier ministry, Supplier<String> client) {
    this.lookups = lookups;
    this.ministry = ministry;
    this.client = client;
  }

  private static long count(NamedParameterJdbcTemplate jdbc, String sql, MapSqlParameterSource p) {
    Long n = jdbc.queryForObject(sql, p, Long.class);
    return n == null ? 0 : n;
  }

  @Override
  public HbsReportRequestDto apply(String reportId, HbsReportRequestDto request) {
    String id = reportId == null ? "" : reportId.toUpperCase(Locale.ROOT);
    if (!id.equals("HBS2R032") && !id.equals("HBS2R033") || ministry.getAsBoolean()) {
      return request;
    }
    String c = client.get();
    if (!StringUtils.hasText(c)) {
      throw new AccessDeniedException("hbs.no_access_right: no client scope on token");
    }
    Map<String, String> p = request.parameters() == null ? Map.of() : request.parameters();
    Long batch = id.equals("HBS2R032") ? number(p.get("RB_BATCH_ID")) : null;
    Long transmission = number(p.get("RB_TRANSMISSION_ID"));
    boolean allowed = batch != null ? lookups.ownBatch(batch, c)
        : transmission != null && lookups.ownTransmission(transmission, c);
    if (!allowed) {
      throw new AccessDeniedException("hbs.no_access_right: transmission or batch is not your client's");
    }
    return request;
  }

  private static Long number(String v) {
    if (v == null || v.isBlank()) return null;
    try {
      return Long.parseLong(v.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Report prompt TRANSMISSION_ID / BATCH_ID takes digits only.");
    }
  }
}
