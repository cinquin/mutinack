package com.jwetherell.algorithms.data_structures;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class Interval<T> implements Serializable {

	private static final long serialVersionUID = 7342284329246513614L;

	public Interval() {
	}

	long center = Long.MIN_VALUE;
	int initialDepth;
	Interval<T> left = null;
	Interval<T> right = null;
	private final List<IntervalData<T>> overlap = new ArrayList<>(), // startComparator
			overlapEnd = new ArrayList<>();

	void add(IntervalData<T> data) {
		overlap.add(data);
		Collections.sort(overlap, IntervalTree.startComparator);
		overlapEnd.add(data);
		Collections.sort(overlapEnd, IntervalTree.endReverseComparator);
	}

	/**
	 * Stabbing query; WARNING: do not modify returned object
	 *
	 * @param index to query for.
	 * @return data at index.
	 */
	@SuppressWarnings({ "unchecked" })
	public IntervalData<T> query(long index) {
		IntervalData<T> results = null;
		Interval<T> i = this;
		boolean mustCopy = false;
		while (i != null) {
			if (index < i.center) {
				// overlap is sorted by start point
				for (IntervalData<T> data : i.overlap) {
					if (data.start > index)
						break;

					if (data.hasIndex(index)) {
						if (results == null) {
							results = data;
							mustCopy = true;
						} else {
							if (mustCopy) {
								mustCopy = false;
								results = data.query(index).add(results);
							} else {
								results.add(data);
							}
						}
					}
				}
			} else if (index >= i.center) {
				// overlapEnd is sorted by end point
				for (IntervalData<T> data : i.overlapEnd) {
					if (data.end < index)
						break;

					if (data.hasIndex(index)) {
						if (results == null) {
							results = data;
							mustCopy = true;
						} else {
							if (mustCopy) {
								mustCopy = false;
								results = data.query(index).add(results);
							} else {
								results.add(data);
							}
						}
					}
				}
			}
			if (index < i.center) {
				i = i.left;
			} else {
				i = i.right;
			}
		}
		return results == null ? IntervalData.EMPTY : results;
	}

	public boolean contains(long index) {
		return forEach(index, i -> false);
	}

	public void forEach(Consumer<T> consumer) {
		overlap.forEach(id -> id.set.forEach(consumer));
		if (left != null) {
			left.forEach(consumer);
		}
		if (right != null) {
			right.forEach(consumer);
		}
	}

	 /** Stabbing query
	 *
	 * @param index to query for
	 * @param keepGoingPredicate
	 * @return true if at least one element was found
	 */
	public boolean forEach(long index, Predicate<T> keepGoingPredicate) {
		Interval<T> i = this;
		boolean foundOne = false;
		while (i != null) {
			if (index < i.center) {
				// overlap is sorted by start point
				final List<IntervalData<T>> overlapCopy = i.overlap;
				final int size = overlapCopy.size();
				for (int dataIndex = 0; dataIndex < size; dataIndex++) {
					final IntervalData<T> data = overlapCopy.get(dataIndex);
					if (data.start > index)
						break;

					if (data.hasIndex(index)) {
						foundOne = !data.set.isEmpty();
						if (!data.forEach(keepGoingPredicate)) {
							return foundOne;
						}
					}
				}
			} else if (index >= i.center) {
				// overlapEnd is sorted by end point
				final List<IntervalData<T>> overlapCopy = i.overlapEnd;
				final int size = overlapCopy.size();
				for (int dataIndex = 0; dataIndex < size; dataIndex++) {
					final IntervalData<T> data = overlapCopy.get(dataIndex);
					if (data.end < index)
						break;

					if (data.hasIndex(index)) {
						foundOne = !data.set.isEmpty();
						if (!data.forEach(keepGoingPredicate)) {
							return foundOne;
						}
					}
				}
			}
			if (index < i.center) {
				i = i.left;
			} else {
				i = i.right;
			}
		}
		return foundOne;
	}

	/**
	 * Range query
	 *
	 * @param start
	 *            of range to query for.
	 * @param end
	 *            of range to query for.
	 * @return data for range.
	 */
	@SuppressWarnings({"unchecked"})
	public IntervalData<T> query(long start, long end) {
		IntervalData<T> results = null;
		Deque<Interval<T>> toExplore = new ArrayDeque<>();
		toExplore.add(this);
		Interval<T> i;
		boolean mustCopy = false;
		while ((i = toExplore.poll()) != null) {
			for (IntervalData<T> data : i.overlap) {
				if (data.start > end)
					break;
				if (!data.hasInterval(start, end)) {
					continue;
				}
				if (results == null) {
					results = data;
					mustCopy = true;
				} else if (!mustCopy) {
					results.add(data);
				} else {
					results = data.query(start, end).add(results);
				}
			}
			if (i.left != null && start < i.center) {
				toExplore.add(i.left);
			}
			if (i.right != null && end >= i.center) {
				toExplore.add(i.right);
			}
		}
		return results != null ? results : IntervalData.EMPTY;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Center=").append(center);
		builder.append(" Set=").append(overlap);
		return builder.toString();
	}
}