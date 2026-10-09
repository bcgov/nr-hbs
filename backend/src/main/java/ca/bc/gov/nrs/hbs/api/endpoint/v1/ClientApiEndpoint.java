package ca.bc.gov.nrs.hbs.api.endpoint.v1;

import ca.bc.gov.nrs.hbs.api.struct.v1.ClientSearchResult;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Forest client lookups (the client picker, and the active org's name),
 * proxied to nr-forest-client-api so its API key stays server-side. Open to
 * any signed-in HBS user; an individual's name is severed for industry
 * viewers other than that client.
 */
@RequestMapping("/api/v1/hbs/clients")
@Tag(name = "Client API", description = "Forest client lookups (nr-forest-client-api)")
public interface ClientApiEndpoint {

  @GetMapping("/search")
  @Operation(summary = "Find clients by number, acronym or name (ranked; an all-digit term is a client number)")
  ResponseEntity<PageableResponse<ClientSearchResult>> searchClients(
      @RequestParam("term") String term,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size);

  @GetMapping("/{clientNumber}")
  @Operation(summary = "One client by number")
  ResponseEntity<ClientSearchResult> getClient(@PathVariable("clientNumber") String clientNumber);

  @GetMapping("/{clientNumber}/locations")
  @Operation(summary = "A client's locations, one row each")
  ResponseEntity<PageableResponse<ClientSearchResult>> getClientLocations(
      @PathVariable("clientNumber") String clientNumber,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "10") int size);
}
