package ca.bc.gov.nrs.hbs.api.submission;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Where submitted scale-data XML waits for the intake batch — the replacement
 * for the legacy {@code XMLinput\<client>\<transmissionId>.xml}
 * SMB share (and XMLarchive after unpacking).
 *
 * <p>Backed by a directory that in OpenShift is a ReadWriteMany PVC mounted at
 * {@code hbs.storage.xml-root} (see backend/openshift.deploy.yml), so every
 * backend pod — web upload and the ShedLock'd intake job alike — sees the same
 * files. Keys are validated to {@code <8-digit client>/<numeric id>.xml}, so a
 * caller can never escape the root (the legacy report servlet had a path
 * traversal hole; see docs/pinch-points.md).
 */
@Component
public class SubmissionStorage {

  public enum Area { INPUT, ARCHIVE, REJECTED }

  private final Path root;

  public SubmissionStorage(@Value("${hbs.storage.xml-root:/data/hbs/xml}") String root) {
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public String store(Area area, String clientNumber, long transmissionId, InputStream content) {
    Path target = resolve(area, clientNumber, transmissionId);
    try {
      Files.createDirectories(target.getParent());
      try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
        content.transferTo(out);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not store transmission " + transmissionId, ex);
    }
    return root.relativize(target).toString();
  }

  public InputStream open(Area area, String clientNumber, long transmissionId) throws IOException {
    return Files.newInputStream(resolve(area, clientNumber, transmissionId));
  }

  /** Moves the file (and its submitter sidecar, if any) to another area, e.g. INPUT → ARCHIVE after unpacking. */
  public void move(Area from, Area to, String clientNumber, long transmissionId) throws IOException {
    Path target = resolve(to, clientNumber, transmissionId);
    Files.createDirectories(target.getParent());
    Files.move(resolve(from, clientNumber, transmissionId), target);
    Path sidecar = submitterFile(from, clientNumber, transmissionId);
    if (Files.exists(sidecar)) {
      Files.move(sidecar, submitterFile(to, clientNumber, transmissionId), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public boolean exists(Area area, String clientNumber, long transmissionId) {
    return Files.isRegularFile(resolve(area, clientNumber, transmissionId));
  }

  /**
   * Who submitted the file, kept next to it for the unpack: the legacy B1012
   * read the submitter's roles (WebADE) when it unpacked, for edit FXA04; the
   * roles now live in the token, so P505 records the one that matters.
   */
  public void storeSubmitter(String clientNumber, long transmissionId, boolean softwareVendor) {
    Properties props = new Properties();
    props.setProperty(SOFTWARE_VENDOR, Boolean.toString(softwareVendor));
    try (var out = Files.newOutputStream(submitterFile(Area.INPUT, clientNumber, transmissionId))) {
      props.store(out, "P505 submitter");
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not store submitter of transmission " + transmissionId, ex);
    }
  }

  /** The submitter recorded at upload: true / false, or null when none was recorded. */
  public Boolean submitterIsSoftwareVendor(String clientNumber, long transmissionId) throws IOException {
    Path file = submitterFile(Area.INPUT, clientNumber, transmissionId);
    if (!Files.exists(file)) {
      return null;
    }
    Properties props = new Properties();
    try (var in = Files.newInputStream(file)) {
      props.load(in);
    }
    return Boolean.parseBoolean(props.getProperty(SOFTWARE_VENDOR));
  }

  /** A storage key ({@code <area>/<client>/<id>.xml}) split into its parts, or null when it is not one. */
  public record Key(Area area, String clientNumber, long transmissionId) {}

  public static Key parseKey(String key) {
    if (key == null) {
      return null;
    }
    var m = java.util.regex.Pattern.compile("(input|archive|rejected)[/\\\\](\\d{8})[/\\\\](\\d{1,18})\\.xml")
        .matcher(key);
    if (!m.matches()) {
      return null;
    }
    return new Key(Area.valueOf(m.group(1).toUpperCase()), m.group(2), Long.parseLong(m.group(3)));
  }

  private Path submitterFile(Area area, String clientNumber, long transmissionId) {
    Path xml = resolve(area, clientNumber, transmissionId);
    return xml.resolveSibling(transmissionId + ".submitter");
  }

  private static final String SOFTWARE_VENDOR = "softwareVendor";

  /** Every file waiting in INPUT, as {@code client/id.xml} relative keys. */
  public Stream<Path> pending() throws IOException {
    Path input = root.resolve(Area.INPUT.name().toLowerCase());
    if (!Files.isDirectory(input)) return Stream.empty();
    return Files.walk(input, 2).filter(p -> p.toString().endsWith(".xml"));
  }

  Path resolve(Area area, String clientNumber, long transmissionId) {
    String client = clientNumber == null || clientNumber.isBlank() ? "00000000" : clientNumber;
    if (!client.matches("\\d{8}") || transmissionId <= 0) {
      throw new IllegalArgumentException("Invalid storage key");
    }
    Path p = root.resolve(area.name().toLowerCase()).resolve(client).resolve(transmissionId + ".xml").normalize();
    if (!p.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
    return p;
  }
}
