package ca.bc.gov.nrs.hbs.api.config;

import ca.bc.gov.nrs.hbs.api.publicaccess.PublicRateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security for the public (anonymous) read-only area — {@code /api/v1/hbs/public/**}
 * and nothing else (docs/public-access.md).
 *
 * <p>A separate filter chain, ordered before the application's default chain
 * ({@code HbsApiResourceApplication.WebSecurityConfiguration}, which stays
 * unchanged: every other path still needs a valid HBS bearer token):
 * <ul>
 *   <li>only {@code GET} is permitted, anonymously; every other method is denied;</li>
 *   <li>no OAuth2 resource server on this chain — an {@code Authorization} header
 *       is ignored, so a logged-in user's token can neither widen nor break what
 *       the public endpoints return (they always answer as the anonymous viewer);</li>
 *   <li>stateless, CSRF off — the same reasoning as the default chain: no cookie or
 *       session is ever issued, and the chain only allows safe (GET) requests.</li>
 * </ul>
 * The rate-limit / concurrency filter ({@link PublicRateLimitFilter}) is a servlet
 * filter registered for the public paths only, ahead of Spring Security.
 */
@Configuration
public class PublicAccessSecurityConfig {

  static final String PUBLIC_PATTERN = "/api/v1/hbs/public/**";

  @Bean
  @Order(1)
  public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
    http
        .securityMatcher(PUBLIC_PATTERN)
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, PUBLIC_PATTERN).permitAll()
            .anyRequest().denyAll())
        .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);
    return http.build();
  }

  @Bean
  public FilterRegistrationBean<PublicRateLimitFilter> publicRateLimitFilter(
      @Value("${hbs.public.enabled:true}") boolean enabled,
      @Value("${hbs.public.rate-limit.capacity:30}") int capacity,
      @Value("${hbs.public.rate-limit.refill-per-minute:60}") int refillPerMinute,
      @Value("${hbs.public.rate-limit.max-concurrent:4}") int maxConcurrent,
      @Value("${hbs.public.rate-limit.client-ip-header:X-Real-IP}") String clientIpHeader) {
    FilterRegistrationBean<PublicRateLimitFilter> bean = new FilterRegistrationBean<>(
        new PublicRateLimitFilter(enabled, capacity, refillPerMinute, maxConcurrent, clientIpHeader));
    bean.addUrlPatterns("/api/v1/hbs/public/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    bean.setName("publicRateLimitFilter");
    return bean;
  }
}
