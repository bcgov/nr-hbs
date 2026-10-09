package ca.bc.gov.nrs.hbs.api.service.v1.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every vendored legacy JCRS report (main + subreports) must compile with the
 * embedded JasperReports engine — catches JasperServer-only constructs that
 * slipped through vendoring, without needing a database.
 */
class ReportTemplatesCompileTest {

  @Test
  void allVendoredReportsCompile() throws Exception {
    List<HbsReportDefinition> defs;
    try (InputStream in = new ClassPathResource("reports/hbs/catalog.json").getInputStream()) {
      defs = new ObjectMapper().readValue(in, new TypeReference<>() {});
    }
    assertThat(defs).hasSizeGreaterThanOrEqualTo(79);
    List<String> failures = new ArrayList<>();
    for (HbsReportDefinition def : defs) {
      List<String> files = new ArrayList<>(def.subreports());
      files.add(def.id());
      for (String f : files) {
        try (InputStream in = new ClassPathResource("reports/hbs/" + def.id() + "/" + f + ".jrxml").getInputStream()) {
          JasperCompileManager.compileReport(in);
        } catch (Exception ex) {
          failures.add(def.id() + "/" + f + ": " + ex.getMessage().lines().findFirst().orElse(""));
        }
      }
    }
    assertThat(failures).as("reports that failed to compile").isEmpty();
  }
}
