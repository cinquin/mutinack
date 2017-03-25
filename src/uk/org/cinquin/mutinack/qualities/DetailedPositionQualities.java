package uk.org.cinquin.mutinack.qualities;

import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.misc_util.collections.PositionAssayToQualityMap;

public class DetailedPositionQualities extends DetailedQualitiesValues<PositionAssay> {
	private static final long serialVersionUID = 997473584963123849L;

	public DetailedPositionQualities() {
		super();
	}

	public static DetailedQualities<?> fromLong(long values) {
		DetailedPositionQualities result = new DetailedPositionQualities();
		result.values = values;
		return result;
	}

	@Override
	protected void qualitiesForEach(BiConsumer<PositionAssay, @NonNull Quality> consumer) {
		enter();
		PositionAssayToQualityMap.forEach(values, consumer);
		leave();
	}

	@Override
	protected boolean isHasMaxGroup() {
		return PositionAssay.hasMaxGroup();
	}

	@Override
	public Map<PositionAssay, Quality> getQualityMap() {
		return PositionAssayToQualityMap.getMap(values);
	}
}
