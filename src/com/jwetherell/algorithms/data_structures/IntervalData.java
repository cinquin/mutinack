package com.jwetherell.algorithms.data_structures;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.set.hash.THashSet;

/**
 * Data structure representing an interval.
 */
public final class IntervalData<T> implements Comparable<IntervalData<T>>, Serializable {

	private static final long serialVersionUID = -6998782952815928057L;
	long start = Long.MIN_VALUE;
	long end = Long.MAX_VALUE;
	final @NonNull THashSet<T> set;
	private static final @NonNull THashSet<Object> emptyTHashSet = new EmptyTHashSet();

	@SuppressWarnings({ "rawtypes" })
	public static final IntervalData EMPTY = new IntervalData<>(emptyTHashSet, Long.MIN_VALUE, Long.MIN_VALUE);

	/**
	 * Interval data using object as its unique identifier
	 *
	 * @param object
	 *            Object which defines the interval data
	 */
	public IntervalData(long index, T object) {
		this.start = index;
		this.end = index;
		set = new THashSet<>();
		this.set.add(object);
	}

	/**
	 *
	 * @param keepGoingPredicate
	 * @return True if keep going (NOT if found an object)
	 */
	public boolean forEach(Predicate<T> keepGoingPredicate) {
		return set.forEach((T arg0) -> keepGoingPredicate.test(arg0));
	}

	/**
	 * Interval data using object as its unique identifier
	 *
	 * @param object
	 *            Object which defines the interval data
	 */
	public IntervalData(long start, long end, T object) {
		this.start = start;
		this.end = end;
		set = new THashSet<>();
		this.set.add(object);
	}

	/**
	 * Interval data list which should all be unique
	 *
	 * @param list
	 *            of interval data objects
	 */
	public IntervalData(long start, long end, @NonNull Set<T> set) {
		this.start = start;
		this.end = end;
		this.set = new THashSet<>(set);
	}

	private IntervalData(@NonNull THashSet<T> uncopiedSet, long start, long end) {
		this.start = start;
		this.end = end;
		this.set = uncopiedSet;
	}

	/**
	 * Get the start of this interval
	 *
	 * @return Start of interval
	 */
	public long getStart() {
		return start;
	}

	/**
	 * Get the end of this interval
	 *
	 * @return End of interval
	 */
	public long getEnd() {
		return end;
	}

	/**
	 * Get the data set in this interval
	 *
	 * @return Unmodifiable collection of data objects
	 */
	public @NonNull Set<T> getData() {
		return Collections.unmodifiableSet(this.set);
	}

	/**
	 * Do not modify the returned set or bad things will happen.
	 * This method is provided to save the creation of an extra object
	 * for each query.
	 * @return
	 */
	public @NonNull Set<T> getUnprotectedData() {
		return set;
	}

	/**
	 * Clear the indices.
	 */
	public void clear() {
		this.start = Long.MIN_VALUE;
		this.end = Long.MAX_VALUE;
		this.set.clear();
	}

	/**
	 * Combine this IntervalData with data.
	 *
	 * @param data to combine with.
	 * @return Data which represents the combination.
	 */
	public IntervalData<T> add(IntervalData<T> data) {
		if (data.start < this.start)
			this.start = data.start;
		if (data.end > this.end)
			this.end = data.end;
		this.set.addAll(data.set);
		return this;
	}

	/**
	 * Deep copy of data.
	 *
	 * @return deep copy.
	 */
	public IntervalData<T> copy() {
		return new IntervalData<>(start, end, set);
	}

	/**
	 * Query inside this data object.
	 *
	 * @param start
	 *            of range to query for.
	 * @param end
	 *            of range to query for.
	 * @return Data queried for or NULL if it doesn't match the query.
	 */
	public IntervalData<T> query(long index) {
		if (index < this.start || index > this.end) {
			return null;
		} else {
			return copy();
		}
	}

	public boolean hasIndex(long index) {
		return index >= this.start && index <= this.end;
	}

	public boolean hasInterval(long start1, long end1) {
		return end1 >= this.start && start1 <= this.end;
	}

	/**
	 * Query inside this data object.
	 *
	 * @param start
	 *            of range to query for.
	 * @param end
	 *            of range to query for.
	 * @return Data queried for or NULL if it doesn't match the query.
	 */
	public IntervalData<T> query(@SuppressWarnings("hiding") long start,
			@SuppressWarnings("hiding") long end) {
		if (end < this.start || start > this.end) {
			return null;
		} else {
			return copy();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int compareTo(IntervalData<T> d) {
		if (this.end < d.end)
			return -1;
		if (d.end < this.end)
			return 1;
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(start).append("->").append(end);
		builder.append(" set=").append(set);
		return builder.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (end ^ (end >>> 32));
		result = prime * result + set.hashCode();
		result = prime * result + (int) (start ^ (start >>> 32));
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof IntervalData))
			return false;
		IntervalData<?> data = (IntervalData<?>) obj;
		if (this.start == data.start && this.end == data.end) {
			if (this.set.size() != data.set.size()) {
				return false;
			}
			return this.set.equals(data.set);
		}
		return false;
	}
}