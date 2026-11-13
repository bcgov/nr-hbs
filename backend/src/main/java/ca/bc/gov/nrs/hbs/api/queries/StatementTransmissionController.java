package ca.bc.gov.nrs.hbs.api.queries;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import ca.bc.gov.nrs.hbs.api.util.RequestUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The statement-delivery transmission files that legacy batch B2065 wrote to the
 * client's statement drop directory ({@code <transmissionId>.xml} / {@code .pdf})
 * and that the statement e-mails link to
 * ({@code <webAppBaseUrl>/StatementDelivery/transmissions/<id>.pdf|.xml},
 * {@code EmailTranmissionDelivery}). Legacy served them from the share
 * ({@code presentation/dad/StatementServlet.handleTransmissionCommand}); this app
 * can't read the share, so it rebuilds them from Oracle
 * ({@link StatementTransmissionFiles}).
 *
 * <p>Access (StatementServlet): a logged-in user; a ministry (MOF_USER) user may
 * open any client's transmission, optionally naming the client with
 * {@code ?clientNumber=} as legacy required; anyone else only their own client's
 * transmissions. Anything else is 404, as legacy's "file not found in your
 * folder". No FOI severing: legacy served the client's own files as written.
 */
@RestController
@RequestMapping("/api/v1/hbs/statements/transmissions")
@Tag(name = "Queries", description = "Statement delivery transmissions (legacy StatementDelivery servlet)")
public class StatementTransmissionController {

  private static final Pattern FILE = Pattern.compile("\\d{1,32}");

  private final StatementTransmissionFiles files;

  public StatementTransmissionController(StatementTransmissionFiles files) {
    this.files = files;
  }

  @GetMapping("/{id}.{format}")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "A statement-delivery transmission (the e-mailed link) as XML or PDF")
  public ResponseEntity<byte[]> transmission(@PathVariable String id, @PathVariable String format,
      @RequestParam(name = "clientNumber", required = false) String clientNumber) {
    Capability.ANY_USER.require();
    boolean pdf = "pdf".equalsIgnoreCase(format);
    if ((!pdf && !"xml".equalsIgnoreCase(format)) || id == null || !FILE.matcher(id).matches()) {
      throw notFound();
    }
    StatementTransmissionFiles.Found found = files.find(Long.parseLong(id))
        .filter(f -> mayRead(RequestUtil.isMinistryUser(), RequestUtil.getCurrentClientNumber(), clientNumber,
            f.clientNumber()))
        .filter(f -> f.has(pdf))
        .orElseThrow(StatementTransmissionController::notFound);
    return files.download(found, pdf);
  }

  /**
   * StatementServlet.handleTransmissionCommand: ministry users read the folder of the
   * {@code clientNumber} they name (any client when they name none); everyone else
   * reads their own client's folder only.
   */
  static boolean mayRead(boolean ministry, String viewerClient, String requestedClient, String owner) {
    if (!StringUtils.hasText(owner)) {
      return false;
    }
    String transmissionClient = owner.trim();
    if (ministry) {
      return !StringUtils.hasText(requestedClient) || requestedClient.trim().equals(transmissionClient);
    }
    return StringUtils.hasText(viewerClient) && viewerClient.trim().equals(transmissionClient);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find the transmission file");
  }
}
