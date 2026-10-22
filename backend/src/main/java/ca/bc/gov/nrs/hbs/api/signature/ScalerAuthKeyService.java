package ca.bc.gov.nrs.hbs.api.signature;

import ca.bc.gov.nrs.hbs.api.signature.ScalerAuthKeyRepository.KeyRow;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.ProcArgs;
import ca.bc.gov.nrs.hbs.api.workflow.TableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException.Problem;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * P362 "Update Scaler Profile" / P363 "Authentication Key Confirmation" —
 * port of {@code $V/presentation/cpm/P362Action} (load + submit) over
 * {@code $E/cpm/ClientProfileManagerBean.createAndStoreHbsScalerAuthKey /
 * updateHbsScalerAuthKey} and {@code HbsScalerAuthKeyQuery}.
 *
 * <p>Two modes, as legacy: <b>managed</b> (HBS_SCALE_ADMIN: any scaler
 * licence, chooses the key's HBS user and the From date) and <b>self
 * service</b> (everyone else, i.e. CLI_SCALER: only the scaler whose key is
 * active for the caller's own user id, From = tomorrow).
 *
 * <p>Submit: requested key + check digit; the key active today is expired the
 * day before the new From date; a futured key (effective after now) is
 * overwritten, otherwise a new row is inserted. The key is encrypted in Java
 * ({@link DbmsCryptoCipher}) exactly as DBMS_CRYPTO did and passed as RAW to
 * the table-API procs; the plain key is returned once (P363) and never stored
 * or logged in clear.
 */
@Service
@Slf4j
public class ScalerAuthKeyService {

  // ApplicationResources.properties texts (hbs-view).
  static final String MSG_LICENCE_REQUIRED = "A scaler licence is required.";
  static final String MSG_LICENCE_INVALID = "Scaler licence is invalid.";
  static final String MSG_LICENCE_NOT_FOUND = "Scaler licence does not exist.";
  static final String MSG_USER_INVALID = "Invalid domain and user id combination.";
  static final String MSG_KEY_LENGTH = "Requested key cannot be less than 7 or more than 9 characters in length";
  static final String MSG_KEY_BLANK = "Requested key cannot be blank";
  static final String MSG_KEY_ALNUM = "Requested key may only contain alpha-numeric characters";
  static final String MSG_KEY_UPPER = "Requested Key must be all UPPER CASE or digits";
  static final String MSG_FROM_DATE = "Requested Active From Scale Date must be a valid date in the format "
      + "\"yyymmdd\" greater than today and less than {0}.";
  static final String MSG_NO_LINK = "An active Scaler Profile link from your Scaler License to your User Id does "
      + "not exist. Please contact your District Scaling Supervisor to correct this error.";

  static final String SEQUENCE = "HBS_SCALER_AUTH_KEY_SEQ";
  private static final DateTimeFormatter ORACLE_DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** What P362 showed on load (legacy {@code P362Form} after {@code loadForm}). */
  public record RequestForm(boolean selfService, String licenceNumber, String clientNumber, String domain,
      String userId, boolean hasKey, LocalDate fromDate, LocalDate maxFromDate, LocalDate toDate, int agingDays) {}

  private final ScalerAuthKeyRepository repository;
  private final TableApi tableApi;
  private final Audit audit;
  private final SignatureKeyProperties keys;
  private final Function<String, DbmsCryptoCipher> cipherFactory;

  @Autowired
  public ScalerAuthKeyService(ScalerAuthKeyRepository repository, TableApi tableApi, Audit audit,
      SignatureKeyProperties keys) {
    this(repository, tableApi, audit, keys, DbmsCryptoCipher::new);
  }

  ScalerAuthKeyService(ScalerAuthKeyRepository repository, TableApi tableApi, Audit audit,
      SignatureKeyProperties keys, Function<String, DbmsCryptoCipher> cipherFactory) {
    this.repository = repository;
    this.tableApi = tableApi;
    this.audit = audit;
    this.keys = keys;
    this.cipherFactory = cipherFactory;
  }

  /** Today in Pacific time (the legacy app server's zone). */
  LocalDate today() {
    return audit.now().toLocalDateTime().toLocalDate();
  }

  // ── Load (P362Action.loadForm) ──────────────────────────────────────

  /**
   * @param selfService   true unless the caller holds HBS_SCALE_ADMIN
   * @param callerUserId  the caller's legacy user id ({@code IDIR\NAME} / {@code BCEID\NAME})
   * @param licenceNumber managed mode: the scaler licence from P360/P364
   */
  public RequestForm load(boolean selfService, String callerUserId, String licenceNumber) {
    int aging = keys.authKeyAgingDays();
    LocalDate tomorrow = today().plusDays(1);
    LocalDate maxFrom = tomorrow.plusDays(aging);
    LocalDate from = tomorrow;
    if (selfService) {
      Scaler own = ownScaler(callerUserId);
      return new RequestForm(true, own.licence(), own.client(), domainOf(callerUserId), userOf(callerUserId),
          true, from, maxFrom, tomorrow.plusDays(aging), aging);
    }
    String licence = upper(licenceNumber);
    String client = clientFor(licence);
    String domain = null;
    String user = null;
    boolean hasKey = false;
    List<KeyRow> history = repository.keysForScaler(client, licence);
    LocalDate today = today();
    for (int i = 0; i < history.size(); i++) {
      KeyRow k = history.get(i);
      if (i == 0) {
        domain = domainOf(k.userId());
        user = userOf(k.userId());
      }
      if (!tomorrow.atStartOfDay().isAfter(k.effectiveDate())) {
        from = k.effectiveDate().toLocalDate(); // futured: P362 pre-fills its From date
      } else if (k.activeOn(today)) {
        hasKey = true;
        break;
      }
    }
    return new RequestForm(false, licence, client, domain, user, hasKey, from, maxFrom,
        tomorrow.plusDays(aging), aging);
  }

  // ── Submit (P362Action.perform ACTION_SUBMIT + submitNewKey) ───────

  /**
   * Validates, then expires / overwrites / inserts the key rows in the
   * caller's transaction. Returns the P363 confirmation (licence, the new key
   * with its check digit, active from / to).
   */
  public Map<String, Object> request(boolean selfService, String callerUserId, Map<String, Object> body) {
    String licence;
    String client;
    String domain;
    String userPart;
    LocalDate from;
    LocalDate tomorrow = today().plusDays(1);
    LocalDate maxFrom = tomorrow.plusDays(keys.authKeyAgingDays());
    if (selfService) {
      Scaler own = ownScaler(callerUserId);
      licence = own.licence();
      client = own.client();
      domain = domainOf(callerUserId);
      userPart = userOf(callerUserId);
      from = tomorrow; // not editable in self service (P362.jsp)
    } else {
      licence = upper(text(body, "licenceNumber"));
      client = clientFor(licence);
      domain = upper(text(body, "domain"));
      userPart = upper(text(body, "userId"));
      from = parseDate(text(body, "fromDate"));
    }
    String requestedKey = text(body, "requestedKey");

    List<Problem> problems = new ArrayList<>();
    String authKeyForUser = validateUser(domain, userPart, problems);
    validateAuthKey(requestedKey, problems);
    if (from == null || from.isBefore(tomorrow) || from.isAfter(maxFrom)) {
      problems.add(new Problem("fromDate", MSG_FROM_DATE.replace("{0}", maxFrom.plusDays(1).format(ORACLE_DATE_ONLY))));
    }
    if (!problems.isEmpty()) {
      throw new WorkflowValidationException(problems);
    }
    // Only now: a missing secret must not hide the user's validation errors,
    // and must stop before any write.
    DbmsCryptoCipher cipher = cipherFactory.apply(keys.requireDbmsCryptoKey());

    LocalDate to = from.plusDays(keys.authKeyAgingDays());
    String authKey = AuthKeyCheckDigit.suffix(licence, requestedKey);
    byte[] encrypted = cipher.encrypt(authKey);
    String user = audit.user();
    Timestamp now = audit.now();

    List<KeyRow> history = repository.keysForScaler(client, licence);
    // findFuturedKey: the latest key (effective_date desc) when it starts after now.
    KeyRow futureKey = !history.isEmpty() && history.get(0).effectiveDate().isAfter(now.toLocalDateTime())
        ? history.get(0) : null;
    // findActiveKey: the key active today; legacy refused more than one.
    LocalDate today = today();
    List<KeyRow> active = history.stream().filter(k -> k.activeOn(today)).toList();
    if (active.size() > 1) {
      throw new WorkflowValidationException(null,
          "Multiple scaler auth keys exist for client number: '" + client + "'.");
    }
    if (!active.isEmpty()) {
      KeyRow a = active.get(0);
      store(a, a.userId(), a.encryptedKey(), a.effectiveDate(), from.minusDays(1).atStartOfDay(), user, now);
    }
    long hsakId;
    if (futureKey != null) {
      hsakId = futureKey.hsakId();
      store(futureKey, authKeyForUser, encrypted, from.atStartOfDay(), to.atStartOfDay(), user, now);
    } else {
      hsakId = tableApi.nextId(SEQUENCE);
      tableApi.call("HBS_CREATE_HBS_SCALER_AUTH_KEY", ProcArgs.of()
          .put("i_hsak_id", hsakId)
          .put("i_user_id", authKeyForUser)
          .put("i_client_number", client)
          .put("i_authentication_key", encrypted)
          .put("i_effective_date", from)
          .put("i_expiry_date", to)
          .put("i_entry_userid", user)
          .put("i_entry_timestamp", now)
          .put("i_update_userid", user)
          .put("i_update_timestamp", now));
    }
    log.info("Scaler authentication key {} {} for licence {} by {} (active {} – {})",
        hsakId, futureKey != null ? "replaced" : "issued", licence, user, from, to);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("hsakId", hsakId);
    result.put("licenceNumber", licence);
    result.put("authenticationKey", authKey);
    result.put("activeFrom", from.toString());
    result.put("activeTo", to.toString());
    return result;
  }

  private void store(KeyRow row, String userId, byte[] encrypted, LocalDateTime effective, LocalDateTime expiry,
      String user, Timestamp now) {
    tableApi.call("HBS_STORE_HBS_SCALER_AUTH_KEY", ProcArgs.of()
        .put("i_hsak_id", row.hsakId())
        .put("i_user_id", userId)
        .put("i_client_number", row.clientNumber())
        .put("i_authentication_key", encrypted)
        .put("i_effective_date", effective)
        .put("i_expiry_date", expiry)
        .put("i_entry_userid", row.entryUserid())
        .put("i_entry_timestamp", row.entryTimestamp())
        .put("i_update_userid", user)
        .put("i_update_timestamp", now));
  }

  // ── Validations (P362Action) ────────────────────────────────────────

  /** validateUser: blank domain and user → no user; otherwise the HBS user must exist. */
  String validateUser(String domain, String userPart, List<Problem> problems) {
    if (domain.isBlank() && userPart.isBlank()) {
      return null; // legacy "" → NULL in Oracle
    }
    String combined = domain + "\\" + userPart;
    if (!repository.userExists(combined)) {
      problems.add(new Problem("userId", MSG_USER_INVALID));
    }
    return combined;
  }

  /** validateAuthKey. */
  static void validateAuthKey(String authKey, List<Problem> problems) {
    if (authKey == null || authKey.trim().isEmpty()) {
      problems.add(new Problem("requestedKey", MSG_KEY_BLANK));
    } else if (authKey.length() < 7 || authKey.length() > 9) {
      problems.add(new Problem("requestedKey", MSG_KEY_LENGTH));
    } else if (!authKey.chars().allMatch(Character::isLetterOrDigit)) {
      problems.add(new Problem("requestedKey", MSG_KEY_ALNUM));
    } else if (!authKey.equals(authKey.trim().toUpperCase(Locale.ROOT))) {
      problems.add(new Problem("requestedKey", MSG_KEY_UPPER));
    }
  }

  // ── Helpers ─────────────────────────────────────────────────────────

  private record Scaler(String licence, String client) {}

  /** P362 loadForm self service: the scaler whose key is active today for this user, else P365. */
  private Scaler ownScaler(String callerUserId) {
    List<KeyRow> mine = callerUserId == null || callerUserId.isBlank()
        ? List.of() : repository.keysForUser(callerUserId.toUpperCase(Locale.ROOT));
    if (mine.isEmpty() || !mine.get(0).activeOn(today())) {
      throw new WorkflowValidationException(null, MSG_NO_LINK);
    }
    String client = mine.get(0).clientNumber();
    String licence = repository.licenceForClient(client)
        .orElseThrow(() -> new WorkflowValidationException(null, MSG_NO_LINK));
    return new Scaler(licence, client);
  }

  /** P360 validateScalerLicenceInput. */
  private String clientFor(String licence) {
    if (licence.isEmpty()) {
      throw new WorkflowValidationException("licenceNumber", MSG_LICENCE_REQUIRED);
    }
    if (licence.length() > 4) {
      throw new WorkflowValidationException("licenceNumber", MSG_LICENCE_INVALID);
    }
    return repository.clientForLicence(licence)
        .orElseThrow(() -> new WorkflowValidationException("licenceNumber", MSG_LICENCE_NOT_FOUND));
  }

  /** CpmHelper.getUsersDomain. */
  static String domainOf(String userId) {
    if (userId == null || userId.isBlank()) return null;
    int i = userId.indexOf('\\');
    return i >= 0 ? userId.substring(0, i) : userId;
  }

  /** CpmHelper.getUsersID. */
  static String userOf(String userId) {
    if (userId == null || userId.isBlank()) return null;
    int i = userId.indexOf('\\');
    return i >= 0 ? userId.substring(i + 1) : userId;
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    return v == null ? "" : v.toString();
  }

  private static String upper(String s) {
    return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
  }

  /** yyyy-MM-dd (SPA) or the legacy yyyyMMdd entry format. */
  static LocalDate parseDate(String s) {
    String v = s == null ? "" : s.trim();
    if (v.isEmpty()) return null;
    try {
      return v.length() == 8 && v.chars().allMatch(Character::isDigit)
          ? LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE)
          : LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
    } catch (DateTimeParseException ex) {
      return null;
    }
  }
}
