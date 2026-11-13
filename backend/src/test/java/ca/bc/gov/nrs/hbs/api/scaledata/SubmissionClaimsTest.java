package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubmissionClaimsTest {

  @TempDir
  Path root;

  static final String FP = SubmissionClaims.sha256("<file/>".getBytes(StandardCharsets.UTF_8));

  private SubmissionClaims claims(Instant now) {
    return new SubmissionClaims(root, Duration.ofDays(30), Clock.fixed(now, ZoneOffset.UTC));
  }

  @Test
  void theFirstClaimWinsAndLaterOnesSeeIt() {
    SubmissionClaims c = claims(Instant.parse("2026-11-01T00:00:00Z"));
    assertThat(c.claimFingerprint("00012345", FP, 101, "acme")).isEmpty();
    Optional<SubmissionClaims.Claim> lost = c.claimFingerprint("00012345", FP, 102, "acme");
    assertThat(lost).get().extracting(SubmissionClaims.Claim::transmissionId).isEqualTo(101L);
    assertThat(c.fingerprint("00012345", FP)).get().extracting(SubmissionClaims.Claim::transmissionId).isEqualTo(101L);
    // per client
    assertThat(c.fingerprint("00067890", FP)).isEmpty();
  }

  @Test
  void concurrentClaimsHaveExactlyOneWinner() throws Exception {
    SubmissionClaims c = claims(Instant.now());
    var pool = Executors.newFixedThreadPool(8);
    List<Future<Optional<SubmissionClaims.Claim>>> results = new ArrayList<>();
    for (int i = 0; i < 16; i++) {
      long id = 200 + i;
      results.add(pool.submit((Callable<Optional<SubmissionClaims.Claim>>) () ->
          c.claimFingerprint("00012345", FP, id, "acme")));
    }
    int winners = 0;
    for (var f : results) {
      if (f.get().isEmpty()) {
        winners++;
      }
    }
    pool.shutdown();
    assertThat(winners).isEqualTo(1);
  }

  @Test
  void expiredClaimsAreIgnoredTakenOverAndPurged() throws Exception {
    Instant then = Instant.parse("2026-09-01T00:00:00Z");
    claims(then).claimFingerprint("00012345", FP, 101, "acme");
    Files.setLastModifiedTime(claims(then).fingerprintPath("00012345", FP), FileTime.from(then));

    SubmissionClaims later = claims(then.plus(Duration.ofDays(31)));
    assertThat(later.fingerprint("00012345", FP)).isEmpty();
    assertThat(later.purgeExpired()).isEqualTo(1);
    assertThat(later.claimFingerprint("00012345", FP, 300, "acme")).isEmpty();
    assertThat(later.fingerprint("00012345", FP)).get().extracting(SubmissionClaims.Claim::transmissionId)
        .isEqualTo(300L);
  }

  @Test
  void replaceAndRelease() {
    SubmissionClaims c = claims(Instant.now());
    c.claimFingerprint("00012345", FP, 101, "acme");
    c.replaceFingerprint("00012345", FP, 102, "acme");
    assertThat(c.fingerprint("00012345", FP)).get().extracting(SubmissionClaims.Claim::transmissionId).isEqualTo(102L);
    c.releaseFingerprint("00012345", FP, 101); // not ours any more: kept
    assertThat(c.fingerprint("00012345", FP)).isPresent();
    c.releaseFingerprint("00012345", FP, 102);
    assertThat(c.fingerprint("00012345", FP)).isEmpty();
  }

  @Test
  void idempotencyKeysArePerAccountAndRememberTheFile() {
    SubmissionClaims c = claims(Instant.now());
    assertThat(c.claimKey("acme", "key-0001-abcd", FP, 101)).isEmpty();
    assertThat(c.key("acme", "key-0001-abcd")).get().extracting(SubmissionClaims.Claim::fingerprint).isEqualTo(FP);
    assertThat(c.key("vendor", "key-0001-abcd")).isEmpty();
    // client text never becomes a path segment
    assertThat(c.keyPath("acme", "../../etc/passwd").normalize()).startsWithRaw(root.toAbsolutePath().normalize().resolve("api-claims"));
  }
}
