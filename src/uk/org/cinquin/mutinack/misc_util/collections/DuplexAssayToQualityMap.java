package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.CheckReturnValue;

import uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.qualities.Quality;

public class DuplexAssayToQualityMap extends CustomEnumQualityMap<DuplexAssay> {

	private static final long serialVersionUID = -3853025218998690278L;

	private static final DuplexAssay[] KEY_UNIVERSE = CustomEnumToEnumMap.getUniverse(DuplexAssay.class);

	static {
		Assert.isTrue(KEY_UNIVERSE.length <= MAX_KEY_UNIVERSE_LENGTH);
	}

	public DuplexAssayToQualityMap() {
		super(DuplexAssay.class);
	}

	@Override
	public void setKeyType(Class<DuplexAssay> keyType) {
		Assert.isTrue(keyType == DuplexAssay.class);
	}

	@Override
	public Class<DuplexAssay> getKeyType() {
		return DuplexAssay.class;
	}

	@Override
	protected DuplexAssay[] getKeyUniverse() {
		return KEY_UNIVERSE;
	}

	@Override
	protected void setKeyUniverse(DuplexAssay[] keyUniverse) {
		Assert.isTrue(keyUniverse == DuplexAssayToQualityMap.KEY_UNIVERSE);
	}

	public static void forEach(long codedMap, BiConsumer<DuplexAssay, Quality> consumer) {
		int keyUniverseLength = KEY_UNIVERSE.length;
		for (int i = 0; i < keyUniverseLength; i++) {
			int codedValue = (int) getValue(codedMap, i, STRIDE, getUnshiftedMaskStatic(i, STRIDE)) - 1;
			if (codedValue >= 0) {
				consumer.accept(KEY_UNIVERSE[i], VALUE_UNIVERSE[codedValue]);
			}
		}
	}

	@CheckReturnValue
	public static Map<DuplexAssay, Quality> getMap(long codedMap) {
		Map<DuplexAssay, Quality> map = new CustomEnumMap<>(DuplexAssay.class);
		forEach(codedMap, map::put);
		return map;
	}

}
