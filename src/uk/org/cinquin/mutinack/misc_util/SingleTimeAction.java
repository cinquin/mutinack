package uk.org.cinquin.mutinack.misc_util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public class SingleTimeAction<T> implements Consumer<T> {

	private final ConcurrentMap<T, T> printed = new ConcurrentHashMap<>();
	private final Consumer<T> action;

	public SingleTimeAction(Consumer<T> action) {
		this.action = action;
	}

	@Override
	public void accept(T t) {
		printed.computeIfAbsent(t, k -> {
			action.accept(k);
			return k;
		});
	}
}
