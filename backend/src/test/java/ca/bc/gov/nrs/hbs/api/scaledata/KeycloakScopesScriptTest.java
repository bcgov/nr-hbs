package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The deploy creates the scale data API's scopes in Keycloak with
 * .github/scripts/ensure-keycloak-scopes.sh; its list must match the scopes the
 * API enforces, or a client could not be given a scope an endpoint needs.
 */
class KeycloakScopesScriptTest {

  private static final Path SCRIPT = Path.of("..", ".github", "scripts", "ensure-keycloak-scopes.sh");
  private static final Pattern ENTRY = Pattern.compile("^\\s*\"([^\"|]+)\\|[^\"]*\"\\s*$", Pattern.MULTILINE);

  @Test
  void scriptCreatesExactlyTheScopesTheApiEnforces() throws IOException {
    String script = Files.readString(SCRIPT);
    String array = script.substring(script.indexOf("SCOPES=("), script.indexOf(")", script.indexOf("SCOPES=(")));
    Matcher m = ENTRY.matcher(array);
    List<String> scopes = new ArrayList<>();
    while (m.find()) {
      scopes.add(m.group(1));
    }
    assertThat(scopes).containsExactlyInAnyOrderElementsOf(ScaleDataApi.SCOPES);
  }
}
