package ca.bc.gov.nrs.hbs.api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client for <b>nr-forest-client-api</b> — the ministry's read API over
 * {@code THE.FOREST_CLIENT} / {@code CLIENT_LOCATION}, published through the
 * API Services Portal (api.gov.bc.ca). It replaces the SIL21 client-search
 * package (PKG_SIL21_CLIENT_SEARCH), which the HBS proxy user has no grant on.
 *
 * <p>Every call carries the portal's API key in {@code X-API-KEY}. The key is
 * HBS's alone and never reaches the browser — the SPA calls
 * {@code /api/v1/hbs/clients/**}, which proxies here.
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code hbs.forest-client-api.base-url} — scheme + host of the API
 *       (paths below start at {@code /api/clients})</li>
 *   <li>{@code hbs.forest-client-api.api-key} — the portal API key</li>
 * </ul>
 * Blank base URL = not configured: calls answer 503 with a message saying so.
 */
@Component
public class ForestClientApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(ForestClientApiClient.class);

  static final String API_KEY_HEADER = "X-API-KEY";
  static final String TOTAL_COUNT_HEADER = "X-Total-Count";

  private static final String SEARCH_PATH = "/api/clients/search/by";
  private static final String BY_NUMBER_PATH = "/api/clients/findByClientNumber/{clientNumber}";
  private static final String LOCATIONS_PATH = "/api/clients/{clientNumber}/locations";

  private final RestClient http;
  private final boolean configured;

  @Autowired
  public ForestClientApiClient(
      @Value("${hbs.forest-client-api.base-url:}") String baseUrl,
      @Value("${hbs.forest-client-api.api-key:}") String apiKey,
      @Value("${hbs.forest-client-api.connect-timeout:5s}") Duration connectTimeout,
      @Value("${hbs.forest-client-api.read-timeout:10s}") Duration readTimeout) {
    this.configured = baseUrl != null && !baseUrl.isBlank();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    factory.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    this.http = build(RestClient.builder()
        .baseUrl(configured ? baseUrl.trim() : "")
        .requestFactory(factory), apiKey);

    if (configured) {
      LOG.info("forest-client-api client active (base-url={}, api-key {})",
          baseUrl, apiKey == null || apiKey.isBlank() ? "NOT set" : "set");
    } else {
      LOG.info("forest-client-api client inactive — set FOREST_CLIENT_API_URL and "
          + "FOREST_CLIENT_API_KEY to enable client lookups");
    }
  }

  /** Test hook: a builder already bound to a MockRestServiceServer, plus the key to send. */
  ForestClientApiClient(RestClient.Builder builder, String apiKey) {
    this.http = build(builder, apiKey);
    this.configured = true;
  }

  private static RestClient build(RestClient.Builder builder, String apiKey) {
    builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    if (apiKey != null && !apiKey.isBlank()) {
      builder.defaultHeader(API_KEY_HEADER, apiKey.trim());
    }
    return builder.build();
  }

  /**
   * Ranked search: a fuzzy (Jaro-Winkler ≥ 0.8) match on the full name, or an
   * exact acronym, or an exact client number — whichever the caller passes.
   */
  public Page<ForestClient> search(String name, String acronym, String number, int page, int size) {
    ResponseEntity<ForestClient[]> resp = call(() -> http.get()
        .uri(b -> {
          b.path(SEARCH_PATH).queryParam("page", page).queryParam("size", size);
          if (name != null) b.queryParam("name", name);
          if (acronym != null) b.queryParam("acronym", acronym);
          if (number != null) b.queryParam("number", number);
          URI uri = b.build();
          LOG.debug("forest-client-api search: {}", uri);
          return uri;
        })
        .retrieve()
        .toEntity(ForestClient[].class));
    return Page.of(resp);
  }

  /** One client by its 8-digit number; empty when the API has no such client. */
  public Optional<ForestClient> findByNumber(String clientNumber) {
    try {
      return Optional.ofNullable(call(() -> http.get()
          .uri(BY_NUMBER_PATH, clientNumber)
          .retrieve()
          .body(ForestClient.class)));
    } catch (NotFound e) {
      return Optional.empty();
    }
  }

  /** A client's locations, one page at a time. */
  public Page<ClientLocation> locations(String clientNumber, int page, int size) {
    try {
      ResponseEntity<ClientLocation[]> resp = call(() -> http.get()
          .uri(b -> b.path(LOCATIONS_PATH)
              .queryParam("page", page)
              .queryParam("size", size)
              .build(clientNumber))
          .retrieve()
          .toEntity(ClientLocation[].class));
      return Page.of(resp);
    } catch (NotFound e) {
      return new Page<>(List.of(), 0);
    }
  }

  // ── Internals ─────────────────────────────────────────────────────

  /** Marks an upstream 404 so lookups can answer "none" rather than an error. */
  private static final class NotFound extends RuntimeException {
    NotFound() {
      super(null, null, false, false);
    }
  }

  private <T> T call(java.util.function.Supplier<T> request) {
    if (!configured) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Client lookup isn't available: the Forest Client API isn't configured.");
    }
    try {
      return request.get();
    } catch (HttpClientErrorException.NotFound e) {
      throw new NotFound();
    } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
      LOG.error("forest-client-api rejected HBS's API key ({})", e.getStatusCode());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Client lookup failed: the Forest Client API rejected HBS's API key.");
    } catch (RestClientException e) {
      LOG.error("forest-client-api call failed: {}", e.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
          "Client lookup failed: the Forest Client API didn't answer. Try again shortly.");
    }
  }

  // ── Wire types (nr-forest-client-api DTOs; unknown fields ignored) ──

  /** {@code ClientPublicViewDto}. {@code clientName} is the surname for an individual, else the company name. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ForestClient(
      String clientNumber,
      String clientName,
      String legalFirstName,
      String legalMiddleName,
      String clientStatusCode,
      String clientTypeCode,
      String acronym) {}

  /** {@code ClientLocationDto} (the fields HBS shows). {@code expired} is "Y" / "N". */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ClientLocation(
      String clientNumber,
      String locationCode,
      String locationName,
      String city,
      String province,
      String expired) {}

  /** One page of results plus the API's {@code X-Total-Count}. */
  public record Page<T>(List<T> content, long total) {
    static <T> Page<T> of(ResponseEntity<T[]> resp) {
      List<T> rows = resp.getBody() == null ? List.of() : Arrays.asList(resp.getBody());
      long total = rows.size();
      String header = resp.getHeaders().getFirst(TOTAL_COUNT_HEADER);
      if (header != null) {
        try {
          total = Math.max(Long.parseLong(header.trim()), rows.size());
        } catch (NumberFormatException ignored) {
          // keep the row count
        }
      }
      return new Page<>(rows, total);
    }
  }
}
