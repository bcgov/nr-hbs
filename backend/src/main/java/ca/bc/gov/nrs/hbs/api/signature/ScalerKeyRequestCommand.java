package ca.bc.gov.nrs.hbs.api.signature;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import ca.bc.gov.nrs.hbs.api.workflow.WorkflowCommand;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@code profiles.scalers.requestKey} — P362 Submit → P363 confirmation
 * (legacy {@code P362Action.submitNewKey}). Body: {@code requestedKey}; in
 * managed mode (HBS_SCALE_ADMIN) also {@code licenceNumber}, {@code domain},
 * {@code userId}, {@code fromDate}. Self service (everyone else) acts only on
 * the scaler whose key is active for the caller's own user id — the row
 * fence legacy applied in {@code loadForm}. See {@link ScalerAuthKeyService}.
 */
@Component
public class ScalerKeyRequestCommand implements WorkflowCommand {

  private final ScalerAuthKeyService service;

  public ScalerKeyRequestCommand(ScalerAuthKeyService service) {
    this.service = service;
  }

  @Override
  public String id() {
    return "profiles.scalers.requestKey";
  }

  @Override
  public String legacy() {
    return "P362/P363 (P362Action.submitNewKey)";
  }

  @Override
  public Capability capability() {
    return Capability.SCALER_PROFILE_EDIT;
  }

  @Override
  public Map<String, Object> execute(Map<String, Object> body) {
    // P362Action.perform: selfService = !isUserInRole(user, "HBS_SCALE_ADMIN").
    boolean selfService = !RequestUtil.hasRole(HbsRoles.SCALE_ADMIN);
    return service.request(selfService, RequestUtil.getCurrentAuditUserId(), body);
  }
}
