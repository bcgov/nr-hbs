package ca.bc.gov.nrs.hbs.api.scaledata;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * The scale data API's error body for refusals that happen before a
 * controller runs (filters, authentication, authorization), with the RFC 6750
 * {@code WWW-Authenticate} challenge. Controllers use
 * {@link ScaleDataControllerSupport}, which writes the same shape.
 */
public final class ScaleDataErrors implements AuthenticationEntryPoint, AccessDeniedHandler {

  /** Request attribute holding the correlation id ({@link ScaleDataRequestFilter}). */
  public static final String CORRELATION_ATTRIBUTE = ScaleDataErrors.class.getName() + ".correlationId";

  // ── 401 ───────────────────────────────────────────────────────────────

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    boolean tokenSent = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    String challenge = tokenSent || ex instanceof InvalidBearerTokenException
        ? "Bearer error=\"invalid_token\", error_description=\"" + headerSafe(describe(ex)) + "\""
        : "Bearer";
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
    write(request, response, HttpServletResponse.SC_UNAUTHORIZED, tokenSent ? "INVALID_TOKEN" : "UNAUTHORIZED",
        tokenSent ? "The token is missing, expired or not valid for this API: " + describe(ex)
            + " Get a new token and try again."
            : "Send a bearer token: Authorization: Bearer <token>.",
        Map.of(), null);
  }

  private static String describe(AuthenticationException ex) {
    String m = ex.getMessage();
    return m == null || m.isBlank() ? "invalid token." : m;
  }

  // ── 403 ───────────────────────────────────────────────────────────────

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    forbidden(request, response, SecurityContextHolder.getContext().getAuthentication());
  }

  static void forbidden(HttpServletRequest request, HttpServletResponse response, Authentication auth)
      throws IOException {
    String scope = requiredScope(request);
    boolean hasScope = scope != null && auth != null && auth.getAuthorities().stream()
        .anyMatch(a -> ("SCOPE_" + scope).equals(a.getAuthority()));
    if (scope != null && !hasScope) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
          "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\"");
      write(request, response, HttpServletResponse.SC_FORBIDDEN, "INSUFFICIENT_SCOPE",
          "This call needs the '" + scope + "' scope on the token. Ask the HBS help desk to grant it.",
          Map.of(), null);
      return;
    }
    write(request, response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
        "This account may not make this call.", Map.of(), null);
  }

  /** The scope an endpoint needs, from its method and path (null for no endpoint). */
  static String requiredScope(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith(ScaleDataApi.BASE + "/")) {
      return null;
    }
    String rest = path.substring(ScaleDataApi.BASE.length());
    boolean post = HttpMethod.POST.matches(request.getMethod());
    boolean get = HttpMethod.GET.matches(request.getMethod());
    if (post && (rest.equals("/submissions") || rest.equals("/submissions:validate"))) {
      return ScaleDataApi.SCOPE_SUBMIT;
    }
    if (get && (rest.equals("/submissions") || rest.startsWith("/submissions/"))) {
      return ScaleDataApi.SCOPE_READ;
    }
    if (get && (rest.equals("/statements") || rest.startsWith("/statements/"))) {
      return ScaleDataApi.SCOPE_STATEMENTS;
    }
    return null;
  }

  // ── writing ───────────────────────────────────────────────────────────

  /**
   * Writes {@code {code, message, errors: [], <extra>, correlationId}}. Extra
   * values must be numbers, booleans or strings.
   */
  static void write(HttpServletRequest request, HttpServletResponse response, int status, String code,
      String message, Map<String, Object> extra, Long retryAfterSeconds) throws IOException {
    if (response.isCommitted()) {
      return;
    }
    String correlationId = correlationId(request);
    response.setStatus(status);
    if (retryAfterSeconds != null) {
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    }
    if (correlationId != null) {
      response.setHeader(ScaleDataApi.HEADER_CORRELATION_ID, correlationId);
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    StringBuilder json = new StringBuilder("{\"code\":").append(quote(code))
        .append(",\"message\":").append(quote(message))
        .append(",\"errors\":[]");
    for (Map.Entry<String, Object> e : extra.entrySet()) {
      json.append(',').append(quote(e.getKey())).append(':');
      Object v = e.getValue();
      json.append(v instanceof Number || v instanceof Boolean ? v.toString() : quote(String.valueOf(v)));
    }
    if (correlationId != null) {
      json.append(",\"correlationId\":").append(quote(correlationId));
    }
    response.getWriter().write(json.append('}').toString());
  }

  static String correlationId(HttpServletRequest request) {
    Object id = request.getAttribute(CORRELATION_ATTRIBUTE);
    return id == null ? null : id.toString();
  }

  static String quote(String s) {
    if (s == null) {
      return "null";
    }
    StringBuilder b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) {
            b.append(String.format("\\u%04x", (int) c));
          } else {
            b.append(c);
          }
        }
      }
    }
    return b.append('"').toString();
  }

  private static String headerSafe(String s) {
    return s.replaceAll("[\"\\\\\\p{Cntrl}]", "'");
  }
}
