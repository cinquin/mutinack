package uk.org.cinquin.mutinack.misc_util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Created by olivier on 12/29/16.
 */
public class CloseableListWrapper<T extends Closeable> implements Closeable {
	private final List<T> list;

	public CloseableListWrapper(@Nullable List<T> list) {
		if (list != null) {
			this.list = list;
		} else {
			this.list = Collections.emptyList();
		}
	}

	@Override
	public void close() throws IOException {
		list.forEach(t -> {
			try {
				t.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}
}
