-- =============================================================================
-- VERIFY (read-only) — run as PROXY_FSA_HBS_READ_WRITE_USER after applying.
-- Every query should succeed; the expected results are in the comments.
-- =============================================================================

-- The backend's role is active in this session (expect FSA_HBS_READ_WRITE_ROLE).
SELECT role FROM session_roles WHERE role = 'FSA_HBS_READ_WRITE_ROLE';

-- The new table resolves through its public synonym (no ORA-00942).
SELECT COUNT(*) AS shedlock_rows FROM hbs_shedlock;
