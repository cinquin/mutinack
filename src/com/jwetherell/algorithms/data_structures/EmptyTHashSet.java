package com.jwetherell.algorithms.data_structures;

import java.util.Collection;

import gnu.trove.set.hash.THashSet;

public class EmptyTHashSet extends THashSet<Object> {
	@Override
	public boolean add(Object obj) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean addAll(Collection<?> collection) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object obj) {
		throw new UnsupportedOperationException();
	};

	@Override
	public boolean removeAll(Collection<?> collection) {
		throw new UnsupportedOperationException();
	};

	@Override
	public Object removeAndGet(Object arg0) {
		throw new UnsupportedOperationException();
	};

}
