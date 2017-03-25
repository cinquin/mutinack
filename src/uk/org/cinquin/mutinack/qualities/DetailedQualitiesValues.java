package uk.org.cinquin.mutinack.qualities;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.candidate_sequences.AssayInfo;
import uk.org.cinquin.mutinack.misc_util.collections.CustomEnumQualityMap;

public abstract class DetailedQualitiesValues<T extends Enum<T> & AssayInfo> extends DetailedQualities<T> {

	private static final long serialVersionUID = 5026078319459229427L;

	protected long values;

	@Override
	public long toLong() {
		return values;
	}

	public DetailedQualitiesValues() {
		super();
	}

	@Override
	protected final Quality qualitiesGet(T t) {
		return CustomEnumQualityMap.get(values, t);
	}

	@Override
	protected final void qualitiesPut(T t, @NonNull Quality q) {
		enter();
		values = CustomEnumQualityMap.put(values, t, q);
		leave();
	}

	@Override
	protected final void qualitiesClear() {
		enter();
		values = 0;
		leave();
	}

	@Override
	public final Quality getMin() {
		enter();
		Quality result = CustomEnumQualityMap.getMin(values);
		leave();
		return result;
	}

	@Override
	public final void setMin(Quality min) {
		enter();
		values = CustomEnumQualityMap.setMin(values, min);
		leave();
	}

	@Override
	public final Quality getMax() {
		enter();
		Quality result = CustomEnumQualityMap.getMax(values);
		leave();
		return result;
	}

	@Override
	public final void setMax(Quality max) {
		enter();
		values = CustomEnumQualityMap.setMax(values, max);
		leave();
	}


}
