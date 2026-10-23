package ca.bc.gov.nrs.hbs.api.queries;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import static ca.bc.gov.nrs.hbs.api.queries.StatementPdfRendererTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * (Public only so the in-memory database can call {@link #statementType}.)
 * The e-mailed transmission links (legacy StatementServlet /transmissions/), rebuilt
 * from the delivery tables on an in-memory database: access rule, availability and
 * the rebuilt files.
 */
public class StatementTransmissionControllerTest {

  private static StatementTransmissionController controller;

  @BeforeAll
  static void database() {
    System.setProperty("java.awt.headless", "true");
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:transmissions;MODE=Oracle;DB_CLOSE_DELAY=-1");
    var jdbc = new NamedParameterJdbcTemplate(ds);
    var sql = jdbc.getJdbcTemplate();
    sql.execute("""
        CREATE TABLE document_delivery_transmission (transmission_id NUMBER(32) PRIMARY KEY,
          client_number VARCHAR2(8), client_locn_code VARCHAR2(2), recipient_type VARCHAR2(7),
          hbs_report_status_code VARCHAR2(3), file_name VARCHAR2(100), pdf_file_name VARCHAR2(100),
          creation_date TIMESTAMP)""");
    sql.execute("""
        CREATE TABLE document_delivery_batch_item (batch_id NUMBER(32), transmission_id NUMBER(32),
          hbs_file_id NUMBER(32))""");
    sql.execute("CREATE TABLE hbs_file (hbs_file_id NUMBER(32) PRIMARY KEY, hbs_ts_version_id NUMBER(32), file_object CLOB)");
    sql.execute("CREATE TABLE hbs_ts_version (hbs_ts_version_id NUMBER(32), schema_version VARCHAR2(80), trans_version VARCHAR2(80))");
    sql.execute("CREATE TABLE stmt_type_fixture (hbs_file_id NUMBER(32), stmt_type VARCHAR2(3))");
    sql.execute("CREATE ALIAS hbs_determine_statement_type FOR \""
        + StatementTransmissionControllerTest.class.getName() + ".statementType\"");
    sql.update("INSERT INTO hbs_ts_version VALUES (7, 'V1_0g', 'V1_0g')");
    sql.update("INSERT INTO hbs_ts_version VALUES (8, 'V1_0h', 'V1_0h')");

    // 101: complete, SEND_TO 00012345 — a PSI, an RS and a second PSI.
    sql.update("INSERT INTO document_delivery_transmission VALUES (101, '00012345', '00', 'SEND_TO', 'SUB',"
        + " '\\\\share\\00012345\\101.xml', '\\\\share\\00012345\\101.pdf', TIMESTAMP '2026-10-01 16:20:00')");
    file(sql, 1001, "PSI", fixture("PSI"));
    file(sql, 1002, "RS", fixture("RS"));
    file(sql, 1003, "PSI", fixture("PSI").replaceFirst("<statement-number>\\d{7}", "<statement-number>1009999"));
    sql.update("INSERT INTO document_delivery_batch_item VALUES (3, 101, 1003)");
    sql.update("INSERT INTO document_delivery_batch_item VALUES (1, 101, 1001)");
    sql.update("INSERT INTO document_delivery_batch_item VALUES (2, 101, 1002)");
    // 102: still processing (no files yet), same client.
    sql.update("INSERT INTO document_delivery_transmission VALUES (102, '00012345', '00', 'SEND_TO', 'PRC',"
        + " NULL, NULL, TIMESTAMP '2026-10-02 09:00:00')");
    sql.update("INSERT INTO document_delivery_batch_item VALUES (4, 102, 1001)");
    // 103: another client's copy.
    sql.update("INSERT INTO document_delivery_transmission VALUES (103, '00067890', '01', 'COPY_TO', 'SUB',"
        + " 'x.xml', 'x.pdf', TIMESTAMP '2026-10-01 16:20:00')");
    sql.update("INSERT INTO document_delivery_batch_item VALUES (5, 103, 1002)");

    controller = new StatementTransmissionController(jdbc, new StatementPdfRenderer(), new StatementXslVersion(jdbc));
  }

  private static void file(org.springframework.jdbc.core.JdbcTemplate sql, long id, String type, String xml) {
    sql.update("INSERT INTO hbs_file VALUES (?, 8, ?)", id, xml);
    sql.update("INSERT INTO stmt_type_fixture VALUES (?, ?)", id, type);
  }

  /** Stand-in for THE.HBS_DETERMINE_STATEMENT_TYPE. */
  public static String statementType(Connection conn, Long fileId) throws SQLException {
    try (PreparedStatement ps = conn.prepareStatement("SELECT stmt_type FROM stmt_type_fixture WHERE hbs_file_id = ?")) {
      ps.setLong(1, fileId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  private static void signIn(String... roles) {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
        .claims(c -> c.putAll(Map.of("identity_provider", roles[0].contains("FOREST_CLIENT") ? "bceidbusiness" : "azureidir",
            "client_roles", List.of(roles))))
        .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private static void assertNotFound(Runnable call) {
    assertThatThrownBy(call::run).isInstanceOfSatisfying(ResponseStatusException.class,
        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void ownClientGetsTheRebuiltXml() {
    signIn("HBS_CLI_USER_FOREST_CLIENT-00012345");
    var res = controller.transmission("101", "xml", null);
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getHeaders().getContentDisposition().getFilename()).isEqualTo("101.xml");
    String xml = new String(res.getBody(), StandardCharsets.UTF_8);
    assertThat(xml).contains("transmission-id=\"101\"", "client-number=\"00012345\"", "client-location-code=\"00\"",
        "recipient-type=\"SEND_TO\"", "date-created=\"2026-10-01\"", "time-created=\"04:20:00\"", "batch-count=\"2\"",
        "batch-type-code=\"RS\" document-count=\"1\"", "batch-type-code=\"PSI\" document-count=\"2\"");
    // Items in DOCUMENT_DELIVERY_BATCH_ITEM order (batch_id), RS batch before PSI batch.
    assertThat(xml.indexOf("<ratio-statements")).isLessThan(xml.indexOf("<piece-scale-invoices"));
    assertThat(xml.indexOf("1000001")).isLessThan(xml.indexOf("1009999"));
  }

  @Test
  void pdfIsRenderedWithTheCurrentStylesheets() {
    signIn("HBS_CLI_USER_FOREST_CLIENT-00012345");
    var res = controller.transmission("101", "PDF", null);
    assertThat(res.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    byte[] pdf = res.getBody();
    assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    assertThat(PdfText.of(pdf)).contains("CoverSheet", "101", "1009999", "RatioStatement");
  }

  @Test
  void otherClientsTransmissionIsNotFound() {
    signIn("HBS_CLI_USER_FOREST_CLIENT-00012345");
    assertNotFound(() -> controller.transmission("103", "pdf", null));
    // ?clientNumber= is a ministry-only parameter.
    assertNotFound(() -> controller.transmission("103", "xml", "00067890"));
  }

  @Test
  void ministryUserReadsAnyClientOrTheNamedOne() {
    signIn("HBS_MOF_USER");
    assertThat(controller.transmission("103", "xml", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(controller.transmission("103", "xml", "00067890").getStatusCode()).isEqualTo(HttpStatus.OK);
    assertNotFound(() -> controller.transmission("103", "xml", "00012345"));
  }

  @Test
  void transmissionWithoutFilesYetOrUnknownIsNotFound() {
    signIn("HBS_MOF_USER");
    assertNotFound(() -> controller.transmission("102", "xml", null));
    assertNotFound(() -> controller.transmission("102", "pdf", null));
    assertNotFound(() -> controller.transmission("999", "xml", null));
    assertNotFound(() -> controller.transmission("101", "zip", null));
    assertNotFound(() -> controller.transmission("10a1", "xml", null));
  }

  @ParameterizedTest
  @CsvSource(nullValues = "null", value = {
      // ministry, viewer client, ?clientNumber, transmission client, may read
      "true,  null,     null,     00012345, true",
      "true,  null,     00012345, 00012345, true",
      "true,  null,     00067890, 00012345, false",
      "false, 00012345, null,     00012345, true",
      "false, 00012345, 00012345, 00067890, false",
      "false, 00067890, null,     00012345, false",
      "false, '',       null,     00012345, false",
      "true,  null,     null,     null,     false"})
  void legacyAccessRule(boolean ministry, String viewer, String requested, String owner, boolean expected) {
    assertThat(StatementTransmissionController.mayRead(ministry, viewer, requested, owner)).isEqualTo(expected);
  }
}
