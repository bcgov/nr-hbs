package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;
import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole;
import static org.springframework.security.authorization.AuthorizationManagers.allOf;

import ca.bc.gov.nrs.hbs.api.config.HbsSecurityConfig;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Security of the scale data API: its own filter chain for
 * {@code /api/v1/hbs/integrations/scale-data/**}, ordered after the public
 * area's and before the application's default chain. The gateway in front is
 * the first gate, not the only one (docs/scale-data-api-hosting.md): every
 * check here holds even if the gateway is misconfigured or bypassed.
 *
 * <ol>
 *   <li><b>Its own JWT decoder.</b> Signature (realm JWKS), issuer, expiry, and
 *       {@link ScaleDataTokenValidator}: {@code azp} is a mapped submission
 *       account and the only HBS role is {@code HBS_SUBMISSION_API}. Staff,
 *       BCeID and share-bridge tokens are refused (401). The staff chain's
 *       decoder in turn never accepts a submission account's {@code azp}, so
 *       these tokens are 401 on every other HBS endpoint.</li>
 *   <li><b>Role and scope per endpoint.</b> {@code HBS_SUBMISSION_API} and the
 *       endpoint's OAuth scope ({@link ScaleDataApi}): submit, read or
 *       statements. Missing scope → 403 {@code insufficient_scope}. Any other
 *       path or method under the prefix is denied.</li>
 *   <li><b>Per-account checks</b> in the controllers: the client number must be
 *       one the account is mapped to (403 CLIENT_NOT_PERMITTED), and reads only
 *       see the account's own clients (404 otherwise).</li>
 *   <li>Stateless, no CSRF (bearer tokens only, no cookies), no sessions; a
 *       correlation id, a size limit before the body is read, and a backstop
 *       per-account rate limit.</li>
 * </ol>
 */
@Configuration
@Slf4j
public class ScaleDataApiSecurityConfig {

  private static final String SUBMISSIONS = ScaleDataApi.BASE + "/submissions";
  private static final String STATEMENTS = ScaleDataApi.BASE + "/statements";

  @Bean
  @Order(2)
  public SecurityFilterChain scaleDataFilterChain(HttpSecurity http, ScaleDataApiProperties properties,
      ApiAccountRegistry accounts) throws Exception {
    log.info("Scale data API {}: tokens from {}, {} account(s) mapped", properties.enabled() ? "enabled" : "disabled",
        properties.issuerUri(), accounts.all().size());

    NimbusJwtDecoder decoder = HbsSecurityConfig.buildJwtDecoder(properties.jwkSetUri());
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
        new ScaleDataTokenValidator(accounts)));
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> ScaleDataAuthorities.from(jwt, properties.acceptRoleScopes()));
    ScaleDataErrors errors = new ScaleDataErrors();

    http
        .securityMatcher(ScaleDataApi.PATHS)
        // Bearer tokens only (no cookie or session is ever issued), so CSRF does not apply.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .anonymous(AbstractHttpConfigurer::disable)
        .addFilterBefore(new ScaleDataRequestFilter(properties), BearerTokenAuthenticationFilter.class)
        .addFilterAfter(new ScaleDataRateLimitFilter(properties.rateLimitCapacity(), properties.rateLimitPerMinute()),
            BearerTokenAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, SUBMISSIONS, SUBMISSIONS + ":validate")
                .access(scope(ScaleDataApi.SCOPE_SUBMIT))
            .requestMatchers(HttpMethod.GET, SUBMISSIONS, SUBMISSIONS + "/*", SUBMISSIONS + "/*/documents")
                .access(scope(ScaleDataApi.SCOPE_READ))
            .requestMatchers(HttpMethod.GET, STATEMENTS, STATEMENTS + "/*")
                .access(scope(ScaleDataApi.SCOPE_STATEMENTS))
            .anyRequest().denyAll())
        .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))
            .authenticationEntryPoint(errors)
            .accessDeniedHandler(errors));
    return http.build();
  }

  /** The submission role and the scope, both required. */
  static AuthorizationManager<RequestAuthorizationContext> scope(String scope) {
    return allOf(hasRole(HbsRoles.SUBMISSION_API), hasAuthority("SCOPE_" + scope));
  }
}
