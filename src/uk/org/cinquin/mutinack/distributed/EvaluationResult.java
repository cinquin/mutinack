package uk.org.cinquin.mutinack.distributed;

import java.io.Serializable;

public class EvaluationResult implements Serializable {
	private static final long serialVersionUID = 5694739630368631127L;
	public Throwable executionThrowable;
	public Serializable output;
}
