package uk.org.cinquin.mutinack;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

public class DuplexKeeperCollectionWrapper implements DuplexKeeper {

	private final Collection<DuplexRead> wrapped;

	public DuplexKeeperCollectionWrapper(Collection<DuplexRead> wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public int size() {
		return wrapped.size();
	}

	@Override
	public boolean isEmpty() {
		return wrapped.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return wrapped.contains(o);
	}

	@Override
	public Iterator<DuplexRead> iterator() {
		return wrapped.iterator();
	}

	@Override
	public Object[] toArray() {
		return wrapped.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return wrapped.toArray(a);
	}

	@Override
	public boolean add(DuplexRead e) {
		return wrapped.add(e);
	}

	@Override
	public boolean remove(Object o) {
		return wrapped.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return wrapped.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends DuplexRead> c) {
		return wrapped.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return wrapped.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return wrapped.retainAll(c);
	}

	@Override
	public void clear() {
		wrapped.clear();
	}

	@Override
	public @NonNull Collection<DuplexRead> getOverlapping(DuplexRead d) {
		return this;
	}

	@Override
	public @NonNull List<DuplexRead> getOverlappingWithSlop(DuplexRead d, int shift, int slop) {
		throw new UnsupportedOperationException();
	}


}
