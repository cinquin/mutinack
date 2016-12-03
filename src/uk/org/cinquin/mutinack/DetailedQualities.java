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
package uk.org.cinquin.mutinack;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.candidate_sequences.AssayInfo;
import uk.org.cinquin.mutinack.candidate_sequences.DuplexAssay;
import uk.org.cinquin.mutinack.candidate_sequences.PositionAssay;
import uk.org.cinquin.mutinack.candidate_sequences.Quality;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.collections.CustomEnumMap;

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
public class DetailedQualities<T extends Enum<T> & AssayInfo> implements Serializable {
	private static final long serialVersionUID = -5423960175598403757L;

	private final @NonNull Map<T, @NonNull Quality> qualities;
	private @Nullable Quality min = null, max = null;

	/**
	 * Currently only used for assertions
	 */
	private final boolean hasMaxGroup;

	//Not a ConcurrentMap because of the high runtime cost (even after the map has been
	//filled), according to sampling
	private final static Map<Class<?>, Method> methodMap = new HashMap<>();

	private static <T> boolean getHasMaxGroup(Class<T> assayClass) {
		try {
			return (boolean)
				methodMap.computeIfAbsent(assayClass, clazz -> {
					try {
						return clazz.getMethod("hasMaxGroup");
					} catch (NoSuchMethodException | SecurityException e) {
						throw new RuntimeException(e);
					}
				}).invoke(null);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}
	
	static {
		getHasMaxGroup(DuplexAssay.class);
		getHasMaxGroup(PositionAssay.class);
	}
	
	public DetailedQualities(Class<T> assayClass) {
		qualities = new CustomEnumMap<>(assayClass);
		hasMaxGroup = getHasMaxGroup(assayClass);
	}
	
	@Override
	public String toString() {
		return qualities.toString();
	}
	
	public Stream<Entry<T, Quality>> getQualities() {
		return qualities.entrySet().stream();
	}

	public boolean qualitiesContain(T a) {
		return qualities.containsKey(a);
	}

	public void addUnique(T assay, @NonNull Quality q) {
		if (qualities.put(assay, q) != null) {
			throw new IllegalStateException(assay + " already defined");
		}
		if (assay.isMinGroup()) {
			updateMin(q);
		}
		if (assay.isMaxGroup()) {
			if (max != null) {
				throw new IllegalStateException("Only 1 max_assay assay allowed for now");
			}
			updateMax(q);
		}
	}
	
	@SuppressWarnings("null")
	private void updateMin(@NonNull Quality q) {
		if (min == null) {
			min = q;
		} else {
			min = Quality.min(q, min);
		}
	}

	@SuppressWarnings("null")
	private void updateMax(@NonNull Quality q) {
		if (max == null) {
			max = q;
		} else {
			max = Quality.max(q, max);
		}
	}

	public void add(T assay, @NonNull Quality q) {
		if (assay.isMaxGroup()) {
			throw new IllegalArgumentException();
		}
		Quality previousQ = qualities.get(assay);
		if (previousQ == null) {
			qualities.put(assay, q);
		} else {
			qualities.put(assay, Quality.min(previousQ, q));
		}
		updateMin(q);
	}
	
	public Quality getValue(boolean allowNullMax) {
		if (!allowNullMax && hasMaxGroup && max == null) {
			throw new IllegalStateException();
		}
		return Quality.nullableMin(max, min);
	}

	public Quality getValue() {
		return getValue(false);
	}
	
	@SuppressWarnings("null")
	public Quality getValueIgnoring(Set<T> assaysToIgnore, boolean allowNullMax) {
		final Handle<@NonNull Quality> min1 = new Handle<>(Quality.MAXIMUM);
		final Handle<@Nullable Quality> max1 = new Handle<>(null);
		qualities.forEach((k, v) -> {
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
		if ((!allowNullMax) && max1Val == null && hasMaxGroup) {
			throw new IllegalStateException();
		}
		return Quality.nullableMin(max1Val, min1.get());
	}
	
	public Quality getValueIgnoring(Set<T> assaysToIgnore) {
		return getValueIgnoring(assaysToIgnore, false);
	}

	public Quality getQuality(T assay) {
		return qualities.get(assay);
	}

	public void forEach(BiConsumer<T, @NonNull Quality> consumer) {
		qualities.forEach(consumer::accept);
	}

	public void reset() {
		qualities.clear();
		min = null;
		max = null;
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

}
