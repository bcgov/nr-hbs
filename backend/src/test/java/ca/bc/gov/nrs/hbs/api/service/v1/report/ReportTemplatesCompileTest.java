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

  /**
   * A numeric variable initialised with a bare integer literal (e.g. {@code 0} on
   * a BigDecimal sum) keeps an Integer when its group sums no rows, and the Java
   * expression evaluator then fails the fill with a ClassCastException
   * (JasperServer's Groovy evaluation tolerated it). Initial values must have
   * the variable's own type, e.g. {@code java.math.BigDecimal.ZERO}.
   */
  @Test
  void numericVariablesStartWithTheirOwnType() throws Exception {
    List<HbsReportDefinition> defs;
    try (InputStream in = new ClassPathResource("reports/hbs/catalog.json").getInputStream()) {
      defs = new ObjectMapper().readValue(in, new TypeReference<>() {});
    }
    List<String> failures = new ArrayList<>();
    for (HbsReportDefinition def : defs) {
      List<String> files = new ArrayList<>(def.subreports());
      files.add(def.id());
      for (String f : files) {
        try (InputStream in = new ClassPathResource("reports/hbs/" + def.id() + "/" + f + ".jrxml").getInputStream()) {
          var report = net.sf.jasperreports.engine.xml.JRXmlLoader.load(in);
          for (var v : report.getVariables()) {
            var init = v.getInitialValueExpression();
            if (init != null && init.getText().trim().matches("-?\\d+")
                && !Integer.class.getName().equals(v.getValueClassName())) {
              failures.add(def.id() + "/" + f + ": " + v.getName() + " (" + v.getValueClassName()
                  + ") starts at integer literal " + init.getText().trim());
            }
          }
        }
      }
    }
    assertThat(failures).as("numeric variables with an Integer initial value").isEmpty();
  }

  @Test
  void sharedSubreportsExistAndCompile() throws Exception {
    // HBS_HEADER is a JCRS repository subreport used by 78 reports; HbsReportService
    // stages it beside every report (missing, fills failed with "Resource not found").
    try (InputStream in = new ClassPathResource("reports/hbs/_shared/HBS_HEADER.jrxml").getInputStream()) {
      assertThat(JasperCompileManager.compileReport(in).getName()).isEqualTo("HBS_HEADER");
    }
  }
}
