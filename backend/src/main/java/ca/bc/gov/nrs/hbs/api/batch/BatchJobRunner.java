package ca.bc.gov.nrs.hbs.api.batch;

import ca.bc.gov.nrs.hbs.api.notification.EmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Common wrapper for every job: start/end logging with the legacy job id
 * (B1011, B9000 …) and a "Batch Failure - <job>" email to the HBS support
 * mailbox on error — the replacement for the legacy log4j
 * HbsRollUpAppender + HbsSMTPAppender. Unlike the legacy setup, successful
 * runs log at INFO, so support no longer gets an email on every run.
 */
@Component
@Slf4j
public class BatchJobRunner {

  private final EmailNotificationService email;

  public BatchJobRunner(EmailNotificationService email) {
    this.email = email;
  }

  @FunctionalInterface
  public interface Job {
    String run() throws Exception;
  }

  public void run(String jobId, String description, Job job) {
    Instant start = Instant.now();
    log.info("Batch {} ({}) started", jobId, description);
    try {
      String summary = job.run();
      log.info("Batch {} completed in {} s: {}", jobId,
          Duration.between(start, Instant.now()).toSeconds(), summary);
    } catch (Exception ex) {
      log.error("Batch {} failed", jobId, ex);
      email.sendBatchFailure(jobId, description + ": " + ex.getMessage());
    }
  }
}
