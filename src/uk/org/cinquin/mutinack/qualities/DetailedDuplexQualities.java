package uk.org.cinquin.mutinack.qualities;

import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay;
import uk.org.cinquin.mutinack.misc_util.collections.DuplexAssayToQualityMap;

public class DetailedDuplexQualities extends DetailedQualitiesValues<DuplexAssay> {
	private static final long serialVersionUID = 997473584963123849L;

	public DetailedDuplexQualities() {
		super();
	}

	public static DetailedQualities<?> fromLong(long values) {
		DetailedDuplexQualities result = new DetailedDuplexQualities();
		result.values = values;
		return result;
	}

	@Override
	protected boolean isHasMaxGroup() {
		return DuplexAssay.hasMaxGroup();
	}

	@Override
	protected void qualitiesForEach(BiConsumer<DuplexAssay, @NonNull Quality> consumer) {
		DuplexAssayToQualityMap.forEach(values, consumer);
	}

	@Override
	public Map<DuplexAssay, Quality> getQualityMap() {
		return DuplexAssayToQualityMap.getMap(values);
	}

}
