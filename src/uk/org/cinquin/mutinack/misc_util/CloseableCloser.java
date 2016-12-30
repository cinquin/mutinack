package uk.org.cinquin.mutinack.misc_util;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Created by olivier on 12/29/16.
 */
public class CloseableCloser implements Closeable {
	private final List<Closeable> closeables = new ArrayList<>();

	public void add(@Nullable Closeable c) {
		if (c != null) {
			closeables.add(c);
		}
	}

	public void clear() {
		closeables.clear();
	}

	@Override
	public void close() throws IOException {
		MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
		for (Closeable c: closeables) {
			gatherer.tryAdd(c::close);
		}
		gatherer.throwIfPresent();
	}
}
