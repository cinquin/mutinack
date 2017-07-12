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

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.map.hash.TIntObjectHashMap;
import uk.org.cinquin.mutinack.misc_util.IterableAdapter;
import uk.org.cinquin.mutinack.misc_util.SettableInteger;

@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class TIntObjectCollectionHashMap<V> implements Iterable<V> {

	public abstract @NonNull Collection<V> getCollection(int i);
	public abstract boolean add(int i, V v);
	public abstract boolean forEach(Predicate<? super V> predicate);

	protected abstract TIntObjectHashMap<Collection> getMap();

	public int size() {
		SettableInteger i = new SettableInteger(0);
		getMap().forEachValue(l -> {
			i.addAndGet(l.size());
			return true;
		});
		return i.get();
	}

	public boolean isEmpty() {
		return !getMap().forEachValue(l -> {
			return !l.isEmpty();
		});
	}

	public void clear() {
		getMap().clear();
	}

	public boolean containsValue(V v) {
		return !getMap().forEachValue(l -> {
			if (l.contains(v)) {
				return false;
			}
			return true;
		});
	}

	public @NonNull Iterable<V> getIterable() {
		return new IterableAdapter<>(this::iterator);
	}

	@Override
	public Iterator<V> iterator() {
		return new Iterator<V>() {
			final Iterator<Collection> listIt =
					getMap().valueCollection().iterator();

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
		getMap().forEachValue(collection -> {
			collection.forEach(consumer);
			return true;
		});
	}

}
