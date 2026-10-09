package ca.bc.gov.nrs.hbs.api.controller.v1;

import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.CommandService;
import ca.bc.gov.nrs.hbs.api.query.QueryRegistry;
import ca.bc.gov.nrs.hbs.api.query.QueryService;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry-driven read/write endpoints behind most HBS screens. Every id maps
 * to a reviewed {@code QueryDefinition}/{@code CommandDefinition} that carries
 * its own capability; the coarse {@code @PreAuthorize} here only requires an
 * HBS base role, the per-id check happens in the services.
 *
 * <pre>
 *   GET  /api/v1/hbs/queries/{id}?criteria…&amp;page&amp;size&amp;sortBy&amp;sortDir  → page
 *   GET  /api/v1/hbs/queries/{id}/list?criteria…                    → all rows
 *   GET  /api/v1/hbs/queries/{id}/one?key…                          → one row
 *   GET  /api/v1/hbs/queries/{id}/export?criteria…                  → CSV
 *   POST /api/v1/hbs/commands/{id}   {body}                         → generated ids
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/hbs")
@Tag(name = "Screens", description = "Registry-driven queries and commands for the HBS screens")
public class QueryController {

  private static final Set<String> PAGING = Set.of("page", "size", "sortBy", "sortDir");

  private final QueryService queryService;
  private final CommandService commandService;
  private final QueryRegistry registry;

  public QueryController(QueryService queryService, CommandService commandService, QueryRegistry registry) {
    this.queryService = queryService;
    this.commandService = commandService;
    this.registry = registry;
  }

  @GetMapping("/queries/{queryId}")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Paged search for a registered screen query")
  public PageableResponse<Map<String, Object>> search(@PathVariable String queryId,
      @RequestParam Map<String, String> params,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortDir) {
    return queryService.search(queryId, criteria(params), page, size, sortBy, sortDir);
  }

  @GetMapping("/queries/{queryId}/list")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "All rows (capped) for a registered query — lookups and child tables")
  public List<Map<String, Object>> list(@PathVariable String queryId, @RequestParam Map<String, String> params) {
    return queryService.list(queryId, criteria(params));
  }

  @GetMapping("/queries/{queryId}/one")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "A single record for a registered detail query")
  public Map<String, Object> one(@PathVariable String queryId, @RequestParam Map<String, String> params) {
    return queryService.single(queryId, criteria(params));
  }

  @GetMapping(value = "/queries/{queryId}/export")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "CSV export of a registered query")
  public ResponseEntity<byte[]> export(@PathVariable String queryId, @RequestParam Map<String, String> params) {
    byte[] csv = queryService.exportCsv(queryId, criteria(params)).getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(queryId + ".csv").build().toString())
        .contentType(new MediaType("text", "csv"))
        .body(csv);
  }

  @GetMapping("/commands")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Ids of the registered commands the caller may run")
  public List<String> availableCommands() {
    return registry.allCommands().stream()
        .filter(c -> c.capability().isGranted())
        .map(CommandDefinition::id)
        .toList();
  }

  @PostMapping("/commands/{commandId}")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "Run a registered write (legacy table-API proc)")
  public Map<String, Object> command(@PathVariable String commandId, @RequestBody(required = false) Map<String, Object> body) {
    return commandService.execute(commandId, body);
  }

  private static Map<String, String> criteria(Map<String, String> params) {
    Map<String, String> out = new HashMap<>(params);
    PAGING.forEach(out::remove);
    return out;
  }
}
