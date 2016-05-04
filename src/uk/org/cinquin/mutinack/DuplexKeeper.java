package uk.org.cinquin.mutinack;

import org.eclipse.jdt.annotation.NonNull;

public interface DuplexKeeper {
	@NonNull Iterable<DuplexRead> getOverlapping(DuplexRead d);
	@NonNull Iterable<DuplexRead> getIterable();
	boolean add(DuplexRead d);
	DuplexRead getAndRemove(DuplexRead d);
	boolean supportsMutableDuplexes();
	boolean contains(DuplexRead duplexRead);
}
