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

package uk.org.cinquin.mutinack.output;

import java.io.Serializable;
import java.util.List;
import java.util.stream.Stream;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;

@PersistenceCapable
public class RunResult implements Serializable {
	private static final long serialVersionUID = -5856926265963435703L;

	public @Persistent @Column(length = 10_000) String mutinackVersion;
	public @Persistent Parameters parameters;
	public @Persistent List<ParedDownMutinack> samples;

	public Stream<CandidateSequence> extractDetections() {
		return samples.stream().
			flatMap(sample -> sample.stats.stream()).
			flatMap(stats -> stats.detections.entrySet().stream()).
			flatMap(e -> e.getValue().candidates.stream());
	}
}
