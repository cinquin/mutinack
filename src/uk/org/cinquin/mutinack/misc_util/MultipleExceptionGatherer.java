package uk.org.cinquin.mutinack.misc_util;

import java.util.ArrayList;
import java.util.List;

public class MultipleExceptionGatherer {
	List<Throwable> exceptions = new ArrayList<>();

	public void tryAdd(Runnable r) {
		try {
			r.run();
		} catch (Exception e) {
			exceptions.add(e);
		}
	}
	
	public void throwIfPresent() {
		if (exceptions.size() > 1) {
			throw new MultipleExceptions(exceptions);
		} else if (exceptions.size() > 0) {
			throw new RuntimeException(exceptions.get(0));
		}
	}
}
