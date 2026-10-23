-- =============================================================================
-- HBS_SHEDLOCK — lock table for the HBS backend's scheduled batch jobs
-- (ShedLock: one pod runs each job per tick). Same shape as THE.FSPTS_SHEDLOCK
-- (nr-fsp-new). Only used when the backend runs with HBS_BATCH_ENABLED=true.
--
-- Run as: THE (or a DBA). Backout: hbs-shedlock-backout.sql.
-- nr-mof-db (branch feature/hbs-shedlock), each with its U… backout:
--   THE/TABLES/V…1__HBS_SHEDLOCK.sql              table, PK, comments
--   THE/GRANTS/V…2__FSA_HBS_READ_WRITE_ROLE.sql   grants
--   DBA/SYNONYMS/V…3__PUBLIC.sql                  public synonym
-- =============================================================================

CREATE TABLE THE.HBS_SHEDLOCK
    (
     NAME       VARCHAR2 (64 BYTE)  NOT NULL ,
     LOCK_UNTIL TIMESTAMP (3)       NOT NULL ,
     LOCKED_AT  TIMESTAMP (3)       NOT NULL ,
     LOCKED_BY  VARCHAR2 (255 BYTE) NOT NULL
    )
;

ALTER TABLE THE.HBS_SHEDLOCK
    ADD CONSTRAINT HBS_SHEDLOCK_PK PRIMARY KEY ( NAME )
;

COMMENT ON TABLE THE.HBS_SHEDLOCK IS 'Distributed lock table used by the HBS backend (ShedLock library) to ensure exactly one pod runs each scheduled batch job per cron tick.'
;
COMMENT ON COLUMN THE.HBS_SHEDLOCK.NAME IS 'Logical name of the scheduled job being coordinated (e.g. HBS_B1012_UNPACK). The PK.'
;
COMMENT ON COLUMN THE.HBS_SHEDLOCK.LOCK_UNTIL IS 'Timestamp until which the current lock holder may keep running. Auto-releases after this point.'
;
COMMENT ON COLUMN THE.HBS_SHEDLOCK.LOCKED_AT IS 'Timestamp when the current lock was acquired.'
;
COMMENT ON COLUMN THE.HBS_SHEDLOCK.LOCKED_BY IS 'Identifier of the pod (HOSTNAME) that holds the current lock.'
;

-- The app writes this table directly (ShedLock), so it needs DML here — the
-- only HBS table it does. SELECT/INSERT/UPDATE, like FSPTS_SHEDLOCK, to the
-- HBS backend's role (held by PROXY_FSA_HBS_READ_WRITE_USER).
GRANT SELECT, INSERT, UPDATE ON THE.HBS_SHEDLOCK TO FSA_HBS_READ_WRITE_ROLE
;

-- The app uses unqualified names, as the legacy app did.
CREATE PUBLIC SYNONYM HBS_SHEDLOCK FOR THE.HBS_SHEDLOCK
;
