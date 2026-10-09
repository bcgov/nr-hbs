package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P038/P039 "HBS Alert and Processing Parameters" (Production Control).
 *
 * <p>The legacy app kept these in three flat files on the app server's share
 * ({@code HBSProcessingParamHelper}: process-parameter CSV
 * {@code <process>,<startLoc>,<timeLimit>}, printer file
 * {@code SEND_TO_PRINTER=} / {@code COPY_TO_PRINTER=}, alert-text file). They
 * become rows of the <b>proposed</b> table {@code THE.HBS_APP_SETTING},
 * written only through the proposed proc {@code THE.HBS_STORE_APP_SETTING}
 * — see {@code docs/db/proposed-ddl.sql}. <b>Requires the DBA change before
 * these queries/commands can run.</b>
 *
 * <p>Setting keys: {@code B2031.START_LOC}, {@code B2031.TIME_LIMIT},
 * {@code B2041.START_LOC}, {@code B2041.TIME_LIMIT},
 * {@code PRINTER.SEND_TO}, {@code PRINTER.COPY_TO}, {@code ALERT_TEXT}. The
 * proc rejects any other key and validates the value format (numeric
 * parameters, {@code \\server\printer} names, 296-char alert), so the key
 * coming from the request body cannot write arbitrary settings.
 */
@Component
public class SubmissionsProcessingParamsCatalog implements QueryCatalog {

  private static final String PROC = "THE.HBS_STORE_APP_SETTING";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P038 form values, pivoted into one row (aggregate → always exactly one row).
        QueryDefinition.builder("admin.processingParameters")
            .legacy("P038")
            .capability(Capability.PRODUCTION_CONTROL)
            .sql("""
                SELECT MAX(CASE WHEN s.setting_key = 'B2031.START_LOC' THEN s.setting_value END) AS b2031_start_loc,
                       MAX(CASE WHEN s.setting_key = 'B2031.TIME_LIMIT' THEN s.setting_value END) AS b2031_time_limit,
                       MAX(CASE WHEN s.setting_key = 'B2041.START_LOC' THEN s.setting_value END) AS b2041_start_loc,
                       MAX(CASE WHEN s.setting_key = 'B2041.TIME_LIMIT' THEN s.setting_value END) AS b2041_time_limit,
                       MAX(CASE WHEN s.setting_key = 'PRINTER.SEND_TO' THEN s.setting_value END) AS send_to_printer,
                       MAX(CASE WHEN s.setting_key = 'PRINTER.COPY_TO' THEN s.setting_value END) AS copy_to_printer,
                       MAX(CASE WHEN s.setting_key = 'ALERT_TEXT' THEN s.setting_value END) AS alert_text,
                       MAX(s.update_timestamp) AS last_update_timestamp
                  FROM hbs_app_setting s
                 WHERE 1=1""")
            .maxRows(1)
            .build(),

        // Audit view of every setting (who changed what, when).
        QueryDefinition.builder("admin.appSettings")
            .legacy("P038")
            .capability(Capability.PRODUCTION_CONTROL)
            .sql("""
                SELECT s.setting_key, s.setting_value, s.update_userid, s.update_timestamp
                  FROM hbs_app_setting s
                 WHERE 1=1""")
            .orderBy("s.setting_key")
            .maxRows(100)
            .build(),

        // Home-page banner (legacy tag hbs3:retrieveAlertText on home/about/P009/P400/...).
        QueryDefinition.builder("admin.alertText")
            .legacy("hbs3:retrieveAlertText")
            .capability(Capability.ANY_USER)
            .sql("""
                SELECT s.setting_value AS alert_text
                  FROM hbs_app_setting s
                 WHERE s.setting_key = 'ALERT_TEXT'
                   AND s.setting_value IS NOT NULL""")
            .maxRows(1)
            .build()
    );
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P038 "Batch Processing Parameters" Save → P039 "Confirm HBS Processing Parameters".
        CommandDefinition.builder("admin.processParameter.save")
            .legacy("P038/P039")
            .capability(Capability.PRODUCTION_CONTROL)
            .procedure(PROC)
            .requiredBody("settingKey", Type.UPPER)    // i_setting_key (B20x1.START_LOC / .TIME_LIMIT)
            .requiredBody("settingValue", Type.STRING) // i_setting_value
            .auditUser()                               // i_update_userid
            .now()                                     // i_update_timestamp
            .build(),
        // P038 "Printer Names" Save.
        CommandDefinition.builder("admin.printer.save")
            .legacy("P038/P039")
            .capability(Capability.PRODUCTION_CONTROL)
            .procedure(PROC)
            .requiredBody("settingKey", Type.UPPER)    // PRINTER.SEND_TO / PRINTER.COPY_TO
            .requiredBody("settingValue", Type.STRING)
            .auditUser()
            .now()
            .build(),
        // P038 "Alert Text" Save → P039 "Confirm Alert Text".
        CommandDefinition.builder("admin.alertText.save")
            .legacy("P038/P039")
            .capability(Capability.PRODUCTION_CONTROL)
            .procedure(PROC)
            .constant("ALERT_TEXT")
            .requiredBody("alertText", Type.STRING)
            .auditUser()
            .now()
            .build(),
        // P038 "Alert Text" Delete → P039 "Delete Alert Text" (value cleared to NULL).
        CommandDefinition.builder("admin.alertText.delete")
            .legacy("P038/P039")
            .capability(Capability.PRODUCTION_CONTROL)
            .procedure(PROC)
            .constant("ALERT_TEXT")
            .constant("")
            .auditUser()
            .now()
            .build()
    );
  }
}
