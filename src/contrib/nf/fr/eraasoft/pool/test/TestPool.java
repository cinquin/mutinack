package contrib.nf.fr.eraasoft.pool.test;

import contrib.nf.fr.eraasoft.pool.ObjectPool;
import contrib.nf.fr.eraasoft.pool.PoolException;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObject;
import contrib.nf.fr.eraasoft.pool.PoolableObjectBase;

import junit.framework.TestCase;

public class TestPool extends TestCase {
	
	public static void testPool() {
		// Create your PoolSettings with an instance of PoolableObject
		PoolSettings<StringBuilder> poolSettings = new PoolSettings<>(
				new PoolableObjectBase<StringBuilder>() {

					@Override
					public StringBuilder make() {
						return new StringBuilder();
					}

					@Override
					public void activate(StringBuilder t) {
						t.setLength(0);
					}
				});
		// Add some settings
		poolSettings.min(0).max(10);

		// Get the objectPool instance using a Singleton Design Pattern is a
		// good idea
		ObjectPool<StringBuilder> objectPool = poolSettings.pool(true);

		// Use your pool
		StringBuilder buffer = null;
		try {

			buffer = objectPool.getObj();
			// Do something with your object
			buffer.append("yyyy");

			objectPool.returnObj(buffer);

		} catch (PoolException e) {
			e.printStackTrace();
		} finally {
			// Don't forget to return object in the pool
			objectPool.returnObj(buffer);
		}
		PoolSettings.shutdown();

	}
	
	public static void testCreatePoolableObect() {
		@SuppressWarnings("unused")
		PoolableObject<StringBuilder> poolableStringBuilder = new PoolableObjectBase<StringBuilder>() {
			@Override
			public StringBuilder make() throws PoolException {
				return new StringBuilder();
			}

			@Override
			public void activate(StringBuilder t) throws PoolException {
				t.setLength(0);
			}
		};
		
	}
}
