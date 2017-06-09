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
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.picard.util.IterableAdapter;

public class DuplexArrayListKeeper extends ArrayList<DuplexRead> implements DuplexKeeper {

	private static final long serialVersionUID = 4777255573227700343L;

	public DuplexArrayListKeeper(int initialCapacity) {
		super(initialCapacity);
	}

	@Override
	public @NonNull List<DuplexRead> getOverlapping(DuplexRead d) {
		return this;
	}

	@Override
	public @NonNull List<DuplexRead> getOverlappingWithSlop(DuplexRead d, int shift, int slop) {
		return this;
	}

	private final transient List<DuplexRead> duplexesAtPosition = new ArrayList<>(10_000);
	@Override
	/**
	 * NOT thread-safe because of overlappingDuplexes reuse (the code
	 * is set up this way to minimize object turnover).
	 */
	public @NonNull Iterable<DuplexRead> getStartingAtPosition(int position) {
		duplexesAtPosition.clear();
		for (DuplexRead dr: this) {
			if (dr.leftAlignmentStart.position == position) {
				duplexesAtPosition.add(dr);
			}
		}
		return new IterableAdapter<>(duplexesAtPosition.iterator());
	}

	@Override
	public @NonNull Iterable<DuplexRead> getIterable() {
		return this;
	}

	@Override
	public DuplexRead getAndRemove(DuplexRead d) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean supportsMutableDuplexes() {
		return true;
	}

	@Override
	public boolean contains(DuplexRead duplexRead) {
		return super.contains(duplexRead);
	}

}
