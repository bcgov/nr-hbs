package ca.bc.gov.nrs.hbs.api.submissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.LegacyMessages;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Legacy P041 / P024 / P901 / P911 criteria checks on the registry searches. */
class SearchCriteriaGuardTest {

  private final BatchSlipReads reads = ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks.of(BatchSlipReads.class);
  private final SearchCriteriaGuard guard = new SearchCriteriaGuard(reads, () -> LocalDate.of(2026, 10, 21));

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static void signIn(String... roles) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("identity_provider", "bceidbusiness")
        .claim("bceid_username", "bob").claim("client_roles", List.of(roles)).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private List<String> messages(String queryId, Object... kv) {
    Map<String, String> p = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) p.put((String) kv[i], (String) kv[i + 1]);
    try {
      guard.validate(queryId, p);
      return List.of();
    } catch (WorkflowValidationException e) {
      return e.problems().stream().map(WorkflowValidationException.Problem::message).toList();
    }
  }

  @Test
  void otherQueries_areNotChecked() {
    assertThat(messages("billing.finalBillStatus.populations", "receivedFrom", "garbage")).isEmpty();
  }

  @Test
  void transmissions_receivedDatesAndSite() {
    when(reads.scaleSiteExists("A12")).thenReturn(true);
    assertThat(messages("submissions.transmissions", "receivedFrom", "2026-10-01", "receivedTo", "2026-10-21",
        "scaleSiteNo", "a12")).isEmpty();
    assertThat(messages("submissions.transmissions", "receivedFrom", "2018-12-31", "receivedTo", "2026-10-22",
        "scaleSiteNo", "ZZZ")).containsExactly(
        "Transmission Received From Date must be on or after 2019-Jan-01",
        "Received To Date must be before the current date.",
        LegacyMessages.SCALE_SITE_REF);
    assertThat(messages("submissions.batches", "receivedFrom", "2026-10-10", "receivedTo", "2026-10-01",
        "orgUnitNo", "99")).containsExactly(LegacyMessages.FROM_BEFORE_TO, LegacyMessages.REGION_DISTRICT_REF);
  }

  @Test
  void lateSubmissions_daysLateRange() {
    assertThat(messages("scaleControl.lateSubmissions.search", "daysLateLimit", "0"))
        .containsExactly("Days Late Limit cannot be less than 1.");
    assertThat(messages("scaleControl.lateSubmissions.search", "daysLateLimit", "732"))
        .containsExactly("Days Late Limit cannot be greater than 731.");
    assertThat(messages("scaleControl.lateSubmissions.search", "daysLateLimit", "1x"))
        .containsExactly("Days Late Limit must be numeric.");
  }

  @Test
  void anomalies_p911Rules() {
    when(reads.scaleSiteExists("A12")).thenReturn(true);
    when(reads.licenceExists("1234")).thenReturn(true);
    assertThat(messages("scaleControl.anomalies.search", "anomalyType", "ARR_DEP", "primaryLicence", "1234",
        "scaleDateFrom", "2026-10-22", "assessedFrom", "2026-10-10", "assessedTo", "2026-10-01"))
        .containsExactly(LegacyMessages.EVENT_FROM_FUTURE, LegacyMessages.ASSESSMENT_FROM_TO,
            LegacyMessages.LICENCE_WITH_ARR_DEP);
    assertThat(messages("scaleControl.anomalies.search", "anomalyType", "ARRIVAL_GAP", "primaryLicence", "9999"))
        .containsExactly(LegacyMessages.LICENCE_WITH_ARRIVAL, LegacyMessages.LICENCE_NOT_FOUND);
    signIn("HBS_CLI_USER_FOREST_CLIENT-00012345");
    assertThat(messages("scaleControl.anomalies.search", "anomalyType", "SCALING", "scaleSite", "a12"))
        .containsExactly(LegacyMessages.ANOMALY_USER_ACCESS);
    when(reads.scaleSiteOwnedBy("A12", "00012345")).thenReturn(true);
    assertThat(messages("scaleControl.anomalies.search", "anomalyType", "SCALING", "scaleSite", "a12")).isEmpty();
  }
}
