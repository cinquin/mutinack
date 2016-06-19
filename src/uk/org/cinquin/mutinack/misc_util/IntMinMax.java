package uk.org.cinquin.mutinack.misc_util;

import java.util.function.ToIntFunction;

import org.eclipse.jdt.annotation.Nullable;

public class IntMinMax<T> {
	private int max = Integer.MIN_VALUE;
	private int defaultMax = Integer.MIN_VALUE;
	private @Nullable T keyMax = null;
	private int min = Integer.MAX_VALUE;
	private int defaultMin = Integer.MAX_VALUE;
	private @Nullable T keyMin = null;

	public IntMinMax<T> defaultMin(int newDefaultMin) {
		this.defaultMin = newDefaultMin;
		return this;
	}

	public IntMinMax<T> defaultMax(int newDefaultMax) {
		this.defaultMax = newDefaultMax;
		return this;
	}

	public IntMinMax<T> acceptMax(final int i, T key) {
		if (i > max) {
			max = i;
			keyMax = key;
		}
		return this;
	}

	public IntMinMax<T> acceptMin(final int i, T key) {
		if (i < min) {
			min = i;
			keyMin = key;
		}
		return this;
	}

	public IntMinMax<T> acceptMinMax(final int i, T key) {
		acceptMin(i, key);
		acceptMax(i, key);
		return this;
	}

	public IntMinMax<T> acceptMax(final Iterable<T> col, ToIntFunction<Object> f) {
		col.forEach(t -> acceptMax(f.applyAsInt(t), t));
		return this;
	}

	public IntMinMax<T> acceptMin(final Iterable<T> col, ToIntFunction<Object> f) {
		col.forEach(t -> acceptMin(f.applyAsInt(t), t));
		return this;
	}

	public IntMinMax<T> acceptMinMax(final Iterable<T> col, ToIntFunction<Object> f) {
		col.forEach(t -> acceptMinMax(f.applyAsInt(t), t));
		return this;
	}

	public int getMin() {
		return min == Integer.MAX_VALUE ? defaultMin : min;
	}

	public @Nullable T getKeyMin() {
		return keyMin;
	}

	public int getMax() {
		return max == Integer.MIN_VALUE ? defaultMax : max;
	}

	public @Nullable T getKeyMax() {
		return keyMax;
	}

}
