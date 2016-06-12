package com.jwetherell.algorithms.data_structures;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.procedure.TObjectProcedure;
import gnu.trove.set.hash.THashSet;

/**
 * An interval tree is an ordered tree data structure to hold intervals.
 * Specifically, it allows one to efficiently find all intervals that overlap
 * with any given interval or point.
 *
 * http://en.wikipedia.org/wiki/Interval_tree
 *
 * @author @copyright Justin Wetherell <phishman3579@gmail.com>
 * Apache license v 2.0
 *
 * Changes by Olivier Cinquin <olivier.cinquin@uci.edu> 2014-2015, to fix a couple
 * bugs and improve performance, in part by minimizing object creation as a
 * result of query processing. See also extended tests in
 * {@link uk.org.cinquin.mutinack.features.tests.GenomeIntervalTest}.
 */
public class IntervalTree<T> implements Serializable {

	private static final long serialVersionUID = -7636548850894064194L;

	private final Interval<T> root;

	private static final Comparator<IntervalData<?>> startComparator = new Comparator<IntervalData<?>>() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int compare(IntervalData<?> arg0, IntervalData<?> arg1) {
			// Compare start only
			if (arg0.start < arg1.start)
				return -1;
			if (arg1.start < arg0.start)
				return 1;
			return 0;
		}
	};

	private static final Comparator<IntervalData<?>> endReverseComparator = new Comparator<IntervalData<?>>() {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int compare(IntervalData<?> arg0, IntervalData<?> arg1) {
			// Compare end only
			if (arg0.end < arg1.end)
				return 1;
			if (arg1.end < arg0.end)
				return -1;
			return 0;
		}
	};

	/**
	 * Create interval tree from list of IntervalData objects;
	 *
	 * @param intervals
	 *            is a list of IntervalData objects
	 */
	public IntervalTree(List<IntervalData<T>> intervals) {
		if (intervals.size() == 0)
			root = new Interval<>();
		else {
			List<IntervalData<T>> sortedList = new ArrayList<>(intervals);
			sortedList.sort((i1, i2) -> Long.compare(i1.end + i1.start, i2.end + i2.start));
			root = createFromList(sortedList, 0);
		}
	}

	protected static <T> Interval<T> createFromList(List<IntervalData<T>> intervals,
			int currentDepth) {
		Interval<T> newInterval = new Interval<>();
		if (intervals.size() == 1) {
			newInterval.initialDepth = 0;
			IntervalData<T> middle = intervals.get(0);
			newInterval.center = ((middle.start + middle.end) / 2);
			newInterval.add(middle);
		} else {
			newInterval.center = (	intervals.get(0).start +
									intervals.get(intervals.size() - 1).end)
								 / 2;
			List<IntervalData<T>> leftIntervals = new ArrayList<>();
			List<IntervalData<T>> rightIntervals = new ArrayList<>();
			for (IntervalData<T> interval : intervals) {
				if (interval.end < newInterval.center) {
					leftIntervals.add(interval);
				} else if (interval.start > newInterval.center) {
					rightIntervals.add(interval);
				} else {
					newInterval.add(interval);
				}
			}
			int maxChildDepth = 0;
			if (leftIntervals.size() > 0) {
				final Interval<T> newLeft = createFromList(leftIntervals, currentDepth + 1);
				newInterval.left = newLeft;
				maxChildDepth = newLeft.initialDepth;
			}
			if (rightIntervals.size() > 0) {
				final Interval<T> newRight = createFromList(rightIntervals, currentDepth + 1);
				newInterval.right = newRight;
				maxChildDepth = Math.max(maxChildDepth, newRight.initialDepth);
			}
			newInterval.initialDepth = maxChildDepth + 1;
		}
		return newInterval;
	}

	/**
	 * Stabbing query
	 *
	 * @param index
	 *            to query for.
	 * @return data at index.
	 */
	public IntervalData<T> query(long index) {
		return root.query(index);
	}

	public boolean contains(long position) {
		return root.contains(position);
	}

	public boolean forEach(long index, Predicate<T> keepGoingPredicate) {
		return root.forEach(index, keepGoingPredicate);
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
	public IntervalData<T> query(long start, long end) {
		return root.query(start, end);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(IntervalTreePrinter.getString(this));
		return builder.toString();
	}

	protected static class IntervalTreePrinter {

		public static <T> String getString(IntervalTree<T> tree) {
			if (tree.root == null)
				return "Tree has no nodes.";
			return getString(tree.root, "", true);
		}

		private static <T> String getString(Interval<T> interval, String prefix, boolean isTail) {
			StringBuilder builder = new StringBuilder();

			builder.append(prefix + (isTail ? "└── " : "├── ") + interval.toString() + "\n");
			List<Interval<T>> children = new ArrayList<>();
			if (interval.left != null)
				children.add(interval.left);
			if (interval.right != null)
				children.add(interval.right);
			if (children.size() > 0) {
				for (int i = 0; i < children.size() - 1; i++) {
					builder.append(getString(children.get(i), prefix + (isTail ? "    " : "│   "), false));
				}
				if (children.size() > 0) {
					builder.append(getString(children.get(children.size() - 1), prefix + (isTail ? "    " : "│   "),
							true));
				}
			}

			return builder.toString();
		}
	}

	public static final class Interval<T> implements Serializable {

		private static final long serialVersionUID = 7342284329246513614L;

		public Interval() {
		}

		private long center = Long.MIN_VALUE;
		private int initialDepth;
		private Interval<T> left = null;
		private Interval<T> right = null;
		private final List<IntervalData<T>> overlap = new ArrayList<>(), // startComparator
				overlapEnd = new ArrayList<>();

		private void add(IntervalData<T> data) {
			overlap.add(data);
			Collections.sort(overlap, startComparator);
			overlapEnd.add(data);
			Collections.sort(overlapEnd, endReverseComparator);
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
			return results == null ? IntervalTree.IntervalData.EMPTY : results;
		}

		public boolean contains(long index) {
			return forEach(index, i -> false);
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
			return results != null ? results : IntervalTree.IntervalData.EMPTY;
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

	/**
	 * Data structure representing an interval.
	 */
	@SuppressWarnings("unchecked")
	public static final class IntervalData<T> implements Comparable<IntervalData<T>>, Serializable {

		private static final long serialVersionUID = -6998782952815928057L;
		private long start = Long.MIN_VALUE;
		private long end = Long.MAX_VALUE;
		private final @NonNull THashSet<T> set;
		@SuppressWarnings("rawtypes")
		private final static THashSet emptyTHashSet = new THashSet() {
			@Override
			public boolean add(Object obj) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean addAll(Collection collection) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean remove(Object obj) {
				throw new UnsupportedOperationException();
			};

			@Override
			public boolean removeAll(Collection collection) {
				throw new UnsupportedOperationException();
			};

			@Override
			public Object removeAndGet(Object arg0) {
				throw new UnsupportedOperationException();
			};
		};

		@SuppressWarnings({ "rawtypes", "null" })
		public final static IntervalData EMPTY = new IntervalData (emptyTHashSet, Long.MIN_VALUE, Long.MIN_VALUE);

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
			return set.forEach(new TObjectProcedure<T>() {
				@Override
				public boolean execute(T arg0) {
					return keepGoingPredicate.test(arg0);
				}
			});
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
		@SuppressWarnings("null")
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
			IntervalData<T> data = (IntervalData<T>) obj;
			if (this.start == data.start && this.end == data.end) {
				if (this.set.size() != data.set.size()) {
					return false;
				}
				return this.set.equals(data.set);
			}
			return false;
		}
	}

}
