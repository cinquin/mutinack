package uk.org.cinquin.mutinack;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.misc_util.TIntObjectListHashMap;

public class DuplexHashMapKeeper extends TIntObjectListHashMap<DuplexRead> implements DuplexKeeper {

	@Override
	public @NonNull Iterable<DuplexRead> getOverlapping(DuplexRead d) {
		return getList(d.position0);
	}

	@Override
	public boolean add(DuplexRead d) {
		return add(d.position0, d);
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
		return containsValue(duplexRead);
	}

}
