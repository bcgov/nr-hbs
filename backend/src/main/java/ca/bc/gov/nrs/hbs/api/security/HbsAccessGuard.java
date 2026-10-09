package ca.bc.gov.nrs.hbs.api.security;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Row-level fence — the HBS counterpart of nr-fsp-new's {@code FspAccessGuard}.
 *
 * <p>The legacy app scoped data three ways (see docs/roles-and-security.md):
 * <ol>
 *   <li><b>Client</b> — industry (CLI/SPC) users only ever see invoices,
 *       statements and scale returns whose payer / mark holder / submitter is
 *       their own client number. Enforced here against the active client.</li>
 *   <li><b>District / scale site / client location</b> data domains
 *       ({@code HBS_USER_DIST_DATA_DOMAIN}, {@code HBS_USER_SITE_DATA_DOMAIN},
 *       {@code HBS_USER_CLI_LOC_DATA_DOMAIN}) for administrative writes —
 *       enforced in the services that perform those writes.</li>
 *   <li><b>Releasability</b> — the report procs blank client names that are
 *       not releasable for non-ministry users; we pass the legacy user type
 *       through so they keep doing so.</li>
 * </ol>
 *
 * <p>Ministry users pass the client fence unconditionally.
 */
@Component
public class HbsAccessGuard {

  /**
   * Narrows a client-number filter to the caller's own client for industry
   * users (whatever they asked for), and passes ministry filters through.
   */
  public String scopeClientFilter(String requestedClientNumber) {
    if (RequestUtil.isMinistryUser()) {
      return requestedClientNumber;
    }
    String own = RequestUtil.getCurrentClientNumber();
    if (!StringUtils.hasText(own)) {
      throw new AccessDeniedException("hbs.no_access_right: no client scope on token");
    }
    return own;
  }

  /** Throws 403 unless the caller is ministry or the record belongs to their client. */
  public void assertClientAccess(String recordClientNumber) {
    if (RequestUtil.isMinistryUser()) return;
    String own = RequestUtil.getCurrentClientNumber();
    if (!StringUtils.hasText(own) || !own.equals(normalize(recordClientNumber))) {
      throw new AccessDeniedException("hbs.no_access_right");
    }
  }

  private static String normalize(String clientNumber) {
    if (clientNumber == null) return "";
    String trimmed = clientNumber.trim();
    // Legacy screens pass client+location as one 10-char key.
    return trimmed.length() > 8 ? trimmed.substring(0, 8) : trimmed;
  }
}
