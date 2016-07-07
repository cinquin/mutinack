/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2016 Olivier Cinquin
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.org.cinquin.mutinack.distributed;

import java.io.Serializable;
import java.util.Collections;
import java.util.Optional;

import com.healthmarketscience.rmiio.RemoteOutputStream;

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
	public volatile boolean completed;
	public volatile boolean cancelled;
	public EvaluationResult result;
	public long timeSubmitted;
	public long timeGivenToWorker;
	public long timeCompletedOnWorker;
	public long timeReturnedToSubmitter;
	public long timeLastWorkerPing;
	public String workerID;
	public RemoteOutputStream stdoutStream;
	public RemoteOutputStream stderrStream;

	@Override
	public String toString() {
		return "Inputs " +
			Optional.ofNullable(parameters).
				flatMap(p -> Optional.ofNullable(p.inputReads)).
				orElse(Collections.emptyList()) +
			", worker " + workerID;
	}
}
