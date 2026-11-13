package ca.bc.gov.nrs.hbs.api.scaledata;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter of the scale-data chain, before authentication:
 *
 * <ul>
 *   <li><b>Kill switch</b>: {@code hbs.scale-data-api.enabled=false} answers 404.</li>
 *   <li><b>Correlation id</b>: the gateway's request id
 *       ({@code hbs.scale-data-api.request-id-header}) or the client's
 *       {@code X-Correlation-ID} when it is a safe token, else a new UUID; put in
 *       the logging MDC ({@code correlationId}), returned as
 *       {@code X-Correlation-ID} and in every error body.</li>
 *   <li><b>Size</b>: a body declared larger than the limit is refused with 413
 *       before it is read (the controller counts the bytes too, for chunked
 *       uploads).</li>
 * </ul>
 *
 * Not a Spring bean: it is added to the scale-data chain only, so it never
 * runs for other paths.
 */
final class ScaleDataRequestFilter extends OncePerRequestFilter {

  static final String MDC_KEY = "correlationId";
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
  /** Multipart framing on top of the file itself. */
  static final long MULTIPART_OVERHEAD = 64 * 1024;

  private final ScaleDataApiProperties properties;

  ScaleDataRequestFilter(ScaleDataApiProperties properties) {
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String correlationId = correlationId(request);
    request.setAttribute(ScaleDataErrors.CORRELATION_ATTRIBUTE, correlationId);
    response.setHeader(ScaleDataApi.HEADER_CORRELATION_ID, correlationId);
    MDC.put(MDC_KEY, correlationId);
    try {
      if (!properties.enabled()) {
        ScaleDataErrors.write(request, response, HttpServletResponse.SC_NOT_FOUND, "NOT_AVAILABLE",
            "The HBS scale data API is not available.", Map.of(), null);
        return;
      }
      long declared = request.getContentLengthLong();
      if (declared > properties.maxBytes() + MULTIPART_OVERHEAD) {
        ScaleDataErrors.write(request, response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "FILE_TOO_LARGE",
            "The file is larger than the " + properties.maxBytes() + "-byte limit. Split it into smaller files.",
            Map.of("maxBytes", properties.maxBytes()), null);
        return;
      }
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  private String correlationId(HttpServletRequest request) {
    for (String header : new String[] {properties.requestIdHeader(), ScaleDataApi.HEADER_CORRELATION_ID}) {
      if (header == null || header.isBlank()) {
        continue;
      }
      String value = request.getHeader(header);
      if (value != null && SAFE_ID.matcher(value.trim()).matches()) {
        return value.trim();
      }
    }
    return UUID.randomUUID().toString();
  }
}
