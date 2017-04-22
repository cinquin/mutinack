/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2016 Olivier Cinquin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package uk.org.cinquin.mutinack.qualities;

import java.io.Serializable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import uk.org.cinquin.mutinack.candidate_sequences.AssayInfo;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.output.json.DetailedQualitiesSerializer;

/**
 * Keeps track of a "min_assay" group, and a "max_assay" group.
 * Overall quality is computed as
 * min(min_{assay: min_assay}(Q(assay)), max_{assay: max_assay}(Q(assay)))
 * where Q(assay) is the quality mapped to each assay using e.g. the {@link #add(Enum, Quality) add}
 * and {@link #addUnique(Enum, Quality) addUnique} methods.
 *
 * For now, only 1 max_assay assay is allowed.
 * @author olivier
 *
 * @param <T>
 */
@JsonSerialize(using = DetailedQualitiesSerializer.class)
public abstract class DetailedQualities<T extends Enum<T> & AssayInfo> implements Serializable {
	private static final long serialVersionUID = -5423960175598403757L;

	public abstract long toLong();

	protected abstract Quality qualitiesGet(T t);
	protected abstract void qualitiesPut(T t, @NonNull Quality q);
	protected abstract void qualitiesForEach(BiConsumer<T, @NonNull Quality> consumer);
	protected abstract void qualitiesClear();
	protected abstract Map<T, Quality> getQualityMap();

	//private AtomicInteger counter = new AtomicInteger();

	protected final void enter() {
		/*if (counter.incrementAndGet() > 1) {
			throw new AssertionFailedException();
		}*/
	}

	protected final void leave() {
		//counter.decrementAndGet();
	}


	@Override
	public String toString() {
		return getQualityMap().toString();
	}

	public Stream<Entry<T, Quality>> getQualities() {
		return getQualityMap().entrySet().stream();
	}

	public boolean qualitiesContain(T a) {
		return qualitiesGet(a) != null;
	}

	public void addUnique(T assay, @NonNull Quality q) {
		if (qualitiesGet(assay) != null) {
			throw new IllegalStateException(assay + " already defined");
		}
		qualitiesPut(assay, q);
		if (assay.isMinGroup()) {
			updateMin(q);
		}
		if (assay.isMaxGroup()) {
			if (getMax() != null) {
				throw new IllegalStateException("Only 1 max_assay assay allowed for now");
			}
			updateMax(q);
		}
	}

	@SuppressWarnings("null")
	private void updateMin(@NonNull Quality q) {
		if (getMin() == null) {
			setMin(q);
		} else {
			setMin(Quality.min(q, getMin()));
		}
	}

	@SuppressWarnings("null")
	private void updateMax(@NonNull Quality q) {
		if (getMax() == null) {
			setMax(q);
		} else {
			setMax(Quality.max(q, getMax()));
		}
	}

	public void add(T assay, @NonNull Quality q) {
		if (assay.isMaxGroup()) {
			throw new IllegalArgumentException();
		}
		Quality previousQ = qualitiesGet(assay);
		if (previousQ == null) {
			qualitiesPut(assay, q);
		} else {
			qualitiesPut(assay, Quality.min(previousQ, q));
		}
		updateMin(q);
	}

	public @Nullable Quality getValue(boolean allowNullMax) {
		if (!allowNullMax && isHasMaxGroup() && getMax() == null) {
			throw new IllegalStateException();
		}
		return Quality.nullableMin(getMax(), getMin());
	}

	public @Nullable Quality getValue() {
		return getValue(false);
	}

	public @NonNull Quality getNonNullValue() {
		Quality v = getValue();
		if (v == null) {
			throw new IllegalStateException();
		}
		return v;
	}

	@SuppressWarnings("null")
	public @NonNull Quality getValueIgnoring(Set<T> assaysToIgnore, boolean allowNullMax) {
		final Handle<Quality> min1 = new Handle<>(Quality.MAXIMUM);
		final Handle<Quality> max1 = new Handle<>(null);
		qualitiesForEach((k, v) -> {
			if (assaysToIgnore.contains(k)) {
				return;
			}
			if (k.isMinGroup()) {
				min1.set(Quality.min(min1.get(), v));
			} else if (k.isMaxGroup()) {
				if (max1.get() == null) {
					max1.set(v);
				} else {
					max1.set(Quality.max(max1.get(), v));
				}
			}//else ignorable assay
		});
		final Quality max1Val = max1.get();
		if ((!allowNullMax) && max1Val == null && isHasMaxGroup()) {
			throw new IllegalStateException();
		}
		return Quality.nullableMin(max1Val, min1.get());
	}

	public Quality getValueIgnoring(Set<T> assaysToIgnore) {
		return getValueIgnoring(assaysToIgnore, false);
	}

	public Quality getQuality(T assay) {
		return qualitiesGet(assay);
	}

	public void forEach(BiConsumer<T, @NonNull Quality> consumer) {
		qualitiesForEach(consumer::accept);
	}

	public void reset() {
		qualitiesClear();
		setMin(null);
		setMax(null);
	}

	public void addAll(DetailedQualities<@NonNull T> q) {
		q.forEach(this::add);
	}

	public void addAllUnique(@NonNull DetailedQualities<T> q) {
		q.forEach(this::addUnique);
	}

	public boolean downgradeUniqueIfFalse(T assay, boolean b) {
		addUnique(assay, b ? Quality.GOOD : Quality.DUBIOUS);
		return b;
	}

	public boolean downgradeIfFalse(T assay, boolean b) {
		add(assay, b ? Quality.GOOD : Quality.DUBIOUS);
		return b;
	}

	protected abstract Quality getMin();

	protected abstract void setMin(Quality min);

	protected abstract Quality getMax();

	protected abstract void setMax(Quality max);

	protected abstract boolean isHasMaxGroup();

}
