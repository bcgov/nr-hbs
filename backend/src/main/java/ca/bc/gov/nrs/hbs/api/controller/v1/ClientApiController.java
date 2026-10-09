package ca.bc.gov.nrs.hbs.api.controller.v1;

import ca.bc.gov.nrs.hbs.api.endpoint.v1.ClientApiEndpoint;
import ca.bc.gov.nrs.hbs.api.service.v1.ClientSearchService;
import ca.bc.gov.nrs.hbs.api.struct.v1.ClientSearchResult;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ClientApiController implements ClientApiEndpoint {

  private final ClientSearchService clientSearchService;

  @Override
  public ResponseEntity<PageableResponse<ClientSearchResult>> searchClients(String term, int page, int size) {
    return ResponseEntity.ok(clientSearchService.search(term, page, size));
  }

  @Override
  public ResponseEntity<ClientSearchResult> getClient(String clientNumber) {
    return ResponseEntity.ok(clientSearchService.find(clientNumber));
  }

  @Override
  public ResponseEntity<PageableResponse<ClientSearchResult>> getClientLocations(
      String clientNumber, int page, int size) {
    return ResponseEntity.ok(clientSearchService.locations(clientNumber, page, size));
  }
}
