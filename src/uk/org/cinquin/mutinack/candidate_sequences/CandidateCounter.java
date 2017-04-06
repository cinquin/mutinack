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

import java.util.List;

import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.eclipse.jdt.annotation.NonNull;

import uk.org.cinquin.mutinack.ExtendedSAMRecord;
import uk.org.cinquin.mutinack.SequenceLocation;
import uk.org.cinquin.mutinack.misc_util.DebugLogControl;
import uk.org.cinquin.mutinack.misc_util.exceptions.AssertionFailedException;

public final class CandidateCounter {
	private final @NonNull ImmutableSet<@NonNull CandidateSequence> candidates;
	private List<@NonNull ExtendedSAMRecord> records;
	private final @NonNull SequenceLocation location;
	public int minBasePhredScore = 0;
	public final @NonNull UnifiedMap<@NonNull CandidateSequence, @NonNull CandidateDuplexEval>
		candidateCounts;
	public final UnifiedSet<ExtendedSAMRecord> keptRecords;

	public long nPhreds, sumPhreds;

	public CandidateCounter(@NonNull ImmutableSet<@NonNull CandidateSequence> candidates,
			@NonNull SequenceLocation location) {
		this.candidates = candidates;
		this.location = location;
		keptRecords = new UnifiedSet<>();
		candidateCounts = new UnifiedMap<>(5);
	}

	public void reset() {
		keptRecords.clear();
		candidateCounts.clear();
	}

	public void compute() {
		reset();
		if (DebugLogControl.NONTRIVIAL_ASSERTIONS) {
			if (!keptRecords.isEmpty() || !candidateCounts.isEmpty()) {
				throw new AssertionFailedException();
			}
		}
		sumPhreds = 0;
		nPhreds = 0;
		//Avoid default interface forEach implementation
		candidates.each(candidate -> {
			for (int i = records.size() - 1; i >= 0; --i) {
				final ExtendedSAMRecord r = records.get(i);
				if (r.isOpticalDuplicate()) {
					continue;
				}
				final int ligSiteDistance = candidate.getNonMutableConcurringReads().get(r);
				if (ligSiteDistance != candidate.getNonMutableConcurringReads().getNoEntryValue()) {
					byte phredScore = r.basePhredScores.get(location);
					if (phredScore != ExtendedSAMRecord.PHRED_NO_ENTRY) {
						sumPhreds += phredScore;
						nPhreds++;
					}
					if (minBasePhredScore > 0) {
						if (phredScore != ExtendedSAMRecord.PHRED_NO_ENTRY && phredScore < minBasePhredScore) {
							continue;
						}
					}
					if (!keptRecords.add(r)) {
						throw new AssertionFailedException();
					}
					final CandidateDuplexEval eval = candidateCounts.computeIfAbsent(candidate,
						key -> new CandidateDuplexEval(candidate));
					eval.count++;
					if (ligSiteDistance > eval.maxDistanceToLigSite) {
						eval.maxDistanceToLigSite = ligSiteDistance;
					}
				}
			}//End loop over records
		}//End loop over candidates
		);
	}

	public void setRecords(@NonNull List<@NonNull ExtendedSAMRecord> records) {
		this.records = records;
	}

}
