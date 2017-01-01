package uk.org.cinquin.mutinack.database;

import java.util.Collection;
import java.util.function.Consumer;

import javax.jdo.JDODataStoreException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import org.datanucleus.store.rdbms.exceptions.MissingTableException;

import uk.org.cinquin.mutinack.Mutinack;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.output.RunResult;

/**
 * Created by olivier on 1/1/17.
 */
public class DatabaseOutput {
	private static void runWithOneAutoCreateRetry(Consumer<Boolean> r) {
		boolean autoCreate = true;//Set to false to try first without autoCreate
		boolean exception = true;
		while (exception) {
			try {
				r.accept(autoCreate);
				exception = false;
			} catch (MissingTableException | JDODataStoreException e) {
				if (autoCreate) {
					throw e;
				} else {
					autoCreate = true;
				}
			}
		}
	}

	public static void outputToDatabase(Parameters param, Collection<Mutinack> analyzers) {
		if (param.outputToDatabaseURL.isEmpty()) {
			return;
		}
		RunResult root = Mutinack.getRunResult(param, analyzers);
		runWithOneAutoCreateRetry(autoCreate -> {
			PersistenceManagerFactory pmf = PMF.getPMF(param, autoCreate);
			try {
				PersistenceManager persistenceManager = pmf.getPersistenceManager();
				final Transaction t = persistenceManager.currentTransaction();
				t.setOptimistic(false);
				t.setSerializeRead(true);
				t.setRestoreValues(false);
				t.begin();
				try {
					persistenceManager.makePersistent(root);
					t.commit();
				} finally {
					if (t.isActive()) {
						t.rollback();
					} else {
						persistenceManager.detachCopy(root);
					}
				}
			} finally {
				pmf.close();
			}
		});
	}
}
