package uk.org.cinquin.mutinack.statistics;

import org.eclipse.jdt.annotation.Nullable;

public interface Traceable {
	/**
	 * Null to turn off tracing.
	 * @param prefix
	 */
	void setPrefix(@Nullable String prefix);
}
