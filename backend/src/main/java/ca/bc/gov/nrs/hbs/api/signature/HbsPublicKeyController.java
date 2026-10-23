package ca.bc.gov.nrs.hbs.api.signature;

import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy {@code /DownloadPublicKeyServlet} (linked from P002 "Scale Return
 * Menu" › HBS Public Key). Legacy rule: any signed-in, non-public user — here
 * any HBS role ({@code ANY_USER}). Same response: {@code HBS_Public_Key.xml}
 * as an {@code application/octet-stream} attachment.
 */
@RestController
@RequestMapping("/api/v1/hbs/signature")
@Tag(name = "HBS public key", description = "Download of the HBS RSA public keys (legacy DownloadPublicKeyServlet)")
public class HbsPublicKeyController {

  private final HbsPublicKeyService service;

  public HbsPublicKeyController(HbsPublicKeyService service) {
    this.service = service;
  }

  @GetMapping("/public-key")
  @PreAuthorize(HbsAuthorities.ANY_USER)
  @Operation(summary = "HBS_Public_Key.xml — the RSA public keys scale software encrypts signatures with")
  public ResponseEntity<byte[]> download() {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + HbsPublicKeyService.FILE_NAME + "\"")
        .body(service.publicKeyXml());
  }
}
