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
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.collections.TIntSortedSetHashMap;

public class DuplexHashMapKeeper extends TIntSortedSetHashMap<Duplex> implements DuplexKeeper {

	@Override
	public @NonNull SortedSet<Duplex> getOverlapping(Duplex d) {
		return getCollection(d.leftAlignmentStart.position);
	}

	@Override
	public @NonNull List<Duplex> getOverlappingWithSlop(Duplex d, int shift, int slop) {
		List<Duplex> result = new ArrayList<>();
		for (int i = d.leftAlignmentStart.position + shift - slop; i <=  d.leftAlignmentStart.position + shift + slop; i++) {
			getCollection(i).forEach(d2 -> {
				if (Math.abs(d2.rightAlignmentEnd.position - d.rightAlignmentEnd.position) <= slop) {
					result.add(d2);
				}
			});
			result.addAll(getCollection(i));
		}
		return result;
	}

	public @NonNull Iterable<Duplex> getStartingAtPosition(int position) {
		return getCollection(position);
	}

	@Override
	public boolean add(Duplex d) {
		return add(d.leftAlignmentStart.position, d);
	}

	@SuppressWarnings("static-method")
	public Duplex getAndRemove(Duplex d) {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("static-method")
	public boolean supportsMutableDuplexes() {
		return true;
	}

	public boolean contains(Duplex duplex) {
		return containsValue(duplex);
	}

	@Override
	public boolean contains(Object o) {
		return containsValue((Duplex) o);
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<? extends Duplex> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

}
