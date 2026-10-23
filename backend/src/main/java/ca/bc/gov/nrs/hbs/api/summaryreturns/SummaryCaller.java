package ca.bc.gov.nrs.hbs.api.summaryreturns;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.util.Locale;
import java.util.Set;

/**
 * Who is acting, as the legacy {@code HBSUser} carried it into the summary-return
 * actions and tags: the legacy user id (for audits and the HBS_USER_* data domains),
 * the canonical HBS roles, and the active forest client of an industry user.
 */
public record SummaryCaller(String userId, Set<String> roles, boolean ministry, String clientNumber) {

  /** The caller of the current request. */
  public static SummaryCaller current(String auditUser) {
    return new SummaryCaller(auditUser == null ? "" : auditUser.toUpperCase(Locale.ROOT),
        RequestUtil.getCurrentRoles(), RequestUtil.isMinistryUser(), RequestUtil.getCurrentClientNumber());
  }

  /** {@code HBSUser.isUserInRole}. */
  public boolean has(String role) {
    return roles.contains(role);
  }

  public boolean hasAny(String... any) {
    for (String r : any) {
      if (roles.contains(r)) return true;
    }
    return false;
  }
}
