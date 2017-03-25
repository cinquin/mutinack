package uk.org.cinquin.mutinack.misc_util.collections;

public class CustomEnumToEnumMapDefault<K extends Enum<K>, V extends Enum<V>> extends CustomEnumToEnumMap<K, V> {

	private static final long serialVersionUID = -4430206276562301265L;

	/**
	 * The <tt>Class</tt> object for the enum type of all the keys of this map.
	 *
	 * @serial
	 */
	private Class<K> keyType;

	private Class<V> valueType;

	/**
	 * All of the values comprising K.  (Cached for performance.)
	 */
	private transient K[] keyUniverse;

	private transient V[] valueUniverse;


	public CustomEnumToEnumMapDefault(Class<K> keyType, Class<V> valueType) {
		super(keyType, valueType);
	}


	@Override
	protected void setValueType(Class<V> valueType) {
		this.valueType = valueType;
	}


	@Override
	protected void setKeyType(Class<K> keyType) {
		this.keyType = keyType;
	}


	@Override
	protected Class<K> getKeyType() {
		return keyType;
	}


	@Override
	protected Class<V> getValueType() {
		return valueType;
	}

	@Override
	protected K[] getKeyUniverse() {
		return keyUniverse;
	}

	@Override
	protected void setKeyUniverse(K[] keyUniverse) {
		this.keyUniverse = keyUniverse;
	}

	@Override
	protected V[] getValueUniverse() {
		return valueUniverse;
	}

	@Override
	protected void setValueUniverse(V[] valueUniverse) {
		this.valueUniverse = valueUniverse;
	}


	@Override
	protected int getStride() {
		return (int) Math.ceil(Math.log(getValueUniverse().length + 1) / Math.log(2));
	}

}
