package ca.bc.gov.nrs.hbs.api.scaledata;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Duplicate and retry detection for submissions (docs/scale-data-api.md
 * "Duplicates and retries"): which transmission a file (SHA-256 of the bytes,
 * per client) or an {@code Idempotency-Key} (per account) already produced.
 *
 * <p>Kept as small claim files on the shared XML volume, next to the files
 * themselves ({@code hbs.storage.xml-root}, a ReadWriteMany PVC), so every pod
 * sees them and no new table or proc is needed in nr-mof-db (open question 6).
 * A claim is written to a temporary file and hard-linked into place, which is
 * atomic and exclusive on the volume, so two identical requests at the same
 * moment are settled here: one wins and the other answers with the winner's
 * transmission. Claims older than the
 * duplicate window are ignored and purged.
 *
 * <pre>
 * &lt;root&gt;/api-claims/fingerprint/&lt;client&gt;/&lt;sha256&gt;.claim
 * &lt;root&gt;/api-claims/key/&lt;sha256(account)&gt;/&lt;sha256(key)&gt;.claim
 * </pre>
 */
@Component
@Slf4j
public class SubmissionClaims {

  /** What a claim records. */
  public record Claim(long transmissionId, String fingerprint, String account, Instant claimedAt) {
  }

  private final Path root;
  private final Duration window;
  private final Clock clock;

  @Autowired
  public SubmissionClaims(@Value("${hbs.storage.xml-root:/data/hbs/xml}") String xmlRoot,
      ScaleDataApiProperties properties) {
    this(Path.of(xmlRoot), properties.duplicateWindow(), Clock.systemUTC());
  }

  SubmissionClaims(Path xmlRoot, Duration window, Clock clock) {
    this.root = xmlRoot.toAbsolutePath().normalize().resolve("api-claims");
    this.window = window;
    this.clock = clock;
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  // ── fingerprints (per client) ──────────────────────────────────────────

  public Optional<Claim> fingerprint(String clientNumber, String fingerprint) {
    return read(fingerprintPath(clientNumber, fingerprint));
  }

  /** Claims the fingerprint for a transmission; empty when won, else the existing claim. */
  public Optional<Claim> claimFingerprint(String clientNumber, String fingerprint, long transmissionId, String account) {
    return claim(fingerprintPath(clientNumber, fingerprint), new Claim(transmissionId, fingerprint, account, now()));
  }

  /** Points the fingerprint at a new transmission (resubmission after a rejection, or a stale claim). */
  public void replaceFingerprint(String clientNumber, String fingerprint, long transmissionId, String account) {
    replace(fingerprintPath(clientNumber, fingerprint), new Claim(transmissionId, fingerprint, account, now()));
  }

  /** Releases a fingerprint claim this request made, when it failed before recording its transmission. */
  public void releaseFingerprint(String clientNumber, String fingerprint, long transmissionId) {
    release(fingerprintPath(clientNumber, fingerprint), transmissionId);
  }

  // ── idempotency keys (per account) ─────────────────────────────────────

  public Optional<Claim> key(String account, String key) {
    return read(keyPath(account, key));
  }

  public Optional<Claim> claimKey(String account, String key, String fingerprint, long transmissionId) {
    return claim(keyPath(account, key), new Claim(transmissionId, fingerprint, account, now()));
  }

  public void replaceKey(String account, String key, String fingerprint, long transmissionId) {
    replace(keyPath(account, key), new Claim(transmissionId, fingerprint, account, now()));
  }

  public void releaseKey(String account, String key, long transmissionId) {
    release(keyPath(account, key), transmissionId);
  }

  // ── housekeeping ───────────────────────────────────────────────────────

  /** Deletes claims older than the window; returns how many. Safe to run on every pod at once. */
  public int purgeExpired() {
    if (!Files.isDirectory(root)) {
      return 0;
    }
    int deleted = 0;
    Instant cutoff = now().minus(window);
    try (Stream<Path> files = Files.walk(root, 4)) {
      for (Path p : files.filter(f -> f.toString().endsWith(".claim") || f.toString().endsWith(".tmp")).toList()) {
        try {
          if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) {
            Files.deleteIfExists(p);
            deleted++;
          }
        } catch (IOException ex) {
          log.debug("Could not purge claim {}", p, ex);
        }
      }
    } catch (IOException ex) {
      log.warn("Could not walk the submission claims under {}", root, ex);
    }
    return deleted;
  }

  // ── files ──────────────────────────────────────────────────────────────

  Path fingerprintPath(String clientNumber, String fingerprint) {
    if (clientNumber == null || !clientNumber.matches("\\d{8}") || !fingerprint.matches("[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid fingerprint claim");
    }
    return root.resolve("fingerprint").resolve(clientNumber).resolve(fingerprint + ".claim");
  }

  Path keyPath(String account, String key) {
    // Hash both: the key is client text, the account id is ours but needn't be a path segment.
    return root.resolve("key").resolve(sha256(account.getBytes(StandardCharsets.UTF_8)))
        .resolve(sha256(key.getBytes(StandardCharsets.UTF_8)) + ".claim");
  }

  private Optional<Claim> read(Path path) {
    Properties props = new Properties();
    try (var in = Files.newInputStream(path)) {
      props.load(in);
    } catch (NoSuchFileException ex) {
      return Optional.empty();
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not read submission claim " + path.getFileName(), ex);
    }
    Claim claim;
    try {
      claim = new Claim(Long.parseLong(props.getProperty("transmissionId")), props.getProperty("fingerprint"),
          props.getProperty("account"), Instant.parse(props.getProperty("claimedAt")));
    } catch (RuntimeException ex) {
      log.warn("Ignoring unreadable submission claim {}", path.getFileName());
      return Optional.empty();
    }
    if (claim.claimedAt().isBefore(now().minus(window))) {
      return Optional.empty();
    }
    return Optional.of(claim);
  }

  /**
   * Writes the claim to a temporary file, then hard-links it into place: the
   * link fails if the claim exists, so exactly one request wins, and a claim
   * file is never seen half-written.
   */
  private Optional<Claim> claim(Path path, Claim claim) {
    try {
      Files.createDirectories(path.getParent());
      Path tmp = temp(path, claim);
      try {
        Files.createLink(path, tmp);
        return Optional.empty();
      } catch (FileAlreadyExistsException ex) {
        Optional<Claim> existing = read(path);
        if (existing.isPresent()) {
          return existing;
        }
        // Expired or unreadable: take it over (rare; a resend 30 days later).
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        return Optional.empty();
      } catch (UnsupportedOperationException ex) {
        // No hard links on this file system: exclusive create (the content follows at once).
        return claimWithoutLinks(path, claim);
      } finally {
        Files.deleteIfExists(tmp);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not record submission claim", ex);
    }
  }

  private Optional<Claim> claimWithoutLinks(Path path, Claim claim) throws IOException {
    try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      write(out, claim);
      return Optional.empty();
    } catch (FileAlreadyExistsException ex) {
      Optional<Claim> existing = read(path);
      // A claim being written right now reads as empty: report it as taken, not free.
      return Optional.of(existing.orElse(claim));
    }
  }

  private static Path temp(Path path, Claim claim) throws IOException {
    Path tmp = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
    try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      write(out, claim);
    }
    return tmp;
  }

  private void replace(Path path, Claim claim) {
    try {
      Files.createDirectories(path.getParent());
      Path tmp = temp(path, claim);
      try {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not record submission claim", ex);
    }
  }

  private void release(Path path, long transmissionId) {
    try {
      Optional<Claim> existing = read(path);
      if (existing.isPresent() && existing.get().transmissionId() == transmissionId) {
        Files.deleteIfExists(path);
      }
    } catch (IOException | UncheckedIOException ex) {
      log.warn("Could not release submission claim for transmission {}", transmissionId, ex);
    }
  }

  private static void write(OutputStream out, Claim claim) throws IOException {
    Properties props = new Properties();
    props.setProperty("transmissionId", Long.toString(claim.transmissionId()));
    props.setProperty("fingerprint", claim.fingerprint() == null ? "" : claim.fingerprint());
    props.setProperty("account", claim.account() == null ? "" : claim.account());
    props.setProperty("claimedAt", claim.claimedAt().toString());
    props.store(out, null);
  }

  private Instant now() {
    return clock.instant();
  }
}
