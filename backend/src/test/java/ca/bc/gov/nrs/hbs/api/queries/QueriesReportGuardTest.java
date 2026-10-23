package ca.bc.gov.nrs.hbs.api.queries;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.legacyrules.NullMocks;
import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** P450Action FOI gate (BT17247) + fence on Print / Print Client Register, and the P403 rate rules. */
class QueriesReportGuardTest {

  private final QueryService queries = mock(QueryService.class);
  private final QueryCriteriaReads reads = NullMocks.of(QueryCriteriaReads.class);
  private final QueriesReportGuard guard = new QueriesReportGuard(queries, reads);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private static void industry() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("identity_provider", "bceidbusiness")
        .claim("bceid_username", "bob").claim("client_roles", List.of("HBS_CLI_USER_FOREST_CLIENT-00012345")).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private static void ministry() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("identity_provider", "idir")
        .claim("idir_username", "jsmith").claim("client_roles", List.of("HBS_MOF_USER")).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private static HbsReportRequestDto request(Map<String, String> prompts) {
    return new HbsReportRequestDto(prompts, null);
  }

  @Test
  void otherReports_untouched() {
    HbsReportRequestDto r = request(Map.of("X", "1"));
    assertThat(guard.apply("HBS3R431", r)).isSameAs(r);
  }

  @Test
  void ministry_documentNumberDropped_noFence() {
    ministry();
    HbsReportRequestDto out = guard.apply("HBS3R451", request(Map.of("RB_DOC_NUMBER", "Q1", "RB_CLIENT_NUMBER", "1")));
    assertThat(out.parameters()).containsEntry("RB_DOC_NUMBER", "").containsEntry("RB_CLIENT_NUMBER", "1");
    verify(queries, never()).list(any(String.class), anyMap());
  }

  @Test
  void industry_forcedToOwnClient_andFoiChecked() {
    industry();
    when(queries.list(eq("issuedDocuments.search"), anyMap())).thenReturn(List.of(
        Map.of("documentControlNumber", new BigDecimal("101")), Map.of("documentControlNumber", 102L)));
    HbsReportRequestDto out = guard.apply("hbs4r452", request(Map.of("RB_TYPE", "PSI",
        "RB_CLIENT_NUMBER", "00099999", "RB_CLIENT_ASSOCIATION", "ORIGINAL", "RB_ISSUE_DATE_FROM", "2026-01-01")));
    assertThat(out.parameters()).containsEntry("RB_CLIENT_NUMBER", "00012345")
        .containsEntry("RB_CLIENT_ASSOCIATION", "BOTH").containsEntry("RB_DOC_NUMBER", "");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> criteria = ArgumentCaptor.forClass(Map.class);
    verify(queries).list(eq("issuedDocuments.search"), criteria.capture());
    assertThat(criteria.getValue()).containsEntry("documentType", "PSI").containsEntry("clientNumber", "00012345")
        .containsEntry("issueDateFrom", "2026-01-01");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Collection<Long>> dcns = ArgumentCaptor.forClass(java.util.Collection.class);
    verify(reads).industryNotReleasable(eq('P'), dcns.capture(), eq("00012345"));
    assertThat(Set.copyOf(dcns.getValue())).containsExactlyInAnyOrder(101L, 102L);
  }

  @Test
  void industry_refusedWhenAnotherIndividualIsAParty() {
    industry();
    when(queries.list(eq("issuedDocuments.search"), anyMap())).thenReturn(List.of(
        Map.of("documentControlNumber", 7L)));
    when(reads.industryNotReleasable(eq('S'), any(), eq("00012345"))).thenReturn(true);
    assertThatThrownBy(() -> guard.apply("HBS3R471", request(Map.of("RB_ISSUE_DATE_FROM", "2026-01-01"))))
        .isInstanceOf(WorkflowValidationException.class)
        .hasMessage("The report contains personal information, and is temporarily unavailable for public viewing");
  }

  @Test
  void industry_ratioStatementsAreNotFoiChecked() {
    industry();
    HbsReportRequestDto out = guard.apply("HBS4R477", request(Map.of("RB_POPULATION", "1234")));
    assertThat(out.parameters()).containsEntry("RB_CLIENT_NUMBER", "00012345");
    verify(queries, never()).list(any(String.class), anyMap());
  }

  @Test
  void agedUnbilledRates() {
    assertThatThrownBy(() -> guard.apply("HBS2R411", request(Map.of("Avg_Rate_Coast", "10", "Avg_Rate_NInterior", "",
        "Avg_Rate_SInterior", "1.555"))))
        .isInstanceOf(WorkflowValidationException.class)
        .satisfies(e -> assertThat(((WorkflowValidationException) e).problems()).extracting("message")
            .containsExactly("Estimated Average Rate is required.",
                "Estimated Average Rate must be numeric currency value."));
    HbsReportRequestDto ok = request(Map.of("Avg_Rate_Coast", "10", "Avg_Rate_NInterior", "9.5",
        "Avg_Rate_SInterior", "8.25"));
    assertThat(guard.apply("HBS2R412", ok)).isSameAs(ok);
  }
}
