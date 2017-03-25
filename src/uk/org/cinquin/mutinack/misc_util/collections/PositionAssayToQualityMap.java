package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.Map;
import java.util.function.BiConsumer;

import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.misc_util.Assert;
import uk.org.cinquin.mutinack.qualities.Quality;

public class PositionAssayToQualityMap extends CustomEnumQualityMap<PositionAssay> {

	private static final long serialVersionUID = -3853025218998690278L;

	private static final PositionAssay[] KEY_UNIVERSE = CustomEnumToEnumMap.getUniverse(PositionAssay.class);

	static {
		Assert.isTrue(KEY_UNIVERSE.length <= MAX_KEY_UNIVERSE_LENGTH);
	}

	public PositionAssayToQualityMap() {
		super(PositionAssay.class);
	}

	@Override
	public void setKeyType(Class<PositionAssay> keyType) {
		Assert.isTrue(keyType == PositionAssay.class);
	}

	@Override
	public Class<PositionAssay> getKeyType() {
		return PositionAssay.class;
	}

	@Override
	protected PositionAssay[] getKeyUniverse() {
		return KEY_UNIVERSE;
	}

	@Override
	protected void setKeyUniverse(PositionAssay[] keyUniverse) {
		Assert.isTrue(keyUniverse == PositionAssayToQualityMap.KEY_UNIVERSE);
	}

	public static void forEach(long codedMap, BiConsumer<PositionAssay, Quality> consumer) {
		int keyUniverseLength = KEY_UNIVERSE.length;
		for (int i = 0; i < keyUniverseLength; i++) {
			int codedValue = (int) getValue(codedMap, i, STRIDE, getUnshiftedMaskStatic(i, STRIDE)) - 1;
			if (codedValue >= 0) {
				consumer.accept(KEY_UNIVERSE[i], VALUE_UNIVERSE[codedValue]);
			}
		}
	}

	public static Map<PositionAssay, Quality> getMap(long codedMap) {
		Map<PositionAssay, Quality> map = new CustomEnumMap<>(PositionAssay.class);
		forEach(codedMap, map::put);
		return map;
	}
}
