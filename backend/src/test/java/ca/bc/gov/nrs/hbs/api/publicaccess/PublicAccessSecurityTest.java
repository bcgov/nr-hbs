package ca.bc.gov.nrs.hbs.api.publicaccess;

import ca.bc.gov.nrs.hbs.api.queries.StatementDocumentService;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.query.Viewer;
import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public area through the real filter chains: anonymous GETs allowed on
 * /api/v1/hbs/public/** only, always answered as the anonymous viewer (even
 * with a ministry token), nothing else opened up. QueryService and the
 * statement service are mocked (no Oracle in unit tests).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicAccessSecurityTest {

  @Autowired
  MockMvc mvc;

  @MockitoBean
  JwtDecoder jwtDecoder;

  @MockitoBean
  QueryService queryService;

  @MockitoBean
  StatementDocumentService documents;

  private static RequestPostProcessor ministryToken() {
    return jwt().jwt(j -> j.claim(TokenRoles.CLAIM_CLIENT_ROLES, List.of("HBS_MOF_USER"))
            .claim(TokenRoles.CLAIM_AZP, "hbs-test-client")
            .claim("identity_provider", "azureidir")
            .claim("idir_username", "JSMITH"))
        .authorities(TokenRoles::authoritiesFrom);
  }

  private static PageableResponse<Map<String, Object>> page(Map<String, Object> row) {
    return PageableResponse.ofPage(List.of(row), 0, 20, 1);
  }

  @Test
  void anonymousSearchIsAllowedAndRunsAsTheAnonymousViewer() throws Exception {
    when(queryService.search(any(QueryDefinition.class), any(Viewer.class), anyMap(), anyInt(), anyInt(),
        any(), any())).thenReturn(page(Map.of("timberMark", "AB1234", "clientName", "Not Releasable")));

    mvc.perform(get("/api/v1/hbs/public/queries/timberMarks.search").param("timberMark", "AB"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].clientName").value("Not Releasable"));

    ArgumentCaptor<QueryDefinition> q = ArgumentCaptor.forClass(QueryDefinition.class);
    verify(queryService).search(q.capture(), eq(Viewer.ANONYMOUS), anyMap(), anyInt(), anyInt(), isNull(), isNull());
    assertThat(q.getValue().id()).isEqualTo("timberMarks.search");
    assertThat(q.getValue().clientScope()).isNull();
  }

  @Test
  void aTokenOnAPublicPathIsIgnored_stillAnonymous() throws Exception {
    when(queryService.list(any(QueryDefinition.class), any(Viewer.class), anyMap())).thenReturn(List.of());
    mvc.perform(get("/api/v1/hbs/public/queries/codes.orgUnits/list").with(ministryToken()))
        .andExpect(status().isOk());
    verify(queryService).list(any(QueryDefinition.class), eq(Viewer.ANONYMOUS), anyMap());
  }

  @Test
  void issuedDocumentLinksPointAtThePublicStatementEndpoint() throws Exception {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("statementNumber", "A1234567");
    row.put("documentXmlUrl", "/api/v1/hbs/statements/PSI/A1234567.xml");
    when(queryService.search(any(QueryDefinition.class), any(Viewer.class), anyMap(), anyInt(), anyInt(),
        any(), any())).thenReturn(page(row));
    mvc.perform(get("/api/v1/hbs/public/queries/issuedDocuments.search").param("statementNumber", "A1234567"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].documentXmlUrl").value("/api/v1/hbs/public/statements/PSI/A1234567.xml"));
  }

  @Test
  void publicSearchesKeepTheLegacyFormRules() throws Exception {
    mvc.perform(get("/api/v1/hbs/public/queries/issuedDocuments.search").param("documentType", "PSI"))
        .andExpect(status().isBadRequest());
    verify(queryService, never()).search(any(QueryDefinition.class), any(Viewer.class), anyMap(), anyInt(),
        anyInt(), any(), any());
  }

  @Test
  void statementCopyIsSeveredForTheAnonymousViewer() throws Exception {
    when(documents.xml("PSI", "A1234567", Viewer.ANONYMOUS))
        .thenReturn(new StatementDocumentService.StatementDocument("A1234567.xml", "<x/>"));
    mvc.perform(get("/api/v1/hbs/public/statements/PSI/A1234567.xml"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("A1234567.xml")));
    verify(documents).xml("PSI", "A1234567", Viewer.ANONYMOUS);
  }

  @Test
  void nonWhitelistedQueriesAre404() throws Exception {
    mvc.perform(get("/api/v1/hbs/public/queries/agedUnbilled.search")).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/hbs/public/queries/statementTransmissions.search")).andExpect(status().isNotFound());
  }

  @Test
  void onlyGetIsOpen() throws Exception {
    mvc.perform(post("/api/v1/hbs/public/queries/timberMarks.search")).andExpect(status().isForbidden());
    mvc.perform(delete("/api/v1/hbs/public/statements/PSI/A1.xml")).andExpect(status().isForbidden());
  }

  @Test
  void everythingElseStillNeedsAToken() throws Exception {
    mvc.perform(get("/api/v1/hbs/queries/timberMarks.search").param("timberMark", "AB"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/hbs/statements/PSI/A1234567.xml")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/hbs/publicity")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/hbs/reports")).andExpect(status().isUnauthorized());
  }

  @Test
  void historyRangeIsCheckedBeforeTheDatabase() throws Exception {
    String from = LocalDate.now().withDayOfMonth(1).minusMonths(30).toString();
    String to = LocalDate.now().withDayOfMonth(1).minusMonths(2).toString();
    mvc.perform(get("/api/v1/hbs/public/queries/harvestHistory.billing")
            .param("billingFrom", from).param("billingTo", to).param("timberMarks", "AB1234"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("You may not specify a date range over one year."));
  }
}
