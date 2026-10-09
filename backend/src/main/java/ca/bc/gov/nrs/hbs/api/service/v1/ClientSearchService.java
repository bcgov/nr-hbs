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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Client lookups for the client picker and the org name shown in the header,
 * served by nr-forest-client-api (see {@link ForestClientApiClient}).
 *
 * <ul>
 *   <li>{@link #search} — the picker's type-ahead: an all-digit term is a
 *       client number (zero-padded to 8), anything else a ranked
 *       name / acronym search.</li>
 *   <li>{@link #find} — one client by number.</li>
 *   <li>{@link #locations} — a client's locations, one row each, carrying
 *       the client's own fields so the picker can show the pair.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ClientSearchService {

  static final String NOT_RELEASABLE = "Not Releasable";
  static final int MAX_PAGE_SIZE = 100;

  private final ForestClientApiClient api;

  public PageableResponse<ClientSearchResult> search(String term, int page, int size) {
    String t = term == null ? "" : term.trim();
    if (t.length() < 3 && !t.matches("\\d+")) {
      throw new IllegalArgumentException("Enter at least 3 characters to search for a client.");
    }
    int p = Math.max(0, page);
    int s = pageSize(size);
    if (t.matches("\\d{1,8}")) {
      List<ClientSearchResult> rows = api.findByNumber(padNumber(t))
          .map(c -> toResult(c, null))
          .stream()
          .toList();
      return PageableResponse.ofPage(p == 0 ? rows : List.of(), p, s, rows.size());
    }
    Page<ForestClient> found = api.search(t, t, null, p, s);
    return PageableResponse.ofPage(
        found.content().stream().map(c -> toResult(c, null)).toList(), p, s, found.total());
  }

  public ClientSearchResult find(String clientNumber) {
    return toResult(requireClient(clientNumber), null);
  }

  public PageableResponse<ClientSearchResult> locations(String clientNumber, int page, int size) {
    ForestClient client = requireClient(clientNumber);
    int p = Math.max(0, page);
    int s = pageSize(size);
    Page<ClientLocation> found = api.locations(client.clientNumber(), p, s);
    return PageableResponse.ofPage(
        found.content().stream().map(l -> toResult(client, l)).toList(), p, s, found.total());
  }

  // ── Internals ─────────────────────────────────────────────────────

  private ForestClient requireClient(String clientNumber) {
    String n = clientNumber == null ? "" : clientNumber.trim();
    if (!n.matches("\\d{1,8}")) {
      throw new IllegalArgumentException("A client number is up to 8 digits.");
    }
    Optional<ForestClient> client = api.findByNumber(padNumber(n));
    return client.orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND, "There is no forest client " + padNumber(n) + "."));
  }

  static String padNumber(String digits) {
    return "0".repeat(Math.max(0, 8 - digits.length())) + digits;
  }

  private static int pageSize(int size) {
    return size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
  }

  static ClientSearchResult toResult(ForestClient c, ClientLocation l) {
    boolean individual = "I".equalsIgnoreCase(trim(c.clientTypeCode()));
    boolean severed = individual && !releasable(c.clientNumber());
    return ClientSearchResult.builder()
        .clientNumber(trim(c.clientNumber()))
        .clientAcronym(trim(c.acronym()))
        .clientName(severed ? NOT_RELEASABLE : displayName(c, individual))
        .legalFirstName(severed ? null : trim(c.legalFirstName()))
        .legalMiddleName(severed ? null : trim(c.legalMiddleName()))
        .clientTypeCode(trim(c.clientTypeCode()))
        .clientStatusCode(trim(c.clientStatusCode()))
        .clientLocnCode(l == null ? null : trim(l.locationCode()))
        .clientLocnName(l == null ? null : (severed ? NOT_RELEASABLE : trim(l.locationName())))
        .city(l == null ? null : trim(l.city()))
        .locationExpired(l == null ? null : "Y".equalsIgnoreCase(trim(l.expired())))
        .build();
  }

  /** "SURNAME, FIRST MIDDLE" for an individual (the SIL21 form), else the company name. */
  private static String displayName(ForestClient c, boolean individual) {
    String name = trim(c.clientName());
    if (!individual) return name;
    String given = String.join(" ",
        java.util.stream.Stream.of(trim(c.legalFirstName()), trim(c.legalMiddleName()))
            .filter(StringUtils::hasText).toList());
    if (!StringUtils.hasText(given)) return name;
    return StringUtils.hasText(name) ? name + ", " + given : given;
  }

  /**
   * An individual's personal data is shown to ministry viewers and to that
   * client itself — the same rule as {@code QueriesReportsCatalog.releasable}.
   */
  private static boolean releasable(String clientNumber) {
    if (RequestUtil.isMinistryUser()) return true;
    String own = RequestUtil.getCurrentClientNumber();
    return StringUtils.hasText(own) && own.equals(trim(clientNumber));
  }

  private static String trim(String v) {
    return v == null ? null : v.trim();
  }
}
