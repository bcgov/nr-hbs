-- =============================================================================
-- PRECHECK (read-only) — run as a DBA before applying anything in docs/db.
-- =============================================================================

-- 1. The new object must not exist yet (expect no rows).
SELECT owner, object_name, object_type
  FROM dba_objects
 WHERE owner = 'THE'
   AND object_name = 'HBS_SHEDLOCK'
UNION ALL
SELECT owner, synonym_name, 'SYNONYM'
  FROM dba_synonyms
 WHERE owner = 'PUBLIC'
   AND synonym_name = 'HBS_SHEDLOCK';

-- 2. The HBS backend's role and user exist and the user holds the role
--    (nr-mof-db PR #582, migration 202610212122). Expect 1 row.
SELECT grantee, granted_role
  FROM dba_role_privs
 WHERE grantee = 'PROXY_FSA_HBS_READ_WRITE_USER'
   AND granted_role = 'FSA_HBS_READ_WRITE_ROLE';

-- 3. Data check, unrelated to the DDL but needed before go-live: detail
--    documents with more than one active version (expect no rows).
@@active-version-duplicates.sql
