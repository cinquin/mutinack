package contrib.nf.fr.eraasoft.pool.impl;

import contrib.nf.fr.eraasoft.pool.ObjectPool;
import contrib.nf.fr.eraasoft.pool.PoolSettings;
import contrib.nf.fr.eraasoft.pool.PoolableObject;

public class PoolFactory<T> {
	final PoolSettings<T> settings;

	AbstractPool<T> pool;
	final PoolableObject<T> poolableObject;

	public PoolFactory(PoolSettings<T> settings, PoolableObject<T> poolableObject) {
		this.settings = settings;
		this.poolableObject = poolableObject;
	}

	public ObjectPool<T> getPool(boolean createIfNecessary) {
		if (pool == null && createIfNecessary)
			createPoolInstance();
		return pool;
	}

	public void clear() {
		if (getPool(false) instanceof Controllable) {
			((Controllable) getPool(false)).clear();
		}
	}

	private static class BBObjectPool<T> extends BlockingQueueObjectPool<T> {

		public BBObjectPool(PoolableObject<T> poolableObject, PoolSettings<T> settings) {
			super(poolableObject, settings);

		}

	}

	private synchronized void createPoolInstance() {
		if (pool == null){
			if (settings.max()>0)
				pool = new BBObjectPool<>(poolableObject, settings);
			else 
				pool = new ConcurrentLinkedQueuePool<>(poolableObject, settings);
		}

	}

	public PoolSettings<T> settings() {
		return settings;
	}

}
