package ca.bc.gov.nrs.hbs.api.service.v1.report;

import ca.bc.gov.nrs.hbs.api.exception.ReportGenerationException;
import ca.bc.gov.nrs.hbs.api.exception.ReportNotFoundException;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportFormat;
import ca.bc.gov.nrs.hbs.api.struct.v1.report.HbsReportRequestDto;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * In-process JasperReports engine for the 79 legacy HBS report units —
 * the replacement for the shared NRS JasperReports Server (JCRS) and, before
 * it, Crystal Enterprise. Same strategy as nr-fsp-new's FspReportService
 * (compile once, fill against the live Oracle datasource, export PDF/CSV),
 * extended for HBS's subreports:
 *
 * <ul>
 *   <li>The JCRS JRXMLs resolve subreports and logos through
 *       {@code $P{SUBREPORT_DIR} + name + $P{SUBREPORT_EXT}}. On first use of a
 *       report we compile the main + every subreport into a per-report
 *       directory under {@code hbs.reports.work-dir} (an emptyDir in
 *       OpenShift), copy the logos alongside, and pass that directory as
 *       {@code SUBREPORT_DIR} with {@code SUBREPORT_EXT=.jasper}.</li>
 *   <li>Prompts that encode the caller's identity are overwritten from the
 *       JWT, never trusted from the request: {@code *_USERTYPE}
 *       (MOF/CLI/SPC), {@code *_USERCLILOC} (client number for industry
 *       users), {@code *_JOBNO}, {@code *_SELECTCOUNT}.</li>
 * </ul>
 */
@Service
public class HbsReportService {

  private static final Logger LOG = LoggerFactory.getLogger(HbsReportService.class);
  private static final String ROOT = "reports/hbs/";
  private static final List<String> IMAGES =
      List.of("hbs_logo", "hbs_logo1.jpg", "hbs_logo2.jpg", "hbs_logo3.jpg");
  /** Legacy reports capped the rows a prompt-driven run could return. */
  private static final String DEFAULT_SELECT_COUNT = "5000";
  /**
   * Several legacy report procs build dynamic SQL by concatenating prompt
   * values (only some use HBS_GET_QUOTE_SAFE). Prompt values are therefore
   * restricted to the characters the legacy screens could produce — no
   * quotes, semicolons or comment markers.
   */
  private static final java.util.regex.Pattern SAFE_PROMPT =
      java.util.regex.Pattern.compile("[A-Za-z0-9 _.,/:%*()&#+-]{0,200}");
  /** Reports whose procs parse dates as 'YYYY-Mon-DD' instead of ISO. */
  private static final java.util.Set<String> MON_DATE_REPORTS =
      java.util.Set.of("HBS3R972", "HBS3R973", "HBS3R974", "HBS3R979");
  private static final java.time.format.DateTimeFormatter MON_DATE =
      java.time.format.DateTimeFormatter.ofPattern("yyyy-MMM-dd", java.util.Locale.CANADA);

  private final DataSource dataSource;
  private final ObjectMapper objectMapper;
  private final Path workDir;
  private final Map<String, HbsReportDefinition> catalog = new LinkedHashMap<>();
  private final ConcurrentHashMap<String, JasperReport> compiledCache = new ConcurrentHashMap<>();

  public HbsReportService(DataSource dataSource, ObjectMapper objectMapper,
      @Value("${hbs.reports.work-dir:${java.io.tmpdir}/hbs-reports}") String workDir) {
    this.dataSource = dataSource;
    this.objectMapper = objectMapper;
    this.workDir = Path.of(workDir);
  }

  @PostConstruct
  void loadCatalog() throws IOException {
    try (InputStream in = new ClassPathResource(ROOT + "catalog.json").getInputStream()) {
      List<HbsReportDefinition> defs = objectMapper.readValue(in, new TypeReference<>() {});
      defs.forEach(d -> catalog.put(d.id().toUpperCase(), d));
    }
    LOG.info("Loaded {} HBS report definitions", catalog.size());
  }

  /** All report definitions, in catalog order. */
  public List<HbsReportDefinition> listReports() {
    return List.copyOf(catalog.values());
  }

  public HbsReportDefinition definition(String reportId) {
    return Optional.ofNullable(reportId)
        .map(id -> catalog.get(id.toUpperCase()))
        .orElseThrow(() -> new ReportNotFoundException(reportId));
  }

  public HbsReportResult generateReport(String reportId, HbsReportRequestDto request) {
    HbsReportDefinition def = definition(reportId);
    HbsReportFormat format = HbsReportFormat.fromNullable(request.format());
    JasperReport jasperReport = compiledCache.computeIfAbsent(def.id(), id -> compile(def));
    Map<String, Object> params = buildParameters(def, request.parameters());

    try (Connection connection = dataSource.getConnection()) {
      JasperPrint print = JasperFillManager.fillReport(jasperReport, params, connection);
      byte[] body = switch (format) {
        case PDF -> JasperExportManager.exportReportToPdf(print);
        case CSV -> exportToCsv(print);
      };
      if (body == null || body.length == 0) {
        throw new ReportGenerationException("Empty " + format.name() + " produced for report " + reportId);
      }
      return new HbsReportResult(body, def.filename(format.getExtension()), format.getMediaType());
    } catch (JRException ex) {
      LOG.error("Jasper fill/export failed for [{}]", reportId, ex);
      throw new ReportGenerationException("Failed to generate report " + reportId, ex);
    } catch (SQLException ex) {
      LOG.error("Database connection failed for report [{}]", reportId, ex);
      throw new ReportGenerationException("Database unavailable for report " + reportId, ex);
    }
  }

  Map<String, Object> buildParameters(HbsReportDefinition def, Map<String, String> supplied) {
    Map<String, Object> params = new HashMap<>();
    Map<String, String> in = supplied == null ? Map.of() : supplied;
    for (String name : def.parameters()) {
      String upper = name.toUpperCase();
      if (upper.endsWith("_USERTYPE") || upper.endsWith("_USERTYPECD")) {
        params.put(name, RequestUtil.getLegacyUserType());
      } else if (upper.endsWith("_USERCLILOC")) {
        params.put(name, RequestUtil.isMinistryUser() ? "" : RequestUtil.getCurrentClientNumber());
      } else if (upper.endsWith("_USERID") || upper.endsWith("_USER_ID") || upper.equals("USERID")) {
        // Several procs (anomalies, late submissions, final bills) only apply
        // their client / data-domain restriction when a user id is passed.
        params.put(name, RequestUtil.getCurrentAuditUserId().toUpperCase());
      } else if (upper.endsWith("_JOBNO")) {
        params.put(name, Long.toString(System.currentTimeMillis()));
      } else if (upper.endsWith("_SELECTCOUNT")) {
        params.put(name, in.getOrDefault(name, DEFAULT_SELECT_COUNT));
      } else {
        params.put(name, safePrompt(def, name, in.getOrDefault(name, "")));
      }
    }
    Map<String, String> prompts = new HashMap<>();
    params.forEach((k, v) -> prompts.put(k, v instanceof String str ? str : null));
    ReportPromptRules.check(def.id(), prompts);
    ReportPromptRules.quoteLists(def.id(), params);
    Path dir = workDir.resolve(def.id());
    params.put("SUBREPORT_DIR", dir.toAbsolutePath() + "/");
    params.put("SUBREPORT_EXT", ".jasper");
    return params;
  }

  static String safePrompt(HbsReportDefinition def, String name, String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.contains("--") || !SAFE_PROMPT.matcher(value).matches()) {
      throw new IllegalArgumentException("Report prompt '" + name + "' contains characters that aren't allowed");
    }
    if (MON_DATE_REPORTS.contains(def.id().toUpperCase()) && value.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return java.time.LocalDate.parse(value).format(MON_DATE);
    }
    return value;
  }

  private JasperReport compile(HbsReportDefinition def) {
    String folder = ROOT + def.id() + "/";
    ClassPathResource main = new ClassPathResource(folder + def.id() + ".jrxml");
    if (!main.exists()) {
      throw new ReportNotFoundException(def.id());
    }
    try {
      Path dir = Files.createDirectories(workDir.resolve(def.id()));
      for (String sub : def.subreports()) {
        try (InputStream in = new ClassPathResource(folder + sub + ".jrxml").getInputStream()) {
          JRSaver.saveObject(JasperCompileManager.compileReport(in), dir.resolve(sub + ".jasper").toFile());
        }
      }
      for (String image : IMAGES) {
        try (InputStream in = new ClassPathResource(ROOT + "_images/" + image).getInputStream()) {
          Files.copy(in, dir.resolve(image), StandardCopyOption.REPLACE_EXISTING);
        }
      }
      try (InputStream in = main.getInputStream()) {
        JasperReport compiled = JasperCompileManager.compileReport(in);
        LOG.info("Compiled report [{}] with {} subreport(s)", def.id(), def.subreports().size());
        return compiled;
      }
    } catch (IOException ex) {
      throw new ReportGenerationException("Failed to stage report " + def.id(), ex);
    } catch (JRException ex) {
      LOG.error("Failed to compile JRXML for [{}]", def.id(), ex);
      throw new ReportGenerationException("Failed to compile report " + def.id(), ex);
    }
  }

  private byte[] exportToCsv(JasperPrint print) throws JRException {
    StringWriter writer = new StringWriter();
    JRCsvExporter exporter = new JRCsvExporter();
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleWriterExporterOutput(writer));
    exporter.exportReport();
    return writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }
}
