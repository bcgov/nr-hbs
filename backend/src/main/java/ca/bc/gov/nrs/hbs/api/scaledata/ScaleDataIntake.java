package ca.bc.gov.nrs.hbs.api.scaledata;

import ca.bc.gov.nrs.hbs.api.intake.UnpackRepository;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.DocumentCounts;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionResult;
import ca.bc.gov.nrs.hbs.api.service.v1.VirusScanner;
import ca.bc.gov.nrs.hbs.api.submission.ScaleDataXmlValidator;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionService;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * {@code POST /submissions} and {@code POST /submissions:validate}
 * (docs/scale-data-api.md "The request pipeline"):
 *
 * <ol>
 *   <li>size and client mapping (cheap refusals first);</li>
 *   <li>a resend of a file HBS already has (same bytes for the client within the
 *       duplicate window, or the same {@code Idempotency-Key}) answers with the
 *       original transmission, and nothing new is stored;</li>
 *   <li>virus scan (fail-closed: 503 when the scanner is down) and the
 *       HBS_Schema_V6_1b check; a file failing either leaves no transmission;</li>
 *   <li>claim, store and record the transmission (RCD/CSA), the same way as a
 *       P505 upload ({@link SubmissionService#receive});</li>
 *   <li>unpack in the request, or queue a large file for the worker (202).</li>
 * </ol>
 */
@Service
@Slf4j
public class ScaleDataIntake {

  /** A claim whose transmission never appeared after this long is from a request that died. */
  static final Duration STALE_CLAIM = Duration.ofMinutes(15);
  static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,128}");
  /** Schema errors returned for one file. */
  static final int MAX_SCHEMA_ERRORS = 200;

  /** One uploaded file, as the controller read it. */
  public record Upload(byte[] bytes, String fileName, String clientNumber, String idempotencyKey) {
  }

  /** The HTTP status and body of a submission. */
  public record Outcome(HttpStatus status, TransmissionResult result) {
  }

  /** The dry run's answer. */
  public record ValidationReport(boolean valid, String clientNumber, boolean testFile, int batchCount,
      int documentCount, List<ApiErrorItem> errors) {
  }

  private final SubmissionService submissions;
  private final VirusScanner virusScanner;
  private final ScaleDataXmlValidator schema;
  private final SubmissionClaims claims;
  private final ScaleDataReads reads;
  private final ScaleDataWorker worker;
  private final UnpackRepository lookups;
  private final EditMessages messages;
  private final ScaleDataApiProperties properties;

  public ScaleDataIntake(SubmissionService submissions, VirusScanner virusScanner, ScaleDataXmlValidator schema,
      SubmissionClaims claims, ScaleDataReads reads, ScaleDataWorker worker, UnpackRepository lookups,
      EditMessages messages, ScaleDataApiProperties properties) {
    this.submissions = submissions;
    this.virusScanner = virusScanner;
    this.schema = schema;
    this.claims = claims;
    this.reads = reads;
    this.worker = worker;
    this.lookups = lookups;
    this.messages = messages;
    this.properties = properties;
  }

  // ── submit ─────────────────────────────────────────────────────────────

  public Outcome submit(ApiAccount account, Upload upload) {
    String client = account.resolveClient(upload.clientNumber());
    byte[] bytes = checkSize(upload.bytes());
    String key = idempotencyKey(upload.idempotencyKey());
    String fingerprint = SubmissionClaims.sha256(bytes);
    String fileName = SubmissionService.sanitizeName(upload.fileName() == null || upload.fileName().isBlank()
        ? "api-" + fingerprint.substring(0, 12) + ".xml" : upload.fileName());

    // A resend of a file HBS already has: answer with the original, store nothing.
    if (key != null) {
      Optional<Outcome> replay = claims.key(account.clientId(), key)
          .flatMap(c -> replay(c, fingerprint, account));
      if (replay.isPresent()) {
        return replay.get();
      }
    } else {
      Optional<Outcome> duplicate = claims.fingerprint(client, fingerprint).flatMap(c -> duplicate(c, account));
      if (duplicate.isPresent()) {
        return duplicate.get();
      }
    }

    scanAndValidate(bytes, fileName);

    long transmissionId = submissions.nextTransmissionId();
    if (key != null) {
      Optional<SubmissionClaims.Claim> lost = claims.claimKey(account.clientId(), key, fingerprint, transmissionId);
      if (lost.isPresent()) {
        Optional<Outcome> replay = replay(lost.get(), fingerprint, account);
        if (replay.isPresent()) {
          return replay.get();
        }
        claims.replaceKey(account.clientId(), key, fingerprint, transmissionId);
      }
    } else {
      Optional<SubmissionClaims.Claim> lost = claims.claimFingerprint(client, fingerprint, transmissionId,
          account.clientId());
      if (lost.isPresent()) {
        Optional<Outcome> duplicate = duplicate(lost.get(), account);
        if (duplicate.isPresent()) {
          return duplicate.get();
        }
        // The earlier transmission was rejected (or its request died): process this one.
        claims.replaceFingerprint(client, fingerprint, transmissionId, account.clientId());
      }
    }

    try {
      submissions.receive(new SubmissionService.Incoming(transmissionId, bytes, fileName, client,
          account.auditUser(), SubmissionStorage.Channel.API, false));
    } catch (RuntimeException ex) {
      if (key != null) {
        claims.releaseKey(account.clientId(), key, transmissionId);
      } else {
        claims.releaseFingerprint(client, fingerprint, transmissionId);
      }
      throw ex;
    }
    if (key != null) {
      // So a later resend without the key is recognised too.
      claims.replaceFingerprint(client, fingerprint, transmissionId, account.clientId());
    }

    worker.purgeClaimsIfDue(claims);
    if (bytes.length > properties.queueThresholdBytes()) {
      worker.enqueue(transmissionId);
    } else {
      worker.processNow(transmissionId);
    }
    TransmissionResult result = reads.transmission(transmissionId)
        .map(reads::result)
        .orElseThrow(() -> new IllegalStateException("Transmission " + transmissionId + " not found after receipt"));
    log.info("Scale data API: {} submitted {} for client {} as transmission {} → {}", account.clientId(), fileName,
        client, transmissionId, result.status());
    return respond(result, false);
  }

  // ── validate (dry run) ─────────────────────────────────────────────────

  public ValidationReport validate(ApiAccount account, Upload upload) {
    String client = account.resolveClient(upload.clientNumber());
    byte[] bytes = checkSize(upload.bytes());
    scanAndValidate(bytes, upload.fileName());
    FormatCheck.Report report = new FormatCheck(lookups, messages::formatEdit).check(bytes);
    return new ValidationReport(report.errors().isEmpty(), client, report.testFile(), report.batchCount(),
        report.documentCount(), report.errors());
  }

  // ── steps ──────────────────────────────────────────────────────────────

  private byte[] checkSize(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      throw ScaleDataApiException.emptyFile();
    }
    if (bytes.length > properties.maxBytes()) {
      throw ScaleDataApiException.fileTooLarge(properties.maxBytes());
    }
    return bytes;
  }

  private static String idempotencyKey(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String key = raw.trim();
    if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
      throw ScaleDataApiException.idempotencyKeyInvalid();
    }
    return key;
  }

  private void scanAndValidate(byte[] bytes, String fileName) {
    virusScanner.check(bytes, fileName).ifPresent(rejection -> {
      if (VirusScanner.CODE_SCAN_UNAVAILABLE.equals(rejection.code())) {
        throw ScaleDataApiException.virusScanUnavailable();
      }
      throw ScaleDataApiException.virusDetected(rejection.message());
    });
    List<ScaleDataXmlValidator.SchemaError> errors = schema.validateDetailed(bytes, MAX_SCHEMA_ERRORS);
    if (!errors.isEmpty()) {
      throw ScaleDataApiException.schemaInvalid(errors.stream()
          .map(e -> ApiErrorItem.file(e.line(), e.column(), e.message())).toList());
    }
  }

  /**
   * The answer to a resend matched by its fingerprint, or empty to process it
   * as new: the earlier transmission was rejected (so a file refused for data
   * HBS has since fixed can be sent again as it is), or its request died before
   * recording it.
   */
  private Optional<Outcome> duplicate(SubmissionClaims.Claim claim, ApiAccount account) {
    Optional<ScaleDataReads.Transmission> earlier = reads.transmission(claim.transmissionId(), account);
    if (earlier.isEmpty()) {
      return inFlight(claim, account);
    }
    TransmissionResult result = reads.result(earlier.get());
    if (ScaleDataStatus.REJECTED.equals(result.status())) {
      return Optional.empty();
    }
    return Optional.of(respond(result, true));
  }

  /** The answer to a resend with the same Idempotency-Key: the original result, or 409 for another file. */
  private Optional<Outcome> replay(SubmissionClaims.Claim claim, String fingerprint, ApiAccount account) {
    if (!fingerprint.equals(claim.fingerprint())) {
      throw ScaleDataApiException.idempotencyKeyReused();
    }
    Optional<ScaleDataReads.Transmission> earlier = reads.transmission(claim.transmissionId(), account);
    if (earlier.isEmpty()) {
      return inFlight(claim, account);
    }
    return Optional.of(respond(reads.result(earlier.get()), true));
  }

  /** The first request is still recording its transmission (202), or died (empty: process again). */
  private Optional<Outcome> inFlight(SubmissionClaims.Claim claim, ApiAccount account) {
    if (claim.claimedAt().isBefore(Instant.now().minus(STALE_CLAIM))) {
      return Optional.empty();
    }
    long id = claim.transmissionId();
    return Optional.of(new Outcome(HttpStatus.ACCEPTED, new TransmissionResult(id, null, null, null, null,
        ScaleDataStatus.QUEUED, "RCD", "PRC", false, true, 0, 0, 0, new DocumentCounts(0, 0, 0, 0, 0, 0, 0),
        List.of(), false, ScaleDataReads.statusUrl(id), ScaleDataReads.documentsUrl(id))));
  }

  /**
   * 201 processed (200 for a duplicate), 202 not final yet, 422 FILE_REJECTED
   * for a file the unpack rejected as a whole.
   */
  static Outcome respond(TransmissionResult result, boolean duplicate) {
    TransmissionResult body = duplicate ? result.asDuplicate() : result;
    if (ScaleDataStatus.REJECTED.equals(body.status())) {
      throw new ScaleDataApiException(HttpStatus.UNPROCESSABLE_CONTENT, "FILE_REJECTED",
          "The file was received but could not be processed.", body.errors(),
          Map.of("transmissionId", body.transmissionId(), "duplicate", duplicate), null);
    }
    if (!body.isFinal()) {
      return new Outcome(HttpStatus.ACCEPTED, body);
    }
    return new Outcome(duplicate ? HttpStatus.OK : HttpStatus.CREATED, body);
  }
}
