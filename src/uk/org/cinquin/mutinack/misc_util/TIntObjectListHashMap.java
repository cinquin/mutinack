package uk.org.cinquin.mutinack.misc_util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import gnu.trove.map.hash.TIntObjectHashMap;

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
