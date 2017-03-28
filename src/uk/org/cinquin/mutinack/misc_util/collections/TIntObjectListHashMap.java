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
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.map.hash.TIntObjectHashMap;
import uk.org.cinquin.mutinack.misc_util.Handle;
import uk.org.cinquin.mutinack.misc_util.IterableAdapter;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;

public class TIntObjectListHashMap<V> implements Iterable<V> {

	private final TIntObjectHashMap<List<V>> map = new TIntObjectHashMap<>();

	public @NonNull List<V> getList(int i) {
		List<V> l = map.get(i);
		if (l != null) {
			return l;
		} else {
			return Collections.emptyList();
		}
	}

	public int size() {
		SettableInteger i = new SettableInteger(0);
		map.forEachValue(l -> {
			i.addAndGet(l.size());
			return true;
		});
		return i.get();
	}

	public boolean containsValue(V v) {
		Handle<Boolean> found = new Handle<>(false);
		map.forEachValue(l -> {
			if (l.contains(v)) {
				found.set(true);
				return false;
			}
			return true;
		});
		return found.get();
	}

	public boolean add(int i, V v) {
		return map.computeIfAbsent(i, () -> new ArrayList<>(100)).add(v);
	}

	public @NonNull Iterable<V> getIterable() {
		return new IterableAdapter<>(this::iterator);
	}

	@Override
	public Iterator<V> iterator() {
		return new Iterator<V>() {
			final Iterator<List<V>> listIt =
					map.valueCollection().iterator();

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
		};
	}

	@Override
	public void forEach(Consumer<? super V> consumer) {
		map.forEachValue(list -> {
			list.forEach(consumer);
			return true;
		});
	}
}
