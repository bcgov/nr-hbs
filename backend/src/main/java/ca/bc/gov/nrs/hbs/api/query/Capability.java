package ca.bc.gov.nrs.hbs.api.query;

import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import org.springframework.security.access.AccessDeniedException;

import java.util.Set;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.*;

/**
 * Programmatic form of the {@link ca.bc.gov.nrs.hbs.api.security.HbsAuthorities}
 * capability matrix, used by the registry-driven query/command endpoints where
 * the guarded resource is chosen at runtime (so a static {@code @PreAuthorize}
 * can't name it). The frontend mirrors this enum in {@code src/routes/access.ts}.
 *
 * <p>Derived from the legacy WebADE action grants (hbs_action_lnk.sql) —
 * see docs/roles-and-security.md for the row-by-row mapping.
 */
public enum Capability {
  ANY_USER(MOF_USER, CLI_USER, SPC_USER),
  MINISTRY(MOF_USER),

  QUERIES_VIEW(MOF_USER, CLI_USER),

  SCALE_RETURNS_VIEW(MOF_USER, CLI_USER, SPC_USER),
  SUMMARY_RETURN_EDIT(SUMM_DATA_CTL, SUMM_DATA_CORR, SCALE_ADMIN, BILL_ADMIN,
      CLI_SCALER, CLI_SITE_ADMIN, CLI_CRUISE_ADMIN, SPC_SUBM_AGNT),
  SUMMARY_RETURN_ADMIN(BILL_ADMIN),
  DETAIL_RETURN_EDIT(DTL_DATA_ENT, SCALE_ADMIN, MOF_SCALER, CLI_SCALER, CLI_SITE_ADMIN),
  DETAIL_RETURN_APPROVE(BILL_ADMIN, INV_CORR_APP),
  XML_SUBMIT(DTL_DATA_ENT, MOF_SCALER, SCALE_ADMIN, CLI_SITE_ADMIN, CLI_SCALER,
      SPC_SFTWR_VNDR, SPC_SUBM_AGNT),
  BATCH_SLIP_EDIT(BILL_ADMIN, DTL_DATA_ENT, PROD_CTL, SCALE_ADMIN, SUMM_DATA_CTL),
  PRODUCTION_CONTROL(PROD_CTL),

  SAMPLING_VIEW(MOF_USER, CLI_USER),
  SAMPLING_ADMIN(SMP_ADMIN, CLI_SMP_ADMIN),

  RATING_VIEW(MOF_USER, CLI_USER),
  RATING_MINISTRY_VIEW(MOF_USER),
  RATE_ADMIN(HbsRoles.RATE_ADMIN),
  WASTE_RATE_ADMIN(WRA_ADMIN),

  BILLING_VIEW(MOF_USER, CLI_USER),
  BILLING_ADMIN(BILL_ADMIN, SUPER_MGR),
  FINAL_BILL_ADMIN(BILL_ADMIN, SUPER_MGR),
  FIELD_SCALED_DECKS(BILL_ADMIN, HbsRoles.SCALE_ADMIN, SUPER_MGR),
  BILLING_FORCE(SUPER_MGR),

  PROFILES_VIEW(MOF_USER, CLI_USER),
  SCALE_SITE_ADMIN(HbsRoles.SCALE_ADMIN),
  PROFILE_BILLING_ADMIN(BILL_ADMIN),
  SCALER_PROFILE_EDIT(HbsRoles.SCALE_ADMIN, CLI_SCALER),

  SCALE_CONTROL_VIEW(MOF_USER, CLI_USER),
  SCALE_CONTROL_MINISTRY_VIEW(MOF_USER),
  SCALE_CONTROL_ADMIN(HbsRoles.SCALE_ADMIN),
  ANOMALY_EDIT(HbsRoles.SCALE_ADMIN, CLI_SCALER, CLI_SITE_ADMIN),
  SOFTWARE_USE_VIEW(MOF_USER, CLI_USER, SPC_SFTWR_VNDR),

  USER_ADMIN(MOF_ADMIN, MOF_MGR, CLI_ADMIN, SPC_ADMIN),
  CACHE_ADMIN(PROD_CTL);

  private final Set<String> roles;

  Capability(String... roles) {
    this.roles = Set.of(roles);
  }

  public Set<String> roles() {
    return roles;
  }

  /** True when the caller (roles scoped to the active client) holds this capability. */
  public boolean isGranted() {
    var mine = RequestUtil.getCurrentRoles();
    return roles.stream().anyMatch(mine::contains);
  }

  /** 403 unless the caller holds this capability. */
  public void require() {
    if (!isGranted()) {
      throw new AccessDeniedException("hbs.no_access_right: missing capability " + name());
    }
  }
}
