package ca.bc.gov.nrs.hbs.api.publicaccess;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-client-IP token bucket, kill switch and path scoping of the public area's guard filter. */
class PublicRateLimitFilterTest {

  private final AtomicLong now = new AtomicLong(1_000_000_000L);

  private PublicRateLimitFilter filter(boolean enabled, int capacity, int perMinute) {
    return new PublicRateLimitFilter(enabled, capacity, perMinute, 4, "X-Real-IP", now::get);
  }

  private static MockHttpServletRequest request(String path, String ip) {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
    req.setRemoteAddr("10.0.0.9"); // the frontend pod
    if (ip != null) {
      req.addHeader("X-Real-IP", ip);
    }
    return req;
  }

  private static int run(PublicRateLimitFilter f, MockHttpServletRequest req) throws Exception {
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();
    f.doFilter(req, res, chain);
    return res.getStatus();
  }

  @Test
  void burstThenTooManyRequestsWithRetryAfter() throws Exception {
    PublicRateLimitFilter f = filter(true, 3, 60);
    for (int i = 0; i < 3; i++) {
      assertThat(run(f, request("/api/v1/hbs/public/queries/timberMarks.search", "203.0.113.5"))).isEqualTo(200);
    }
    MockHttpServletResponse res = new MockHttpServletResponse();
    f.doFilter(request("/api/v1/hbs/public/queries/timberMarks.search", "203.0.113.5"), res, new MockFilterChain());
    assertThat(res.getStatus()).isEqualTo(429);
    assertThat(res.getHeader("Retry-After")).isNotBlank();
    assertThat(res.getContentAsString()).contains("Too many requests");
  }

  @Test
  void tokensRefillOverTime() throws Exception {
    PublicRateLimitFilter f = filter(true, 1, 60); // one per second
    assertThat(run(f, request("/api/v1/hbs/public/x", "203.0.113.5"))).isEqualTo(200);
    assertThat(run(f, request("/api/v1/hbs/public/x", "203.0.113.5"))).isEqualTo(429);
    now.addAndGet(TimeUnit.SECONDS.toNanos(1));
    assertThat(run(f, request("/api/v1/hbs/public/x", "203.0.113.5"))).isEqualTo(200);
  }

  @Test
  void bucketsArePerClientIp() throws Exception {
    PublicRateLimitFilter f = filter(true, 1, 1);
    assertThat(run(f, request("/api/v1/hbs/public/x", "203.0.113.5"))).isEqualTo(200);
    assertThat(run(f, request("/api/v1/hbs/public/x", "203.0.113.5"))).isEqualTo(429);
    // A different client behind the same proxy has its own bucket.
    assertThat(run(f, request("/api/v1/hbs/public/x", "198.51.100.7"))).isEqualTo(200);
  }

  @Test
  void clientIpComesFromTheConfiguredHeaderElseTheSocket() {
    PublicRateLimitFilter f = filter(true, 1, 1);
    assertThat(f.clientIp(request("/api/v1/hbs/public/x", "203.0.113.5, 10.1.1.1"))).isEqualTo("203.0.113.5");
    assertThat(f.clientIp(request("/api/v1/hbs/public/x", null))).isEqualTo("10.0.0.9");
    PublicRateLimitFilter socketOnly = new PublicRateLimitFilter(true, 1, 1, 4, "", now::get);
    assertThat(socketOnly.clientIp(request("/api/v1/hbs/public/x", "203.0.113.5"))).isEqualTo("10.0.0.9");
  }

  @Test
  void onlyPublicPathsAreLimited() throws Exception {
    PublicRateLimitFilter f = filter(true, 1, 1);
    for (int i = 0; i < 5; i++) {
      assertThat(run(f, request("/api/v1/hbs/queries/timberMarks.search", "203.0.113.5"))).isEqualTo(200);
    }
  }

  @Test
  void disabledAreaIs404() throws Exception {
    PublicRateLimitFilter f = filter(false, 10, 60);
    assertThat(run(f, request("/api/v1/hbs/public/queries/timberMarks.search", "203.0.113.5"))).isEqualTo(404);
    assertThat(run(f, request("/api/v1/hbs/commands", "203.0.113.5"))).isEqualTo(200);
  }
}
