package ca.bc.gov.nrs.hbs.api.scaledata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A refusal with the scale data API's error contract:
 * {@code { code, message, errors[], correlationId }} plus any extra fields
 * (for example {@code transmissionId}, {@code maxBytes}).
 */
public class ScaleDataApiException extends RuntimeException {

  private final HttpStatus status;
  private final String code;
  private final List<ApiErrorItem> errors;
  private final Map<String, Object> extra;
  private final Long retryAfterSeconds;

  public ScaleDataApiException(HttpStatus status, String code, String message) {
    this(status, code, message, List.of(), Map.of(), null);
  }

  public ScaleDataApiException(HttpStatus status, String code, String message, List<ApiErrorItem> errors,
      Map<String, Object> extra, Long retryAfterSeconds) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors == null ? List.of() : List.copyOf(errors);
    this.extra = extra == null ? Map.of() : new LinkedHashMap<>(extra);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public List<ApiErrorItem> errors() {
    return errors;
  }

  public Map<String, Object> extra() {
    return extra;
  }

  public Long retryAfterSeconds() {
    return retryAfterSeconds;
  }

  // ── the documented refusals ─────────────────────────────────────────────

  static ScaleDataApiException clientNumberRequired() {
    return new ScaleDataApiException(HttpStatus.BAD_REQUEST, "CLIENT_NUMBER_REQUIRED",
        "This account submits for several clients: name one in the " + ScaleDataApi.HEADER_CLIENT_NUMBER
            + " header (or the clientNumber parameter).");
  }

  static ScaleDataApiException clientNumberInvalid() {
    return new ScaleDataApiException(HttpStatus.BAD_REQUEST, "CLIENT_NUMBER_INVALID",
        "The client number must be 8 digits.");
  }

  static ScaleDataApiException clientNotPermitted() {
    return new ScaleDataApiException(HttpStatus.FORBIDDEN, "CLIENT_NOT_PERMITTED",
        "This account is not set up for that client number. Contact the HBS help desk.");
  }

  static ScaleDataApiException fileTooLarge(long maxBytes) {
    return new ScaleDataApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
        "The file is larger than the " + maxBytes + "-byte limit. Split it into smaller files.",
        List.of(), Map.of("maxBytes", maxBytes), null);
  }

  static ScaleDataApiException emptyFile() {
    return new ScaleDataApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "The request contains no file.");
  }

  static ScaleDataApiException unsupportedMediaType() {
    return new ScaleDataApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
        "Send the file as application/xml (or text/xml), or as multipart/form-data with a part named 'file'.");
  }

  static ScaleDataApiException schemaInvalid(List<ApiErrorItem> errors) {
    return new ScaleDataApiException(HttpStatus.UNPROCESSABLE_CONTENT, "SCHEMA_INVALID",
        "The file does not match HBS_Schema_V6_1b.", errors, Map.of(), null);
  }

  static ScaleDataApiException virusDetected(String message) {
    return new ScaleDataApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VIRUS_DETECTED", message);
  }

  static ScaleDataApiException virusScanUnavailable() {
    return new ScaleDataApiException(HttpStatus.SERVICE_UNAVAILABLE, "VIRUS_SCAN_UNAVAILABLE",
        "The virus scanner could not check the file. Send the same file again after the Retry-After delay.",
        List.of(), Map.of(), 60L);
  }

  static ScaleDataApiException idempotencyKeyReused() {
    return new ScaleDataApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
        "This Idempotency-Key was already used for a different file. Use a new key for a new file.");
  }

  static ScaleDataApiException idempotencyKeyInvalid() {
    return new ScaleDataApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID",
        "An Idempotency-Key must be 8 to 128 characters: letters, digits, '-' or '_' (a UUID works).");
  }

  static ScaleDataApiException notFound(String what) {
    return new ScaleDataApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " was not found for this account.");
  }

  static ScaleDataApiException badRequest(String message) {
    return new ScaleDataApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
  }
}
