package ca.bc.gov.nrs.hbs.api.scaledata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiAccountRegistryTest {

  @TempDir
  Path dir;

  static ScaleDataApiProperties properties(String accountsFile) {
    return new ScaleDataApiProperties(true, "https://sso.example/realms/standard", "", "", accountsFile, 1000, 500,
        Duration.ofDays(30), 60, 60, "X-Request-ID", false, 500, 50, 200, Duration.ofDays(400));
  }

  @Test
  void parsesAccountsAndSkipsBadLines() {
    Map<String, ApiAccount> accounts = ApiAccountRegistry.parse("""
        # comment
        hbs-scale-acme = 00012345          # one client
        hbs-scale-vendor = 00012345, 00067890

        no-equals-sign
        bad id! = 00012345
        hbs-short = 1234
        hbs-empty =
        """);
    assertThat(accounts).containsOnlyKeys("hbs-scale-acme", "hbs-scale-vendor");
    assertThat(accounts.get("hbs-scale-vendor").clientNumbers()).containsExactly("00012345", "00067890");
  }

  @Test
  void reloadsWhenTheFileChanges() throws Exception {
    Path file = dir.resolve("SCALE_DATA_API_ACCOUNTS");
    ApiAccountRegistry registry = new ApiAccountRegistry(properties(file.toString()));
    assertThat(registry.find("hbs-scale-acme")).isEmpty(); // no file = no accounts

    Files.writeString(file, "hbs-scale-acme = 00012345\n");
    assertThat(registry.find("hbs-scale-acme")).isPresent();

    Files.writeString(file, "hbs-scale-other = 00067890\n");
    Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));
    assertThat(registry.find("hbs-scale-acme")).isEmpty();
    assertThat(registry.find("hbs-scale-other")).isPresent();
    assertThat(registry.find(null)).isEmpty();
  }

  @Test
  void accountResolvesTheClient() {
    ApiAccount single = new ApiAccount("hbs-scale-acme", List.of("00012345"));
    assertThat(single.resolveClient(null)).isEqualTo("00012345");
    assertThat(single.resolveClient(" 00012345 ")).isEqualTo("00012345");
    assertThatThrownBy(() -> single.resolveClient("00099999"))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> assertThat(e.code()).isEqualTo("CLIENT_NOT_PERMITTED"));
    assertThatThrownBy(() -> single.resolveClient("12345"))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> assertThat(e.code()).isEqualTo("CLIENT_NUMBER_INVALID"));

    ApiAccount vendor = new ApiAccount("hbs-scale-vendor", List.of("00012345", "00067890"));
    assertThatThrownBy(() -> vendor.resolveClient(""))
        .isInstanceOfSatisfying(ScaleDataApiException.class, e -> assertThat(e.code()).isEqualTo("CLIENT_NUMBER_REQUIRED"));
    assertThat(vendor.clientsFor(null)).containsExactly("00012345", "00067890");
    assertThat(vendor.clientsFor("00067890")).containsExactly("00067890");
  }

  @Test
  void auditUserFitsTheLegacyColumn() {
    assertThat(new ApiAccount("hbs-scale-acme", List.of("00012345")).auditUser()).isEqualTo("API\\hbs-scale-acme");
    assertThat(new ApiAccount("a-very-long-css-client-id-for-a-vendor-1234", List.of("00012345")).auditUser())
        .hasSize(30).startsWith("API\\");
  }
}
