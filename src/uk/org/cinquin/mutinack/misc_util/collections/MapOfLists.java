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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import contrib.net.sf.picard.util.IterableAdapter;

/**
 * NB: Unlike most other classes in this project, this class is not thread safe.
 * @author olivier
 *
 * @param <K>
 * @param <V>
 */
public class MapOfLists<K, V> implements Serializable, Iterable<Entry<K, List<V>>> {

	private static final long serialVersionUID = -7056033474949626637L;
	private final Map<K, List<V>> map = new HashMap<>();
	
	public void addAt(K k, V v) {
		map.computeIfAbsent(k, key -> new ArrayList<>()).add(v);
	}
	
	public void addAt(K k, List<V> list) {
		map.computeIfAbsent(k, key -> new ArrayList<>()).addAll(list);
	}

	public List<V> get(K k) {
		List<V> l = map.get(k);
		if (l == null) {
			l = Collections.emptyList();
		}
		return l;
	}

	public Set<K> keySet() {
		return map.keySet();
	}
	
	public Set<Entry<K, List<V>>> entrySet() {
		return map.entrySet();
	}

	public Collection<List<V>> values() {
		return map.values();
	}

	//Return value is an approximation
	public boolean isEmpty() {
		return map.isEmpty();
	}

	public void addAll(MapOfLists<K, V> other) {
		other.map.forEach(this::addAt);
	}

	public void clear() {
		map.clear();
	}

	@Override
	public Iterator<Entry<K, List<V>>> iterator() {
		return map.entrySet().iterator();
	}

	public Iterable<K> keys() {
		return new IterableAdapter<>(map.keySet().iterator());
	}

	@Override
	public String toString() {
		return map.toString();
	}

}
