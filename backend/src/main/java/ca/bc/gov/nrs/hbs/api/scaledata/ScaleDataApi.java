package ca.bc.gov.nrs.hbs.api.scaledata;

import java.util.List;

/**
 * Paths, scopes and headers of the scale data API, the replacement for the FTP
 * channel (docs/scale-data-api.md, docs/scale-data-api-client-guide.md).
 *
 * <p>Every endpoint needs a client-credentials token from a mapped submission
 * account ({@link ApiAccountRegistry}) carrying the service role
 * {@code HBS_SUBMISSION_API} <em>and</em> the endpoint's OAuth scope:
 *
 * <table>
 *   <tr><th>Scope</th><th>Endpoints</th></tr>
 *   <tr><td>{@value #SCOPE_SUBMIT}</td><td>{@code POST /submissions}, {@code POST /submissions:validate}</td></tr>
 *   <tr><td>{@value #SCOPE_READ}</td><td>{@code GET /submissions}, {@code GET /submissions/{id}},
 *       {@code GET /submissions/{id}/documents}</td></tr>
 *   <tr><td>{@value #SCOPE_STATEMENTS}</td><td>{@code GET /statements}, {@code GET /statements/{id}.pdf|.xml}</td></tr>
 * </table>
 */
public final class ScaleDataApi {

  private ScaleDataApi() {
  }

  /** Base path; versioned in the path ({@code v1}). */
  public static final String BASE = "/api/v1/hbs/integrations/scale-data";
  /** Everything under {@link #BASE}: served only by the scale-data filter chain. */
  public static final String PATHS = BASE + "/**";

  /** Submit and dry-run files. */
  public static final String SCOPE_SUBMIT = "hbs.scale-data.submit";
  /** Read transmission results and documents. */
  public static final String SCOPE_READ = "hbs.scale-data.read";
  /** List and download statements. */
  public static final String SCOPE_STATEMENTS = "hbs.statements.read";
  public static final List<String> SCOPES = List.of(SCOPE_SUBMIT, SCOPE_READ, SCOPE_STATEMENTS);

  /** The 8-digit client number a file is for; required when the account maps to several. */
  public static final String HEADER_CLIENT_NUMBER = "X-HBS-Client-Number";
  /** The client's file name, stored as the transmission's FILE_NAME. */
  public static final String HEADER_FILE_NAME = "X-HBS-File-Name";
  /** Optional explicit retry key (one per file, reused on every retry). */
  public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
  /** Returned on every response and in every error body as {@code correlationId}. */
  public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";

  /** springdoc group of the client-facing OpenAPI document ({@code /v3/api-docs/scale-data}). */
  public static final String OPENAPI_GROUP = "scale-data";
  /** Security scheme names in the OpenAPI document. */
  public static final String SECURITY_SCHEME = "scaleDataClientCredentials";
  public static final String BEARER_SCHEME = "bearerToken";
}
