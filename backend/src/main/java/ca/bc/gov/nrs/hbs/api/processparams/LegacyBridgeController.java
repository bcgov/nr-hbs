package ca.bc.gov.nrs.hbs.api.processparams;

import ca.bc.gov.nrs.hbs.api.exception.errors.ApiError;
import ca.bc.gov.nrs.hbs.api.processparams.LegacyProcessParameterFiles.LegacyFile;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read endpoint of the legacy share bridge (docs/coexistence.md): a script
 * on the Windows batch host, scheduled by AutoMate, fetches the processing
 * parameters from the settings ConfigMap (P038) and writes them into the legacy
 * {@code PROCESS_PARAMETER_FILE_PATH} folder, where the legacy batch reads them.
 *
 * <pre>
 * GET /api/v1/hbs/integrations/legacy/process-parameters
 * 200 {"generatedAt":"2026-10-21T18:00:00Z",
 *      "files":[{"name":"HBSProcessingParameter.csv","content":"B2031,0,9999999999\nB2041,0,9999999999\n",
 *                "encoding":"windows-1252"}, …]}
 * 503 ApiError — the settings could not be read, or a value is missing / invalid;
 *               the bridge leaves the files alone
 * </pre>
 *
 * Only a service-account token with role {@code HBS_LEGACY_BRIDGE} may call it
 * (and that token may call nothing else — {@code LegacyBridgeAccess}).
 */
@RestController
@RequestMapping("/api/v1/hbs/integrations/legacy")
@Tag(name = "Legacy integration", description = "Reads for the legacy batch share bridge (service account)")
@Slf4j
public class LegacyBridgeController {

  private final AppSettings settings;

  public LegacyBridgeController(AppSettings settings) {
    this.settings = settings;
  }

  public record ProcessParameterFiles(String generatedAt, List<LegacyFile> files) {}

  @GetMapping(value = "/process-parameters", produces = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize(HbsAuthorities.LEGACY_BRIDGE)
  @Operation(summary = "Legacy ProcessParam file contents (process parameters, printer names, alert text)")
  public ResponseEntity<Object> processParameters() {
    Map<String, String> values;
    try {
      values = settings.all();
    } catch (UncheckedIOException ex) {
      log.error("Share bridge: the settings ConfigMap could not be read", ex);
      return unavailable("The processing parameters could not be read; leave the legacy files unchanged and"
          + " retry later.");
    }
    List<String> problems = LegacyProcessParameterFiles.problems(values);
    if (!problems.isEmpty()) {
      log.error("Share bridge: settings ConfigMap problems: {}", problems);
      return unavailable("Settings ConfigMap: " + String.join("; ", problems) + ". Leave the legacy files unchanged.");
    }
    return ResponseEntity.ok(new ProcessParameterFiles(Instant.now().toString(),
        LegacyProcessParameterFiles.build(values)));
  }

  private static ResponseEntity<Object> unavailable(String message) {
    ApiError error = new ApiError(HttpStatus.SERVICE_UNAVAILABLE);
    error.setMessage(message);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
  }
}
