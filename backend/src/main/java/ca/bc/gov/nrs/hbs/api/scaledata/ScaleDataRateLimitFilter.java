package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.security.TokenRoles;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Backstop rate limit per submission account (token bucket per pod), after
 * authentication. The gateway is the real limiter (docs/scale-data-api-hosting.md:
 * 60 a minute per account, 429 + Retry-After); this keeps one account from
 * hogging a pod if the gateway is misconfigured or bypassed from inside the
 * cluster. {@code hbs.scale-data-api.rate-limit.capacity=0} turns it off.
 */
final class ScaleDataRateLimitFilter extends OncePerRequestFilter {

  private final int capacity;
  private final double tokensPerNano;
  private final LongSupplier clock;
  private final Cache<String, Bucket> buckets = CacheBuilder.newBuilder()
      .expireAfterAccess(Duration.ofMinutes(10))
      .maximumSize(10_000)
      .build();

  ScaleDataRateLimitFilter(int capacity, int refillPerMinute) {
    this(capacity, refillPerMinute, System::nanoTime);
  }

  ScaleDataRateLimitFilter(int capacity, int refillPerMinute, LongSupplier clock) {
    this.capacity = capacity;
    this.tokensPerNano = Math.max(1, refillPerMinute) / (double) Duration.ofMinutes(1).toNanos();
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String account = account(SecurityContextHolder.getContext().getAuthentication());
    if (capacity <= 0 || account == null) {
      chain.doFilter(request, response);
      return;
    }
    long waitNanos;
    try {
      waitNanos = buckets.get(account, () -> new Bucket(capacity, clock.getAsLong()))
          .tryConsume(clock.getAsLong(), tokensPerNano);
    } catch (ExecutionException ex) {
      throw new IllegalStateException(ex);
    }
    if (waitNanos > 0) {
      long seconds = Math.max(1, (long) Math.ceil(waitNanos / 1e9));
      ScaleDataErrors.write(request, response, 429, "RATE_LIMITED",
          "Too many requests for this account. Wait for the Retry-After delay, then try again.", Map.of(), seconds);
      return;
    }
    chain.doFilter(request, response);
  }

  private static String account(Authentication auth) {
    if (auth instanceof JwtAuthenticationToken jwt) {
      return jwt.getToken().getClaimAsString(TokenRoles.CLAIM_AZP);
    }
    return null;
  }

  /** Classic token bucket; fractional tokens accrue continuously. */
  static final class Bucket {
    private final int capacity;
    private double tokens;
    private long last;

    Bucket(int capacity, long now) {
      this.capacity = capacity;
      this.tokens = capacity;
      this.last = now;
    }

    /** Takes one token; 0 when allowed, else the nanoseconds until one is available. */
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
