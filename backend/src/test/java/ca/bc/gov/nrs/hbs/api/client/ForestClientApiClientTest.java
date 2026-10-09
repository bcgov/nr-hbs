package ca.bc.gov.nrs.hbs.api.client;

import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient.ClientLocation;
import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient.ForestClient;
import ca.bc.gov.nrs.hbs.api.client.ForestClientApiClient.Page;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * HTTP-contract tests for {@link ForestClientApiClient} against the
 * nr-forest-client-api endpoints, using a {@link MockRestServiceServer}.
 */
class ForestClientApiClientTest {

  private MockRestServiceServer server;
  private ForestClientApiClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://fc.test");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new ForestClientApiClient(builder, "secret-key");
  }

  @Test
  void search_sendsKeyAndCriteria_readsTotalCountHeader() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Total-Count", "42");
    server.expect(requestTo(startsWith("http://fc.test/api/clients/search/by")))
        .andExpect(header("X-API-KEY", "secret-key"))
        .andExpect(queryParam("name", "CANFOR"))
        .andExpect(queryParam("acronym", "CANFOR"))
        .andExpect(queryParam("page", "0"))
        .andExpect(queryParam("size", "15"))
        .andRespond(withSuccess(
            "[{\"clientNumber\":\"00001271\",\"clientName\":\"CANADIAN FOREST PRODUCTS LTD.\","
                + "\"clientStatusCode\":\"ACT\",\"clientTypeCode\":\"C\",\"acronym\":\"CANFOR\","
                + "\"count\":42,\"somethingNew\":true}]",
            MediaType.APPLICATION_JSON).headers(headers));

    Page<ForestClient> page = client.search("CANFOR", "CANFOR", null, 0, 15);

    server.verify();
    assertThat(page.total()).isEqualTo(42);
    assertThat(page.content()).singleElement()
        .satisfies(c -> {
          assertThat(c.clientNumber()).isEqualTo("00001271");
          assertThat(c.acronym()).isEqualTo("CANFOR");
        });
  }

  @Test
  void findByNumber_mapsClient_andAnswersEmptyOn404() {
    server.expect(requestTo("http://fc.test/api/clients/findByClientNumber/00000002"))
        .andRespond(withSuccess(
            "{\"clientNumber\":\"00000002\",\"clientName\":\"BAXTER\",\"legalFirstName\":\"JAMES\","
                + "\"clientTypeCode\":\"I\"}",
            MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://fc.test/api/clients/findByClientNumber/99999999"))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.findByNumber("00000002")).get()
        .extracting(ForestClient::clientName).isEqualTo("BAXTER");
    assertThat(client.findByNumber("99999999")).isEmpty();
    server.verify();
  }

  @Test
  void locations_pagesAndMapsLocations() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Total-Count", "3");
    server.expect(requestTo("http://fc.test/api/clients/00001271/locations?page=0&size=10"))
        .andRespond(withSuccess(
            "[{\"clientNumber\":\"00001271\",\"locationCode\":\"00\",\"locationName\":\"HEAD OFFICE\","
                + "\"city\":\"VANCOUVER\",\"expired\":\"N\"},"
                + "{\"clientNumber\":\"00001271\",\"locationCode\":\"01\",\"city\":\"PRINCE GEORGE\","
                + "\"expired\":\"Y\"}]",
            MediaType.APPLICATION_JSON).headers(headers));

    Page<ClientLocation> page = client.locations("00001271", 0, 10);

    server.verify();
    assertThat(page.total()).isEqualTo(3);
    assertThat(page.content()).extracting(ClientLocation::locationCode).containsExactly("00", "01");
    assertThat(page.content().get(1).expired()).isEqualTo("Y");
  }

  @Test
  void rejectedKey_isBadGatewayWithAPlainMessage() {
    server.expect(requestTo(startsWith("http://fc.test/api/clients/search/by")))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> client.search("ACME", "ACME", null, 0, 10))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> {
          ResponseStatusException rse = (ResponseStatusException) e;
          assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
          assertThat(rse.getReason()).contains("API key");
        });
  }

  @Test
  void unconfigured_isServiceUnavailable() {
    ForestClientApiClient blank = new ForestClientApiClient("", "", Duration.ofSeconds(1), Duration.ofSeconds(1));

    assertThatThrownBy(() -> blank.findByNumber("00000001"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
  }
}
