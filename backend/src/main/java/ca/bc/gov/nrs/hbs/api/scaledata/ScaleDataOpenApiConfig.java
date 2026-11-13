package ca.bc.gov.nrs.hbs.api.scaledata;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Two OpenAPI documents (springdoc groups):
 *
 * <ul>
 *   <li>{@code /v3/api-docs/scale-data}: the client-facing scale data API only,
 *       with the OAuth 2.0 client-credentials scheme and its three scopes. This
 *       is the document the gateway publishes; the gateway reaches the backend
 *       Service in-cluster (backend/openshift.scale-data-gateway.yml) and its
 *       configuration exposes only this path and the API. Swagger UI:
 *       {@code /swagger-ui/index.html?urls.primaryName=scale-data} (internal).</li>
 *   <li>{@code /v3/api-docs/hbs}: everything else (staff and public endpoints),
 *       internal only: neither the frontend route nor the gateway exposes it.</li>
 * </ul>
 */
@Configuration
public class ScaleDataOpenApiConfig {

  static final String STAFF_GROUP = "hbs";

  @Bean
  public GroupedOpenApi scaleDataOpenApi(ScaleDataApiProperties properties) {
    return GroupedOpenApi.builder()
        .group(ScaleDataApi.OPENAPI_GROUP)
        .displayName("HBS Scale Data API (clients and software vendors)")
        .pathsToMatch(ScaleDataApi.PATHS)
        .addOpenApiCustomizer(openApi -> {
          openApi.info(new Info()
              .title("HBS Scale Data API")
              .version("v1")
              .description("""
                  Send scale return XML files to the Harvest Billing System and collect statements, over HTTPS. \
                  Replaces the FTP inbound and outbound folders. The file format is unchanged \
                  (HBS_Schema_V6_1b, namespace gov.bc.ca/forests/hbs/v3).

                  **Authentication.** OAuth 2.0 client credentials: exchange your client id and secret at the \
                  token URL for a short-lived access token, and send it on every call as \
                  `Authorization: Bearer <token>`. Each endpoint needs one scope: \
                  `hbs.scale-data.submit` (send and check files), `hbs.scale-data.read` (read results), \
                  `hbs.statements.read` (statements). Your account is set up for specific client numbers; \
                  name the client in `X-HBS-Client-Number` if you submit for more than one.

                  **Errors** share one body: `{ code, message, errors[], correlationId }`. Quote the \
                  correlationId (also in the X-Correlation-ID header) to the HBS help desk.""")
              .contact(new Contact().name("HBS help desk").email("FORHVAP.HBSHELP@gov.bc.ca")));
          if (StringUtils.hasText(properties.publicBaseUrl())) {
            openApi.servers(List.of(new Server().url(properties.publicBaseUrl()).description("HBS API gateway")));
          }
          Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
          components.addSecuritySchemes(ScaleDataApi.SECURITY_SCHEME, new SecurityScheme()
              .type(SecurityScheme.Type.OAUTH2)
              .description("Client credentials from BC Gov SSO. One account per submitting organization.")
              .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                  .tokenUrl(properties.tokenUrl())
                  .scopes(new Scopes()
                      .addString(ScaleDataApi.SCOPE_SUBMIT, "Send and check scale data files")
                      .addString(ScaleDataApi.SCOPE_READ, "Read file results and documents")
                      .addString(ScaleDataApi.SCOPE_STATEMENTS, "List and download statements")))));
          openApi.components(components);
        })
        .build();
  }

  @Bean
  public GroupedOpenApi staffOpenApi() {
    return GroupedOpenApi.builder()
        .group(STAFF_GROUP)
        .displayName("HBS (staff and public, internal)")
        .pathsToMatch("/**")
        .pathsToExclude(ScaleDataApi.PATHS)
        .build();
  }
}
