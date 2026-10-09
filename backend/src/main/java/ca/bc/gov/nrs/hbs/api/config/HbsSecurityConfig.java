package ca.bc.gov.nrs.hbs.api.config;

import ca.bc.gov.nrs.hbs.api.security.AuthorizedPartyValidator;
import ca.bc.gov.nrs.hbs.api.security.HbsRoleValidator;
import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Validates BC Gov SSO (Keycloak, standard realm) <b>access tokens</b> — the
 * same setup as nr-fta / nr-rept (there is no AWS Cognito any more):
 *
 * <ul>
 *   <li>signature against the realm's JWKS
 *       ({@code <issuer>/protocol/openid-connect/certs}), cached with
 *       refresh-ahead and retry so a brief IdP hiccup doesn't 401 users;</li>
 *   <li>issuer + expiry;</li>
 *   <li>{@code azp} must be HBS's CSS client id ({@link AuthorizedPartyValidator});</li>
 *   <li>at least one HBS role in {@code client_roles} ({@link HbsRoleValidator}).</li>
 * </ul>
 *
 * <p>Authorities are {@code ROLE_<HBS base role>} for every role on the token
 * (roles stack; FAM scope suffixes stripped) — see {@link TokenRoles}.
 */
@Configuration
@EnableMethodSecurity
public class HbsSecurityConfig {

  private static final Logger LOG = LoggerFactory.getLogger(HbsSecurityConfig.class);

  @Bean
  public JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
      @Value("${hbs.keycloak.client-id}") String expectedClientId) {
    LOG.info("Validating Keycloak access tokens from {} for client {}", issuerUri, expectedClientId);
    NimbusJwtDecoder decoder = buildJwtDecoder(jwkSetUri);
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        new AuthorizedPartyValidator(expectedClientId),
        new HbsRoleValidator()));
    return decoder;
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(TokenRoles::authoritiesFrom);
    return converter;
  }

  /**
   * Nimbus {@link JWKSourceBuilder}: cached JWKS with refresh-ahead and retry,
   * explicit timeouts (nr-fta's decoder, which replaced Spring's default after
   * intermittent "Connect timed out" 401s).
   */
  private static NimbusJwtDecoder buildJwtDecoder(String jwkSetUri) {
    URL jwkSetUrl;
    try {
      jwkSetUrl = new URI(jwkSetUri).toURL();
    } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
      throw new IllegalStateException("Invalid jwk-set-uri: " + jwkSetUri, e);
    }
    DefaultResourceRetriever retriever = new DefaultResourceRetriever(
        (int) Duration.ofSeconds(10).toMillis(), (int) Duration.ofSeconds(15).toMillis(), 50 * 1024);
    JWKSource<SecurityContext> jwkSource = JWKSourceBuilder.create(jwkSetUrl, retriever)
        .retrying(true)
        .refreshAheadCache(true)
        .build();
    ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
    return new NimbusJwtDecoder(processor);
  }
}
