package ca.bc.gov.nrs.hbs.api.admin;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Client fence for the {@code admin.userDataDomains.*} registry commands
 * (AdminCatalog). A {@code CommandDefinition} has no client scope of its own,
 * but the legacy data-domain actions (SiteDataDomainAction,
 * DistrictDataDomainAction, LocationsDomainAction) only let a client / SPC
 * administrator change users of their own client — never ministry users.
 * This advice enforces that before the proc runs:
 *
 * <ul>
 *   <li>ministry callers (MOF_ADMIN / MOF_MGR) may change any user;</li>
 *   <li>industry callers may only target an HBS_USER of their active client
 *       and their own user type (CLI / SPC);</li>
 *   <li>district domains apply only to ministry roles, so industry callers
 *       may not add/remove them;</li>
 *   <li>an industry caller may only add scale sites their client owns
 *       (the legacy P018 list showed only those sites).</li>
 * </ul>
 * Client-location domains are already forced to the caller's client by the
 * command's CLIENT_NUMBER argument.
 */
@Aspect
@Component
public class UserDataDomainGuard {

  static final String PREFIX = "admin.userDataDomains.";

  private final JdbcTemplate jdbc;

  public UserDataDomainGuard(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Before("execution(* ca.bc.gov.nrs.hbs.api.query.CommandService.execute(..))")
  public void guard(JoinPoint jp) {
    Object[] args = jp.getArgs();
    if (args.length < 2 || !(args[0] instanceof String commandId) || !commandId.startsWith(PREFIX)) {
      return;
    }
    if (RequestUtil.isMinistryUser()) {
      return;
    }
    Map<?, ?> body = args[1] instanceof Map<?, ?> m ? m : Map.of();
    String userId = text(body.get("userId"));
    String client = RequestUtil.getCurrentClientNumber();
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(client)) {
      throw new AccessDeniedException("hbs.no_access_right: no client scope for user data domains");
    }
    if (commandId.endsWith("District")) {
      throw new AccessDeniedException("hbs.no_access_right: district data domains are ministry-only");
    }
    Integer own = jdbc.queryForObject(
        "SELECT COUNT(*) FROM hbs_user WHERE user_id = ? AND client_number = ? AND hbs_user_type_code = ?",
        Integer.class, userId.toUpperCase(), client, RequestUtil.getLegacyUserType());
    if (own == null || own == 0) {
      throw new AccessDeniedException("hbs.no_access_right: you are not authorized to access this user");
    }
    if ("admin.userDataDomains.addSite".equals(commandId)) {
      Integer site = jdbc.queryForObject(
          "SELECT COUNT(*) FROM scale_site WHERE scale_site_id_nmbr = ? AND owner_cli_number = ?",
          Integer.class, text(body.get("scaleSiteIdNmbr")).toUpperCase(), client);
      if (site == null || site == 0) {
        throw new AccessDeniedException("hbs.no_access_right: scale site is not owned by your client");
      }
    }
  }

  private static String text(Object o) {
    return o == null ? "" : o.toString().trim();
  }
}
