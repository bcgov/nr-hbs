package ca.bc.gov.nrs.hbs.api.service.v1;

import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient;
import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient.ClientLocation;
import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient.ForestClient;
import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient.Page;
import ca.bc.gov.nrs.hbs.api.struct.v1.ClientSearchResult;
import ca.bc.gov.nrs.hbs.api.struct.v1.PageableResponse;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientSearchServiceTest {

  private static final ForestClient COMPANY =
      new ForestClient("00001271", "CANADIAN FOREST PRODUCTS LTD.", null, null, "ACT", "C", "CANFOR");
  private static final ForestClient PERSON =
      new ForestClient("00000002", "BAXTER", "JAMES", "CANTER", "ACT", "I", null);

  private ForestClientApiClient api;
  private ClientSearchService service;
  private MockedStatic<RequestUtil> request;

  @BeforeEach
  void setUp() {
    api = mock(ForestClientApiClient.class);
    service = new ClientSearchService(api);
    request = mockStatic(RequestUtil.class);
    viewer(true, "");
  }

  @AfterEach
  void tearDown() {
    request.close();
  }

  private void viewer(boolean ministry, String ownClient) {
    request.when(RequestUtil::isMinistryUser).thenReturn(ministry);
    request.when(RequestUtil::getCurrentClientNumber).thenReturn(ownClient);
  }

  @Test
  void digitTerm_isAZeroPaddedNumberLookup() {
    when(api.findByNumber("00001271")).thenReturn(Optional.of(COMPANY));

    PageableResponse<ClientSearchResult> page = service.search("1271", 0, 10);

    assertThat(page.getContent()).singleElement()
        .satisfies(r -> assertThat(r.getClientAcronym()).isEqualTo("CANFOR"));
    assertThat(page.getPage().getTotalElements()).isEqualTo(1);
    verify(api, never()).search(anyString(), anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void textTerm_searchesNameAndAcronym_andCapsPageSize() {
    when(api.search("canfor", "canfor", null, 0, 100)).thenReturn(new Page<>(List.of(COMPANY), 7));

    PageableResponse<ClientSearchResult> page = service.search(" canfor ", 0, 500);

    assertThat(page.getContent()).hasSize(1);
    assertThat(page.getPage().getTotalElements()).isEqualTo(7);
    assertThat(page.getPage().getSize()).isEqualTo(100);
  }

  @Test
  void shortTextTerm_isRejected() {
    assertThatThrownBy(() -> service.search("ab", 0, 10)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void individual_isNamedSurnameFirst_forMinistry() {
    when(api.findByNumber("00000002")).thenReturn(Optional.of(PERSON));

    assertThat(service.find("2").getClientName()).isEqualTo("BAXTER, JAMES CANTER");
  }

  @Test
  void individual_isSeveredForOtherIndustryViewers_butNotForThemselves() {
    when(api.findByNumber("00000002")).thenReturn(Optional.of(PERSON));

    viewer(false, "00001271");
    ClientSearchResult other = service.find("00000002");
    assertThat(other.getClientName()).isEqualTo(ClientSearchService.NOT_RELEASABLE);
    assertThat(other.getLegalFirstName()).isNull();

    viewer(false, "00000002");
    assertThat(service.find("00000002").getClientName()).isEqualTo("BAXTER, JAMES CANTER");
  }

  @Test
  void locations_carryTheClientsFields() {
    when(api.findByNumber("00001271")).thenReturn(Optional.of(COMPANY));
    when(api.locations("00001271", 0, 10)).thenReturn(new Page<>(List.of(
        new ClientLocation("00001271", "00", "HEAD OFFICE", "VANCOUVER", "BC", "N"),
        new ClientLocation("00001271", "01", "MILL", "PRINCE GEORGE", "BC", "Y")), 2));

    List<ClientSearchResult> rows = service.locations("00001271", 0, 10).getContent();

    assertThat(rows).extracting(ClientSearchResult::getClientLocnCode).containsExactly("00", "01");
    assertThat(rows).extracting(ClientSearchResult::getClientAcronym).containsOnly("CANFOR");
    assertThat(rows).extracting(ClientSearchResult::getLocationExpired).containsExactly(false, true);
  }

  @Test
  void unknownClient_is404() {
    when(api.findByNumber("99999999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.locations("99999999", 0, 10))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
