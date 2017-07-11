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
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.map.hash.TIntObjectHashMap;

@SuppressWarnings("unchecked")
public class TIntObjectListHashMap<V> extends TIntObjectCollectionHashMap<V> implements Iterable<V> {

	private final TIntObjectHashMap<ArrayList<V>> map = new TIntObjectHashMap<>();

	@Override
	@SuppressWarnings("rawtypes")
	protected TIntObjectHashMap<Collection> getMap() {
		return (TIntObjectHashMap) map;
	}

	@Override
	public @NonNull Collection<V> getCollection(int i) {
		Collection<V> l = getMap().get(i);
		if (l != null) {
			return l;
		} else {
			return Collections.emptyList();
		}
	}

	@Override
	public boolean add(int i, V v) {
		return getMap().computeIfAbsent(i, () -> new ArrayList<V>(100)).add(v);
	}

	@Override
	public boolean forEach(Predicate<? super V> predicate) {
		return map.forEachValue(list -> {
			int size = list.size();
			for (int index = 0; index < size; index++) {
				if (!predicate.test(list.get(index))) {
					return false;
				}
			}
			return true;
		});
	}
}
