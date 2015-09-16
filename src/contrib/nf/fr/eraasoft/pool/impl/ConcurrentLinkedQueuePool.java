package contrib.nf.fr.eraasoft.pool.impl;

import java.util.concurrent.ConcurrentLinkedQueue;

import contrib.nf.fr.eraasoft.pool.PoolException;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObject;

public class ConcurrentLinkedQueuePool<T> extends AbstractPool<T> {



	public ConcurrentLinkedQueuePool(final PoolableObject<T> poolableObject, final PoolSettings<T> settings) {
		super(poolableObject, settings);
		queue = new ConcurrentLinkedQueue<>();
		try {
			init();
		} catch (PoolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public synchronized T getObj() throws PoolException {
		T t = queue.poll();
		if (t==null) {
			t = poolableObject.make();
			totalSize.incrementAndGet();
		}
		poolableObject.activate(t);
		return t;
	}

}
