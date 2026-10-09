package ca.bc.gov.nrs.hbs.api.service.v1.report;

import org.springframework.http.MediaType;

public record HbsReportResult(byte[] content, String filename, MediaType mediaType) {}
