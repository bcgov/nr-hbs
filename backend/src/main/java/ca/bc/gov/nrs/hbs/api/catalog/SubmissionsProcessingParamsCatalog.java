package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P038 "HBS Alert and Processing Parameters" (Production Control), read-only.
 *
 * <p>The legacy app kept these in three flat files on the app server's share
 * ({@code HBSProcessingParamHelper}). They now live in the backend's settings
 * ConfigMap, one key each ({@code processparams.AppSettings}):
 * {@code B2031.START_LOC}, {@code B2031.TIME_LIMIT}, {@code B2041.START_LOC},
 * {@code B2041.TIME_LIMIT}, {@code PRINTER.SEND_TO}, {@code PRINTER.COPY_TO},
 * {@code ALERT_TEXT}. They are changed by editing the ConfigMap, not here; the
 * queries select them as server-side binds ({@code :hbsSetting_…}) from DUAL.
 */
@Component
public class SubmissionsProcessingParamsCatalog implements QueryCatalog {

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P038 form values, as one row.
        QueryDefinition.builder("admin.processingParameters")
            .legacy("P038")
            .capability(Capability.PRODUCTION_CONTROL)
            .sql("""
                SELECT :hbsSetting_B2031_START_LOC AS b2031_start_loc,
                       :hbsSetting_B2031_TIME_LIMIT AS b2031_time_limit,
                       :hbsSetting_B2041_START_LOC AS b2041_start_loc,
                       :hbsSetting_B2041_TIME_LIMIT AS b2041_time_limit,
                       :hbsSetting_PRINTER_SEND_TO AS send_to_printer,
                       :hbsSetting_PRINTER_COPY_TO AS copy_to_printer,
                       :hbsSetting_ALERT_TEXT AS alert_text
                  FROM dual
                 WHERE 1=1""")
            .maxRows(1)
            .build(),

        // Home-page banner (legacy tag hbs3:retrieveAlertText on home/about/P009/P400/...).
        QueryDefinition.builder("admin.alertText")
            .legacy("hbs3:retrieveAlertText")
            .capability(Capability.ANY_USER)
            .sql("""
                SELECT :hbsSetting_ALERT_TEXT AS alert_text
                  FROM dual
                 WHERE :hbsSetting_ALERT_TEXT IS NOT NULL""")
            .maxRows(1)
            .build()
    );
  }

  /** No commands: the values are edited in the ConfigMap (docs/coexistence.md). */
  @Override
  public List<CommandDefinition> commands() {
    return List.of();
  }
}
