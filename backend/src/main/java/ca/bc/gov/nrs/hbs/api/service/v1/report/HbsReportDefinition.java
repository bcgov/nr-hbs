package ca.bc.gov.nrs.hbs.api.service.v1.report;

import java.util.List;

/**
 * One vendored JCRS report unit, as listed in
 * {@code classpath:reports/hbs/catalog.json} (generated from the legacy
 * {@code jasper-server} export — see docs/reports.md).
 *
 * @param id          report id = legacy unit name (e.g. {@code HBS2R051})
 * @param title       legacy unit label
 * @param procedure   stored procedure the main JRXML calls
 * @param parameters  prompt names, in legacy order
 * @param subreports  subreport JRXML base names in the same folder
 */
public record HbsReportDefinition(
    String id,
    String title,
    String procedure,
    List<String> parameters,
    List<String> subreports) {

  public String filename(String extension) {
    return id + "." + extension;
  }
}
