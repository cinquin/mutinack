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

import java.io.Closeable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.misc_util.Pair;

/**
 * This class is used to increase performance of non-concurrent maps that have a slow put method (e.g.
 * because they serialize the objects stored in the map), by transparently parallelizing across a
 * number of internal sub-maps that are not exposed to users. Note that this class breaks the Map
 * contract; that could be relatively easily addressed, but the class is good enough as such for our
 * purposes. 
 * @author olivier
 *
 * @param <K>
 * @param <V>
 */
public final class MapBalancer<@NonNull K, @Nullable V> implements Map<K, V>, Closeable {
		
	private volatile RuntimeException storageException = null;
	private volatile boolean terminated = false;
	private final List<Thread> threads = new ArrayList<>();
	private final List<Map<K, V>> maps;
	private final List<LinkedBlockingQueue<Pair<K,V>>> mapPutQueues;
	private final int nMaps;
	
	public MapBalancer(int nMaps, int initialCapacity, Supplier<Map<K, V>> factory) {
		if ((nMaps & (nMaps - 1)) != 0) {
			throw new IllegalArgumentException("nMaps not a power of 2");
		}
		this.nMaps = nMaps;
		maps = new ArrayList<>(nMaps);
		mapPutQueues = new ArrayList<>(nMaps);
		for (int i = 0; i < nMaps; i++) {
			final Map<K, V> map = factory.get(); 
			maps.add(map);
			final LinkedBlockingQueue<Pair<K,V>> queue = new LinkedBlockingQueue<>(10);
			mapPutQueues.add(queue);
			Runnable r = () -> 
			{
				Pair<K,V> entry = null;
				while (true) {
					try {
						entry = queue.poll(Long.MAX_VALUE, TimeUnit.DAYS);
						map.put(entry.fst, entry.snd);
					} catch (InterruptedException ei) {
						break;
					} catch (Throwable e0) {
						storageException = new RuntimeException("Exception while storing " + (entry != null ?
								(entry.fst + "=" + entry.snd) : ""), e0);
						close();
						throw storageException;
					}
				}
			};
			Thread t = new Thread(r, "Map " + i);
			threads.add(t);
			t.start();
		}
	}
	
	@Override
	/**
	 * May not put anything new into the Map after close has been called, but already-present
	 * values can be read. Call this to release writer threads.
	 */
	public void close() {
		terminated = true;
		for (Thread t: threads) {
			t.interrupt();
		}
	}
	
	@Override
	public int size() {
		int sum = 0;
		for (Map<K, V> map: maps) {
			sum += map.size();
		}
		return sum;
	}

	@Override
	public boolean isEmpty() {
		for (Map<K, V> map: maps) {
			if (!map.isEmpty())
				return false;
		}
		return true;
	}

	@Override
	public boolean containsKey(Object key) {
		return maps.get(key.hashCode() & (nMaps-1)).containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		for (Map<K, V> map: maps) {
			if (map.containsValue(value))
				return true;
		}
		return false;
	}

	@Override
	public V get(Object key) {
		if (storageException != null) {
			throw new RuntimeException(storageException);
		}
		return maps.get(key.hashCode() & (nMaps-1)).get(key);
	}

	@Override
	/**
	 * Breaks Map contract: does not return already-present value.
	 */
	public V put(K key, @Nullable V value) {
		if (terminated) {
			throw new IllegalStateException();
		}
		if (storageException != null) {
			throw new RuntimeException(storageException);
		}
		final int index = key.hashCode() & (nMaps - 1);
		try {
			mapPutQueues.get(index).put(new Pair<>(key, value));
		} catch (InterruptedException e) {
			throw new RuntimeException();
		}
		return null;
	}

	@Override
	public V remove(Object key) {
		V v = null;
		for (Map<K, V> map: maps) {
			V v2;
			if ((v2 = map.remove(key)) != null)
				v = v2;
		}
		return v;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		if (terminated) {
			throw new IllegalStateException();
		}
		if (storageException != null) {
			throw new RuntimeException(storageException);
		}
		for (Entry< ? extends K, ? extends V> e: m.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}

	@Override
	public void clear() {
		for (Map<K, V> map: maps) {
			map.clear();
		}
	}

	@Override
	public Set<K> keySet() {
		Set<K> keys = new HashSet<>();
		for (Map<K, V> map: maps) {
			keys.addAll(map.keySet());
		}
		return keys;
	}

	@Override
	public Collection<V> values() {
		Collection<V> values = new ArrayList<>();
		for (Map<K, V> map: maps) {
			values.addAll(map.values());
		}
		return values;
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		if (storageException != null) {
			throw new RuntimeException(storageException);
		}
		Set<Entry<K, V>> entries = new HashSet<>();
		for (Map<K, V> map: maps) {
			for (Entry<K, V> e: map.entrySet()) {
				entries.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
			}
		}
		return entries;
	}

}
