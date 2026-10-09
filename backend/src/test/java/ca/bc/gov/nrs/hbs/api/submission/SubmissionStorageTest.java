package ca.bc.gov.nrs.hbs.api.submission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionStorageTest {

  @TempDir
  Path root;

  @Test
  void storesUnderClientFolder() throws Exception {
    var storage = new SubmissionStorage(root.toString());
    String key = storage.store(SubmissionStorage.Area.INPUT, "00012345", 42L,
        new ByteArrayInputStream("<x/>".getBytes()));
    assertThat(key).isEqualTo("input/00012345/42.xml");
    assertThat(Files.readString(root.resolve(key))).isEqualTo("<x/>");
  }

  @Test
  void ministryUploadsUseNeutralFolder() {
    var storage = new SubmissionStorage(root.toString());
    assertThat(storage.resolve(SubmissionStorage.Area.INPUT, null, 7L).toString())
        .endsWith("input/00000000/7.xml");
  }

  @Test
  void rejectsTraversalAndBadKeys() {
    var storage = new SubmissionStorage(root.toString());
    assertThatThrownBy(() -> storage.resolve(SubmissionStorage.Area.INPUT, "../../etc", 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.resolve(SubmissionStorage.Area.INPUT, "00012345", -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
