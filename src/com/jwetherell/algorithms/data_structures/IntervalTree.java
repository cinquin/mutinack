package com.jwetherell.algorithms.data_structures;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

	static final Comparator<IntervalData<?>> startComparator = new Comparator<IntervalData<?>>() {

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

	static final Comparator<IntervalData<?>> endReverseComparator = new Comparator<IntervalData<?>>() {

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
		if (intervals.isEmpty())
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
			if (!leftIntervals.isEmpty()) {
				final Interval<T> newLeft = createFromList(leftIntervals, currentDepth + 1);
				newInterval.left = newLeft;
				maxChildDepth = newLeft.initialDepth;
			}
			if (!rightIntervals.isEmpty()) {
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

	public void forEach(Consumer<T> consumer) {
		root.forEach(consumer);
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
			if (!children.isEmpty()) {
				for (int i = 0; i < children.size() - 1; i++) {
					builder.append(getString(children.get(i), prefix + (isTail ? "    " : "│   "), false));
				}
				if (!children.isEmpty()) {
					builder.append(getString(children.get(children.size() - 1), prefix + (isTail ? "    " : "│   "),
							true));
				}
			}

			return builder.toString();
		}
	}

}
