package ca.bc.gov.nrs.hbs.api.batch;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * B9000 — purge archived scale-data XML. The legacy job deleted files from the
 * XMLarchive share once the transmission's retention passed; here the archive
 * is {@code <xml-root>/archive}. Retention defaults to the legacy 7-year
 * document look-back (LimitYear.SEVEN) and is configurable.
 *
 * <p>Rejected files ({@code <xml-root>/rejected}) follow the same rule.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "hbs.batch.enabled", havingValue = "true")
public class XmlArchivePurgeJob {

  private final BatchJobRunner runner;
  private final Path root;
  private final Duration retention;

  public XmlArchivePurgeJob(BatchJobRunner runner,
      @Value("${hbs.storage.xml-root:/data/hbs/xml}") String root,
      @Value("${hbs.batch.purge.retention-days:2557}") long retentionDays) {
    this.runner = runner;
    this.root = Path.of(root);
    this.retention = Duration.ofDays(retentionDays);
  }

  @Scheduled(cron = "${hbs.batch.purge.cron}", zone = "${hbs.batch.zone}")
  @SchedulerLock(name = "HBS_B9000_XML_PURGE", lockAtMostFor = "PT1H")
  public void purge() {
    runner.run("B9000", "Purge archived scale-data XML", () -> {
      FileTime cutoff = FileTime.from(Instant.now().minus(retention));
      AtomicInteger deleted = new AtomicInteger();
      for (String area : new String[] {"archive", "rejected"}) {
        Path dir = root.resolve(area);
        if (!Files.isDirectory(dir)) continue;
        try (Stream<Path> files = Files.walk(dir, 2)) {
          files.filter(Files::isRegularFile).forEach(f -> {
            try {
              if (Files.getLastModifiedTime(f).compareTo(cutoff) < 0) {
                Files.delete(f);
                deleted.incrementAndGet();
              }
            } catch (IOException ex) {
              log.warn("Could not purge {}", f, ex);
            }
          });
        }
      }
      return deleted.get() + " file(s) purged";
    });
  }
}
