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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;

import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.DuplexDisagreement;
import uk.org.cinquin.mutinack.Parameters;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.candidate_sequences.CandidateSequence;
import uk.org.cinquin.mutinack.misc_util.Pair;
import uk.org.cinquin.mutinack.qualities.Quality;

@PersistenceCapable
public class RunResult implements Serializable {
	private static final long serialVersionUID = -5856926265963435703L;

	public @Persistent @Column(length = 10_000) String mutinackVersion;
	public @Persistent Parameters parameters;
	public @Persistent List<ParedDownMutinack> samples;

	public Stream<Pair<@NonNull SequenceLocation, LocationAnalysis>> extractLocationAnalyses() {
		return samples.stream().
			flatMap(sample -> sample.stats.stream()).
			flatMap(stats -> stats.detections.entrySet().stream()).
			map(e -> new Pair<>(e.getKey(), e.getValue()));
	}

	public Stream<CandidateSequence> extractDetections() {
		return extractLocationAnalyses().
			flatMap(e -> e.snd.candidates.stream());
	}

	public Stream<Pair<@NonNull SequenceLocation, @NonNull DuplexDisagreement>> extractDisagreements() {
		return extractLocationAnalyses().
			flatMap(e -> e.snd.disagreements.stream().
			map(dis -> new Pair<>(e.fst, dis)));
	}

	@SuppressWarnings("null")
	public SortedSet<Pair<DuplexDisagreement, Long>> getQ2DisagreementCounts() {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		SortedSet<Pair<DuplexDisagreement, Long>> sortedSet =
			new TreeSet(Pair.pairBySecondSorter);
		extractDisagreements().
			filter(pair -> pair.snd.quality.atLeast(Quality.GOOD)).
			collect(Collectors.groupingBy(p -> p.snd, Collectors.counting())).
			entrySet().stream().
			map(Pair::fromEntry).
			forEach(sortedSet::add);
		return sortedSet;
	}
}
