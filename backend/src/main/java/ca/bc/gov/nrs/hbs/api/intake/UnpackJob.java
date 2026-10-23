package ca.bc.gov.nrs.hbs.api.intake;

import ca.bc.gov.nrs.hbs.api.batch.BatchJobRunner;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * B1012 for files uploaded through this app's P505 page: unpacks every
 * transmission this app received (RECD_DIRECTORY = a {@code SubmissionStorage}
 * key) that waits at RECEIVING/SUCCESS, one transaction each, then moves its
 * file to the archive area. FTP submissions stay with legacy B1011/B1012,
 * which only scan the XMLinput share and never see these files.
 *
 * <p>Off unless {@code hbs.batch.enabled} AND {@code hbs.batch.unpack.enabled}
 * are true. A transmission that fails with anything but a format edit is
 * rolled back (still RCD/CSA, file still in the input area), retried on the
 * next run, and reported by a failure e-mail; {@code XmlIntakeMonitorJob}
 * alerts while its file waits.
 */
@Component
@Slf4j
@ConditionalOnExpression("${hbs.batch.enabled:false} and ${hbs.batch.unpack.enabled:false}")
public class UnpackJob {

  private final BatchJobRunner runner;
  private final UnpackRepository repository;
  private final UnpackService service;
  private final SubmissionStorage storage;

  public UnpackJob(BatchJobRunner runner, UnpackRepository repository, UnpackService service,
      SubmissionStorage storage) {
    this.runner = runner;
    this.repository = repository;
    this.service = service;
    this.storage = storage;
  }

  @Scheduled(cron = "${hbs.batch.unpack.cron}", zone = "${hbs.batch.zone}")
  @SchedulerLock(name = "HBS_B1012_UNPACK", lockAtMostFor = "PT1H")
  public void unpack() {
    runner.run("B1012", "Unpack scale-data XML uploaded in the new app", this::runOnce);
  }

  /** One pass over the waiting transmissions; throws (→ failure e-mail) when any could not be unpacked. */
  String runOnce() {
    int unpacked = 0;
    int rejected = 0;
    long documents = 0;
    List<String> failures = new ArrayList<>();
    for (UnpackRepository.Pending pending : repository.pendingTransmissions()) {
      SubmissionStorage.Key key = SubmissionStorage.parseKey(pending.recdDirectory());
      if (key == null || key.area() != SubmissionStorage.Area.INPUT || key.transmissionId() != pending.transmissionId()) {
        failures.add(pending.transmissionId() + ": unexpected RECD_DIRECTORY " + pending.recdDirectory());
        continue;
      }
      if (!storage.exists(SubmissionStorage.Area.INPUT, key.clientNumber(), key.transmissionId())) {
        failures.add(pending.transmissionId() + ": file " + pending.recdDirectory() + " not found");
        continue;
      }
      try {
        TransmissionUnpacker.Outcome outcome = service.unpack(pending, key);
        documents += outcome.documents();
        if (TransmissionUnpacker.STATUS_SUCCESS.equals(outcome.status())) {
          unpacked++;
        } else {
          rejected++;
        }
        log.info("Transmission {} unpacked: {} ({} documents{})", pending.transmissionId(), outcome.status(),
            outcome.documents(), outcome.formatError() == null ? "" : ", " + outcome.formatError());
      } catch (RuntimeException ex) {
        log.error("Transmission {} could not be unpacked; rolled back", pending.transmissionId(), ex);
        failures.add(pending.transmissionId() + ": " + ex.getMessage());
        continue;
      }
      try {
        storage.move(SubmissionStorage.Area.INPUT, SubmissionStorage.Area.ARCHIVE, key.clientNumber(),
            key.transmissionId());
      } catch (Exception ex) {
        log.error("Transmission {} unpacked but its file could not be archived", pending.transmissionId(), ex);
        failures.add(pending.transmissionId() + ": unpacked, archive move failed: " + ex.getMessage());
      }
    }
    String summary = unpacked + " unpacked, " + rejected + " rejected (UNP/CUS), " + documents + " documents";
    if (!failures.isEmpty()) {
      throw new IllegalStateException(summary + "; " + failures.size() + " failed: " + String.join("; ", failures));
    }
    return summary;
  }
}
