package ca.bc.gov.nrs.hbs.api.submission;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

  public void move(Area from, Area to, String clientNumber, long transmissionId) throws IOException {
    Path target = resolve(to, clientNumber, transmissionId);
    Files.createDirectories(target.getParent());
    Files.move(resolve(from, clientNumber, transmissionId), target);
  }

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
