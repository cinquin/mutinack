package contrib.nf.fr.eraasoft.pool;

import contrib.nf.fr.eraasoft.pool.impl.PoolController;
import contrib.nf.fr.eraasoft.pool.impl.PoolFactory;

/**
 * Class used to configure your object pooling. Instance of this classes must be used in a Singleton context (static variable).<br>
 *
 *
 * @author eddie
 *
 * @param <T>
 */
public class PoolSettings<T> {
	/**
	 * Wait (in second) before
	 */
	public static final int DEFAUL_MAX_WAIT = 5;
	public static final int DEFAULT_MIN = 1;
	public static final int DEFAULT_MAX = 10;
	/**
	 * Control thread 
	 */
	public static final int DEFAULT_SECONDS_BETWEEN_TWO_CONTROLS = 30;
	private static int SECONDS_BETWEEN_TWO_CONTROLS = DEFAULT_SECONDS_BETWEEN_TWO_CONTROLS;

	private int maxWait = DEFAUL_MAX_WAIT;
	private int min = DEFAULT_MIN;
	private int max = DEFAULT_MAX;
	private int maxIdle = min;
	private boolean validateWhenReturn = false;
	private boolean debug = false;

	public static void timeBetweenTwoControls(int time) {
		SECONDS_BETWEEN_TWO_CONTROLS = time;
	}

	public static int timeBetweenTwoControls() {
		return SECONDS_BETWEEN_TWO_CONTROLS;
	}


	private final PoolFactory<T> poolFactory;

	/**
	 * Create a new PoolSetting instance with a Poolable object<br>
	 * An instance of this pool setting is added to the PoolController<br>
	 * @param poolableObject
	 */
	public PoolSettings(final PoolableObject<T> poolableObject) {
		this.poolFactory = new PoolFactory<>(this, poolableObject);
		PoolController.addPoolSettings(this);
	}

	/** Return the ObjectPool associated with this PoolSetting
	 *  
	 **/
	public ObjectPool<T> pool(boolean createIfNecessary) {
		return poolFactory.getPool(createIfNecessary);
	}

	@SuppressWarnings("hiding")
	public PoolSettings<T> maxIdle(final int maxIdle) {
		this.maxIdle = maxIdle < min ? min : maxIdle;
		return this;
	}

	public int maxIdle() {
		return this.maxIdle;
	}

	@SuppressWarnings("hiding")
	public PoolSettings<T> maxWait(final int maxWait) {
		this.maxWait = maxWait;
		return this;
	}

	/**
	 * Define the minimum number of element in the pool
	 * @param min
	 * @return 
	 */
	@SuppressWarnings("hiding")
	public PoolSettings<T> min(final int min) {
		this.min = min;
		maxIdle = min;
		if (max>0 && min > max) {
			max(min);
		}
		return this;
	}

	/**
	 * if  
	 * @param max
	 * @return
	 */
	@SuppressWarnings("hiding")
	public PoolSettings<T> max(final int max) {
		this.max = max;
		if (max>0 && max < min) {
			min(max);
		}
		return this;
	}

	public int min() {
		return min;
	}

	public int maxWait() {
		return maxWait;
	}

	public int max() {
		return max;
	}

	/**
	 * Shutdown all the pools referenced in the PoolController<br/>
	 * 
	 */
	public static void shutdown() {
		PoolController.shutdown();
	}

	/**
	 * Clear the ObjectPool associated with this PoolSetting and remove it to the PoolController<br>
	 * 
	 */
	public static void removePoolSetting(@SuppressWarnings("rawtypes") PoolSettings poolSettings) {
		PoolController.removePoolSettings(poolSettings);
	}

	/**
	 * if true invoke PoolableObject.validate() method
	 * @param validateWhenReturn
	 * @return
	 */
	@SuppressWarnings("hiding")
	public PoolSettings<T> validateWhenReturn(boolean validateWhenReturn) {
		this.validateWhenReturn = validateWhenReturn;
		return this;
	}

	public boolean validateWhenReturn() {
		return validateWhenReturn;
	}

	@SuppressWarnings("hiding")
	public PoolSettings<T> debug(boolean debug) {
		this.debug = debug;
		return this;
	}

	public boolean debug() {
		return debug;
	}

	public void clearCurrentPool() {
		poolFactory.clear();
	}


}
 
