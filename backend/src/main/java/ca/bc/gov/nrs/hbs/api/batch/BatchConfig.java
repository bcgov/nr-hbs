package ca.bc.gov.nrs.hbs.api.batch;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * Scheduling for the re-hosted legacy batch jobs (hbs-batch + the AutoMate 5
 * tasks on the Windows batch host). Same pattern as nr-fsp-new's designate
 * digest: every backend pod runs the scheduler, ShedLock (table
 * {@code THE.HBS_SHEDLOCK}, DDL in docs/db/hbs-shedlock.sql) guarantees one
 * pod per tick.
 *
 * <p>Off unless {@code hbs.batch.enabled=true}. The legacy batch keeps
 * running until cut-over; running both would double-process returns.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT2H")
@ConditionalOnProperty(name = "hbs.batch.enabled", havingValue = "true")
public class BatchConfig {

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
        .withJdbcTemplate(new JdbcTemplate(dataSource))
        .withTableName("HBS_SHEDLOCK")
        .usingDbTime()
        .build());
  }
}
