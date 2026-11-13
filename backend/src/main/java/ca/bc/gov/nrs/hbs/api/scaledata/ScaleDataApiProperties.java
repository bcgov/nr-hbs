package ca.bc.gov.nrs.hbs.api.scaledata;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@code hbs.scale-data-api.*} settings (application.properties documents each).
 */
@Component
public class ScaleDataApiProperties {

  private final boolean enabled;
  private final String issuerUri;
  private final String tokenUrl;
  private final String publicBaseUrl;
  private final String accountsFile;
  private final long maxBytes;
  private final long queueThresholdBytes;
  private final Duration duplicateWindow;
  private final int rateLimitCapacity;
  private final int rateLimitPerMinute;
  private final String requestIdHeader;
  private final boolean acceptRoleScopes;
  private final int maxDocumentErrors;
  private final int defaultPageSize;
  private final int maxPageSize;
  private final Duration listLookback;

  public ScaleDataApiProperties(
      @Value("${hbs.scale-data-api.enabled:true}") boolean enabled,
      @Value("${hbs.scale-data-api.issuer-uri:${spring.security.oauth2.resourceserver.jwt.issuer-uri}}") String issuerUri,
      @Value("${hbs.scale-data-api.token-url:}") String tokenUrl,
      @Value("${hbs.scale-data-api.public-base-url:}") String publicBaseUrl,
      @Value("${hbs.scale-data-api.accounts-file:${hbs.settings.dir}/SCALE_DATA_API_ACCOUNTS}") String accountsFile,
      @Value("${hbs.scale-data-api.max-bytes:10485760}") long maxBytes,
      @Value("${hbs.scale-data-api.queue-threshold-bytes:5242880}") long queueThresholdBytes,
      @Value("${hbs.scale-data-api.duplicate-window:P30D}") Duration duplicateWindow,
      @Value("${hbs.scale-data-api.rate-limit.capacity:60}") int rateLimitCapacity,
      @Value("${hbs.scale-data-api.rate-limit.refill-per-minute:60}") int rateLimitPerMinute,
      @Value("${hbs.scale-data-api.request-id-header:X-Request-ID}") String requestIdHeader,
      @Value("${hbs.scale-data-api.accept-role-scopes:false}") boolean acceptRoleScopes,
      @Value("${hbs.scale-data-api.max-document-errors:500}") int maxDocumentErrors,
      @Value("${hbs.scale-data-api.page-size:50}") int defaultPageSize,
      @Value("${hbs.scale-data-api.max-page-size:200}") int maxPageSize,
      @Value("${hbs.scale-data-api.list-lookback:P400D}") Duration listLookback) {
    if (maxBytes <= 0 || queueThresholdBytes <= 0 || queueThresholdBytes > maxBytes) {
      throw new IllegalStateException("hbs.scale-data-api: need 0 < queue-threshold-bytes <= max-bytes");
    }
    this.enabled = enabled;
    this.issuerUri = trimTrailingSlash(issuerUri);
    this.tokenUrl = StringUtils.hasText(tokenUrl) ? tokenUrl.trim()
        : this.issuerUri + "/protocol/openid-connect/token";
    this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    this.accountsFile = accountsFile;
    this.maxBytes = maxBytes;
    this.queueThresholdBytes = queueThresholdBytes;
    this.duplicateWindow = duplicateWindow;
    this.rateLimitCapacity = rateLimitCapacity;
    this.rateLimitPerMinute = rateLimitPerMinute;
    this.requestIdHeader = requestIdHeader;
    this.acceptRoleScopes = acceptRoleScopes;
    this.maxDocumentErrors = maxDocumentErrors;
    this.defaultPageSize = Math.max(1, defaultPageSize);
    this.maxPageSize = Math.max(this.defaultPageSize, maxPageSize);
    this.listLookback = listLookback;
  }

  private static String trimTrailingSlash(String s) {
    if (s == null) {
      return "";
    }
    String t = s.trim();
    return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
  }

  /** Kill switch: false answers 404 on every scale-data path. */
  public boolean enabled() {
    return enabled;
  }

  /** Issuer of the submission accounts' tokens (defaults to the staff issuer). */
  public String issuerUri() {
    return issuerUri;
  }

  public String jwkSetUri() {
    return issuerUri + "/protocol/openid-connect/certs";
  }

  /** Token endpoint advertised in the OpenAPI document. */
  public String tokenUrl() {
    return tokenUrl;
  }

  /** The gateway's base URL, advertised as the OpenAPI server ("" = relative). */
  public String publicBaseUrl() {
    return publicBaseUrl;
  }

  public String accountsFile() {
    return accountsFile;
  }

  /** Hard size limit of one file (413 above it). */
  public long maxBytes() {
    return maxBytes;
  }

  /** Files above this are stored and answered 202, then processed by the worker. */
  public long queueThresholdBytes() {
    return queueThresholdBytes;
  }

  /** How long a file's fingerprint (and an Idempotency-Key) is remembered. */
  public Duration duplicateWindow() {
    return duplicateWindow;
  }

  /** Per-account burst (per pod); 0 turns the backstop limiter off. */
  public int rateLimitCapacity() {
    return rateLimitCapacity;
  }

  public int rateLimitPerMinute() {
    return rateLimitPerMinute;
  }

  /** Request-id header the gateway forwards; used as the correlation id when valid. */
  public String requestIdHeader() {
    return requestIdHeader;
  }

  /**
   * Also accept the scopes as CSS client roles ({@code HBS_SCALE_DATA_SUBMIT},
   * {@code HBS_SCALE_DATA_READ}, {@code HBS_STATEMENTS_READ}), for an issuer
   * that cannot put custom scopes in the {@code scope} claim.
   */
  public boolean acceptRoleScopes() {
    return acceptRoleScopes;
  }

  /** Document errors returned inline before {@code errorsTruncated}. */
  public int maxDocumentErrors() {
    return maxDocumentErrors;
  }

  public int defaultPageSize() {
    return defaultPageSize;
  }

  public int maxPageSize() {
    return maxPageSize;
  }

  /** How far back {@code GET /submissions} looks for transmissions whose results changed. */
  public Duration listLookback() {
    return listLookback;
  }
}
