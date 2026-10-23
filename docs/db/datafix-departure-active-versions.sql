-- =============================================================================
-- DATA FIX (one-off) — three departure ledgers with two ACTIVE versions,
-- found by active-version-duplicates.sql in PROD. Leaves the newest version of
-- each document as its only active one:
--
--   DEP0000175424 v2  DEL -> inactive         (discarded edit never deactivated; v3 NTI stays)
--   DEP0000205287 v3  HLD -> SPR, inactive    (parallel line from a double submit; v4 NTI stays)
--   DEP0000216512 v3  DEL -> inactive         (parallel line from a double submit; v4 DEL stays)
--
-- Run as: THE (or a DBA), once. Backout: datafix-departure-active-versions-backout.sql.
--
-- Before running: set c_user in the block to the IDIR of the person the fix
-- is done for. It goes in UPDATE_USERID and
-- the audit rows, as the legacy data-fix tool did.
--
-- What it does:
--   1. Copies the three rows as they are now to THE.HBS_DF_DEP_ACTIVE_BAK (the
--      backout restores from it). Fails if that table exists, so a second run
--      stops here.
--   2. Updates each row only if it is still active and in the state above;
--      otherwise raises and rolls everything back.
--   3. Writes the audit the app writes for a version change: one HBS_AUDIT row
--      (DSR, UPD, "DDN,version") and HBS_SCALE_RETURN_AUDIT rows for
--      Version_Status (when it changes) and Version_Active.
--   4. Checks each document now has exactly one active version, then commits.
-- =============================================================================

CREATE TABLE THE.HBS_DF_DEP_ACTIVE_BAK AS
SELECT *
  FROM THE.SCL_SITE_DEPARTURE_LEDGER
 WHERE (DETAIL_DOCUMENT_NUMBER, VERSION) IN
       (('DEP0000175424', 2), ('DEP0000205287', 3), ('DEP0000216512', 3))
;

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'IDIR\CHANGE_ME';
  v_now  CONSTANT DATE := SYSDATE;
  v_n    NUMBER;

  PROCEDURE audit_field(p_audit_id NUMBER, p_field VARCHAR2, p_old VARCHAR2, p_new VARCHAR2) IS
  BEGIN
    INSERT INTO THE.HBS_SCALE_RETURN_AUDIT
      (AUDIT_ID, FIELD_NAME, ACTION_TYPE_CODE, HBS_OBJECT_TYPE_CODE, OLD_VALUE, NEW_VALUE)
    VALUES (p_audit_id, p_field, 'UPD', 'DSR', p_old, p_new);
  END;

  PROCEDURE deactivate(p_ddn VARCHAR2, p_version NUMBER, p_state VARCHAR2, p_new_state VARCHAR2) IS
    v_audit_id NUMBER;
  BEGIN
    UPDATE THE.SCL_SITE_DEPARTURE_LEDGER
       SET ACTIVE_VERSION_IND         = 'N',
           SCL_RTN_VERSION_STATE_CODE = p_new_state,
           UPDATE_USERID              = c_user,
           UPDATE_TIMESTAMP           = v_now
     WHERE DETAIL_DOCUMENT_NUMBER     = p_ddn
       AND VERSION                    = p_version
       AND ACTIVE_VERSION_IND         = 'Y'
       AND SCL_RTN_VERSION_STATE_CODE = p_state;
    IF SQL%ROWCOUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20001, p_ddn || ' v' || p_version || ' is no longer active in '
        || p_state || '. Nothing was changed.');
    END IF;

    SELECT THE.HBS_AUDIT_SEQ.NEXTVAL INTO v_audit_id FROM DUAL;
    INSERT INTO THE.HBS_AUDIT
      (AUDIT_ID, AUDIT_DATE, OBJECT_OLD_ID, OBJECT_NEW_ID, HBS_OBJECT_TYPE_CODE, ACTION_TYPE_CODE,
       USER_ID_RECORDING_ACTIONS_FOR, USER_ID)
    VALUES (v_audit_id, v_now, p_ddn || ',' || p_version, p_ddn || ',' || p_version, 'DSR', 'UPD',
       c_user, c_user);
    IF p_new_state <> p_state THEN
      audit_field(v_audit_id, 'Version_Status', p_state, p_new_state);
    END IF;
    audit_field(v_audit_id, 'Version_Active', 'Y', 'N');
  END;
BEGIN
  IF c_user = 'IDIR\CHANGE_ME' THEN
    RAISE_APPLICATION_ERROR(-20000, 'Set c_user to the IDIR the fix is done for.');
  END IF;
  SELECT COUNT(*) INTO v_n FROM THE.HBS_DF_DEP_ACTIVE_BAK;
  IF v_n <> 3 THEN
    RAISE_APPLICATION_ERROR(-20002, 'Expected 3 rows in the backup, found ' || v_n || '.');
  END IF;

  deactivate('DEP0000175424', 2, 'DEL', 'DEL');
  deactivate('DEP0000205287', 3, 'HLD', 'SPR');
  deactivate('DEP0000216512', 3, 'DEL', 'DEL');

  -- Exactly one active version per document afterwards.
  SELECT COUNT(*) INTO v_n
    FROM (SELECT DETAIL_DOCUMENT_NUMBER
            FROM THE.SCL_SITE_DEPARTURE_LEDGER
           WHERE DETAIL_DOCUMENT_NUMBER IN ('DEP0000175424', 'DEP0000205287', 'DEP0000216512')
             AND ACTIVE_VERSION_IND = 'Y'
           GROUP BY DETAIL_DOCUMENT_NUMBER
          HAVING COUNT(*) = 1);
  IF v_n <> 3 THEN
    RAISE_APPLICATION_ERROR(-20003, 'A document does not have exactly one active version. Rolled back.');
  END IF;

  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK;
    RAISE;
END;
/

-- Check (expect one row per document, each with ACTIVE_VERSION_IND = 'Y'):
SELECT DETAIL_DOCUMENT_NUMBER, VERSION, SCL_RTN_VERSION_STATE_CODE, ACTIVE_VERSION_IND
  FROM THE.SCL_SITE_DEPARTURE_LEDGER
 WHERE DETAIL_DOCUMENT_NUMBER IN ('DEP0000175424', 'DEP0000205287', 'DEP0000216512')
   AND ACTIVE_VERSION_IND = 'Y'
 ORDER BY 1
;

-- Keep THE.HBS_DF_DEP_ACTIVE_BAK until the fix is accepted; the backout needs
-- it. Then: DROP TABLE THE.HBS_DF_DEP_ACTIVE_BAK PURGE;
-- If the block above failed, drop it before running the script again.
