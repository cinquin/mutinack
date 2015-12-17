package uk.org.cinquin.mutinack;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;

import contrib.net.sf.picard.util.IterableAdapter;

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
		return new IterableAdapter<>(iterator());
	}

}
