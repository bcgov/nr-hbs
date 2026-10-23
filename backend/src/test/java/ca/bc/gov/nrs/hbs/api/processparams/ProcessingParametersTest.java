package ca.bc.gov.nrs.hbs.api.processparams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.hbs.api.processparams.LegacyProcessParameterFiles.LegacyFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

/** The P038 settings read from the mounted ConfigMap, and the legacy ProcessParam files served to the share bridge. */
class ProcessingParametersTest {

  @TempDir
  Path dir;

  private static Map<String, String> complete() {
    Map<String, String> s = new HashMap<>();
    s.put("B2031.START_LOC", "15");
    s.put("B2031.TIME_LIMIT", "240");
    s.put("B2041.START_LOC", "0");
    s.put("B2041.TIME_LIMIT", "9999999999");
    return s;
  }

  // ---- ConfigMap mount -------------------------------------------------------------------------

  @Test
  void settings_readTrimmedFromOneFilePerKey_missingAndBlankLeftOut() throws IOException {
    Files.writeString(dir.resolve("B2031.START_LOC"), "15\n");
    Files.writeString(dir.resolve("ALERT_TEXT"), "HBS will be down Saturday.\n");
    Files.writeString(dir.resolve("PRINTER.SEND_TO"), "  ");
    AppSettings settings = new AppSettings(dir.toString());
    assertThat(settings.all()).containsExactly(
        Map.entry("B2031.START_LOC", "15"), Map.entry("ALERT_TEXT", "HBS will be down Saturday."));
    assertThat(settings.value("PRINTER.COPY_TO")).isNull();
    assertThatThrownBy(() -> settings.value("../secret")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void settings_noMountMeansNoValues() {
    AppSettings settings = new AppSettings(dir.resolve("absent").toString());
    assertThat(settings.all()).isEmpty();
  }

  @Test
  void queryBinds_everyKeyBound_missingAsEmpty() throws IOException {
    Files.writeString(dir.resolve("ALERT_TEXT"), "Down Saturday");
    Map<String, Object> binds = new AppSettings(dir.toString()).values();
    assertThat(binds).hasSize(AppSettings.KEYS.size())
        .containsEntry("hbsSetting_ALERT_TEXT", "Down Saturday")
        .containsEntry("hbsSetting_B2031_START_LOC", "");
  }

  // ---- share bridge files ----------------------------------------------------------------------

  @Test
  void legacyFiles_exactLegacyFormat() {
    Map<String, String> s = complete();
    s.put("B2031.START_LOC", "015");
    s.put("PRINTER.SEND_TO", "\\\\VICPRT01\\HBS1");
    s.put("PRINTER.COPY_TO", "\\\\VICPRT01\\HBS2");
    s.put("ALERT_TEXT", "HBS will be down Saturday.");
    List<LegacyFile> files = LegacyProcessParameterFiles.build(s);
    assertThat(files).containsExactly(
        new LegacyFile("HBSProcessingParameter.csv", "B2031,15,240\nB2041,0,9999999999\n", "windows-1252"),
        new LegacyFile("Printer_names.txt", "COPY_TO_PRINTER=\\\\VICPRT01\\HBS2\nSEND_TO_PRINTER=\\\\VICPRT01\\HBS1",
            "windows-1252"),
        new LegacyFile("HBS_ALERT_FILE.txt", "HBS will be down Saturday.", "windows-1252"));
  }

  @Test
  void legacyFiles_noPrintersNoAlert() {
    List<LegacyFile> files = LegacyProcessParameterFiles.build(complete());
    assertThat(files).extracting(LegacyFile::name).containsExactly("HBSProcessingParameter.csv", "HBS_ALERT_FILE.txt");
    assertThat(files.get(1).content()).isEmpty();
  }

  @Test
  void legacyFiles_refusedWhenAValueIsMissingOrInvalid() {
    Map<String, String> s = complete();
    s.remove("B2041.TIME_LIMIT");
    s.put("B2031.START_LOC", "x");
    s.put("PRINTER.COPY_TO", "printer1");
    assertThat(LegacyProcessParameterFiles.problems(s)).containsExactly(
        "B2031.START_LOC must be a whole number", "B2041.TIME_LIMIT is not set",
        "PRINTER.COPY_TO must be \\\\server\\printer");
    assertThatThrownBy(() -> LegacyProcessParameterFiles.build(s)).isInstanceOf(IllegalStateException.class);
    // An unset ConfigMap never writes defaults over what the batch uses now.
    assertThat(LegacyProcessParameterFiles.problems(Map.of())).hasSize(4);
  }

  @Test
  void bridgeController_okAnd503() {
    AppSettings ok = mock(AppSettings.class);
    when(ok.all()).thenReturn(complete());
    var response = new LegacyBridgeController(ok).processParameters();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    var payload = (LegacyBridgeController.ProcessParameterFiles) response.getBody();
    assertThat(payload.generatedAt()).endsWith("Z");
    assertThat(payload.files()).hasSize(2);

    AppSettings empty = mock(AppSettings.class);
    when(empty.all()).thenReturn(Map.of());
    assertThat(new LegacyBridgeController(empty).processParameters().getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    AppSettings down = mock(AppSettings.class);
    when(down.all()).thenThrow(new UncheckedIOException(new IOException("mount")));
    assertThat(new LegacyBridgeController(down).processParameters().getStatusCode())
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }
}
