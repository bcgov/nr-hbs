package ca.bc.gov.nrs.hbs.api.config;

import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import ca.bc.gov.nrs.hbs.api.users.HbsUserProvisioningInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration (request/response logging interceptor).
 */
@Configuration
public class HbsApiMVCConfig implements WebMvcConfigurer {

  /**
   * The School api interceptor.
   */
  @Getter(AccessLevel.PRIVATE)
  private final RequestResponseInterceptor requestResponseInterceptor;

  private final HbsUserProvisioningInterceptor userProvisioningInterceptor;
  /**
   * Instantiates a new School api mvc config.
   *
   * @param requestResponseInterceptor the School api interceptor
   */
  @Autowired
  public HbsApiMVCConfig(final RequestResponseInterceptor requestResponseInterceptor,
      final HbsUserProvisioningInterceptor userProvisioningInterceptor) {
    this.requestResponseInterceptor = requestResponseInterceptor;
    this.userProvisioningInterceptor = userProvisioningInterceptor;
  }

  /**
   * Add interceptors.
   *
   * @param registry the registry
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(requestResponseInterceptor).addPathPatterns("/**");
    // Keeps the legacy HBS_USER row current for logged-in users (docs/coexistence.md).
    registry.addInterceptor(userProvisioningInterceptor)
        .addPathPatterns("/api/v1/hbs/**")
        .excludePathPatterns("/api/v1/hbs/public/**");
  }
}
