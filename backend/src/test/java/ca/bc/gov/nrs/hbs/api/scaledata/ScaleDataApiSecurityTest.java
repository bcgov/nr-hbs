package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.DocumentCounts;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionResult;
import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The scale data API through the real filter chains: who gets in (mapped
 * submission accounts with the role and the endpoint's scope), who doesn't, the
 * error contract, and that its tokens can't reach staff endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "hbs.scale-data-api.max-bytes=1000",
    "hbs.scale-data-api.queue-threshold-bytes=500",
    "hbs.scale-data-api.rate-limit.capacity=0"})
class ScaleDataApiSecurityTest {

  static final String BASE = ScaleDataApi.BASE;
  static final String ACME = "hbs-scale-acme";

  @Autowired
  MockMvc mvc;

  @MockitoBean
  JwtDecoder jwtDecoder;

  @MockitoBean
  ApiAccountRegistry accounts;

  @MockitoBean
  ScaleDataIntake intake;

  @MockitoBean
  ScaleDataReads reads;

  @BeforeEach
  void setUp() {
    when(accounts.find(ACME)).thenReturn(Optional.of(new ApiAccount(ACME, List.of("00012345"))));
  }

  /** A token as the scale-data chain's converter would build it. */
  private static RequestPostProcessor token(String azp, List<String> roles, String scope) {
    return jwt().jwt(j -> {
      j.claim(TokenRoles.CLAIM_CLIENT_ROLES, roles).claim(TokenRoles.CLAIM_AZP, azp);
      if (scope != null) {
        j.claim("scope", scope);
      }
    }).authorities(j -> ScaleDataAuthorities.from(j, false));
  }

  private static RequestPostProcessor submitter(String scope) {
    return token(ACME, List.of("HBS_SUBMISSION_API"), scope);
  }

  static TransmissionResult result(long id) {
    return new TransmissionResult(id, "00012345", "f.xml", null, null, "PROCESSED", "HBE", "CSA", false, false, 1, 0,
        1, new DocumentCounts(0, 1, 0, 0, 0, 0, 0), List.of(), false, BASE + "/submissions/" + id,
        BASE + "/submissions/" + id + "/documents");
  }

  // ── who gets in ────────────────────────────────────────────────────────

  @Test
  void noToken_is401WithTheErrorContract() throws Exception {
    mvc.perform(post(BASE + "/submissions").contentType(MediaType.APPLICATION_XML).content("<x/>")
            .header("X-Request-ID", "gw-req-00001234"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(header().string(ScaleDataApi.HEADER_CORRELATION_ID, "gw-req-00001234"))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.correlationId").value("gw-req-00001234"));
    verify(intake, never()).submit(any(), any());
  }

  @Test
  void aSubmitterWithTheSubmitScope_submits() throws Exception {
    when(intake.submit(any(), any())).thenReturn(new ScaleDataIntake.Outcome(HttpStatus.CREATED, result(812345)));
    mvc.perform(post(BASE + "/submissions").with(submitter("hbs.scale-data.submit"))
            .contentType(MediaType.APPLICATION_XML).content("<Submission/>")
            .header(ScaleDataApi.HEADER_FILE_NAME, "SITE42.xml"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", BASE + "/submissions/812345"))
        .andExpect(header().exists(ScaleDataApi.HEADER_CORRELATION_ID))
        .andExpect(jsonPath("$.transmissionId").value(812345))
        .andExpect(jsonPath("$.status").value("PROCESSED"));
    verify(intake).submit(eq(new ApiAccount(ACME, List.of("00012345"))),
        org.mockito.ArgumentMatchers.argThat(u -> "SITE42.xml".equals(u.fileName())
            && new String(u.bytes()).equals("<Submission/>")));
  }

  @Test
  void theWrongScope_is403InsufficientScope() throws Exception {
    mvc.perform(post(BASE + "/submissions").with(submitter("hbs.scale-data.read"))
            .contentType(MediaType.APPLICATION_XML).content("<x/>"))
        .andExpect(status().isForbidden())
        .andExpect(header().string("WWW-Authenticate",
            "Bearer error=\"insufficient_scope\", scope=\"hbs.scale-data.submit\""))
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
    mvc.perform(get(BASE + "/statements").with(submitter("hbs.scale-data.read hbs.scale-data.submit")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
    verify(intake, never()).submit(any(), any());
  }

  @Test
  void aPersonsTokenIsRefusedEvenWithTheScope() throws Exception {
    mvc.perform(get(BASE + "/submissions/1")
            .with(token("hbs-test-client", List.of("HBS_MOF_USER", "HBS_PROD_CTL"), "hbs.scale-data.read")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void aSubmissionTokenCannotUseStaffEndpoints() throws Exception {
    var token = jwt().jwt(j -> j.claim(TokenRoles.CLAIM_CLIENT_ROLES, List.of("HBS_SUBMISSION_API"))
        .claim(TokenRoles.CLAIM_AZP, ACME)).authorities(TokenRoles::authoritiesFrom);
    mvc.perform(get("/api/v1/hbs/commands").with(token)).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/hbs/submissions").with(token)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/hbs/integrations/legacy/process-parameters").with(token))
        .andExpect(status().isForbidden());
  }

  @Test
  void anythingElseUnderThePrefixIsDenied() throws Exception {
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .delete(BASE + "/submissions/1").with(submitter("hbs.scale-data.read hbs.scale-data.submit")))
        .andExpect(status().isForbidden());
    mvc.perform(get(BASE + "/admin").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isForbidden());
  }

  // ── endpoints ──────────────────────────────────────────────────────────

  @Test
  void readsAreFencedToTheAccount() throws Exception {
    when(reads.result(eq(812345L), any())).thenReturn(Optional.of(result(812345)));
    when(reads.result(eq(999L), any())).thenReturn(Optional.empty());
    mvc.perform(get(BASE + "/submissions/812345").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transmissionId").value(812345))
        .andExpect(jsonPath("$", not(hasKey("password"))));
    mvc.perform(get(BASE + "/submissions/999").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get(BASE + "/submissions/abc").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isNotFound());
  }

  @Test
  void listChecksItsParameters() throws Exception {
    mvc.perform(get(BASE + "/submissions").param("updatedSince", "yesterday").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    mvc.perform(get(BASE + "/submissions").param("clientNumber", "00099999").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_PERMITTED"));
    mvc.perform(get(BASE + "/submissions").param("limit", "5000").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isBadRequest());
    verify(reads, never()).list(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void bodiesAreCheckedBeforeTheyReachTheIntake() throws Exception {
    mvc.perform(post(BASE + "/submissions").with(submitter("hbs.scale-data.submit"))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    // Declared larger than the limit (+ multipart allowance): refused before authentication.
    mvc.perform(post(BASE + "/submissions").contentType(MediaType.APPLICATION_XML)
            .content(new byte[1000 + (int) ScaleDataRequestFilter.MULTIPART_OVERHEAD + 1]))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
        .andExpect(jsonPath("$.maxBytes").value(1000));
    // Over the limit but within the allowance: counted by the controller.
    mvc.perform(post(BASE + "/submissions").with(submitter("hbs.scale-data.submit"))
            .contentType(MediaType.APPLICATION_XML).content(new byte[1001]))
        .andExpect(status().isPayloadTooLarge());
    verify(intake, never()).submit(any(), any());
  }

  @Test
  void intakeRefusalsUseTheErrorContract() throws Exception {
    when(intake.submit(any(), any())).thenThrow(ScaleDataApiException.clientNotPermitted());
    mvc.perform(post(BASE + "/submissions").with(submitter("hbs.scale-data.submit"))
            .contentType(MediaType.APPLICATION_XML).content("<x/>"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_PERMITTED"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void unexpectedFailuresAre500WithoutDetails() throws Exception {
    when(reads.result(anyLong(), any())).thenThrow(new IllegalStateException("ORA-00942 secret detail"));
    mvc.perform(get(BASE + "/submissions/1").with(submitter("hbs.scale-data.read")))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message", not(containsString("ORA-"))));
  }

  // ── the client OpenAPI document ────────────────────────────────────────

  @Test
  void theClientOpenApiDocumentHasOnlyTheScaleDataApi() throws Exception {
    mvc.perform(get("/v3/api-docs/" + ScaleDataApi.OPENAPI_GROUP))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("HBS Scale Data API"))
        .andExpect(jsonPath("$.paths", hasKey(BASE + "/submissions")))
        .andExpect(jsonPath("$.paths", hasKey(BASE + "/submissions:validate")))
        .andExpect(jsonPath("$.paths", hasKey(BASE + "/statements/{transmissionId}.{format}")))
        .andExpect(jsonPath("$.paths", not(hasKey("/api/v1/hbs/submissions"))))
        .andExpect(jsonPath("$.components.securitySchemes." + ScaleDataApi.SECURITY_SCHEME
            + ".flows.clientCredentials.scopes", hasKey("hbs.scale-data.submit")))
        .andExpect(jsonPath("$.paths['" + BASE + "/submissions'].post.security[0]."
            + ScaleDataApi.SECURITY_SCHEME + "[0]").value("hbs.scale-data.submit"));
  }
}
