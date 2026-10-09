-- =============================================================================
-- PROPOSED DDL — requires a DBA change in nr-mof-db (scripts/THE/...).
-- Not yet applied anywhere. Used by
--   backend/.../catalog/SubmissionsProcessingParamsCatalog.java
--   (queries admin.processingParameters, admin.appSettings, admin.alertText;
--    commands admin.processParameter.save, admin.printer.save,
--    admin.alertText.save, admin.alertText.delete)
--
-- Why: legacy P038/P039 "HBS Alert and Processing Parameters" kept its values
-- in three flat files on the WebLogic server's share (PROCESS_PARAMETER_FILE_PATH:
-- process-parameter CSV, printer-name file, alert-text file — see
-- hbs-ejb/.../dac/srt/detail/HBSProcessingParamHelper.java). Files on a pod
-- file system are neither shared across replicas nor durable, so the settings
-- move to the database.
--
-- Existing tables considered and rejected:
--   THE.APPLICATION_PREFERENCE  - shared multi-application table (S3 params),
--                                 no table-API proc, HBS has no write path.
--   THE.SCALE_CTL_SYSTEM_SETTING - owned by the Scale Control System, 4-char key.
--   THE.PREFERENCE               - per-user/euser preference model.
--
-- Suggested placement: TABLES/V2.xxxxx__HBS_APP_SETTING.sql,
-- PROCEDURES/V7.xxxxx__HBS_STORE_APP_SETTING.sql, plus grants matching the
-- other HBS_* tables (SELECT on the table, EXECUTE on the proc for the HBS
-- application proxy user).
-- =============================================================================

CREATE TABLE THE.HBS_APP_SETTING
    (
     SETTING_KEY      VARCHAR2 (30 BYTE)   NOT NULL ,
     SETTING_VALUE    VARCHAR2 (4000 BYTE) ,
     UPDATE_USERID    VARCHAR2 (30 BYTE)   NOT NULL ,
     UPDATE_TIMESTAMP DATE                 NOT NULL
    )
;

ALTER TABLE THE.HBS_APP_SETTING
    ADD CONSTRAINT HBS_APP_SETTING_PK PRIMARY KEY ( SETTING_KEY )
;

ALTER TABLE THE.HBS_APP_SETTING
    ADD CONSTRAINT HBS_APP_SETTING_KEY_CHK CHECK ( SETTING_KEY IN (
        'B2031.START_LOC', 'B2031.TIME_LIMIT',
        'B2041.START_LOC', 'B2041.TIME_LIMIT',
        'PRINTER.SEND_TO', 'PRINTER.COPY_TO',
        'ALERT_TEXT') )
;

COMMENT ON TABLE THE.HBS_APP_SETTING IS 'HBS application-wide settings maintained by Production Control (HBS Alert and Processing Parameters): batch process start location / time limit, batch printer names and the site-wide alert banner. Replaces the legacy flat files on the application server share.'
;
COMMENT ON COLUMN THE.HBS_APP_SETTING.SETTING_KEY IS 'Setting identifier, e.g. B2031.START_LOC, PRINTER.SEND_TO, ALERT_TEXT.'
;
COMMENT ON COLUMN THE.HBS_APP_SETTING.SETTING_VALUE IS 'Setting value; NULL when cleared (e.g. alert text deleted).'
;
COMMENT ON COLUMN THE.HBS_APP_SETTING.UPDATE_USERID IS 'The unique user id who last updated the record.'
;
COMMENT ON COLUMN THE.HBS_APP_SETTING.UPDATE_TIMESTAMP IS 'The date and time on which the record was last updated.'
;

-- Upsert one setting, with the validations legacy P038Action/P038Form applied:
--   *.START_LOC / *.TIME_LIMIT : required, whole number (errors.long)
--   PRINTER.*                  : required, \\server\printer (\\{2}?[^\\]+\\{1}?[^\\]+)
--   ALERT_TEXT                 : max 296 characters; NULL = delete
CREATE OR REPLACE EDITIONABLE PROCEDURE THE.HBS_STORE_APP_SETTING (
  i_setting_key      IN VARCHAR2,
  i_setting_value    IN VARCHAR2,
  i_update_userid    IN VARCHAR2,
  i_update_timestamp IN DATE) IS
  v_value VARCHAR2(4000) := TRIM(i_setting_value);
BEGIN
  IF i_setting_key IN ('B2031.START_LOC', 'B2031.TIME_LIMIT', 'B2041.START_LOC', 'B2041.TIME_LIMIT') THEN
    IF v_value IS NULL OR NOT REGEXP_LIKE(v_value, '^[0-9]{1,10}$') THEN
      RAISE_APPLICATION_ERROR(-20001, 'Process Start Location and Process Time Limit must be whole numbers.');
    END IF;
  ELSIF i_setting_key IN ('PRINTER.SEND_TO', 'PRINTER.COPY_TO') THEN
    IF v_value IS NULL OR LENGTH(v_value) > 50 OR NOT REGEXP_LIKE(v_value, '^\\\\[^\\]+\\[^\\]+$') THEN
      RAISE_APPLICATION_ERROR(-20002, 'Printer names must be in the form \\server\printer.');
    END IF;
  ELSIF i_setting_key = 'ALERT_TEXT' THEN
    IF LENGTH(v_value) > 296 THEN
      RAISE_APPLICATION_ERROR(-20003, 'Alert text may not exceed 296 characters.');
    END IF;
  ELSE
    RAISE_APPLICATION_ERROR(-20004, 'Unknown HBS setting ' || i_setting_key);
  END IF;

  MERGE INTO hbs_app_setting s
  USING (SELECT i_setting_key AS setting_key FROM dual) k
     ON (s.setting_key = k.setting_key)
   WHEN MATCHED THEN UPDATE
        SET s.setting_value = v_value,
            s.update_userid = i_update_userid,
            s.update_timestamp = i_update_timestamp
   WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, update_userid, update_timestamp)
        VALUES (i_setting_key, v_value, i_update_userid, i_update_timestamp);
END;
/

-- Optional seed (legacy "Set to Defaults": start 0, limit 9999999999).
-- INSERT INTO THE.HBS_APP_SETTING VALUES ('B2031.START_LOC', '0', 'HBS_MIGRATION', SYSDATE);
-- INSERT INTO THE.HBS_APP_SETTING VALUES ('B2031.TIME_LIMIT', '9999999999', 'HBS_MIGRATION', SYSDATE);
-- INSERT INTO THE.HBS_APP_SETTING VALUES ('B2041.START_LOC', '0', 'HBS_MIGRATION', SYSDATE);
-- INSERT INTO THE.HBS_APP_SETTING VALUES ('B2041.TIME_LIMIT', '9999999999', 'HBS_MIGRATION', SYSDATE);
