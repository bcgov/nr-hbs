package ca.bc.gov.nrs.hbs.api.controller.v1;

import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.service.v1.UserDirectoryService;
import ca.bc.gov.nrs.hbs.api.struct.v1.UserResolveRequest;
import ca.bc.gov.nrs.hbs.api.struct.v1.UserSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * IDIR / BCeID directory endpoints (nr-user-lookup-api pass-through) — same
 * contract as nr-fsp-new's /users/search and /users/resolve, used by the
 * shared UserSearchModal and the &lt;UserName&gt; display-name cache.
 */
@RestController
@RequestMapping("/api/v1/hbs/users")
@Tag(name = "Users", description = "IDIR/BCeID directory lookups")
public class UserApiController {

  private final UserDirectoryService userDirectoryService;

  public UserApiController(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  @GetMapping("/search")
  @PreAuthorize(HbsAuthorities.MINISTRY)
  @Operation(summary = "Search IDIR users via nr-user-lookup-api")
  public ResponseEntity<UserSearchResponse> searchUsers(
      @RequestParam(name = "userId", required = false) String userId,
      @RequestParam(name = "firstName", required = false) String firstName,
      @RequestParam(name = "lastName", required = false) String lastName,
      @RequestParam(name = "size", required = false, defaultValue = "0") int size) {
    return ResponseEntity.ok(userDirectoryService.searchUsers(userId, firstName, lastName, size));
  }

  @PostMapping("/resolve")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Resolve a batch of user ids (IDIR/BCeID) to display names")
  public ResponseEntity<Map<String, String>> resolveUserNames(@RequestBody UserResolveRequest request) {
    List<String> ids = request == null ? null : request.userIds();
    return ResponseEntity.ok(userDirectoryService.resolveDisplayNames(ids));
  }
}
