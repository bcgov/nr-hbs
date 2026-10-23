package ca.bc.gov.nrs.hbs.api.profiles;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.util.Locale;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The caller as the profile rules need it: roles (for the legacy
 * {@code DacHelper} data-domain branches), the legacy {@code HBS_USER.USER_ID}
 * and the client fence. A bean so tests can stand in for the JWT.
 */
@Component
public class ProfileCaller {

  /** Legacy HBS_USER.USER_ID form ({@code IDIR\NAME}), upper-cased as QueryService binds {@code :hbsUserId}. */
  public String userId() {
    return RequestUtil.getCurrentAuditUserId().toUpperCase(Locale.ROOT);
  }

  public boolean hasRole(String canonicalRole) {
    return RequestUtil.hasRole(canonicalRole);
  }

  public boolean isMinistry() {
    return RequestUtil.isMinistryUser();
  }

  public String clientNumber() {
    return RequestUtil.getCurrentClientNumber();
  }

  /** 403 unless the caller is ministry or the record belongs to their active client. */
  public void requireClient(String recordClientNumber) {
    if (isMinistry()) return;
    String own = clientNumber();
    if (!StringUtils.hasText(own) || !own.equals(recordClientNumber == null ? "" : recordClientNumber.trim())) {
      throw new AccessDeniedException("hbs.no_access_right");
    }
  }
}
