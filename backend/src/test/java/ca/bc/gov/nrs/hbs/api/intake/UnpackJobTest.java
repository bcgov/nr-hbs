package ca.bc.gov.nrs.hbs.api.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.hbs.api.intake.xml.ScaleXmlFixtures;
import ca.bc.gov.nrs.hbs.api.submission.SubmissionStorage;
import ca.bc.gov.nrs.hbs.api.workflow.Audit;
import ca.bc.gov.nrs.hbs.api.workflow.RecordingTableApi;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The job loop: which transmissions it takes, archiving after the unpack, failures reported. */
class UnpackJobTest {

  @TempDir
  Path root;

  @Test
  void unpacksWaitingUploadsAndArchivesTheirFiles() {
    SubmissionStorage storage = new SubmissionStorage(root.toString());
    StubUnpackRepository repo = new StubUnpackRepository();
    repo.receivedTransmission(501L);
    storage.store(SubmissionStorage.Area.INPUT, "00012345", 501L,
        new ByteArrayInputStream(ScaleXmlFixtures.bytes(ScaleXmlFixtures.signed("all-types-v3.xml"))));
    storage.storeSubmitter("00012345", 501L, false);
    RecordingTableApi api = new RecordingTableApi();
    UnpackService service = new UnpackService(api, repo, storage, new Audit("HBSBATCH"));

    String summary = new UnpackJob(null, repo, service, storage).runOnce();

    assertThat(summary).startsWith("1 unpacked, 0 rejected");
    assertThat(Files.exists(root.resolve("archive/00012345/501.xml"))).isTrue();
    assertThat(Files.exists(root.resolve("archive/00012345/501.submitter"))).isTrue();
    assertThat(Files.exists(root.resolve("input/00012345/501.xml"))).isFalse();
    assertThat(api.callsTo("HBS_STORE_DTL_SCL_DOC_TRANS")).last()
        .satisfies(c -> assertThat(c.arg("i_hbs_processing_status_code")).isEqualTo("CSA"));
  }

  @Test
  void aFailureLeavesTheFileAndIsReportedWhileOthersCarryOn() {
    SubmissionStorage storage = new SubmissionStorage(root.toString());
    StubUnpackRepository repo = new StubUnpackRepository();
    repo.receivedTransmission(601L);
    repo.receivedTransmission(602L);
    storage.store(SubmissionStorage.Area.INPUT, "00012345", 601L, new ByteArrayInputStream(new byte[] {'x'}));
    // 602's file is missing
    List<Long> unpacked = new ArrayList<>();
    UnpackService failing = new UnpackService(null, repo, storage, new Audit("HBSBATCH")) {
      @Override
      public TransmissionUnpacker.Outcome unpack(UnpackRepository.Pending pending, SubmissionStorage.Key key) {
        unpacked.add(pending.transmissionId());
        throw new IllegalStateException("database down");
      }
    };
    UnpackJob job = new UnpackJob(null, repo, failing, storage);

    assertThatThrownBy(job::runOnce).isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("601: database down").hasMessageContaining("602: file");
    assertThat(unpacked).containsExactly(601L);
    assertThat(Files.exists(root.resolve("input/00012345/601.xml"))).isTrue();
  }

  @Test
  void storageKeysAreParsedStrictly() {
    assertThat(SubmissionStorage.parseKey("input/00012345/501.xml"))
        .isEqualTo(new SubmissionStorage.Key(SubmissionStorage.Area.INPUT, "00012345", 501L));
    assertThat(SubmissionStorage.parseKey("\\\\server\\XMLinput\\00012345\\501.xml")).isNull();
    assertThat(SubmissionStorage.parseKey("input/../00012345/501.xml")).isNull();
    assertThat(SubmissionStorage.parseKey(null)).isNull();
  }
}
