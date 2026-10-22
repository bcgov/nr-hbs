package ca.bc.gov.nrs.hbs.api.billing;

import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One nested transaction (savepoint) per selected row inside the command's
 * transaction, as {@code detailreturns.command.BulkCommand} does: the legacy
 * bulk actions autocommitted each row, so a failed row must not undo the
 * others. A failed row is rolled back to its savepoint; the rows that worked
 * commit together when the command ends. Without a transaction manager (unit
 * tests) the work just runs.
 */
final class RowTransactions {

  private final TransactionTemplate nested;

  RowTransactions(ObjectProvider<PlatformTransactionManager> tx) {
    PlatformTransactionManager tm = tx == null ? null : tx.getIfAvailable();
    if (tm == null) {
      this.nested = null;
    } else {
      this.nested = new TransactionTemplate(tm);
      this.nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
    }
  }

  <T> T run(Supplier<T> work) {
    return nested == null ? work.get() : nested.execute(st -> work.get());
  }
}
