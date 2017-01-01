package uk.org.cinquin.mutinack.misc_util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Created by olivier on 12/29/16.
 */
public class CloseableListWrapper<T extends Closeable> implements Closeable {
	private final @NonNull List<@Nullable T> list;

	public CloseableListWrapper(@Nullable List<@Nullable T> list) {
		if (list != null) {
			this.list = list;
		} else {
			this.list = Collections.emptyList();
		}
	}

	@Override
	public void close() throws IOException {
		MultipleExceptionGatherer gatherer = new MultipleExceptionGatherer();
		list.forEach(t -> {
			if (t == null) {
				return;
			}
			gatherer.tryAdd(t::close);
		});
		gatherer.throwIfPresent();
	}
}
