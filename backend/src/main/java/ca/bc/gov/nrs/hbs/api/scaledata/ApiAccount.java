package ca.bc.gov.nrs.hbs.api.scaledata;

import java.util.List;
import org.springframework.util.StringUtils;

/**
 * A scale-data submission account: the CSS client id of its client-credentials
 * service account and the forest clients it may submit for and read. On FTP the
 * folder named the client; the token carries no client scope, so the mapping
 * says it ({@link ApiAccountRegistry}).
 *
 * @param clientId      the token's {@code azp}
 * @param clientNumbers the 8-digit client numbers, in mapping order (never empty)
 */
public record ApiAccount(String clientId, List<String> clientNumbers) {

  /** Legacy audit columns (ENTRY_USERID, INPUT_USERID) are VARCHAR2(30). */
  static final int AUDIT_USER_MAX = 30;

  public ApiAccount {
    clientNumbers = List.copyOf(clientNumbers);
    if (clientNumbers.isEmpty()) {
      throw new IllegalArgumentException("An API account needs at least one client number");
    }
  }

  /** {@code API\<client id>}, truncated to the audit columns, so P032 shows the channel and account. */
  public String auditUser() {
    String user = "API\\" + clientId;
    return user.length() <= AUDIT_USER_MAX ? user : user.substring(0, AUDIT_USER_MAX);
  }

  public boolean mayUse(String clientNumber) {
    return clientNumber != null && clientNumbers.contains(clientNumber);
  }

  /**
   * The client a submission is for: the {@code X-HBS-Client-Number} header,
   * which must be one of the account's clients, or the only client when the
   * header is left out.
   *
   * @throws ScaleDataApiException 400 CLIENT_NUMBER_REQUIRED / CLIENT_NUMBER_INVALID, 403 CLIENT_NOT_PERMITTED
   */
  public String resolveClient(String requested) {
    if (!StringUtils.hasText(requested)) {
      if (clientNumbers.size() == 1) {
        return clientNumbers.get(0);
      }
      throw ScaleDataApiException.clientNumberRequired();
    }
    String client = requested.trim();
    if (!client.matches("\\d{8}")) {
      throw ScaleDataApiException.clientNumberInvalid();
    }
    if (!mayUse(client)) {
      throw ScaleDataApiException.clientNotPermitted();
    }
    return client;
  }

  /** For list reads: the requested client (checked), or every client of the account. */
  public List<String> clientsFor(String requested) {
    return StringUtils.hasText(requested) ? List.of(resolveClient(requested)) : clientNumbers;
  }
}
