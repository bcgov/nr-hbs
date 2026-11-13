package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

/**
 * What the scale-data controllers share: the calling account, parameter
 * parsing, and the error contract. The {@code @ExceptionHandler}s live here
 * (not in a {@code @ControllerAdvice}) so they win over the application-wide
 * {@code RestExceptionHandler} for these controllers only, and staff endpoints
 * keep their own error shape.
 */
@Slf4j
abstract class ScaleDataControllerSupport {

  protected final ApiAccountRegistry accounts;
  protected final ScaleDataApiProperties properties;

  protected ScaleDataControllerSupport(ApiAccountRegistry accounts, ScaleDataApiProperties properties) {
    this.accounts = accounts;
    this.properties = properties;
  }

  /** The calling account (the token was decoded against the mapping moments ago). */
  protected ApiAccount account() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String azp = auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaimAsString(TokenRoles.CLAIM_AZP)
        : null;
    return accounts.find(azp).orElseThrow(() -> new ScaleDataApiException(HttpStatus.FORBIDDEN,
        "ACCOUNT_NOT_MAPPED", "This client is not set up for the HBS scale data API. Contact the HBS help desk."));
  }

  protected int limit(Integer requested) {
    if (requested == null) {
      return properties.defaultPageSize();
    }
    if (requested < 1 || requested > properties.maxPageSize()) {
      throw ScaleDataApiException.badRequest("limit must be between 1 and " + properties.maxPageSize() + ".");
    }
    return requested;
  }

  protected static OffsetDateTime timestamp(String name, String value, OffsetDateTime fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return OffsetDateTime.parse(value.trim());
    } catch (DateTimeParseException ex) {
      throw ScaleDataApiException.badRequest(name + " must be an ISO-8601 date-time with an offset, "
          + "for example 2026-10-29T06:00:00-07:00 or 2026-10-29T13:00:00Z.");
    }
  }

  protected static long id(String value, String what) {
    if (value == null || !value.matches("\\d{1,18}")) {
      throw ScaleDataApiException.notFound(what);
    }
    return Long.parseLong(value);
  }

  // ── errors ─────────────────────────────────────────────────────────────

  @ExceptionHandler(ScaleDataApiException.class)
  ResponseEntity<Map<String, Object>> apiError(ScaleDataApiException ex, HttpServletRequest request) {
    if (ex.status().is5xxServerError()) {
      log.warn("Scale data API {}: {}", ex.code(), ex.getMessage());
    } else {
      log.info("Scale data API refused {} {}: {} {}", request.getMethod(), request.getRequestURI(), ex.status().value(),
          ex.code());
    }
    return body(ex.status(), ex.code(), ex.getMessage(), ex.errors(), ex.extra(), ex.retryAfterSeconds(), request);
  }

  /** {@code @PreAuthorize} on a method (AuthorizationDeniedException is a subclass). */
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Map<String, Object>> denied(AccessDeniedException ex, HttpServletRequest request) {
    String scope = ScaleDataErrors.requiredScope(request);
    ResponseEntity<Map<String, Object>> body = body(HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE",
        scope == null ? "This account may not make this call."
            : "This call needs the '" + scope + "' scope on the token. Ask the HBS help desk to grant it.",
        List.of(), Map.of(), null, request);
    return scope == null ? body : ResponseEntity.status(HttpStatus.FORBIDDEN)
        .headers(body.getHeaders())
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\"")
        .body(body.getBody());
  }

  @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<Map<String, Object>> badRequest(Exception ex, HttpServletRequest request) {
    return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "A parameter is missing or not valid.", List.of(), Map.of(),
        null, request);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Map<String, Object>> mediaType(Exception ex, HttpServletRequest request) {
    ScaleDataApiException e = ScaleDataApiException.unsupportedMediaType();
    return body(e.status(), e.code(), e.getMessage(), List.of(), Map.of(), null, request);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, Object>> tooLarge(Exception ex, HttpServletRequest request) {
    ScaleDataApiException e = ScaleDataApiException.fileTooLarge(properties.maxBytes());
    return body(e.status(), e.code(), e.getMessage(), List.of(), e.extra(), null, request);
  }

  @ExceptionHandler(MultipartException.class)
  ResponseEntity<Map<String, Object>> multipart(Exception ex, HttpServletRequest request) {
    return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The multipart request could not be read.", List.of(),
        Map.of(), null, request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, Object>> status(ResponseStatusException ex, HttpServletRequest request) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    status = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    return body(status, status.name(), ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), List.of(),
        Map.of(), null, request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
    log.error("Scale data API {} {} failed", request.getMethod(), request.getRequestURI(), ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "HBS could not complete the request. It is safe to send the same file again; quote the correlationId "
            + "to the help desk if it keeps failing.",
        List.of(), Map.of(), null, request);
  }

  static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message,
      List<ApiErrorItem> errors, Map<String, Object> extra, Long retryAfter, HttpServletRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    body.put("errors", errors);
    body.putAll(extra);
    String correlationId = ScaleDataErrors.correlationId(request);
    if (correlationId != null) {
      body.put("correlationId", correlationId);
    }
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
    if (retryAfter != null) {
      builder.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
    }
    return builder.body(body);
  }
}
