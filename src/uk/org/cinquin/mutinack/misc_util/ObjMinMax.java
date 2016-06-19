package uk.org.cinquin.mutinack.misc_util;

import java.util.Comparator;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

public class ObjMinMax<T> {
	private @Nullable T max = null;
	private @Nullable T min = null;

	private final Comparator<T> comparator;

	public ObjMinMax(Comparator<T> comparator) {
		this.comparator = comparator;
	}

	public ObjMinMax(T defaultMin, T defaultMax, Comparator<T> comparator) {
		max = defaultMax;
		min = defaultMin;
		this.comparator = comparator;
	}

	public ObjMinMax<T> acceptMax(final T t) {
		if (max != null) {
			if (comparator.compare(t, max) > 0) {
				max = t;
			}
		} else {
			max = t;
		}
		return this;
	}

	public ObjMinMax<T> acceptMax(final Iterable<T> col) {
		col.forEach(this::acceptMax);
		return this;
	}

	public ObjMinMax<T> acceptMax(final Iterable<?> col, Function<Object, T> f) {
		col.forEach(o -> acceptMax(f.apply(o)));
		return this;
	}

	public ObjMinMax<T> acceptMin(final T t) {
		if (min != null) {
			if (comparator.compare(t, min) < 0) {
				min = t;
			}
		} else {
			min = t;
		}
		return this;
	}

	public ObjMinMax<T> acceptMin(final Iterable<T> col) {
		col.forEach(this::acceptMin);
		return this;
	}

	public ObjMinMax<T> acceptMinMax(final T t) {
		acceptMin(t);
		acceptMax(t);
		return this;
	}

	public ObjMinMax<T> acceptMinMax(final Iterable<T> col) {
		col.forEach(this::acceptMinMax);
		return this;
	}

	public @Nullable T getMin() {
		return min;
	}

	public @Nullable T getMax() {
		return max;
	}
}
