package contrib.nf.fr.eraasoft.pool.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import contrib.nf.fr.eraasoft.pool.PoolSettings;


public class PoolController extends Thread {
	static PoolController instance = null;

	boolean alive = false;
	final Set<PoolSettings<?>> listPoolSettings =
		Collections.synchronizedSet(new HashSet<PoolSettings<?>>());

	private PoolController() {
		setName("PoolController");
	}
	/**
	 *
	 */
	private static synchronized void launch() {
		if (instance == null) {
			instance = new PoolController();
		}
		if (!instance.alive) {
			instance.alive = true;
			instance.start();
		}
	}


	public static synchronized void addPoolSettings(PoolSettings<?> poolSettings) {
		launch();
		instance.listPoolSettings.add(poolSettings);
	}

	public static synchronized void removePoolSettings(PoolSettings<?> poolSettings) {
		poolSettings.clearCurrentPool();
		instance.listPoolSettings.remove(poolSettings);
	}

	public static void shutdown() {
		if (instance != null) {
			instance.alive = false;

			for (PoolSettings<?> poolSettings : instance.listPoolSettings) {
				if (poolSettings.pool(false) instanceof Controllable) {
					Controllable controllable = (Controllable) poolSettings.pool(false);
					controllable.destroy();
				}
			}
			instance.listPoolSettings.clear();
			instance.interrupt();
			instance = null;
		}
	}



	@Override
	public void run() {
		alive = true;
		while (alive) {
			try {
				sleep(PoolSettings.timeBetweenTwoControls()*1000);
				checkPool();
			} catch (InterruptedException e) {
				alive = false;
			}
		}

	}

	/**
	 * Remove idle <br>
	 * Validate idle
	 *
	 *
	 */
	private void checkPool() {
		synchronized (listPoolSettings) {
			for (PoolSettings<?> poolSettings : listPoolSettings) {

				if (poolSettings.pool(false) instanceof Controllable) {
					Controllable controllable = (Controllable) poolSettings.pool(false);
					if (poolSettings.debug()) System.out.println(controllable.toString());

					/*
					 * Remove idle
					 */
					int idleToRemoves = controllable.idles() - poolSettings.maxIdle();
					if (idleToRemoves > 0)
						controllable.remove(idleToRemoves);

					/*
					 * Check idle
					 */
					controllable.validateIdles();

				}
			}
		}
	}

}
