package uk.org.cinquin.mutinack.distributed;

import java.io.Serializable;

import uk.org.cinquin.mutinack.Parameters;

public final class Job implements Serializable {
	
	private static final long serialVersionUID = 2926806228113642260L;
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result1 = 1;
		result1 = prime * result1 + ((parameters == null) ? 0 : parameters.hashCode());
		result1 = prime * result1 + ((pathToWorkDir == null) ? 0 : pathToWorkDir.hashCode());
		return result1;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Job other = (Job) obj;
		if (parameters == null) {
			if (other.parameters != null)
				return false;
		} else if (!parameters.equals(other.parameters))
			return false;
		if (pathToWorkDir == null) {
			if (other.pathToWorkDir != null)
				return false;
		} else if (!pathToWorkDir.equals(other.pathToWorkDir))
			return false;
		return true;
	}
	public Parameters parameters;
	public String pathToWorkDir;
	public boolean completed;
	public EvaluationResult result;
	public long timeSubmitted;
	public long timeGivenToWorker;
	public long timeCompletedOnWorker;
	public long timeReturnedToSubmitter;
	public String workerID;
}
