package ca.bc.gov.nrs.hbs.api.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.users.HbsUserProvisioning.Profile;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class HbsUserProvisioningTest {

  private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
  private final RecordingTableApi api = new RecordingTableApi();
  private final HbsUserProvisioning provisioning =
      new HbsUserProvisioning(jdbc, api, true, Duration.ofHours(6), Clock.systemUTC());

  private static final Profile CLIENT_USER =
      new Profile("BCEID\\JDOE", "CLI", "Jane", "Doe", "jane@example.com", "00012345");

  private void existing(Map<String, Object> row) {
    when(jdbc.queryForList(anyString(), any(SqlParameterSource.class))).thenReturn(row == null ? List.of() : List.of(row));
  }

  private static Map<String, Object> row(String status, String email) {
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("USER_ID", "BCEID\\JDOE");
    r.put("HBS_USER_TYPE_CODE", "CLI");
    r.put("HBS_USER_STATUS_CODE", status);
    r.put("LAST_NAME", "Doe");
    r.put("FIRST_NAME", "Jane");
    r.put("MIDDLE_INITIAL", "Q");
    r.put("PHONE", "2505551234");
    r.put("EMAIL", email);
    r.put("CLIENT_NUMBER", "00012345");
    r.put("CLIENT_LOCN_CODE", "00");
    r.put("ORG_UNIT_NO", null);
    return r;
  }

  @Test
  void createsAMissingUserAsActive() {
    existing(null);
    provisioning.sync(CLIENT_USER);
    var call = api.callsTo("HBS_CREATE_USER").get(0);
    assertThat(call.arg("i_user_id")).isEqualTo("BCEID\\JDOE");
    assertThat(call.arg("i_hbs_user_status_code")).isEqualTo("ACT");
    assertThat(call.arg("i_email")).isEqualTo("jane@example.com");
    assertThat(call.arg("i_client_number")).isEqualTo("00012345");
    assertThat(call.arg("i_phone")).isEqualTo(" ");
  }

  @Test
  void skipsCreateWithoutAnEmail() {
    existing(null);
    provisioning.sync(new Profile("IDIR\\X", "MOF", "X", "Y", null, null));
    assertThat(api.calls).isEmpty();
  }

  @Test
  void leavesAnUpToDateRowAlone() {
    existing(row("ACT", "jane@example.com"));
    provisioning.sync(CLIENT_USER);
    assertThat(api.calls).isEmpty();
  }

  @Test
  void refreshesEmailAndStatusButKeepsLegacyColumns() {
    existing(row("INA", "old@example.com"));
    provisioning.sync(CLIENT_USER);
    var call = api.callsTo("HBS_STORE_USER").get(0);
    assertThat(call.arg("i_email")).isEqualTo("jane@example.com");
    assertThat(call.arg("i_hbs_user_status_code")).isEqualTo("ACT");
    assertThat(call.arg("i_phone")).isEqualTo("2505551234");
    assertThat(call.arg("i_middle_initial")).isEqualTo("Q");
    assertThat(call.arg("i_client_locn_code")).isEqualTo("00");
  }
}
