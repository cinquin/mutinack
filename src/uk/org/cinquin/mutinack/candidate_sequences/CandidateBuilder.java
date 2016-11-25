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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.Assert;


public final class CandidateBuilder {
	
	private final @NonNull Map<@NonNull SequenceLocation, @NonNull CandidateSequence> candidates = new THashMap<>();
	private final boolean negativeStrand;

	public CandidateBuilder add(@NonNull CandidateSequence c, @NonNull SequenceLocation l) {
		if (negativeStrand) {
			c.incrementNegativeStrandCount(1);
		} else {
			c.incrementPositiveStrandCount(1);
		}
		@Nullable CandidateSequence previousCandidate = candidates.get(l);
		Assert.isNull(previousCandidate);
		candidates.put(l, c);
		return this;
	}
	
	public @NonNull Map<@NonNull SequenceLocation, @NonNull CandidateSequence> build() {
		return candidates;
	}
	
	public CandidateBuilder(boolean negativeStrand) {
		this.negativeStrand = negativeStrand;
	}
}
