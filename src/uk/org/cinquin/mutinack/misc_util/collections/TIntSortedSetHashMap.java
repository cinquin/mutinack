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
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNull;

import contrib.jdk.collections.TreeSetWithForEach;
import gnu.trove.map.hash.TIntObjectHashMap;
import uk.org.cinquin.mutinack.Duplex;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

@SuppressWarnings("unchecked")
public class TIntSortedSetHashMap<V> extends TIntObjectCollectionHashMap<V> implements Iterable<V> {

	private final TIntObjectHashMap<TreeSetWithForEach<V>> map = new TIntObjectHashMap<>();

	@Override
	@SuppressWarnings("rawtypes")
	protected TIntObjectHashMap<Collection> getMap() {
		return (TIntObjectHashMap) map;
	}

	@SuppressWarnings("rawtypes")
	public static final @NonNull TreeSetWithForEach emptyTreeSet = new TreeSetWithForEach<>();

	@Override
	public @NonNull TreeSetWithForEach<V> getCollection(int i) {
		TreeSetWithForEach<V> l = map.get(i);
		if (l != null) {
			return l;
		} else {
			if (!emptyTreeSet.isEmpty()) {
				throw new AssertionFailedException();
			}
			return emptyTreeSet;
		}
	}

	@Override
	public boolean add(int i, V v) {
		return getMap().computeIfAbsent(i, () -> new TreeSetWithForEach<>(
			Duplex.duplexCountQualComparator)).add(v);
	}

	@Override
	public void forEach(Consumer<? super V> consumer) {
		getMap().forEachValue(collection -> {
			collection.forEach(consumer);
			return true;
		});
	}

	@Override
	public boolean forEach(Predicate<? super V> consumer) {
		return map.forEachValue(collection -> {
			return collection.forEach(consumer);
		});
	}
}
