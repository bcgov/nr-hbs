package ca.bc.gov.nrs.hbs.api.struct.v1.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.http.MediaType;

public enum HbsReportFormat {
  PDF("pdf", MediaType.APPLICATION_PDF),
  CSV("csv", new MediaType("text", "csv"));

  private final String extension;
  private final MediaType mediaType;

  HbsReportFormat(String extension, MediaType mediaType) {
    this.extension = extension;
    this.mediaType = mediaType;
  }

  public String getExtension() {
    return extension;
  }

  public MediaType getMediaType() {
    return mediaType;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static HbsReportFormat fromJsonValue(String value) {
    if (value == null || value.isBlank()) return PDF;
    for (HbsReportFormat format : values()) {
      if (format.name().equalsIgnoreCase(value) || format.extension.equalsIgnoreCase(value)) {
        return format;
      }
    }
    throw new IllegalArgumentException("Unknown report format: " + value);
  }

  public static HbsReportFormat fromNullable(HbsReportFormat format) {
    return format == null ? PDF : format;
  }
}
