package ca.bc.gov.nrs.hbs.api.sampling;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import org.springframework.stereotype.Component;

/**
 * Who may change a sampling plan: {@code SmpHelper.isMinistryAdmin} /
 * {@code isIndustryAdmin} as used by {@code $V/smp/SamplePlanFunctionsTag}
 * (which buttons P883 shows) and {@code HBSValidator.validateDistrictUser}.
 *
 * <ul>
 *   <li>ministry admin: role HBS_SMP_ADMIN and the plan's district in the
 *       user's HBS_USER_DIST_DATA_DOMAIN rows;</li>
 *   <li>industry admin: role HBS_CLI_SMP_ADMIN and the plan's client = the
 *       user's (active) client.</li>
 * </ul>
 *
 * Caller facts come from overridable methods so the workflow tests can fake them.
 */
@Component
public class SamplingAccess {

  private final SamplingReads reads;

  public SamplingAccess(SamplingReads reads) {
    this.reads = reads;
  }

  protected boolean hasRole(String role) {
    return RequestUtil.hasRole(role);
  }

  /** Legacy HBS_USER.USER_ID form ({@code IDIR\NAME}). */
  protected String userId() {
    return RequestUtil.getCurrentAuditUserId();
  }

  protected String userClientNumber() {
    return RequestUtil.getCurrentClientNumber();
  }

  /** {@code SmpHelper.isMinistryAdmin(request, orgUnit)}. */
  public boolean isMinistryAdmin(Long orgUnitNo) {
    return orgUnitNo != null && hasRole(HbsRoles.SMP_ADMIN) && reads.userHasDistrict(userId(), orgUnitNo);
  }

  /** {@code SmpHelper.isIndustryAdmin(request, clientNo)}. */
  public boolean isIndustryAdmin(String clientNumber) {
    String mine = userClientNumber();
    return hasRole(HbsRoles.CLI_SMP_ADMIN) && clientNumber != null && !clientNumber.isEmpty()
        && clientNumber.equals(mine);
  }

  /** EDIT / PROPOSE / DELETE / COPY / CREATE_ACTUALS_PLAN functions. */
  public boolean isPlanAdmin(Long orgUnitNo, String clientNumber) {
    return isMinistryAdmin(orgUnitNo) || isIndustryAdmin(clientNumber);
  }
}
