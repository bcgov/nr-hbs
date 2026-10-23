package ca.bc.gov.nrs.hbs.api.signature;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads behind the scaler authentication key workflow — the legacy
 * {@code HbsScalerAuthKeyQuery.selectByCriteria} finders and the
 * {@code QuantLicenseEntity} / {@code HBSUserManager} lookups P360/P362 used,
 * with bound parameters (legacy concatenated licence and user id into SQL).
 * The encrypted key is read as RAW bytes only to re-send it unchanged to
 * {@code HBS_STORE_HBS_SCALER_AUTH_KEY}; it is never decrypted or returned.
 */
@Repository
public class ScalerAuthKeyRepository {

  /** One {@code HBS_SCALER_AUTH_KEY} row. */
  public record KeyRow(long hsakId, String userId, String clientNumber, byte[] encryptedKey,
      LocalDateTime effectiveDate, LocalDateTime expiryDate, String entryUserid, Timestamp entryTimestamp,
      String updateUserid, Timestamp updateTimestamp) {

    /** {@code effective_date <= day <= expiry_date} (legacy selectByCriteria with an effective date). */
    public boolean activeOn(LocalDate day) {
      LocalDateTime d = day.atStartOfDay();
      return effectiveDate != null && expiryDate != null && !d.isBefore(effectiveDate) && !d.isAfter(expiryDate);
    }
  }

  private static final String COLUMNS = """
      SELECT k.hsak_id, k.user_id, k.client_number, k.encrypted_authentication_key,
             k.effective_date, k.expiry_date, k.entry_userid, k.entry_timestamp,
             k.update_userid, k.update_timestamp
        FROM hbs_scaler_auth_key k""";

  private static final RowMapper<KeyRow> MAPPER = (rs, i) -> new KeyRow(
      rs.getLong("hsak_id"), rs.getString("user_id"), rs.getString("client_number"),
      rs.getBytes("encrypted_authentication_key"),
      toLdt(rs.getTimestamp("effective_date")), toLdt(rs.getTimestamp("expiry_date")),
      rs.getString("entry_userid"), rs.getTimestamp("entry_timestamp"),
      rs.getString("update_userid"), rs.getTimestamp("update_timestamp"));

  private final NamedParameterJdbcTemplate jdbc;

  public ScalerAuthKeyRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static LocalDateTime toLdt(Timestamp t) {
    return t == null ? null : t.toLocalDateTime();
  }

  /** P360 validateScalerLicenceInput / P362 findClientNumber: QUANT_LICENSE PK (licence, 'S'). */
  public Optional<String> clientForLicence(String licenceNumber) {
    return jdbc.queryForList("""
        SELECT client_number FROM quant_license
         WHERE license_number = :licence AND quant_lic_type_cd = 'S'""",
        new MapSqlParameterSource("licence", licenceNumber), String.class).stream().findFirst();
  }

  /** P362 loadForm (self service): {@code qleHome.findByCriteria("S", clientNumber)}. */
  public Optional<String> licenceForClient(String clientNumber) {
    return jdbc.queryForList("""
        SELECT license_number FROM quant_license
         WHERE client_number = :client AND quant_lic_type_cd = 'S'
         ORDER BY license_number""",
        new MapSqlParameterSource("client", clientNumber), String.class).stream().findFirst();
  }

  /**
   * {@code findHbsScalerAuthKeysByCriteria(null, client, null, licence,
   * "effective_date desc,expiry_date desc,hsak_id desc")}.
   */
  public List<KeyRow> keysForScaler(String clientNumber, String licenceNumber) {
    return jdbc.query(COLUMNS + """
         WHERE k.client_number = :client
           AND k.client_number IN (SELECT q.client_number FROM quant_license q WHERE q.license_number = :licence)
         ORDER BY k.effective_date DESC, k.expiry_date DESC, k.hsak_id DESC""",
        new MapSqlParameterSource("client", clientNumber).addValue("licence", licenceNumber), MAPPER);
  }

  /** P362 loadForm (self service): the caller's keys, the one active today first. */
  public List<KeyRow> keysForUser(String userId) {
    return jdbc.query(COLUMNS + """
         WHERE k.user_id = :userId
         ORDER BY CASE WHEN TRUNC(SYSDATE) BETWEEN k.effective_date AND k.expiry_date THEN 1 ELSE 99 END,
                  k.effective_date DESC, k.expiry_date DESC, k.hsak_id DESC""",
        new MapSqlParameterSource("userId", userId), MAPPER);
  }

  /** P362 validateUser: {@code HBSUserManager.getHBSUser(domain\\user)}. */
  public boolean userExists(String userId) {
    Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM hbs_user WHERE user_id = :userId",
        new MapSqlParameterSource("userId", userId), Integer.class);
    return n != null && n > 0;
  }
}
