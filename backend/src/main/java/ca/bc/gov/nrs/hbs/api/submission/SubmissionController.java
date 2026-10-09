package ca.bc.gov.nrs.hbs.api.submission;

import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** P505/P506 — upload a file of detail scale returns (XML). */
@RestController
@RequestMapping("/api/v1/hbs/submissions")
@Tag(name = "Scale data submission", description = "XML e-submission of detail scale returns (legacy P505)")
public class SubmissionController {

  private final SubmissionService submissionService;

  public SubmissionController(SubmissionService submissionService) {
    this.submissionService = submissionService;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize(HbsAuthorities.XML_SUBMIT)
  @Operation(summary = "Validate and receive a scale data XML file; returns the transmission id")
  public ResponseEntity<SubmissionService.SubmissionResult> submit(@RequestPart("file") MultipartFile file)
      throws IOException {
    var result = submissionService.submit(file);
    return result.errors().isEmpty()
        ? ResponseEntity.ok(result)
        : ResponseEntity.unprocessableEntity().body(result);
  }
}
