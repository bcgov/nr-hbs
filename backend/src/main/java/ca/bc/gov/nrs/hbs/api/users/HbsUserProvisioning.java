package ca.bc.gov.nrs.hbs.api.users;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Keeps the caller's legacy {@code HBS_USER} row in step with their login.
 *
 * <p>Legacy enrolment (P010 / P016 request approval) created and maintained
 * {@code HBS_USER}; accounts and roles are now managed in FAM, so nothing else
 * writes it. The row still matters while the legacy batch runs
 * (docs/coexistence.md):
 * <ul>
 *   <li>statement e-mails (B2065 → {@code DocumentDeliveryTransmissionManagerBean
 *       .findDocumentDeliveryAddresses}) go to {@code HBS_USER.EMAIL} of the users in
 *       the client location's data domain ({@code HBS_USER_CLI_LOC_DATA_DOMAIN});</li>
 *   <li>the data domains themselves (User Services, {@code admin.userDataDomains.*})
 *       and the {@code :hbsUserId} "associated sites / districts" filters key on it.</li>
 * </ul>
 *
 * <p>On a user's first authenticated request (then at most once per
 * {@code hbs.user-provisioning.refresh}), the row is created with
 * {@code HBS_CREATE_USER}, or its name, e-mail, type and active status are
 * refreshed with {@code HBS_STORE_USER}; the columns legacy enrolment collected
 * that the token doesn't carry (phone, middle initial, client location, org unit)
 * are kept as stored. Holding an HBS role in FAM is what "active" means now, so
 * the status is set to {@code ACT}. Failures are logged and never fail the
 * request.
 */
@Component
@Slf4j
public class HbsUserProvisioning {

  static final String ACTIVE = "ACT";
  /** NOT NULL columns legacy enrolment filled from the request form; the token has no value for them. */
  static final String UNKNOWN = " ";

  record Profile(String userId, String type, String firstName, String lastName, String email, String clientNumber) {}

  private final NamedParameterJdbcTemplate jdbc;
  private final TableApi tableApi;
  private final Duration refresh;
  private final boolean enabled;
  private final Clock clock;
  private final Map<String, Instant> lastChecked = new ConcurrentHashMap<>();

  @Autowired
  public HbsUserProvisioning(NamedParameterJdbcTemplate jdbc, TableApi tableApi,
      @Value("${hbs.user-provisioning.enabled:true}") boolean enabled,
      @Value("${hbs.user-provisioning.refresh:PT6H}") Duration refresh) {
    this(jdbc, tableApi, enabled, refresh, Clock.systemUTC());
  }

  HbsUserProvisioning(NamedParameterJdbcTemplate jdbc, TableApi tableApi, boolean enabled, Duration refresh,
      Clock clock) {
    this.jdbc = jdbc;
    this.tableApi = tableApi;
    this.enabled = enabled;
    this.refresh = refresh;
    this.clock = clock;
  }

  /** Provision the current caller, if due. Never throws. */
  public void provisionCurrentUser() {
    if (!enabled) return;
    try {
      Profile profile = currentProfile();
      if (profile == null) return;
      Instant now = clock.instant();
      String key = profile.toString();
      Instant last = lastChecked.get(key);
      if (last != null && last.plus(refresh).isAfter(now)) return;
      lastChecked.put(key, now);
      sync(profile);
    } catch (RuntimeException ex) {
      log.warn("HBS_USER provisioning failed: {}", ex.getMessage());
    }
  }

  /** The caller as an HBS_USER, or null when they hold no HBS role (nothing to provision). */
  static Profile currentProfile() {
    String userId = RequestUtil.getCurrentAuditUserId();
    String type = RequestUtil.getLegacyUserType();
    if (userId == null || userId.isBlank() || !List.of("MOF", "CLI", "SPC").contains(type)) {
      return null;
    }
    Jwt jwt = RequestUtil.getCurrentJwt();
    String username = RequestUtil.getCurrentUserName();
    String first = firstText(jwt.getClaimAsString("given_name"), jwt.getClaimAsString("display_name"), username);
    String last = firstText(jwt.getClaimAsString("family_name"), username);
    String email = firstText(jwt.getClaimAsString("email"));
    String client = "MOF".equals(type) ? null : blankToNull(RequestUtil.getCurrentClientNumber());
    return new Profile(userId, type, cut(first, 40), cut(last, 40), cut(email, 128), client);
  }

  /** Create or refresh the row (package-private for tests). */
  void sync(Profile p) {
    List<Map<String, Object>> rows = jdbc.queryForList("""
        SELECT user_id, hbs_user_type_code, hbs_user_status_code, last_name, first_name,
               middle_initial, phone, email, client_number, client_locn_code, org_unit_no
          FROM hbs_user
         WHERE user_id = :userId""", new MapSqlParameterSource("userId", p.userId()));
    if (rows.isEmpty()) {
      if (p.email() == null) {
        // EMAIL is NOT NULL; legacy never created a user without one.
        log.warn("Not creating HBS_USER {}: the token has no e-mail address", p.userId());
        return;
      }
      tableApi.call("HBS_CREATE_USER", ProcArgs.of()
          .put("i_user_id", p.userId())
          .put("i_hbs_user_type_code", p.type())
          .put("i_hbs_user_status_code", ACTIVE)
          .put("i_last_name", p.lastName())
          .put("i_first_name", p.firstName())
          .put("i_middle_initial", UNKNOWN)
          .put("i_phone", UNKNOWN)
          .put("i_email", p.email())
          .put("i_client_number", p.clientNumber())
          .put("i_client_locn_code", null)
          .put("i_org_unit_no", null));
      log.info("Created HBS_USER {} ({})", p.userId(), p.type());
      return;
    }
    Map<String, Object> row = rows.get(0);
    String email = p.email() == null ? str(row.get("EMAIL")) : p.email();
    String client = str(row.get("CLIENT_NUMBER")) == null ? p.clientNumber() : str(row.get("CLIENT_NUMBER"));
    boolean changed = !Objects.equals(p.type(), str(row.get("HBS_USER_TYPE_CODE")))
        || !ACTIVE.equals(str(row.get("HBS_USER_STATUS_CODE")))
        || !Objects.equals(p.lastName(), str(row.get("LAST_NAME")))
        || !Objects.equals(p.firstName(), str(row.get("FIRST_NAME")))
        || !Objects.equals(email, str(row.get("EMAIL")))
        || !Objects.equals(client, str(row.get("CLIENT_NUMBER")));
    if (!changed) return;
    ProcArgs args = tableApi.argsFromRow("HBS_STORE_USER", row)
        .put("i_hbs_user_type_code", p.type())
        .put("i_hbs_user_status_code", ACTIVE)
        .put("i_last_name", p.lastName())
        .put("i_first_name", p.firstName())
        .put("i_email", email)
        .put("i_client_number", client);
    tableApi.call("HBS_STORE_USER", args);
    log.info("Refreshed HBS_USER {}", p.userId());
  }

  private static String firstText(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v.trim();
    }
    return null;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private static String cut(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }

  private static String str(Object o) {
    if (o == null) return null;
    String s = o.toString().trim();
    return s.isEmpty() ? null : s;
  }
}
