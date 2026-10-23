package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.util.RequestUtil;

/**
 * Who a query or document is being produced for — the values behind the viewer
 * binds every registry query receives ({@code :hbsIsMinistry},
 * {@code :hbsViewerClient}, {@code :hbsUserType}, {@code :hbsUserId}) and the
 * statement FOI check.
 *
 * <p>Authenticated requests use {@link #current()} (read off the JWT). The
 * public read-only area ({@code /api/v1/hbs/public/**}) always uses
 * {@link #ANONYMOUS}, whatever the request carries: not ministry, no client,
 * user type {@code PUB} (legacy {@code Constants.PARAM_USR_PUB}, the type the
 * legacy app gave its {@code Public_User}) and no user id — so every FOI
 * {@code CASE} severs and no client fence can apply.
 *
 * @param ministry     the viewer holds a ministry (IDIR) role
 * @param clientNumber the viewer's active forest client ("" for ministry / anonymous)
 * @param userType     legacy user-type code: MOF / CLI / SPC / PUB
 * @param userId       legacy {@code HBS_USER.USER_ID} form ({@code IDIR\NAME}), "" when anonymous
 */
public record Viewer(boolean ministry, String clientNumber, String userType, String userId) {

  /** The anonymous public viewer (legacy {@code Public_User}). */
  public static final Viewer ANONYMOUS = new Viewer(false, "", "PUB", "");

  public Viewer {
    clientNumber = clientNumber == null ? "" : clientNumber;
    userType = userType == null ? "PUB" : userType;
    userId = userId == null ? "" : userId;
  }

  /** The caller of the current authenticated request. */
  public static Viewer current() {
    boolean ministry = RequestUtil.isMinistryUser();
    return new Viewer(ministry, ministry ? "" : RequestUtil.getCurrentClientNumber(),
        RequestUtil.getLegacyUserType(), RequestUtil.getCurrentAuditUserId().toUpperCase());
  }

  public boolean anonymous() {
    return !ministry && clientNumber.isEmpty() && userId.isEmpty();
  }
}
