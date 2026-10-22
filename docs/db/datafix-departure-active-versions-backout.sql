-- =============================================================================
-- BACKOUT for datafix-departure-active-versions.sql. Run as: THE (or a DBA).
--
-- Restores the three rows (active flag, state, UPDATE_USERID, UPDATE_TIMESTAMP)
-- from THE.HBS_DF_DEP_ACTIVE_BAK, then drops that table. Each row is restored
-- only if it is still as the fix left it; otherwise everything rolls back.
--
-- The fix's audit rows stay (they are history); the backout adds its own
-- audit rows for the reversal, the same way. Set c_user as in the fix.
-- =============================================================================

DECLARE
  c_user CONSTANT VARCHAR2(30) := 'IDIR\CHANGE_ME';
  v_now  CONSTANT DATE := SYSDATE;

  PROCEDURE audit_field(p_audit_id NUMBER, p_field VARCHAR2, p_old VARCHAR2, p_new VARCHAR2) IS
  BEGIN
    INSERT INTO THE.HBS_SCALE_RETURN_AUDIT
      (AUDIT_ID, FIELD_NAME, ACTION_TYPE_CODE, HBS_OBJECT_TYPE_CODE, OLD_VALUE, NEW_VALUE)
    VALUES (p_audit_id, p_field, 'UPD', 'DSR', p_old, p_new);
  END;

  PROCEDURE restore(p_ddn VARCHAR2, p_version NUMBER, p_fixed_state VARCHAR2) IS
    b          THE.HBS_DF_DEP_ACTIVE_BAK%ROWTYPE;
    v_audit_id NUMBER;
  BEGIN
    SELECT * INTO b
      FROM THE.HBS_DF_DEP_ACTIVE_BAK
     WHERE DETAIL_DOCUMENT_NUMBER = p_ddn AND VERSION = p_version;

    UPDATE THE.SCL_SITE_DEPARTURE_LEDGER
       SET ACTIVE_VERSION_IND         = b.ACTIVE_VERSION_IND,
           SCL_RTN_VERSION_STATE_CODE = b.SCL_RTN_VERSION_STATE_CODE,
           UPDATE_USERID              = b.UPDATE_USERID,
           UPDATE_TIMESTAMP           = b.UPDATE_TIMESTAMP
     WHERE DETAIL_DOCUMENT_NUMBER     = p_ddn
       AND VERSION                    = p_version
       AND ACTIVE_VERSION_IND         = 'N'
       AND SCL_RTN_VERSION_STATE_CODE = p_fixed_state;
    IF SQL%ROWCOUNT <> 1 THEN
      RAISE_APPLICATION_ERROR(-20001, p_ddn || ' v' || p_version || ' has changed since the fix ('
        || 'not inactive in ' || p_fixed_state || '). Nothing was restored.');
    END IF;

    SELECT THE.HBS_AUDIT_SEQ.NEXTVAL INTO v_audit_id FROM DUAL;
    INSERT INTO THE.HBS_AUDIT
      (AUDIT_ID, AUDIT_DATE, OBJECT_OLD_ID, OBJECT_NEW_ID, HBS_OBJECT_TYPE_CODE, ACTION_TYPE_CODE,
       USER_ID_RECORDING_ACTIONS_FOR, USER_ID)
    VALUES (v_audit_id, v_now, p_ddn || ',' || p_version, p_ddn || ',' || p_version, 'DSR', 'UPD',
       c_user, c_user);
    IF b.SCL_RTN_VERSION_STATE_CODE <> p_fixed_state THEN
      audit_field(v_audit_id, 'Version_Status', p_fixed_state, b.SCL_RTN_VERSION_STATE_CODE);
    END IF;
    audit_field(v_audit_id, 'Version_Active', 'N', b.ACTIVE_VERSION_IND);
  END;
BEGIN
  IF c_user = 'IDIR\CHANGE_ME' THEN
    RAISE_APPLICATION_ERROR(-20000, 'Set c_user to the IDIR the backout is done for.');
  END IF;

  restore('DEP0000175424', 2, 'DEL');
  restore('DEP0000205287', 3, 'SPR');
  restore('DEP0000216512', 3, 'DEL');

  COMMIT;
EXCEPTION
  WHEN OTHERS THEN
    ROLLBACK;
    RAISE;
END;
/

-- Check (expect the three documents back in active-version-duplicates.sql):
SELECT DETAIL_DOCUMENT_NUMBER, VERSION, SCL_RTN_VERSION_STATE_CODE, ACTIVE_VERSION_IND
  FROM THE.SCL_SITE_DEPARTURE_LEDGER
 WHERE DETAIL_DOCUMENT_NUMBER IN ('DEP0000175424', 'DEP0000205287', 'DEP0000216512')
   AND ACTIVE_VERSION_IND = 'Y'
 ORDER BY 1, 2
;

-- Run only after the block above succeeded:
DROP TABLE THE.HBS_DF_DEP_ACTIVE_BAK PURGE
;
