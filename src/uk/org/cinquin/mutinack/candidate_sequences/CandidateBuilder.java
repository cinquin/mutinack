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
package uk.org.cinquin.mutinack.candidate_sequences;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.annotation.CheckReturnValue;

import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.features.GenomeFeatureTester;


public final class CandidateBuilder {

	private final Map<@NonNull SequenceLocation, @NonNull CandidateSequence> candidates;
	private final BiFunction<@NonNull SequenceLocation, @NonNull CandidateSequence, @NonNull CandidateSequence> function;
	private final boolean negativeStrand;
	private final @Nullable GenomeFeatureTester codingStrandTester;

	@CheckReturnValue
	@SuppressWarnings("ReferenceEquality")
	public CandidateSequence add(@NonNull CandidateSequence c, @NonNull SequenceLocation l) {
		if (negativeStrand) {
			c.incrementNegativeStrandCount(1);
		} else {
			c.incrementPositiveStrandCount(1);
		}
		final CandidateSequence returned;
		if (function != null) {
			returned = function.apply(l, c);
		} else {
			@Nullable CandidateSequence previousCandidate = candidates.get(l);
			if (previousCandidate != null) {
				if (previousCandidate.getMutationType() == MutationType.WILDTYPE &&
						c.getMutationType() == MutationType.REARRANGEMENT) {
					//Nothing special to do; will replace wildtype by rearrangement
				} else if (previousCandidate.getMutationType() == MutationType.REARRANGEMENT &&
						c.getMutationType() == MutationType.WILDTYPE) {
					return previousCandidate;
				} else
					throw new IllegalStateException("Trying to add " + c + " at " + l + " but " +
						previousCandidate + " already inserted");
			}
			candidates.put(l, c);
			returned = c;
		}
		//Reference equality check on line below is as intended
		if (returned == c && codingStrandTester != null) {//This is the first candidate inserted at this location
			@SuppressWarnings("null")
			final Boolean negativeCodingStrand = Optional.ofNullable(codingStrandTester).
				flatMap(tester -> tester.getNegativeStrand(l)).
				orElse(null);

			c.setNegativeCodingStrand(negativeCodingStrand);
		}
		return returned;
	}

	public @NonNull Map<@NonNull SequenceLocation, @NonNull CandidateSequence> build() {
		return Objects.requireNonNull(candidates);
	}

	public CandidateBuilder(
			boolean negativeStrand,
			@Nullable GenomeFeatureTester codingStrandTester,
			BiFunction<@NonNull SequenceLocation, @NonNull CandidateSequence, @NonNull CandidateSequence> consumer) {
		this.negativeStrand = negativeStrand;
		this.function = consumer;
		this.codingStrandTester = codingStrandTester;
		if (consumer == null) {
			candidates = new UnifiedMap<>(300);
		} else {
			candidates = null;
		}
	}
}
