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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNull;

import contrib.edu.stanford.nlp.util.Interval;
import contrib.edu.stanford.nlp.util.IntervalTree;
import contrib.net.sf.picard.util.IterableAdapter;

public class DuplexITKeeper extends IntervalTree<Integer, DuplexRead> implements DuplexKeeper  {

	public DuplexITKeeper() {
		throw new RuntimeException();
	}

	public @NonNull Iterable<DuplexRead> getIterable() {
		return new IterableAdapter<>(iterator());
	}

	@Override
	public void forEach(Consumer<? super DuplexRead> consumer) {
		Iterator<DuplexRead> it = iterator();
		while (it.hasNext()) {
			consumer.accept(it.next());
		}
	}

	private final @NonNull List<DuplexRead> overlappingDuplexes = new ArrayList<>(10_000);
	@Override
	/**
	 * NOT thread-safe because of overlappingDuplexes reuse (the code
	 * is set up this way to minimize object turnover).
	 */
	public @NonNull List<DuplexRead> getOverlapping(DuplexRead d) {
		overlappingDuplexes.clear();
		getOverlapping(d.getInterval(), overlappingDuplexes);
		return overlappingDuplexes;
	}

	@Override
	public @NonNull List<DuplexRead> getOverlappingWithSlop(DuplexRead d, int shift, int slop) {
		List<DuplexRead> result = new ArrayList<>();
		getOverlapping(d.getIntervalWithSlop(shift, slop), result);
		return result;
	}


	private final List<DuplexRead> duplexesAtPosition0 = new ArrayList<>(10_000);
	private final List<DuplexRead> duplexesAtPosition1 = new ArrayList<>(10_000);

	/**
	 * NOT thread-safe because of overlappingDuplexes reuse (the code
	 * is set up this way to minimize object turnover).
	 */
	public @NonNull Iterable<DuplexRead> getStartingAtPosition(int position) {
		duplexesAtPosition0.clear();
		duplexesAtPosition1.clear();
		getOverlapping(Interval.toInterval(position, position), duplexesAtPosition0);
		for (DuplexRead dr: duplexesAtPosition0) {
			if (dr.leftAlignmentStart.position == position) {
				duplexesAtPosition1.add(dr);
			}
		}
		return new IterableAdapter<>(duplexesAtPosition1.iterator());
	}


	public static boolean supportsMutableDuplexes() {
		return false;
	}

	public DuplexRead getAndRemove(DuplexRead d) {
		return super.getAndRemove(d);
	}

}
