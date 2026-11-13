package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.intake.TransmissionUnpacker;
import ca.bc.gov.nrs.hbs.api.intake.UnpackRepository;
import ca.bc.gov.nrs.hbs.api.intake.UnpackService;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Processes received API files: in the request for normal files, on a small
 * worker pool for large ones (answered 202). Unpacking is the B1012 port
 * ({@link UnpackService}, one transaction, row-locked against
 * {@code intake.UnpackJob}); the file moves to the archive area after the
 * commit, as the job does.
 *
 * <p>Nothing is lost if a pod stops mid-file: the transmission stays at
 * RCD/CSA with its file in the input area, and {@code UnpackJob}
 * ({@code HBS_BATCH_UNPACK_ENABLED}) is the sweep that finishes it.
 *
 * <p>The detail edits (B1021) are not ported yet: after the unpack a file waits
 * at UNP/CSA (PROCESSING) for the legacy batch, which picks up transmissions
 * at that step exactly as it does for P505 uploads.
 */
@Component
@Slf4j
public class ScaleDataWorker {

  private final UnpackRepository repository;
  private final UnpackService unpackService;
  private final SubmissionStorage storage;
  private final ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(100), new ThreadFactoryBuilder().setNameFormat("scale-data-intake-%d").build(),
      new ThreadPoolExecutor.AbortPolicy());

  public ScaleDataWorker(UnpackRepository repository, UnpackService unpackService, SubmissionStorage storage) {
    this.repository = repository;
    this.unpackService = unpackService;
    this.storage = storage;
  }

  /**
   * Unpacks the transmission now. Returns false when it couldn't (the error is
   * logged; the sweep retries), true when it was unpacked here or already
   * elsewhere.
   */
  public boolean processNow(long transmissionId) {
    UnpackRepository.Pending pending = repository.pendingTransmission(transmissionId);
    if (pending == null) {
      return true; // already unpacked (or never ours): nothing to do
    }
    SubmissionStorage.Key key = SubmissionStorage.parseKey(pending.recdDirectory());
    if (key == null || key.area() != SubmissionStorage.Area.INPUT || key.transmissionId() != transmissionId) {
      log.error("Transmission {} has an unexpected RECD_DIRECTORY {}", transmissionId, pending.recdDirectory());
      return false;
    }
    TransmissionUnpacker.Outcome outcome;
    try {
      outcome = unpackService.unpack(pending, key);
    } catch (RuntimeException ex) {
      log.error("Transmission {} could not be unpacked; rolled back, left for the sweep", transmissionId, ex);
      return false;
    }
    if (outcome == null) {
      return true;
    }
    log.info("Transmission {} unpacked in the request: {} ({} documents{})", transmissionId, outcome.status(),
        outcome.documents(), outcome.formatError() == null ? "" : ", " + outcome.formatError());
    try {
      storage.move(SubmissionStorage.Area.INPUT, SubmissionStorage.Area.ARCHIVE, key.clientNumber(), transmissionId);
    } catch (Exception ex) {
      log.error("Transmission {} unpacked but its file could not be archived", transmissionId, ex);
    }
    return true;
  }

  private static final long PURGE_EVERY_MILLIS = java.time.Duration.ofHours(6).toMillis();
  private final java.util.concurrent.atomic.AtomicLong lastPurge = new java.util.concurrent.atomic.AtomicLong();

  /**
   * Deletes expired duplicate/retry claims, at most every six hours per pod, on
   * the worker pool (never in the request). Safe on several pods at once.
   */
  public void purgeClaimsIfDue(SubmissionClaims claims) {
    long now = System.currentTimeMillis();
    long last = lastPurge.get();
    if (now - last < PURGE_EVERY_MILLIS || !lastPurge.compareAndSet(last, now)) {
      return;
    }
    try {
      pool.execute(() -> {
        int purged = claims.purgeExpired();
        if (purged > 0) {
          log.info("Purged {} expired scale-data submission claims", purged);
        }
      });
    } catch (RejectedExecutionException ex) {
      lastPurge.set(last); // try again on a later request
    }
  }

  /** Queues the transmission for the worker pool; when the queue is full it is left for the sweep. */
  public void enqueue(long transmissionId) {
    Map<String, String> mdc = MDC.getCopyOfContextMap();
    try {
      pool.execute(() -> {
        if (mdc != null) {
          MDC.setContextMap(mdc);
        }
        try {
          processNow(transmissionId);
        } finally {
          MDC.clear();
        }
      });
    } catch (RejectedExecutionException ex) {
      log.warn("Scale-data worker queue full; transmission {} is left for the unpack sweep", transmissionId);
    }
  }

  @PreDestroy
  void shutdown() throws InterruptedException {
    pool.shutdown();
    if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
      log.warn("Scale-data worker stopped with files in progress; the unpack sweep will finish them");
      pool.shutdownNow();
    }
  }
}
