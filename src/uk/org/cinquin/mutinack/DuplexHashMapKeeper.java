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

import uk.org.cinquin.mutinack.misc_util.collections.TIntObjectListHashMap;

public class DuplexHashMapKeeper extends TIntObjectListHashMap<DuplexRead> implements DuplexKeeper {

	@Override
	public @NonNull List<DuplexRead> getOverlapping(DuplexRead d) {
		return getList(d.position0);
	}

	@Override
	public @NonNull List<DuplexRead> getOverlappingWithSlop(DuplexRead d, int shift, int slop) {
		List<DuplexRead> result = new ArrayList<>();
		for (int i = d.position0 + shift - slop; i <=  d.position0 + shift + slop; i++) {
			getList(i).forEach(d2 -> {
				if (Math.abs(d2.position3 - d.position3) <= slop) {
					result.add(d2);
				}
			});
			result.addAll(getList(i));
		}
		return result;
	}

	@Override
	public @NonNull Iterable<DuplexRead> getStartingAtPosition(int position) {
		return getList(position);
	}

	@Override
	public boolean add(DuplexRead d) {
		return add(d.position0, d);
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
		return containsValue(duplexRead);
	}

}
