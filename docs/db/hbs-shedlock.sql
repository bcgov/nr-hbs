-- ShedLock table for the HBS backend's scheduled batch jobs.
-- Same shape as THE.FSPTS_SHEDLOCK (nr-fsp-new). To be added to nr-mof-db as a
-- versioned migration, with SELECT/INSERT/UPDATE granted to the HBS proxy user.
CREATE TABLE THE.HBS_SHEDLOCK (
  NAME       VARCHAR2(64 CHAR)  NOT NULL,
  LOCK_UNTIL TIMESTAMP(3)       NOT NULL,
  LOCKED_AT  TIMESTAMP(3)       NOT NULL,
  LOCKED_BY  VARCHAR2(255 CHAR) NOT NULL,
  CONSTRAINT HBS_SHEDLOCK_PK PRIMARY KEY (NAME)
);
