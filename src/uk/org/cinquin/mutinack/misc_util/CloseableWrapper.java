package uk.org.cinquin.mutinack.misc_util;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Created by olivier on 12/29/16.
 */
public class CloseableWrapper<T> implements Closeable {
	private final T t;
	private final Consumer<T> closer;

	public CloseableWrapper(@Nullable T t, Consumer<T> closer) {
		this.t = t;
		this.closer = closer;
	}

	@Override
	public void close() throws IOException {
		if (t != null) {
			closer.accept(t);
		}
	}
}
