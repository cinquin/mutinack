package uk.org.cinquin.mutinack.misc_util.collections;

import gnu.trove.set.hash.THashSet;

public class InterningSet<T> extends THashSet<T> {
	public InterningSet(int i) {
		super(i);
	}

	public InterningSet() {
		super();
	}

	public T intern(T l) {
		T previous = get(l);
		if (previous != null) {
			return previous;
		}
		add(l);
		return l;
	}
}