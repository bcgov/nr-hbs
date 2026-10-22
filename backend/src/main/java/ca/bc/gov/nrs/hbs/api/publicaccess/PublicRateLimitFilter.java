package ca.bc.gov.nrs.hbs.api.publicaccess;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Guards the anonymous {@code /api/v1/hbs/public/**} endpoints, which reach
 * the shared Oracle database without a login:
 *
 * <ol>
 *   <li><b>Kill switch</b> — {@code hbs.public.enabled=false} answers 404 for
 *       every public path (the public area can be switched off without a build).</li>
 *   <li><b>Per-client-IP token bucket</b> — {@code capacity} requests in a burst,
 *       refilled at {@code refillPerMinute}; over the limit → 429 with
 *       {@code Retry-After}. Buckets live in memory per pod (bounded, idle ones
 *       expire), so with N replicas the effective limit is N × the configured one.</li>
 *   <li><b>Concurrency cap</b> — at most {@code maxConcurrent} public requests run at
 *       once per pod, so anonymous traffic can never take the whole JDBC pool from
 *       logged-in users; a request that can't get a slot within 2 s → 503.</li>
 * </ol>
 *
 * <p>The client IP is read from {@code clientIpHeader} (default {@code X-Real-IP}),
 * which the frontend Caddy sets from its {@code client_ip} after walking
 * {@code X-Forwarded-For} through the trusted OpenShift router hops
 * (frontend/Caddyfile). The backend accepts connections only from the frontend
 * pod (NetworkPolicy {@code *-backend-*-ingress}), so a caller cannot set that
 * header directly. Without the header (local runs) the socket address is used.
 */
@Slf4j
public class PublicRateLimitFilter extends OncePerRequestFilter {

  static final String PUBLIC_PREFIX = "/api/v1/hbs/public/";

  private final boolean enabled;
  private final int capacity;
  private final double tokensPerNano;
  private final String clientIpHeader;
  private final Semaphore concurrency;
  private final LongSupplier clock;
  private final Cache<String, TokenBucket> buckets = CacheBuilder.newBuilder()
      .maximumSize(50_000)
      .expireAfterAccess(15, TimeUnit.MINUTES)
      .build();

  public PublicRateLimitFilter(boolean enabled, int capacity, int refillPerMinute, int maxConcurrent,
      String clientIpHeader) {
    this(enabled, capacity, refillPerMinute, maxConcurrent, clientIpHeader, System::nanoTime);
  }

  PublicRateLimitFilter(boolean enabled, int capacity, int refillPerMinute, int maxConcurrent,
      String clientIpHeader, LongSupplier clock) {
    this.enabled = enabled;
    this.capacity = Math.max(1, capacity);
    this.tokensPerNano = Math.max(1, refillPerMinute) / (double) TimeUnit.MINUTES.toNanos(1);
    this.clientIpHeader = clientIpHeader;
    this.concurrency = new Semaphore(Math.max(1, maxConcurrent), true);
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !path(request).startsWith(PUBLIC_PREFIX);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!enabled) {
      reject(response, HttpServletResponse.SC_NOT_FOUND, 0, "The public area is not available.");
      return;
    }
    String ip = clientIp(request);
    TokenBucket bucket;
    try {
      bucket = buckets.get(ip, () -> new TokenBucket(capacity, clock.getAsLong()));
    } catch (ExecutionException ex) {
      throw new IllegalStateException(ex);
    }
    long waitNanos = bucket.tryConsume(clock.getAsLong(), tokensPerNano);
    if (waitNanos > 0) {
      log.info("Public rate limit exceeded for {} on {}", ip, path(request));
      reject(response, 429, Math.max(1, TimeUnit.NANOSECONDS.toSeconds(waitNanos) + 1),
          "Too many requests. Please wait a moment and try again.");
      return;
    }
    boolean acquired;
    try {
      acquired = concurrency.tryAcquire(2, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      acquired = false;
    }
    if (!acquired) {
      reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, 5,
          "The public area is busy. Please try again in a few seconds.");
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      concurrency.release();
    }
  }

  String clientIp(HttpServletRequest request) {
    if (StringUtils.hasText(clientIpHeader)) {
      String header = request.getHeader(clientIpHeader);
      if (StringUtils.hasText(header)) {
        // A forwarded list (X-Forwarded-For style) — the first hop is the client.
        String first = header.split(",")[0].trim();
        if (!first.isEmpty() && first.length() <= 64) {
          return first;
        }
      }
    }
    return request.getRemoteAddr();
  }

  private static String path(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String ctx = request.getContextPath();
    return ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
  }

  private static void reject(HttpServletResponse response, int status, long retryAfterSeconds, String message)
      throws IOException {
    response.setStatus(status);
    if (retryAfterSeconds > 0) {
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
  }

  /** Classic token bucket; fractional tokens accrue continuously. */
  static final class TokenBucket {
    private final int capacity;
    private double tokens;
    private long last;

    TokenBucket(int capacity, long now) {
      this.capacity = capacity;
      this.tokens = capacity;
      this.last = now;
    }

    /** Takes one token; returns 0 when allowed, else the nanoseconds until one is available. */
    synchronized long tryConsume(long now, double tokensPerNano) {
      if (now > last) {
        tokens = Math.min(capacity, tokens + (now - last) * tokensPerNano);
        last = now;
      }
      if (tokens >= 1) {
        tokens -= 1;
        return 0;
      }
      return (long) Math.ceil((1 - tokens) / tokensPerNano);
    }
  }
}
