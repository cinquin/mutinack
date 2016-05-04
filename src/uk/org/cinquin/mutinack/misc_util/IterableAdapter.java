package uk.org.cinquin.mutinack.misc_util;

import java.util.Iterator;
import java.util.function.Supplier;

public class IterableAdapter<V> implements Iterable<V> {
	
	private Supplier<Iterator<V>> supplier;

	public IterableAdapter(Supplier<Iterator<V>> supplier) {
		this.supplier = supplier;
	}

	@Override
	public Iterator<V> iterator() {
		return supplier.get();
	}

}
