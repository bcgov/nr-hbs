package ca.bc.gov.nrs.hbs.api.users;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Runs {@link HbsUserProvisioning} for authenticated API calls (registered in HbsApiMVCConfig). */
@Component
public class HbsUserProvisioningInterceptor implements HandlerInterceptor {

  private final HbsUserProvisioning provisioning;

  public HbsUserProvisioningInterceptor(HbsUserProvisioning provisioning) {
    this.provisioning = provisioning;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    provisioning.provisionCurrentUser();
    return true;
  }
}
