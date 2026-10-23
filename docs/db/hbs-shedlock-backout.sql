-- =============================================================================
-- BACKOUT for hbs-shedlock.sql. Run as: THE (or a DBA).
-- Safe at any time: the table only holds transient locks. Turn the backend's
-- batch off first (HBS_BATCH_ENABLED=false), or its jobs log lock errors.
-- =============================================================================

DROP PUBLIC SYNONYM HBS_SHEDLOCK
;

REVOKE SELECT, INSERT, UPDATE ON THE.HBS_SHEDLOCK FROM FSA_HBS_READ_WRITE_ROLE
;

-- Drops the PK constraint and its index with it.
DROP TABLE THE.HBS_SHEDLOCK PURGE
;
