package uk.org.cinquin.mutinack.misc_util.collections;

import javax.annotation.concurrent.NotThreadSafe;

import gnu.trove.set.hash.THashSet;

@NotThreadSafe
public class InterningSet<T> extends THashSet<T> {
	public InterningSet(int i) {
		super(i, 0.2f);
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