package uk.org.cinquin.mutinack;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;

import contrib.edu.standford.nlp.util.IntervalTree;
import contrib.net.sf.picard.util.IterableAdapter;

public class DuplexITKeeper extends IntervalTree<Integer, DuplexRead> implements DuplexKeeper  {

	public DuplexITKeeper() {
		throw new RuntimeException();
	}
	
	@Override
	public @NonNull Iterable<DuplexRead> getIterable() {
		return new IterableAdapter<>(iterator());
	}
	
	final List<DuplexRead> overlappingDuplexes = new ArrayList<>(10_000);
	
	@Override
	/**
	 * NOT thread-safe because of overlappingDuplexes reuse (the code
	 * is set up this way to minimize object turnover).
	 */
	public @NonNull Iterable<DuplexRead> getOverlapping(DuplexRead d) {
		overlappingDuplexes.clear();
		getOverlapping(d.getInterval(), overlappingDuplexes);
		return new IterableAdapter<>(overlappingDuplexes.iterator());
	}

	@Override
	public boolean supportsMutableDuplexes() {
		return false;
	}

	@Override
	public DuplexRead getAndRemove(DuplexRead d) {
		return super.getAndRemove(d);
	}

}
