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

import contrib.edu.stanford.nlp.util.IntervalTree;
import contrib.net.sf.picard.util.IterableAdapter;

public class DuplexITKeeper extends IntervalTree<Integer, DuplexRead> implements DuplexKeeper  {

	public DuplexITKeeper() {
		throw new RuntimeException();
	}
	
	@Override
	public @NonNull Iterable<DuplexRead> getIterable() {
		return new IterableAdapter<>(iterator());
	}
	
	final List<DuplexRead> overlappingDuplexes = new ArrayList<>(10_000);
	
	@Override
	/**
	 * NOT thread-safe because of overlappingDuplexes reuse (the code
	 * is set up this way to minimize object turnover).
	 */
	public @NonNull Iterable<DuplexRead> getOverlapping(DuplexRead d) {
		overlappingDuplexes.clear();
		getOverlapping(d.getInterval(), overlappingDuplexes);
		return new IterableAdapter<>(overlappingDuplexes.iterator());
	}

	@Override
	public boolean supportsMutableDuplexes() {
		return false;
	}

	@Override
	public DuplexRead getAndRemove(DuplexRead d) {
		return super.getAndRemove(d);
	}

}
