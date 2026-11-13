package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.intake.UnpackRepository;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.DocumentCounts;
import ca.bc.gov.nrs.hbs.api.scaledata.ScaleDataModels.TransmissionResult;
import ca.bc.gov.nrs.hbs.api.service.v1.VirusScanner;
import ca.bc.gov.nrs.hbs.api.submission.ScaleDataXmlValidator;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionService;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** The submit pipeline's decisions, with the database and the unpack mocked. */
class ScaleDataIntakeTest {

  @TempDir
  Path root;

  static final ApiAccount ACME = new ApiAccount("hbs-scale-acme", List.of("00012345"));
  static final byte[] FILE = ("<?xml version=\"1.0\"?><Submission xmlns=\"gov.bc.ca/forests/hbs/v3\"/>")
      .getBytes(StandardCharsets.UTF_8);

  SubmissionService submissions = mock(SubmissionService.class);
  VirusScanner virus = mock(VirusScanner.class);
  ScaleDataXmlValidator schema = mock(ScaleDataXmlValidator.class);
  ScaleDataReads reads = mock(ScaleDataReads.class);
  ScaleDataWorker worker = mock(ScaleDataWorker.class);
  SubmissionClaims claims;
  ScaleDataIntake intake;
  long nextId = 800;

  @BeforeEach
  void setUp() {
    ScaleDataApiProperties props = new ScaleDataApiProperties(true, "https://sso.example/realms/standard", "", "",
        "none", 1000, 100, Duration.ofDays(30), 60, 60, "X-Request-ID", false, 500, 50, 200, Duration.ofDays(400));
    claims = new SubmissionClaims(root, Duration.ofDays(30), java.time.Clock.systemUTC());
    intake = new ScaleDataIntake(submissions, virus, schema, claims, reads, worker, mock(UnpackRepository.class),
        mock(EditMessages.class), props);
    when(virus.check(any(), any())).thenReturn(Optional.empty());
    when(schema.validateDetailed(any(), anyInt())).thenReturn(List.of());
    when(submissions.nextTransmissionId()).thenAnswer(i -> ++nextId);
  }

  private void transmissionIs(long id, String status) {
    var t = new ScaleDataReads.Transmission(id, "00012345", "f.xml", null, null, "UNP", "CSA", false, 1);
    when(reads.transmission(id)).thenReturn(Optional.of(t));
    when(reads.transmission(eq(id), any())).thenReturn(Optional.of(t));
    when(reads.result(t)).thenReturn(result(id, status));
  }

  static TransmissionResult result(long id, String status) {
    return new TransmissionResult(id, "00012345", "f.xml", null, null, status, "UNP", "CSA", false, false, 1, 0, 1,
        new DocumentCounts(1, 0, 0, 0, 0, 0, 0), List.of(), false, "/s/" + id, "/s/" + id + "/documents");
  }

  private ScaleDataIntake.Upload upload(byte[] bytes, String key) {
    return new ScaleDataIntake.Upload(bytes, "SITE42.xml", null, key);
  }

  @Test
  void aNewFileIsReceivedUnpackedAndAnswered() {
    transmissionIs(801, ScaleDataStatus.PROCESSING);
    ScaleDataIntake.Outcome outcome = intake.submit(ACME, upload(FILE, null));

    assertThat(outcome.status()).isEqualTo(HttpStatus.ACCEPTED); // B1021 not ported: still PROCESSING
    ArgumentCaptor<SubmissionService.Incoming> in = ArgumentCaptor.forClass(SubmissionService.Incoming.class);
    verify(submissions).receive(in.capture());
    assertThat(in.getValue().recdClientNumber()).isEqualTo("00012345");
    assertThat(in.getValue().auditUser()).isEqualTo("API\\hbs-scale-acme");
    assertThat(in.getValue().channel()).isEqualTo(SubmissionStorage.Channel.API);
    assertThat(in.getValue().softwareVendor()).isFalse();
    verify(worker).processNow(801);
  }

  @Test
  void processedIs201() {
    transmissionIs(801, ScaleDataStatus.PROCESSED);
    assertThat(intake.submit(ACME, upload(FILE, null)).status()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void theSameFileAgainIsADuplicateAndNothingIsStored() {
    transmissionIs(801, ScaleDataStatus.PROCESSED);
    intake.submit(ACME, upload(FILE, null));

    ScaleDataIntake.Outcome again = intake.submit(ACME, upload(FILE, null));
    assertThat(again.status()).isEqualTo(HttpStatus.OK);
    assertThat(again.result().duplicate()).isTrue();
    assertThat(again.result().transmissionId()).isEqualTo(801);
    verify(submissions, org.mockito.Mockito.times(1)).receive(any());
  }

  @Test
  void aResendAfterARejectionIsProcessedAgain() {
    transmissionIs(801, ScaleDataStatus.REJECTED);
    assertThatThrownBy(() -> intake.submit(ACME, upload(FILE, null)))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> {
          assertThat(e.code()).isEqualTo("FILE_REJECTED");
          assertThat(e.extra()).containsEntry("transmissionId", 801L);
        });
    transmissionIs(802, ScaleDataStatus.PROCESSING);
    assertThat(intake.submit(ACME, upload(FILE, null)).result().transmissionId()).isEqualTo(802);
    assertThat(claims.fingerprint("00012345", SubmissionClaims.sha256(FILE))).get()
        .extracting(SubmissionClaims.Claim::transmissionId).isEqualTo(802L);
  }

  @Test
  void anIdempotencyKeyReusedForAnotherFileIs409() {
    transmissionIs(801, ScaleDataStatus.PROCESSED);
    intake.submit(ACME, upload(FILE, "6f1c2a40-key"));
    byte[] other = (new String(FILE, StandardCharsets.UTF_8) + " ").getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> intake.submit(ACME, upload(other, "6f1c2a40-key")))
        .isInstanceOfSatisfying(ScaleDataApiException.class,
            e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    // the same key and file: the original
    assertThat(intake.submit(ACME, upload(FILE, "6f1c2a40-key")).result().duplicate()).isTrue();
    // a new key is processed even though the contents match
    transmissionIs(802, ScaleDataStatus.PROCESSED);
    assertThat(intake.submit(ACME, upload(FILE, "7a2d3b51-key")).result().transmissionId()).isEqualTo(802);
  }

  @Test
  void schemaErrorsAndVirusesStoreNothing() {
    when(schema.validateDetailed(any(), anyInt()))
        .thenReturn(List.of(new ScaleDataXmlValidator.SchemaError(14, 9, "cvc-complex-type.2.4.a")));
    assertThatThrownBy(() -> intake.submit(ACME, upload(FILE, null)))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> {
          assertThat(e.code()).isEqualTo("SCHEMA_INVALID");
          assertThat(e.errors()).singleElement().satisfies(item -> {
            assertThat(item.level()).isEqualTo("FILE");
            assertThat(item.line()).isEqualTo(14);
          });
        });
    when(virus.check(any(), any())).thenReturn(Optional.of(
        new VirusScanner.Rejection(VirusScanner.CODE_SCAN_UNAVAILABLE, "down")));
    assertThatThrownBy(() -> intake.submit(ACME, upload(FILE, null)))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> {
          assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
          assertThat(e.retryAfterSeconds()).isEqualTo(60L);
        });
    verify(submissions, never()).receive(any());
    verify(submissions, never()).nextTransmissionId();
  }

  @Test
  void sizeClientAndKeyAreCheckedFirst() {
    assertThatThrownBy(() -> intake.submit(ACME, upload(new byte[1001], null)))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> assertThat(e.code()).isEqualTo("FILE_TOO_LARGE"));
    assertThatThrownBy(() -> intake.submit(ACME, upload(new byte[0], null)))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> assertThat(e.code()).isEqualTo("FILE_EMPTY"));
    assertThatThrownBy(() -> intake.submit(ACME, new ScaleDataIntake.Upload(FILE, null, "00067890", null)))
        .isInstanceOfSatisfying(ScaleDataApiException.class,
            e -> assertThat(e.code()).isEqualTo("CLIENT_NOT_PERMITTED"));
    assertThatThrownBy(() -> intake.submit(ACME, upload(FILE, "bad key with spaces")))
        .isInstanceOfSatisfying(ScaleDataApiException.class,
            e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_INVALID"));
    verify(virus, never()).check(any(), any());
  }

  @Test
  void aLargeFileIsQueued() {
    byte[] large = new byte[500];
    System.arraycopy(FILE, 0, large, 0, FILE.length);
    transmissionIs(801, ScaleDataStatus.QUEUED);
    assertThat(intake.submit(ACME, upload(large, null)).status()).isEqualTo(HttpStatus.ACCEPTED);
    verify(worker).enqueue(801);
    verify(worker, never()).processNow(anyLong());
  }

  @Test
  void aFailedReceiptReleasesTheClaim() {
    org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(submissions).receive(any());
    assertThatThrownBy(() -> intake.submit(ACME, upload(FILE, null))).isInstanceOf(IllegalStateException.class);
    assertThat(claims.fingerprint("00012345", SubmissionClaims.sha256(FILE))).isEmpty();
  }
}
