package ca.bc.gov.nrs.hbs.api.signature;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.security.HbsRoles;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P362 load — the defaults the key-request page shows (legacy
 * {@code P362Action.loadForm}): managed or self service, the scaler, the key's
 * HBS user, whether a key is active, the default / latest From date and the
 * aging period. The submit is the {@code profiles.scalers.requestKey} command.
 */
@RestController
@RequestMapping("/api/v1/hbs/scaler-keys")
@Tag(name = "Scaler authentication keys", description = "Legacy P362/P363 scaler key requests")
public class ScalerKeyController {

  private final ScalerAuthKeyService service;
  private final SignatureKeyProperties keys;

  public ScalerKeyController(ScalerAuthKeyService service, SignatureKeyProperties keys) {
    this.service = service;
    this.keys = keys;
  }

  public record RequestFormResponse(ScalerAuthKeyService.RequestForm form, boolean keyIssuingAvailable) {}

  @GetMapping("/request")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Defaults for a scaler authentication key request (P362 load)")
  public RequestFormResponse load(@RequestParam(required = false) String licenceNumber) {
    Capability.SCALER_PROFILE_EDIT.require();
    boolean selfService = !RequestUtil.hasRole(HbsRoles.SCALE_ADMIN);
    return new RequestFormResponse(
        service.load(selfService, RequestUtil.getCurrentAuditUserId(), licenceNumber),
        keys.cryptoKeyConfigured());
  }
}
