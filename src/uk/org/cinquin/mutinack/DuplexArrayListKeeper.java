package uk.org.cinquin.mutinack;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;

public class DuplexArrayListKeeper extends ArrayList<DuplexRead> implements DuplexKeeper {

	private static final long serialVersionUID = 4777255573227700343L;
	
	public DuplexArrayListKeeper(int initialCapacity) {
		super(initialCapacity);
	}

	@Override
	public @NonNull Iterable<DuplexRead> getOverlapping(DuplexRead d) {
		return getIterable();
	}

	@Override
	public @NonNull Iterable<DuplexRead> getIterable() {
		return this;
	}

	@Override
	public DuplexRead getAndRemove(DuplexRead d) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean supportsMutableDuplexes() {
		return true;
	}

	@Override
	public boolean contains(DuplexRead duplexRead) {
		return super.contains(duplexRead);
	}

}
