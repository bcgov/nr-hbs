package ca.bc.gov.nrs.hbs.api.batch;

import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Intake watchdog for B1011/B1012 (receive + unpack scale-data XML).
 *
 * <p>The unpack itself (legacy {@code hbs-batch B1012}, 2.7K LOC: Castor
 * unmarshal, scaler digital-signature verification, creation of transmission
 * → batch → document → version rows via ~40 HBS_CREATE_* procs) is NOT yet
 * ported — see docs/batch-jobs.md and docs/legacy-logic-to-port.md. Until it
 * is, this job reports files that have waited in the input area longer than
 * expected so nothing submitted through the new upload is silently stranded.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "hbs.batch.enabled", havingValue = "true")
public class XmlIntakeMonitorJob {

  private static final Duration STALE = Duration.ofHours(2);

  private final BatchJobRunner runner;
  private final SubmissionStorage storage;

  public XmlIntakeMonitorJob(BatchJobRunner runner, SubmissionStorage storage) {
    this.runner = runner;
    this.storage = storage;
  }

  @Scheduled(cron = "${hbs.batch.xml-intake.cron}", zone = "${hbs.batch.zone}")
  @SchedulerLock(name = "HBS_B1011_INTAKE_MONITOR", lockAtMostFor = "PT10M")
  public void check() {
    runner.run("B1011", "Scale-data intake monitor", () -> {
      Instant cutoff = Instant.now().minus(STALE);
      long stale;
      try (Stream<java.nio.file.Path> pending = storage.pending()) {
        stale = pending.filter(p -> {
          try {
            return Files.getLastModifiedTime(p).toInstant().isBefore(cutoff);
          } catch (java.io.IOException e) {
            return false;
          }
        }).count();
      }
      if (stale > 0) {
        throw new IllegalStateException(stale + " submitted file(s) have waited more than "
            + STALE.toHours() + "h for unpacking");
      }
      return "no stale submissions";
    });
  }
}
