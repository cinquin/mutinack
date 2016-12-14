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

package uk.org.cinquin.mutinack.misc_util.collections;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.map.hash.TIntObjectHashMap;
import uk.org.cinquin.mutinack.misc_util.IterableAdapter;

public class TIntObjectListHashMap<V> extends TIntObjectHashMap<List<V>> {

	@SuppressWarnings("null")
	public @NonNull List<V> getList(int i) {
		List<V> l = get(i);
		if (l != null) {
			return l;
		} else {
			return Collections.emptyList();
		}
	}

	public boolean add(int i, V v) {
		return putIfAbsent(i, () -> new ArrayList<>(100)).add(v);
	}

	public @NonNull Iterable<V> getIterable() {

		return new IterableAdapter<>(() -> new Iterator<V>() {
			final Iterator<List<V>> listIt =
					TIntObjectListHashMap.this.valueCollection().iterator();

			Iterator<V> it;

			//NB assume that there are no empty lists

			@Override
			public boolean hasNext() {
				return (listIt.hasNext() || (it != null && it.hasNext()));
			}

			@Override
			public V next() {
				while (true) {
					if (it != null) {
						if (it.hasNext()) {
							return it.next();
						}
					}
					it = listIt.next().iterator();
				}
			}

		});
	}
}
