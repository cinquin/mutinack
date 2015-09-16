/**
 * Mutinack mutation detection program.
 * Copyright (C) 2014-2015 Olivier Cinquin
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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import gnu.trove.map.hash.THashMap;
import uk.org.cinquin.mutinack.MutationType;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.DebugControl;
import uk.org.cinquin.mutinack.misc_util.Util;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

import java.util.Map;


public final class CandidateBuilder {
	
	private final @NonNull Map<@NonNull SequenceLocation, @NonNull CandidateSequence> candidates = new THashMap<>();
	final private boolean negativeStrand;

	public CandidateBuilder add(@NonNull CandidateSequence c, @NonNull SequenceLocation l) {
		c.setNegativeStrand(negativeStrand);
		@Nullable CandidateSequence previousCandidate = Util.nullableify(candidates.get(l));
		
		if (previousCandidate == null) {
			candidates.put(l, c);
		} else {
			CandidateSequenceCombo combo;
			if (previousCandidate instanceof CandidateSequenceCombo) {
				throw new AssertionFailedException("Merging new candidate " + c + "to combo " + this);
			} else {
				combo = new CandidateSequenceCombo(c.getOwningAnalyzer(), l, null, Integer.MAX_VALUE);
				combo.mergeCandidate(previousCandidate);
				combo.mergeCandidate(c);
				//TODO Ad-hoc cases below should be streamlined, and ideally just all handled by
				//combo.mergeCandidate
				if (previousCandidate.getMutationType() == MutationType.INSERTION) {
					combo.readName = previousCandidate.readName;
					combo.getMutableConcurringReads().putAll(previousCandidate.getNonMutableConcurringReads());
					combo.acceptLigSiteDistance(Math.max(c.minDistanceToLigSite, previousCandidate.minDistanceToLigSite));
				} else if (c.getMutationType() == MutationType.INSERTION) {
					combo.readName = c.readName;
					combo.getMutableConcurringReads().putAll(c.getNonMutableConcurringReads());
					combo.acceptLigSiteDistance(Math.max(c.minDistanceToLigSite, previousCandidate.minDistanceToLigSite));
				} else {
					combo.acceptLigSiteDistance(c.minDistanceToLigSite);
					combo.acceptLigSiteDistance(previousCandidate.minDistanceToLigSite);
					combo.getMutableConcurringReads().putAll(previousCandidate.getNonMutableConcurringReads());
					combo.getMutableConcurringReads().putAll(c.getNonMutableConcurringReads());					
				}
				if (DebugControl.NONTRIVIAL_ASSERTIONS && c.getOwningAnalyzer() != previousCandidate.getOwningAnalyzer()) {
					throw new AssertionFailedException();
				}
				candidates.put(l, combo);
			}
		}
		return this;
	}
	
	public @NonNull Map<@NonNull SequenceLocation, @NonNull CandidateSequence> build() {
		return candidates;
	}
	
	public CandidateBuilder(boolean negativeStrand) {
		this.negativeStrand = negativeStrand;
	}
}
