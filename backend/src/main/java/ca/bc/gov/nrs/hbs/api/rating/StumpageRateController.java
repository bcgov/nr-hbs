package ca.bc.gov.nrs.hbs.api.rating;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P230/P231 Search for Stumpage Rate. A read, but not a registry query: the
 * legacy rate lookup is the {@code HBS_RATES} OUT-parameter procedures, which
 * a SELECT cannot call ({@link StumpageRateService}).
 *
 * <pre>
 *   GET /api/v1/hbs/rating/stumpage-rate?timberMark&amp;fieldScaleDeckId&amp;species&amp;product&amp;grade
 *       &amp;scaleDate&amp;returnType=Normal|Beachcomb|Waste&amp;beachcombTimberMark&amp;cutBlock
 * </pre>
 * {@code product} is the SCALE_PRODUCT_CODE as is — a single space for logs.
 */
@RestController
@RequestMapping("/api/v1/hbs/rating")
@Tag(name = "Rating", description = "Stumpage rate lookup (legacy P230/P231)")
public class StumpageRateController {

  private final StumpageRateService service;

  public StumpageRateController(StumpageRateService service) {
    this.service = service;
  }

  @GetMapping("/stumpage-rate")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Stumpage rate for one segregation (HBS_RATES), with the mark's appraisal")
  public Map<String, Object> stumpageRate(@RequestParam Map<String, String> params) {
    // P230 was public in the legacy app; here it needs a Rating role (docs/areas/rating-and-sampling.md).
    Capability.RATING_VIEW.require();
    return service.lookup(StumpageRateService.Request.from(new HashMap<String, Object>(params)));
  }
}
