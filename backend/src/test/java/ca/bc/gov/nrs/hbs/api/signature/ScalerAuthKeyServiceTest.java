package ca.bc.gov.nrs.hbs.api.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.signature.ScalerAuthKeyRepository.KeyRow;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.server.ResponseStatusException;

/** P362Action submit / load rules (managed and self service) over RecordingTableApi. */
class ScalerAuthKeyServiceTest {

  static final String CRYPTO_KEY = "2b7e151628aed2a6abf7158809cf4f3c";
  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 15, 0);
  static final LocalDate TOMORROW = LocalDate.of(2026, 10, 22);

  /** In-memory reads. */
  static class FakeRepository extends ScalerAuthKeyRepository {
    final Map<String, String> clientByLicence = new HashMap<>();
    final List<KeyRow> rows = new ArrayList<>();
    final Set<String> users = new java.util.HashSet<>();

    FakeRepository() {
      super(null);
    }

    @Override
    public Optional<String> clientForLicence(String licence) {
      return Optional.ofNullable(clientByLicence.get(licence));
    }

    @Override
    public Optional<String> licenceForClient(String client) {
      return clientByLicence.entrySet().stream().filter(e -> e.getValue().equals(client)).map(Map.Entry::getKey)
          .findFirst();
    }

    @Override
    public List<KeyRow> keysForScaler(String client, String licence) {
      return rows.stream().filter(r -> r.clientNumber().equals(client))
          .sorted((a, b) -> b.effectiveDate().compareTo(a.effectiveDate())).toList();
    }

    @Override
    public List<KeyRow> keysForUser(String userId) {
      return rows.stream().filter(r -> userId.equals(r.userId()))
          .sorted((a, b) -> Boolean.compare(b.activeOn(NOW.toLocalDate()), a.activeOn(NOW.toLocalDate()))).toList();
    }

    @Override
    public boolean userExists(String userId) {
      return users.contains(userId);
    }
  }

  static class FixedAudit extends Audit {
    FixedAudit() {
      super("HBSBATCH");
    }

    @Override
    public String user() {
      return "IDIR\\ADMIN";
    }

    @Override
    public Timestamp now() {
      return Timestamp.valueOf(NOW);
    }
  }

  FakeRepository repo;
  RecordingTableApi api;
  ScalerAuthKeyService service;
  final DbmsCryptoCipher cipher = new DbmsCryptoCipher(CRYPTO_KEY);

  @BeforeEach
  void setUp() {
    repo = new FakeRepository();
    repo.clientByLicence.put("S001", "00012345");
    repo.users.add("IDIR\\JSMITH");
    repo.users.add("BCEID\\SCALER1");
    api = new RecordingTableApi();
    service = service(CRYPTO_KEY);
  }

  ScalerAuthKeyService service(String cryptoKey) {
    return new ScalerAuthKeyService(repo, api, new FixedAudit(),
        new SignatureKeyProperties(cryptoKey, "", "", "JKS", 365));
  }

  static KeyRow key(long id, String user, LocalDate from, LocalDate to) {
    return new KeyRow(id, user, "00012345", new byte[] {1, 2, 3}, from.atStartOfDay(), to.atStartOfDay(),
        "IDIR\\OLD", Timestamp.valueOf("2020-01-01 08:00:00"), null, null);
  }

  static Map<String, Object> managedBody(String key, String from) {
    Map<String, Object> body = new HashMap<>();
    body.put("licenceNumber", "s001");
    body.put("domain", "IDIR");
    body.put("userId", "jsmith");
    body.put("requestedKey", key);
    body.put("fromDate", from);
    return body;
  }

  @Test
  void managedFirstKeyIsInserted() {
    Map<String, Object> result = service.request(false, "IDIR\\ADMIN", managedBody("KEY12345", "2026-10-22"));

    assertThat(api.names()).containsExactly("HBS_CREATE_HBS_SCALER_AUTH_KEY");
    var call = api.calls.get(0);
    assertThat(call.arg("i_hsak_id")).isEqualTo(new BigDecimal(1001));
    assertThat(call.arg("i_user_id")).isEqualTo("IDIR\\JSMITH");
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat((byte[]) call.arg("i_authentication_key")).isEqualTo(cipher.encrypt("KEY12345T"));
    assertThat(call.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2026-10-22 00:00:00"));
    assertThat(call.arg("i_expiry_date")).isEqualTo(Timestamp.valueOf("2027-10-22 00:00:00"));
    assertThat(call.arg("i_entry_userid")).isEqualTo("IDIR\\ADMIN");
    assertThat(result).containsEntry("authenticationKey", "KEY12345T")
        .containsEntry("licenceNumber", "S001")
        .containsEntry("activeFrom", "2026-10-22")
        .containsEntry("activeTo", "2027-10-22");
  }

  @Test
  void activeKeyIsExpiredTheDayBeforeTheNewKey() {
    repo.rows.add(key(7, "IDIR\\JSMITH", LocalDate.of(2025, 11, 1), LocalDate.of(2026, 11, 1)));

    service.request(false, "IDIR\\ADMIN", managedBody("KEY12345", "2026-11-01"));

    assertThat(api.names()).containsExactly("HBS_STORE_HBS_SCALER_AUTH_KEY", "HBS_CREATE_HBS_SCALER_AUTH_KEY");
    var expire = api.calls.get(0);
    assertThat(expire.arg("i_hsak_id")).isEqualTo(new BigDecimal(7));
    assertThat(expire.arg("i_expiry_date")).isEqualTo(Timestamp.valueOf("2026-10-31 00:00:00"));
    assertThat((byte[]) expire.arg("i_authentication_key")).containsExactly(1, 2, 3); // unchanged
    assertThat(expire.arg("i_entry_userid")).isEqualTo("IDIR\\OLD");
    assertThat(expire.arg("i_update_userid")).isEqualTo("IDIR\\ADMIN");
  }

  @Test
  void futuredKeyIsOverwrittenNotDuplicated() {
    repo.rows.add(key(7, "IDIR\\JSMITH", LocalDate.of(2025, 11, 1), LocalDate.of(2026, 11, 30)));
    repo.rows.add(key(8, "IDIR\\OTHER", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1)));

    service.request(false, "IDIR\\ADMIN", managedBody("ABCDEFG", "2026-12-01"));

    assertThat(api.names()).containsExactly("HBS_STORE_HBS_SCALER_AUTH_KEY", "HBS_STORE_HBS_SCALER_AUTH_KEY");
    var future = api.calls.get(1);
    assertThat(future.arg("i_hsak_id")).isEqualTo(new BigDecimal(8));
    assertThat(future.arg("i_user_id")).isEqualTo("IDIR\\JSMITH");
    assertThat((byte[]) future.arg("i_authentication_key")).isEqualTo(cipher.encrypt("ABCDEFG" + AuthKeyCheckDigit.of("S001ABCDEFG")));
    assertThat(future.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2026-12-01 00:00:00"));
  }

  @Test
  void twoActiveKeysAreRefused() {
    repo.rows.add(key(7, null, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31)));
    repo.rows.add(key(6, null, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31)));
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", managedBody("KEY12345", "2026-10-22")))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessage("Multiple scaler auth keys exist for client number: '00012345'.");
    assertThat(api.calls).isEmpty();
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', value = {
      "''          | Requested key cannot be blank",
      "ABC12       | Requested key cannot be less than 7 or more than 9 characters in length",
      "ABCDEFGHIJ  | Requested key cannot be less than 7 or more than 9 characters in length",
      "ABC-1234    | Requested key may only contain alpha-numeric characters",
      "abc12345    | Requested Key must be all UPPER CASE or digits",
  })
  void requestedKeyValidation(String key, String message) {
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", managedBody(key, "2026-10-22")))
        .isInstanceOf(WorkflowValidationException.class).hasMessage(message);
    assertThat(api.calls).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"2026-10-21", "2027-10-23", "garbage", "''"})
  void fromDateMustBeTomorrowToAgingLimit(String from) {
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", managedBody("KEY12345", from)))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessage("Requested Active From Scale Date must be a valid date in the format \"yyymmdd\" "
            + "greater than today and less than 2027-10-23.");
  }

  @Test
  void fromDateAcceptsLegacyFormatAndLastDay() {
    service.request(false, "IDIR\\ADMIN", managedBody("KEY12345", "20271022"));
    assertThat(api.calls.get(0).arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2027-10-22 00:00:00"));
  }

  @Test
  void unknownUserAndLicence() {
    var body = managedBody("KEY12345", "2026-10-22");
    body.put("userId", "nobody");
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", body))
        .hasMessage("Invalid domain and user id combination.");
    body.put("licenceNumber", "X999");
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", body))
        .hasMessage("Scaler licence does not exist.");
    body.put("licenceNumber", "");
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", body))
        .hasMessage("A scaler licence is required.");
  }

  @Test
  void blankDomainAndUserIssuesKeyWithoutUser() {
    var body = managedBody("KEY12345", "2026-10-22");
    body.put("domain", "");
    body.put("userId", "");
    service.request(false, "IDIR\\ADMIN", body);
    assertThat(api.calls.get(0).arg("i_user_id")).isNull();
  }

  @Test
  void missingCryptoKeyFailsAfterValidationWithoutWriting() {
    service = service("");
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", managedBody("KEY12345", "2026-10-22")))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("HBS_SIGNATURE_DBMS_CRYPTO_KEY");
    assertThat(api.calls).isEmpty();
    // validation errors still come first
    assertThatThrownBy(() -> service.request(false, "IDIR\\ADMIN", managedBody("x", "2026-10-22")))
        .isInstanceOf(WorkflowValidationException.class);
  }

  @Test
  void selfServiceActsOnOwnActiveKeyOnly() {
    repo.rows.add(key(9, "BCEID\\SCALER1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    var body = managedBody("NEWKEY77", "2027-01-15"); // licence / user / date in the body are ignored
    body.put("licenceNumber", "X999");

    Map<String, Object> result = service.request(true, "BCEID\\SCALER1", body);

    assertThat(api.names()).containsExactly("HBS_STORE_HBS_SCALER_AUTH_KEY", "HBS_CREATE_HBS_SCALER_AUTH_KEY");
    assertThat(api.calls.get(0).arg("i_expiry_date")).isEqualTo(Timestamp.valueOf("2026-10-21 00:00:00"));
    var create = api.calls.get(1);
    assertThat(create.arg("i_user_id")).isEqualTo("BCEID\\SCALER1");
    assertThat(create.arg("i_effective_date")).isEqualTo(Timestamp.valueOf("2026-10-22 00:00:00"));
    assertThat(result).containsEntry("licenceNumber", "S001");
  }

  @Test
  void selfServiceWithoutActiveKeyGetsP365Message() {
    repo.rows.add(key(9, "BCEID\\SCALER1", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1)));
    assertThatThrownBy(() -> service.request(true, "BCEID\\SCALER1", managedBody("NEWKEY77", "")))
        .hasMessageStartingWith("An active Scaler Profile link from your Scaler License to your User Id does not exist.");
    assertThat(api.calls).isEmpty();
  }

  @Test
  void loadManagedPrefillsFromFuturedKey() {
    repo.rows.add(key(7, "IDIR\\JSMITH", LocalDate.of(2025, 11, 1), LocalDate.of(2026, 11, 30)));
    repo.rows.add(key(8, "IDIR\\OTHER", LocalDate.of(2026, 12, 1), LocalDate.of(2027, 12, 1)));

    var form = service.load(false, "IDIR\\ADMIN", "s001");

    assertThat(form.selfService()).isFalse();
    assertThat(form.clientNumber()).isEqualTo("00012345");
    assertThat(form.domain()).isEqualTo("IDIR");
    assertThat(form.userId()).isEqualTo("OTHER");
    assertThat(form.hasKey()).isTrue();
    assertThat(form.fromDate()).isEqualTo(LocalDate.of(2026, 12, 1));
    assertThat(form.maxFromDate()).isEqualTo(TOMORROW.plusDays(365));
  }

  @Test
  void loadSelfService() {
    repo.rows.add(key(9, "BCEID\\SCALER1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
    var form = service.load(true, "BCEID\\SCALER1", null);
    assertThat(form.selfService()).isTrue();
    assertThat(form.licenceNumber()).isEqualTo("S001");
    assertThat(form.domain()).isEqualTo("BCEID");
    assertThat(form.userId()).isEqualTo("SCALER1");
    assertThat(form.fromDate()).isEqualTo(TOMORROW);
  }
}
