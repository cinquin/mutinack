package uk.org.cinquin.mutinack.misc_util.collections;

import javax.annotation.CheckReturnValue;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.qualities.Quality;

public abstract class CustomEnumQualityMap<K extends Enum<K>> extends CustomEnumToEnumMap<K, @NonNull Quality> {

	private static final long serialVersionUID = -8624899797963564208L;

	static final @NonNull
	protected Quality[] VALUE_UNIVERSE = CustomEnumToEnumMap.getUniverse(Quality.class);

	protected static final int STRIDE = 3;

	static {
		Assert.isTrue(VALUE_UNIVERSE.length < Math.pow(2, STRIDE));
	}

	public static final int MAX_KEY_UNIVERSE_LENGTH = 64 / STRIDE - 2;//Keep space for two special key slots:
	//one for max and one for min

	protected static final int OFFSET_FOR_MIN = MAX_KEY_UNIVERSE_LENGTH;
	protected static final int OFFSET_FOR_MAX = MAX_KEY_UNIVERSE_LENGTH + 1;

	@Override
	protected int getStride() {
		return STRIDE;
	}

	private static final long[] MASKS, SHIFTED_MASKS;

	static {
		MASKS = new long[OFFSET_FOR_MAX + 1];
		SHIFTED_MASKS = new long[MASKS.length];
		for (int i = 0; i < MASKS.length; i++) {
			MASKS[i] = computeUnshiftedMask(i, STRIDE);
			SHIFTED_MASKS[i] = MASKS[i] << (i * STRIDE);
		}
	}

	protected static long getUnshiftedMaskStatic(int index, int stride) {
		Assert.isTrue(stride == STRIDE);
		return MASKS[index];
	}

	@Override
	protected long getUnshiftedMask(int index, int stride) {
		return getUnshiftedMaskStatic(index, stride);
	}

	@Override
	protected long getShiftedMask(int index, int stride) {
		return getShiftedMaskStatic(index, stride);
	}

	private static long getShiftedMaskStatic(int index, int stride) {
		Assert.isTrue(stride == STRIDE);
		return SHIFTED_MASKS[index];
	}

	protected CustomEnumQualityMap(Class<K> clazz) {
		super(clazz, Quality.class);
	}

	@Override
	public Class<@NonNull Quality> getValueType() {
		return Quality.class;
	}

	@Override
	public void setValueType(Class<@NonNull Quality> valueType) {
		Assert.isTrue(valueType == Quality.class);
	}

	@Override
	protected @NonNull Quality[] getValueUniverse() {
		return VALUE_UNIVERSE;
	}

	@Override
	protected void setValueUniverse(Quality[] valueUniverse) {
		Assert.isTrue(valueUniverse == CustomEnumQualityMap.VALUE_UNIVERSE);
	}

	@CheckReturnValue
	private static long setValue(long codedMap, int index, final long value) {
		return setValue(codedMap, index, value, STRIDE, getShiftedMaskStatic(index, STRIDE));
	}

	@CheckReturnValue
	public final static<K extends Enum<K>> Quality getMax(long codedMap) {
		return get(codedMap, OFFSET_FOR_MAX);
	}

	@CheckReturnValue
	public final static<K extends Enum<K>> Quality getMin(long codedMap) {
		return get(codedMap, OFFSET_FOR_MIN);
	}

	@CheckReturnValue
	public final static<K extends Enum<K>> long setMin(long codedMap, Quality q) {
		return setValue(codedMap, OFFSET_FOR_MIN, valueToInt(q));
	}

	@CheckReturnValue
	public final  static<K extends Enum<K>> long setMax(long codedMap, Quality q) {
		return setValue(codedMap, OFFSET_FOR_MAX, valueToInt(q));
	}

	@CheckReturnValue
	public final static<K extends Enum<K>> long put(long codedMap, K key, Quality q) {
		return setValue(codedMap, key.ordinal(), valueToInt(q));
	}

	@CheckReturnValue
	public final static<K extends Enum<K>> Quality get(long codedMap, int keyIndex) {
		int codedValue = (int) getValue(codedMap, keyIndex, STRIDE, getUnshiftedMaskStatic(keyIndex, STRIDE)) - 1;
		if (codedValue < 0) {
			return null;
		} else {
			return VALUE_UNIVERSE[codedValue];
		}
	}

	@CheckReturnValue
	public final static<K extends Enum<K>> Quality get(long codedMap, K key) {
		final int ordinal = key.ordinal();
		Assert.isTrue(ordinal < OFFSET_FOR_MIN);
		return get(codedMap, key.ordinal());
	}

}
